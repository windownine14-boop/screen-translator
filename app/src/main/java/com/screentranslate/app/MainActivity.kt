package com.screentranslate.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.screentranslate.app.engine.QuotaManager
import com.screentranslate.app.model.EngineType
import com.screentranslate.app.model.QuotaStatus
import com.screentranslate.app.service.ScreenTranslatorService
import com.screentranslate.app.ui.MainScreen
import com.screentranslate.app.ui.theme.ScreenTranslatorTheme

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasCapturePermission by mutableStateOf(false)

    private val quotaManager by lazy { QuotaManager(this) }
    private var currentQuotaStatus by mutableStateOf(
        QuotaStatus(usedToday = 0, remaining = 1500, resetsInHours = 0, resetsInMinutes = 0)
    )
    private var currentApiKey by mutableStateOf("")
    private var activeEngine = EngineType.GEMINI_FLASH_LITE

    private var captureResultCode: Int = 0
    private var captureData: Intent? = null

    // Register Activity Result for Overlay Permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    // Register Activity Result for MediaProjection Screen Capture
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            captureResultCode = result.resultCode
            captureData = result.data
            hasCapturePermission = true
            Toast.makeText(this, "อนุญาตการดึงภาพหน้าจอเรียบร้อย", Toast.LENGTH_SHORT).show()
            startScreenServices()
        } else {
            hasCapturePermission = false
            Toast.makeText(this, "จำเป็นต้องอนุญาตการบันทึกภาพหน้าจอเพื่อแปลภาษา", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        refreshQuota()

        setContent {
            ScreenTranslatorTheme {
                MainScreen(
                    isServiceRunning = isServiceRunning,
                    hasOverlayPermission = hasOverlayPermission,
                    hasCapturePermission = hasCapturePermission,
                    quotaStatus = currentQuotaStatus,
                    currentApiKey = currentApiKey,
                    onSaveApiKey = { newKey ->
                        quotaManager.setApiKey(newKey)
                        currentApiKey = newKey
                        Toast.makeText(this, "บันทึก API Key สำเร็จ!", Toast.LENGTH_SHORT).show()
                    },
                    onToggleService = { enable, engine ->
                        activeEngine = engine
                        if (enable) {
                            startTranslationWorkflow()
                        } else {
                            stopScreenServices()
                        }
                    },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestCapturePermission = { requestCapturePermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        refreshQuota()
    }

    private fun refreshQuota() {
        currentQuotaStatus = quotaManager.getQuotaStatus()
        currentApiKey = quotaManager.getApiKey()
    }

    private fun checkPermissions() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestCapturePermission() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startTranslationWorkflow() {
        if (!hasOverlayPermission) {
            Toast.makeText(this, "กรุณาเปิดสิทธิ์ 'แสดงทับแอปอื่น' ก่อนเริ่มใช้งาน", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        if (!hasCapturePermission || captureData == null) {
            requestCapturePermission()
            return
        }

        startScreenServices()
    }

    private fun startScreenServices() {
        if (captureData == null) return

        // Start unified ScreenTranslatorService
        val serviceIntent = Intent(this, com.screentranslate.app.service.ScreenTranslatorService::class.java).apply {
            putExtra(com.screentranslate.app.service.ScreenTranslatorService.EXTRA_RESULT_CODE, captureResultCode)
            putExtra(com.screentranslate.app.service.ScreenTranslatorService.EXTRA_DATA, captureData)
            putExtra(com.screentranslate.app.service.ScreenTranslatorService.EXTRA_ENGINE, activeEngine.name)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        isServiceRunning = true
        Toast.makeText(this, "เริ่มการทำงานปุ่มลอยแล้ว! กดปุ่มโฮมเพื่อทดสอบ", Toast.LENGTH_LONG).show()
    }

    private fun stopScreenServices() {
        stopService(Intent(this, com.screentranslate.app.service.ScreenTranslatorService::class.java))
        isServiceRunning = false
        Toast.makeText(this, "ปิดการทำงานเรียบร้อย", Toast.LENGTH_SHORT).show()
    }
}
