package com.screentranslate.app.engine

import android.content.Context
import android.content.SharedPreferences
import com.screentranslate.app.model.QuotaStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Manages daily Gemini API quota tracking, rate limits, and reset times.
 * Google AI Studio free tier resets daily at 00:00 Pacific Time (approx. 14:00/15:00 Thai Time).
 */
class QuotaManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Los_Angeles")
    }

    /**
     * Get the current date string in Pacific Time to align with Google AI Studio's daily rollover.
     */
    private fun getCurrentPacificDate(): String {
        return dateFormat.format(Date())
    }

    /**
     * Check if a new day has arrived and reset counter if needed.
     */
    @Synchronized
    fun checkAndResetDay() {
        val today = getCurrentPacificDate()
        val recordedDate = prefs.getString(KEY_RECORDED_DATE, "")

        if (recordedDate != today) {
            // New day in Pacific Time: Reset counter!
            prefs.edit()
                .putString(KEY_RECORDED_DATE, today)
                .putInt(KEY_REQUESTS_COUNT, 0)
                .apply()
        }
    }

    /**
     * Record a new API request.
     * Returns true if quota is still available, false if limit reached.
     */
    @Synchronized
    fun recordRequest(): Boolean {
        checkAndResetDay()
        val currentCount = prefs.getInt(KEY_REQUESTS_COUNT, 0)
        if (currentCount >= DAILY_MAX_REQUESTS) {
            return false
        }
        prefs.edit().putInt(KEY_REQUESTS_COUNT, currentCount + 1).apply()
        return true
    }

    /**
     * Get real-time status of the quota.
     */
    fun getQuotaStatus(): QuotaStatus {
        checkAndResetDay()
        val used = prefs.getInt(KEY_REQUESTS_COUNT, 0)
        val remaining = (DAILY_MAX_REQUESTS - used).coerceAtLeast(0)

        // Calculate hours and minutes until next Pacific midnight
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"))
        val nowMillis = cal.timeInMillis

        // Set to next midnight
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val resetMillis = cal.timeInMillis

        val diffMillis = (resetMillis - nowMillis).coerceAtLeast(0)
        val diffHours = (diffMillis / (1000 * 60 * 60)).toInt()
        val diffMinutes = ((diffMillis / (1000 * 60)) % 60).toInt()

        return QuotaStatus(
            usedToday = used,
            maxDaily = DAILY_MAX_REQUESTS,
            remaining = remaining,
            resetsInHours = diffHours,
            resetsInMinutes = diffMinutes
        )
    }

    /**
     * Get or set user's custom Gemini API key.
     */
    fun getApiKey(): String {
        val saved = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (saved.isNotBlank()) return saved
        return try {
            com.screentranslate.app.BuildConfig.DEFAULT_GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "gemini_quota_prefs"
        private const val KEY_RECORDED_DATE = "recorded_date"
        private const val KEY_REQUESTS_COUNT = "requests_count"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"

        // Gemini Flash-Lite Free Tier standard limit
        const val DAILY_MAX_REQUESTS = 1500
    }
}
