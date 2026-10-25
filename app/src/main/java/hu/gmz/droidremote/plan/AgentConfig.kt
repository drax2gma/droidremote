package hu.gmz.droidremote.plan

import android.content.Context

class AgentConfig(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var maxSteps: Int
        get() = prefs.getInt(KEY_MAX_STEPS, DEFAULT_MAX_STEPS)
        set(value) = prefs.edit().putInt(KEY_MAX_STEPS, value.coerceIn(1, 200)).apply()

    fun chatUrl(): String = baseUrl.trimEnd('/') + "/chat/completions"

    fun modelsUrl(): String = baseUrl.trimEnd('/') + "/models"

    companion object {
        private const val PREFS = "droidremote"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_MAX_STEPS = "max_steps"

        const val DEFAULT_BASE_URL = "https://ollama.com/v1"
        const val DEFAULT_MAX_STEPS = 40
    }
}
