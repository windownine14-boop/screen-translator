package com.screentranslate.app.engine

import com.screentranslate.app.model.RecognizedTextItem
import com.screentranslate.app.model.SupportedLanguage

interface TranslationEngine {
    suspend fun translateItems(
        items: List<RecognizedTextItem>,
        sourceLanguage: SupportedLanguage
    ): List<RecognizedTextItem>

    suspend fun translateSingle(
        text: String,
        sourceLanguage: SupportedLanguage
    ): String

    fun close()
}
