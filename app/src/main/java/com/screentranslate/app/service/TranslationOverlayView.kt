package com.screentranslate.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.screentranslate.app.model.PresentationStyle
import com.screentranslate.app.model.RecognizedTextItem

class TranslationOverlayView(context: Context) : View(context) {

    private var items: List<RecognizedTextItem> = emptyList()
    private var presentationStyle: PresentationStyle = PresentationStyle.IN_PLACE
    private var isVisibleTranslation = true

    // Paints for In-Place Drawing (Deep Slate 96% opacity to completely cover original text)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F50D1117") // Deep dark background
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6038BDF8") // Subtle glowing cyan border
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
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
        val cornerRadius = 14f
        val paddingHorizontal = 12f
        val paddingVertical = 8f

        for (item in items) {
            val text = item.translatedText.ifEmpty { item.originalText }
            if (text.isBlank()) continue

            val box = item.boundingBox
            if (box.width() <= 0 || box.height() <= 0) continue

            // 1. Calculate an optimal, comfortable text size based on bounding box
            val baseSize = (box.height() * 0.72f).coerceIn(24f, 52f)
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textSize = baseSize
                setShadowLayer(4f, 0f, 2f, Color.parseColor("#E6000000"))
            }

            // 2. Determine target width for text wrapping (at least original box width)
            val minTextWidth = maxOf(box.width(), 80)
            val maxAvailableWidth = (width - 32).coerceAtLeast(100)
            val targetTextWidth = minTextWidth.coerceAtMost(maxAvailableWidth)

            // 3. Build multiline StaticLayout
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, targetTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .setIncludePad(true)
                .build()

            // 4. Measure card bounds to completely cover the original text and fit new Thai text
            val contentWidth = layout.width.toFloat()
            val contentHeight = layout.height.toFloat()

            val cardWidth = maxOf(box.width().toFloat(), contentWidth) + (paddingHorizontal * 2)
            val cardHeight = maxOf(box.height().toFloat(), contentHeight) + (paddingVertical * 2)

            // Position card directly over original box, constrained within screen
            val left = (box.left.toFloat() - paddingHorizontal).coerceIn(8f, (width - cardWidth - 8f).coerceAtLeast(8f))
            val top = (box.top.toFloat() - paddingVertical).coerceIn(8f, (height - cardHeight - 8f).coerceAtLeast(8f))
            val rectF = RectF(left, top, left + cardWidth, top + cardHeight)

            // 5. Draw dark backdrop card to completely obscure original English text underneath
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

            // 6. Draw clean multiline Thai text inside the card
            canvas.save()
            val textDrawX = rectF.left + paddingHorizontal
            val textDrawY = rectF.top + paddingVertical + ((rectF.height() - (paddingVertical * 2) - contentHeight) / 2).coerceAtLeast(0f)
            canvas.translate(textDrawX, textDrawY)
            layout.draw(canvas)
            canvas.restore()
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
}
