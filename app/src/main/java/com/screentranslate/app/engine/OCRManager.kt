package com.screentranslate.app.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.screentranslate.app.model.RecognizedTextItem
import com.screentranslate.app.model.SupportedLanguage
import kotlinx.coroutines.tasks.await

class OCRManager {

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    /**
     * Get the appropriate recognizer based on selected language.
     */
    private fun getRecognizer(language: SupportedLanguage): TextRecognizer {
        return when (language) {
            SupportedLanguage.JAPANESE -> japaneseRecognizer
            SupportedLanguage.CHINESE -> chineseRecognizer
            SupportedLanguage.KOREAN -> koreanRecognizer
            else -> latinRecognizer
        }
    }

    /**
     * Process bitmap and extract text blocks with bounding boxes.
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        language: SupportedLanguage = SupportedLanguage.ENGLISH
    ): List<RecognizedTextItem> {
        val recognizer = getRecognizer(language)
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result: Text = recognizer.process(image).await()
            val items = mutableListOf<RecognizedTextItem>()

            for (block in result.textBlocks) {
                // Group by meaningful lines or blocks
                val blockText = block.text.trim()
                val box = block.boundingBox ?: Rect(0, 0, 0, 0)
                val lines = block.lines
                val lineCount = maxOf(lines.size, 1)

                if (blockText.isNotEmpty()) {
                    items.add(
                        RecognizedTextItem(
                            originalText = blockText,
                            translatedText = "",
                            boundingBox = box,
                            lineCount = lineCount
                        )
                    )
                }
            }
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun close() {
        latinRecognizer.close()
        japaneseRecognizer.close()
        chineseRecognizer.close()
        koreanRecognizer.close()
    }
}
