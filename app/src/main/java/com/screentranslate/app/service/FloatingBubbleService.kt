package com.screentranslate.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.screentranslate.app.R
import com.screentranslate.app.engine.GeminiTranslator
import com.screentranslate.app.engine.MLKitTranslator
import com.screentranslate.app.engine.OCRManager
import com.screentranslate.app.engine.QuotaExceededException
import com.screentranslate.app.engine.QuotaManager
import com.screentranslate.app.engine.TranslationEngine
import com.screentranslate.app.model.EngineType
import com.screentranslate.app.model.PresentationStyle
import com.screentranslate.app.model.SupportedLanguage
import com.screentranslate.app.model.TranslationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var overlayView: TranslationOverlayView? = null

    private var captureService: ScreenCaptureService? = null
    private var isBound = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var liveJob: Job? = null

    private val ocrManager by lazy { OCRManager() }
    private val mlKitTranslator: TranslationEngine by lazy { MLKitTranslator() }
    private val quotaManager by lazy { QuotaManager(this) }

    // State settings
    private var currentMode = TranslationMode.SNAP
    private var presentationStyle = PresentationStyle.IN_PLACE
    private var sourceLanguage = SupportedLanguage.ENGLISH
    private var selectedEngine = EngineType.ML_KIT_OFFLINE
    private var isTranslating = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ScreenCaptureService.LocalBinder
            captureService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            captureService = null
            isBound = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        bindService(
            Intent(this, ScreenCaptureService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )

        createOverlayView()
        createFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val engineName = it.getStringExtra(EXTRA_ENGINE)
            if (engineName != null) {
                selectedEngine = try {
                    EngineType.valueOf(engineName)
                } catch (e: Exception) {
                    EngineType.ML_KIT_OFFLINE
                }
            }
        }
        return START_STICKY
    }

    private fun createOverlayView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = TranslationOverlayView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                // Tap overlay to clear translation
                clear()
            }
        }
        windowManager.addView(overlayView, overlayParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            val size = (56 * resources.displayMetrics.density).toInt()
            layoutParams = WindowManager.LayoutParams(size, size)
            elevation = 16f
        }
        bubbleView = imageView

        // Touch listener for dragging and snapping to edge
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        imageView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }

                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onBubbleClicked()
                    } else {
                        // Snap to nearest screen edge (left or right)
                        val screenWidth = resources.displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        bubbleParams.x = if (bubbleParams.x < middle) 16 else screenWidth - imageView.width - 16
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun onBubbleClicked() {
        if (currentMode == TranslationMode.LIVE_AUTO) {
            if (liveJob?.isActive == true) {
                stopLiveMode()
                overlayView?.clear()
            } else {
                startLiveMode()
            }
        } else {
            performTranslate()
        }
    }

    fun setTranslationMode(mode: TranslationMode) {
        this.currentMode = mode
        if (mode == TranslationMode.LIVE_AUTO) {
            startLiveMode()
        } else {
            stopLiveMode()
        }
    }

    fun setPresentationStyle(style: PresentationStyle) {
        this.presentationStyle = style
    }

    fun setSourceLanguage(lang: SupportedLanguage) {
        this.sourceLanguage = lang
    }

    fun setEngineType(engine: EngineType) {
        this.selectedEngine = engine
    }

    private fun startLiveMode() {
        liveJob?.cancel()
        liveJob = serviceScope.launch {
            while (isActive) {
                performTranslate()
                delay(1500) // Capture frame every 1.5s for battery & CPU efficiency
            }
        }
    }

    private fun stopLiveMode() {
        liveJob?.cancel()
        liveJob = null
    }

    private fun performTranslate() {
        if (isTranslating || captureService == null) return

        serviceScope.launch {
            isTranslating = true
            try {
                // 1. Capture screen frame into temporary in-memory Bitmap
                val bitmap = captureService?.captureScreen()
                if (bitmap != null) {
                    // 2. Perform OCR via ML Kit
                    val recognizedItems = ocrManager.recognizeText(bitmap, sourceLanguage)

                    // 3. Translate recognized texts using chosen engine
                    val translatedItems = if (selectedEngine == EngineType.GEMINI_FLASH_LITE) {
                        val apiKey = quotaManager.getApiKey()
                        if (apiKey.isNotBlank()) {
                            try {
                                val gemini = GeminiTranslator(apiKey, quotaManager)
                                gemini.translateItems(recognizedItems, sourceLanguage)
                            } catch (e: QuotaExceededException) {
                                // Quota exceeded! Show alert and fall back gracefully to ML Kit
                                Toast.makeText(
                                    this@FloatingBubbleService,
                                    "⚠️ โควต้า Gemini ฟรีวันนี้หมดแล้ว! สลับไปใช้ Google ML Kit แทน",
                                    Toast.LENGTH_LONG
                                ).show()
                                mlKitTranslator.translateItems(recognizedItems, sourceLanguage)
                            }
                        } else {
                            mlKitTranslator.translateItems(recognizedItems, sourceLanguage)
                        }
                    } else {
                        mlKitTranslator.translateItems(recognizedItems, sourceLanguage)
                    }

                    // 4. Update overlay UI
                    overlayView?.updateResults(translatedItems, presentationStyle)

                    // Recycle bitmap immediately
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isTranslating = false
            }
        }
    }

    override fun onDestroy() {
        stopLiveMode()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        bubbleView?.let { windowManager.removeView(it) }
        overlayView?.let { windowManager.removeView(it) }
        ocrManager.close()
        mlKitTranslator.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ENGINE = "extra_engine"
    }
}
