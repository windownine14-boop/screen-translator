package com.screentranslate.app.model

import android.graphics.Rect

/**
 * Represent a detected text block with its bounding box and translation.
 */
data class RecognizedTextItem(
    val originalText: String,
    var translatedText: String = "",
    val boundingBox: Rect,
    val lineCount: Int = 1
)

/**
 * Real-time Quota Information for Gemini Free Tier.
 */
data class QuotaStatus(
    val usedToday: Int,
    val maxDaily: Int = 1500,
    val remaining: Int,
    val resetsInHours: Int,
    val resetsInMinutes: Int
)

/**
 * Translation trigger modes.
 */
enum class TranslationMode(val title: String, val description: String) {
    SNAP("โหมดแตะแปล (Snap Mode)", "กดปุ่มลอย 1 ครั้งเพื่อแปลหน้านั้น ประหยัดแบตเตอรี่"),
    LIVE_AUTO("โหมดแปลสด (Live Auto)", "ระบบตรวจจับและอัปเดตคำแปลอัตโนมัติแบบเรียลไทม์")
}

/**
 * Visual presentation style of the translation overlay.
 */
enum class PresentationStyle(val title: String, val description: String) {
    IN_PLACE("วาดทับตำแหน่งเดิม (In-Place)", "แปะทับกล่องข้อความเดิม เหมาะกับเกมและมังงะ"),
    SUBTITLE("แถบซับไตเติล (Subtitle Bar)", "แสดงเป็นแถบด้านล่าง เหมาะกับดูวิดีโอหรือคัตซีน")
}

/**
 * Supported Source Languages.
 */
enum class SupportedLanguage(
    val title: String,
    val mlKitCode: String,
    val ocrScript: String
) {
    AUTO("ตรวจจับอัตโนมัติ (Auto)", "auto", "latin"),
    ENGLISH("ภาษาอังกฤษ (English)", "en", "latin"),
    JAPANESE("ภาษาญี่ปุ่น (Japanese)", "ja", "japanese"),
    CHINESE("ภาษาจีน (Chinese)", "zh", "chinese"),
    KOREAN("ภาษาเกาหลี (Korean)", "ko", "korean")
}

/**
 * AI Engine choice.
 */
enum class EngineType(val title: String, val isOffline: Boolean) {
    ML_KIT_OFFLINE("Google ML Kit (ออฟไลน์ 100% ฟรีและเร็ว)", true),
    GEMINI_FLASH_LITE("Google Gemini 3.5 Flash-Lite (แปลสละสลวยพิเศษ)", false)
}
