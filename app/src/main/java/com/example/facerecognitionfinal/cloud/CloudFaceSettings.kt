package com.example.facerecognitionfinal.cloud

import android.content.Context

/**
 * Stores cloud demo settings for a coursework scenario.
 *
 * API keys are saved only so the optional cloud comparison can be tested before
 * a presentation. A production app should move secrets to a backend or encrypted
 * storage instead of keeping provider credentials in client preferences.
 */
class CloudFaceSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Config {
        return Config(
            provider = CloudProvider.fromStorageValue(prefs.getString(KEY_PROVIDER, CloudProvider.FACE_PLUS_PLUS.storageValue)),
            baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            apiSecret = prefs.getString(KEY_API_SECRET, "").orEmpty(),
            faceSetOuterId = prefs.getString(KEY_FACESET_OUTER_ID, DEFAULT_FACESET_OUTER_ID).orEmpty(),
            threshold = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        )
    }

    fun save(config: Config) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.storageValue)
            .putString(KEY_BASE_URL, config.baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_API_SECRET, config.apiSecret.trim())
            .putString(KEY_FACESET_OUTER_ID, config.faceSetOuterId.trim())
            .putFloat(KEY_THRESHOLD, config.threshold)
            .apply()
    }

    fun loadConnectionStatus(): ConnectionStatus? {
        val testedAtMillis = prefs.getLong(KEY_STATUS_TESTED_AT, 0L)
        if (testedAtMillis <= 0L) return null
        return ConnectionStatus(
            provider = CloudProvider.fromStorageValue(prefs.getString(KEY_STATUS_PROVIDER, null)),
            success = prefs.getBoolean(KEY_STATUS_SUCCESS, false),
            testedAtMillis = testedAtMillis,
            message = prefs.getString(KEY_STATUS_MESSAGE, "").orEmpty()
        )
    }

    fun saveConnectionStatus(status: ConnectionStatus) {
        prefs.edit()
            .putString(KEY_STATUS_PROVIDER, status.provider.storageValue)
            .putBoolean(KEY_STATUS_SUCCESS, status.success)
            .putLong(KEY_STATUS_TESTED_AT, status.testedAtMillis)
            .putString(KEY_STATUS_MESSAGE, status.message)
            .apply()
    }

    data class Config(
        val provider: CloudProvider = CloudProvider.FACE_PLUS_PLUS,
        val baseUrl: String,
        val apiKey: String,
        val apiSecret: String = "",
        val faceSetOuterId: String = DEFAULT_FACESET_OUTER_ID,
        val threshold: Float = DEFAULT_THRESHOLD
    ) {
        val isConfigured: Boolean
            get() = when (provider) {
                CloudProvider.COMPREFACE -> baseUrl.isNotBlank() && apiKey.isNotBlank()
                CloudProvider.FACE_PLUS_PLUS -> baseUrl.isNotBlank() &&
                    apiKey.isNotBlank() &&
                    apiSecret.isNotBlank() &&
                    faceSetOuterId.isNotBlank()
            }
    }

    data class ConnectionStatus(
        val provider: CloudProvider,
        val success: Boolean,
        val testedAtMillis: Long,
        val message: String
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api-us.faceplusplus.com"
        const val DEFAULT_COMPREFACE_BASE_URL = "http://10.0.2.2:8000"
        const val DEFAULT_FACESET_OUTER_ID = "final_project_faces"
        const val DEFAULT_THRESHOLD = 0.75f
        private const val PREFS_NAME = "cloud_face_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_FACESET_OUTER_ID = "faceset_outer_id"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_STATUS_PROVIDER = "status_provider"
        private const val KEY_STATUS_SUCCESS = "status_success"
        private const val KEY_STATUS_TESTED_AT = "status_tested_at"
        private const val KEY_STATUS_MESSAGE = "status_message"
    }
}

enum class CloudProvider(val storageValue: String, val displayName: String) {
    FACE_PLUS_PLUS("face_plus_plus", "Face++ 托管云端"),
    COMPREFACE("compreface", "CompreFace 自部署");

    companion object {
        fun fromStorageValue(value: String?): CloudProvider {
            return values().firstOrNull { it.storageValue == value } ?: FACE_PLUS_PLUS
        }
    }
}
