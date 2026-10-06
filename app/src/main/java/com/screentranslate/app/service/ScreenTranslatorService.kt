package com.screentranslate.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.screentranslate.app.MainActivity
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
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Unified, crash-proof Foreground Service combining Screen Capture and Floating Bubble Overlay.
 */
class ScreenTranslatorService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var overlayView: TranslationOverlayView? = null

    // MediaProjection components
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 320

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
    private var isShowingTranslation = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupDisplayMetrics()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. MUST start foreground notification first
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Parse Intent Extras safely
        intent?.let {
            val engineName = it.getStringExtra(EXTRA_ENGINE)
            if (engineName != null) {
                selectedEngine = try {
                    EngineType.valueOf(engineName)
                } catch (e: Exception) {
                    EngineType.ML_KIT_OFFLINE
                }
            }

            val resultCode = it.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_DATA) as? Intent
            }

            if (resultCode != 0 && data != null && mediaProjection == null) {
                try {
                    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpManager.getMediaProjection(resultCode, data)
                    initVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Create Views if not already created
        if (overlayView == null) {
            createOverlayView()
        }
        if (bubbleView == null) {
            createFloatingBubble()
        }

        return START_NOT_STICKY
    }

    private fun setupDisplayMetrics() {
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
            screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 1920
            screenDensity = if (metrics.densityDpi > 0) metrics.densityDpi else 320
        } catch (e: Exception) {
            screenWidth = 1080
            screenHeight = 1920
            screenDensity = 320
        }
    }

    private fun initVirtualDisplay() {
        if (mediaProjection == null) return
        try {
            imageReader?.close()
            virtualDisplay?.release()

            // Mandatory requirement for Android 14: registerCallback before createVirtualDisplay
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    try {
                        virtualDisplay?.release()
                    } catch (e: Exception) {}
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenTranslatorCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * CRITICAL FIX: The full screen overlay MUST have FLAG_NOT_TOUCHABLE
     * so that user touch events pass through to apps/games underneath!
     */
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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // << PASSES ALL TOUCHES THROUGH!
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        overlayView = TranslationOverlayView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        try {
            windowManager.addView(overlayView, overlayParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val size = (56 * resources.displayMetrics.density).toInt()
        val bubbleParams = WindowManager.LayoutParams(
            size,
            size,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 350
        }

        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            elevation = 20f
        }
        bubbleView = imageView

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
                    try {
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onBubbleClicked()
                    } else {
                        // Snap to nearest edge
                        val screenW = resources.displayMetrics.widthPixels
                        val middle = screenW / 2
                        bubbleParams.x = if (bubbleParams.x < middle) 16 else screenW - imageView.width - 16
                        try {
                            windowManager.updateViewLayout(bubbleView, bubbleParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onBubbleClicked() {
        if (isShowingTranslation) {
            // If already showing translation, clear overlay
            overlayView?.clear()
            isShowingTranslation = false
            return
        }

        if (currentMode == TranslationMode.LIVE_AUTO) {
            if (liveJob?.isActive == true) {
                stopLiveMode()
                overlayView?.clear()
                isShowingTranslation = false
            } else {
                startLiveMode()
            }
        } else {
            performTranslate()
        }
    }

    private fun startLiveMode() {
        liveJob?.cancel()
        liveJob = serviceScope.launch {
            while (isActive) {
                performTranslate()
                delay(1500)
            }
        }
    }

    private fun stopLiveMode() {
        liveJob?.cancel()
        liveJob = null
    }

    private suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.Default) {
        var image: Image? = null
        try {
            image = imageReader?.acquireLatestImage()
            if (image == null) return@withContext null

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding != 0) {
                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cleanBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            image?.close()
        }
    }

    private fun performTranslate() {
        if (isTranslating) return

        serviceScope.launch {
            isTranslating = true
            try {
                val bitmap = captureScreen()
                if (bitmap != null) {
                    val recognizedItems = ocrManager.recognizeText(bitmap, sourceLanguage)

                    if (recognizedItems.isEmpty()) {
                        Toast.makeText(this@ScreenTranslatorService, "ไม่พบข้อความบนหน้าจอ", Toast.LENGTH_SHORT).show()
                    } else {
                        val translatedItems = if (selectedEngine == EngineType.GEMINI_FLASH_LITE) {
                            val apiKey = quotaManager.getApiKey()
                            if (apiKey.isNotBlank()) {
                                try {
                                    val gemini = GeminiTranslator(apiKey, quotaManager)
                                    gemini.translateItems(recognizedItems, sourceLanguage)
                                } catch (e: QuotaExceededException) {
                                    Toast.makeText(
                                        this@ScreenTranslatorService,
                                        "⚠️ โควต้า Gemini เต็ม! สลับไปใช้ Google ML Kit แทน",
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

                        overlayView?.updateResults(translatedItems, presentationStyle)
                        isShowingTranslation = true
                    }

                    bitmap.recycle()
                } else {
                    Toast.makeText(this@ScreenTranslatorService, "ไม่สามารถจับภาพหน้าจอได้ กรุณาลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isTranslating = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopLiveMode()
        try {
            bubbleView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {}
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {}
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {}
        ocrManager.close()
        mlKitTranslator.close()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "screen_translator_channel"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_ENGINE = "extra_engine"
    }
}
