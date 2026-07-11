package com.example.expensetracker.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OcrTrackingManager {

    private const val TAG = "OcrTrackingManager"
    private const val PREF_FILE = "finora_prefs"
    private const val PREF_KEY = "dev_tracking_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, false)

    fun isFeedbackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean("feedback_enabled", false)

    /**
     * Writes screenshot + tracking.json to getExternalFilesDir("OCRData")/<sessionId>/.
     * Runs on a background thread; never throws to the caller.
     *
     * @param bitmap      The processed receipt image (may be null if already recycled)
     * @param predicted   Fields as extracted by OCR, before any user editing
     * @param actual      Fields as actually saved (may differ if user edited)
     * @param wasEdited   True when user went through Review & Edit before saving
     */
    /**
     * @param feedback  User's accuracy rating + comment; null if feedback was skipped or not collected.
     */
    fun saveSession(
        context: Context,
        sessionId: String,
        capturedAt: String,
        bitmap: Bitmap?,
        predicted: Map<String, String>,
        actual: Map<String, String>,
        wasEdited: Boolean,
        feedback: Map<String, String>? = null
    ) {
        val appContext = context.applicationContext
        Thread {
            try {
                val root = appContext.getExternalFilesDir("OCRData") ?: run {
                    Log.w(TAG, "External files dir not available, skipping session save")
                    return@Thread
                }
                val sessionDir = File(root, sessionId)
                sessionDir.mkdirs()

                // 1. Screenshot
                if (bitmap != null && !bitmap.isRecycled) {
                    val imgFile = File(sessionDir, "screenshot.jpg")
                    imgFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }

                // 2. tracking.json
                val savedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                // Fields whose value changed between OCR prediction and what the user saved
                val editedFields = JSONArray(
                    predicted.keys.filter { key -> predicted[key] != actual[key] }
                )
                val json = JSONObject().apply {
                    put("sessionId", sessionId)
                    put("capturedAt", capturedAt)
                    put("savedAt", savedAt)
                    put("wasEdited", wasEdited)
                    put("editedFields", editedFields)
                    put("feedback", if (feedback != null) JSONObject(feedback) else JSONObject.NULL)
                    put("predicted", JSONObject(predicted))
                    put("actual", JSONObject(actual))
                }
                File(sessionDir, "tracking.json").writeText(json.toString(2))

                Log.d(TAG, "Session saved → OCRData/$sessionId/")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save OCR session $sessionId", e)
            }
        }.start()
    }
}
