package com.screentranslate.app.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.screentranslate.app.model.RecognizedTextItem
import com.screentranslate.app.model.SupportedLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class MLKitTranslator : TranslationEngine {

    private val translators = ConcurrentHashMap<String, Translator>()

    private fun getLanguageCode(lang: SupportedLanguage): String {
        return when (lang) {
            SupportedLanguage.JAPANESE -> TranslateLanguage.JAPANESE
            SupportedLanguage.CHINESE -> TranslateLanguage.CHINESE
            SupportedLanguage.KOREAN -> TranslateLanguage.KOREAN
            else -> TranslateLanguage.ENGLISH
        }
    }

    private suspend fun getOrCreateTranslator(sourceLang: SupportedLanguage): Translator {
        val srcCode = getLanguageCode(sourceLang)
        val key = "$srcCode-th"

        var translator = translators[key]
        if (translator == null) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcCode)
                .setTargetLanguage(TranslateLanguage.THAI)
                .build()
            translator = Translation.getClient(options)

            // Download model if not available yet (typically ~30MB)
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()

            translators[key] = translator
        }
        return translator
    }

    override suspend fun translateItems(
        items: List<RecognizedTextItem>,
        sourceLanguage: SupportedLanguage
    ): List<RecognizedTextItem> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope items

        val translator = getOrCreateTranslator(sourceLanguage)

        // Translate in parallel for maximum speed
        val deferredList = items.map { item ->
            async {
                try {
                    val translated = translator.translate(item.originalText).await()
                    item.translatedText = translated
                } catch (e: Exception) {
                    item.translatedText = item.originalText
                }
                item
            }
        }
        deferredList.awaitAll()
    }

    override suspend fun translateSingle(
        text: String,
        sourceLanguage: SupportedLanguage
    ): String {
        if (text.isBlank()) return text
        return try {
            val translator = getOrCreateTranslator(sourceLanguage)
            translator.translate(text).await()
        } catch (e: Exception) {
            text
        }
    }

    override fun close() {
        for (translator in translators.values) {
            translator.close()
        }
        translators.clear()
    }
}
