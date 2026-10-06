package com.screentranslate.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.screentranslate.app.model.PresentationStyle
import com.screentranslate.app.model.RecognizedTextItem

class TranslationOverlayView(context: Context) : View(context) {

    private var items: List<RecognizedTextItem> = emptyList()
    private var presentationStyle: PresentationStyle = PresentationStyle.IN_PLACE
    private var isVisibleTranslation = true

    // Paints for In-Place Drawing
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E60F172A") // Deep slate with 90% opacity
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8038BDF8") // Light cyan border
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }

    // Paints for Subtitle Bar Drawing
    private val subtitleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2000000") // Black with 95% opacity
        style = Paint.Style.FILL
    }

    private val subtitleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EAB308") // Amber border
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val subtitleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun updateResults(
        newItems: List<RecognizedTextItem>,
        style: PresentationStyle = PresentationStyle.IN_PLACE
    ) {
        this.items = newItems
        this.presentationStyle = style
        this.isVisibleTranslation = true
        invalidate()
    }

    fun toggleVisibility(): Boolean {
        isVisibleTranslation = !isVisibleTranslation
        invalidate()
        return isVisibleTranslation
    }

    fun clear() {
        this.items = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isVisibleTranslation || items.isEmpty()) return

        when (presentationStyle) {
            PresentationStyle.IN_PLACE -> drawInPlaceOverlay(canvas)
            PresentationStyle.SUBTITLE -> drawSubtitleOverlay(canvas)
        }
    }

    private fun drawInPlaceOverlay(canvas: Canvas) {
        val cornerRadius = 12f
        val padding = 8f

        for (item in items) {
            val text = item.translatedText.ifEmpty { item.originalText }
            if (text.isBlank()) continue

            val box = item.boundingBox
            val rectF = RectF(
                (box.left - padding).coerceAtLeast(0f),
                (box.top - padding).coerceAtLeast(0f),
                (box.right + padding).coerceAtMost(width.toFloat()),
                (box.bottom + padding).coerceAtMost(height.toFloat())
            )

            // Draw background card
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

            // Calculate optimal font size to fit inside the box
            adjustTextSize(rectF.width() - (padding * 2), rectF.height() - (padding * 2), text)

            // Draw text centered vertically and horizontally
            val fontMetrics = textPaint.fontMetrics
            val textHeight = fontMetrics.descent - fontMetrics.ascent
            val x = rectF.left + padding
            val y = rectF.centerY() + (textHeight / 2) - fontMetrics.descent

            canvas.drawText(text, x, y, textPaint)
        }
    }

    private fun drawSubtitleOverlay(canvas: Canvas) {
        // Collect all translated text into a single cohesive paragraph
        val fullText = items.joinToString(" ") { it.translatedText.ifEmpty { it.originalText } }
        if (fullText.isBlank()) return

        val margin = 40f
        val boxHeight = 220f
        val rectF = RectF(
            margin,
            height - boxHeight - margin,
            width - margin,
            height - margin
        )

        // Draw subtitle background box
        canvas.drawRoundRect(rectF, 24f, 24f, subtitleBgPaint)
        canvas.drawRoundRect(rectF, 24f, 24f, subtitleBorderPaint)

        // Draw Subtitle text (wrapped)
        val textX = rectF.left + 30f
        var textY = rectF.top + 60f
        val maxWidth = rectF.width() - 60f

        val words = fullText.split(" ")
        var line = StringBuilder()

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (subtitleTextPaint.measureText(testLine) < maxWidth) {
                line = StringBuilder(testLine)
            } else {
                canvas.drawText(line.toString(), textX, textY, subtitleTextPaint)
                textY += subtitleTextPaint.textSize * 1.3f
                line = StringBuilder(word)
                if (textY > rectF.bottom - 30f) break
            }
        }
        if (line.isNotEmpty() && textY <= rectF.bottom - 30f) {
            canvas.drawText(line.toString(), textX, textY, subtitleTextPaint)
        }
    }

    private fun adjustTextSize(availableWidth: Float, availableHeight: Float, text: String) {
        var size = (availableHeight * 0.7f).coerceIn(24f, 60f)
        textPaint.textSize = size
        while (textPaint.measureText(text) > availableWidth && size > 16f) {
            size -= 2f
            textPaint.textSize = size
        }
    }
}
