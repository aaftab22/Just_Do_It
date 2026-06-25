package com.darksunTechnologies.justdoit.ai

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation

/**
 * Manages Gemini Nano on-device AI availability via AICore.
 *
 * Call [initialize] once from MainActivity.onCreate() on Dispatchers.IO.
 * If the device doesn't support Gemini Nano (older hardware, no AICore),
 * [isAvailable] stays false and the app silently uses SmartTaskParser (regex).
 *
 * Privacy: The model runs entirely on-device. No data leaves the phone.
 */
object GeminiNanoManager {
    private const val TAG = "GeminiNanoManager"

    /** Whether the on-device model is downloaded and ready for inference. */
    var isAvailable: Boolean = false
        private set

    private var generativeModel: GenerativeModel? = null

    /**
     * Check model availability and trigger download if needed.
     * This is non-blocking and should be called from a coroutine on IO dispatcher.
     */
    suspend fun initialize(context: Context) {
        try {
            val model = Generation.getClient()
            generativeModel = model

            val status = model.checkStatus()
            when (status) {
                FeatureStatus.AVAILABLE -> {
                    isAvailable = true
                    Log.d(TAG, "Gemini Nano AVAILABLE on this device.")
                }
                FeatureStatus.DOWNLOADABLE -> {
                    Log.d(TAG, "Gemini Nano DOWNLOADABLE. Triggering download...")
                    model.download().collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadStarted ->
                                Log.d(TAG, "Download started")
                            is DownloadStatus.DownloadProgress ->
                                Log.d(TAG, "Download progress: ${downloadStatus.totalBytesDownloaded}")
                            DownloadStatus.DownloadCompleted -> {
                                Log.d(TAG, "Gemini Nano download complete.")
                                isAvailable = true
                            }
                            is DownloadStatus.DownloadFailed -> {
                                Log.e(TAG, "Download failed: ${downloadStatus.e.message}")
                                isAvailable = false
                            }
                        }
                    }
                }
                FeatureStatus.DOWNLOADING -> {
                    Log.d(TAG, "Gemini Nano currently downloading...")
                    // Wait for it to complete on next init call
                    isAvailable = false
                }
                else -> {
                    isAvailable = false
                    Log.d(TAG, "Gemini Nano UNAVAILABLE. Regex-only mode active.")
                }
            }
        } catch (e: Exception) {
            isAvailable = false
            Log.e(TAG, "Gemini Nano initialization failed", e)
        }
    }

    /**
     * Returns the GenerativeModel client if available, null otherwise.
     * Callers should always check for null and fall back to regex.
     */
    fun getClient(): GenerativeModel? = if (isAvailable) generativeModel else null
}
