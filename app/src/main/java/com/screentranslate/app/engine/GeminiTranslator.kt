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

    // Gemini 2.0 Flash-Lite endpoint (Fastest & most cost-effective with generous Free Tier)
    private val modelEndpoint = "gemini-2.0-flash-lite:generateContent"

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
        promptBuilder.append("Translate each of the following lines into natural, fluent Thai. ")
        promptBuilder.append("Keep the exact same numbering format (e.g., 1. ..., 2. ...). Do not add any extra commentary:\n\n")

        items.forEachIndexed { index, item ->
            promptBuilder.append("${index + 1}. ${item.originalText}\n")
        }

        try {
            val responseText = callGeminiApi(promptBuilder.toString())
            val translatedLines = parseNumberedResponse(responseText, items.size)

            items.forEachIndexed { index, item ->
                item.translatedText = translatedLines.getOrNull(index) ?: item.originalText
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
        val result = mutableListOf<String>()
        val lines = response.lines()

        for (line in lines) {
            val trimmed = line.trim()
            val match = Regex("""^\d+[\.\)]\s*(.*)""").find(trimmed)
            if (match != null) {
                result.add(match.groupValues[1].trim())
            }
        }

        if (result.size < expectedCount) {
            while (result.size < expectedCount) {
                result.add("")
            }
        }
        return result
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
    }
}
