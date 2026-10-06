package com.screentranslate.app.engine

import com.screentranslate.app.model.RecognizedTextItem
import com.screentranslate.app.model.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class QuotaExceededException(message: String) : Exception(message)

class GeminiTranslator(
    private val apiKey: String,
    private val quotaManager: QuotaManager? = null
) : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Gemini 3.5 Flash-Lite endpoint (Official fast & accurate model with free tier)
    private val modelEndpoint = "gemini-3.5-flash-lite:generateContent"

    override suspend fun translateItems(
        items: List<RecognizedTextItem>,
        sourceLanguage: SupportedLanguage
    ): List<RecognizedTextItem> = withContext(Dispatchers.IO) {
        if (items.isEmpty() || apiKey.isBlank()) return@withContext items

        // Check quota availability before calling API
        if (quotaManager != null) {
            val hasQuota = quotaManager.recordRequest()
            if (!hasQuota) {
                throw QuotaExceededException("โควต้าฟรี Gemini 3.5 Flash-Lite วันนี้หมดแล้ว")
            }
        }

        // Batch translate by sending a numbered list to minimize API calls
        val promptBuilder = StringBuilder()
        promptBuilder.append("You are a professional game translator. Translate each numbered line into concise, natural, fluent Thai suitable for in-game UI overlay. ")
        promptBuilder.append("Output ONLY the numbered list with format '1. <Thai translation>'. Exactly one line per numbered item. Do not include notes, phonetic guides, or markdown formatting:\n\n")

        items.forEachIndexed { index, item ->
            val cleanText = item.originalText.replace("\n", " ").replace("\r", " ").trim()
            promptBuilder.append("${index + 1}. $cleanText\n")
        }

        try {
            val responseText = callGeminiApi(promptBuilder.toString())
            val translatedLines = parseNumberedResponse(responseText, items.size)

            items.forEachIndexed { index, item ->
                val translated = translatedLines.getOrNull(index)?.trim() ?: ""
                item.translatedText = if (translated.isNotBlank()) translated else item.originalText
            }
        } catch (e: QuotaExceededException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to original text on failure
            items.forEach { it.translatedText = it.originalText }
        }

        items
    }

    override suspend fun translateSingle(
        text: String,
        sourceLanguage: SupportedLanguage
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || apiKey.isBlank()) return@withContext text

        if (quotaManager != null) {
            val hasQuota = quotaManager.recordRequest()
            if (!hasQuota) {
                throw QuotaExceededException("โควต้าฟรี Gemini 3.5 Flash-Lite วันนี้หมดแล้ว")
            }
        }

        val prompt = "Translate this text directly to natural, fluent Thai. Return only the translation:\n$text"
        try {
            callGeminiApi(prompt)
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }

    private fun callGeminiApi(prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelEndpoint?key=$apiKey"

        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) {
                throw QuotaExceededException("โควต้า Gemini เต็มชั่วคราว (Rate limit 429)")
            }
            if (!response.isSuccessful) {
                throw RuntimeException("Gemini API error: ${response.code} ${response.message}")
            }
            val respBody = response.body?.string() ?: ""
            val root = JSONObject(respBody)
            val candidates = root.getJSONArray("candidates")
            val candidate = candidates.getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text").trim()
        }
    }

    private fun parseNumberedResponse(response: String, expectedCount: Int): List<String> {
        val resultMap = mutableMapOf<Int, String>()
        val lines = response.lines()

        for (line in lines) {
            val trimmed = line.trim()
            val match = Regex("""^(\d+)[\.\:\)]\s*(.*)""").find(trimmed)
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()
                var text = match.groupValues[2].trim()
                // Clean markdown bold/stars if any
                text = text.replace("**", "").replace("*", "").trim()
                if (index != null && index in 1..expectedCount) {
                    resultMap[index - 1] = text
                }
            }
        }

        return (0 until expectedCount).map { i ->
            resultMap[i] ?: ""
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
    }
}
