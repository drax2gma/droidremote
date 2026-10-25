package hu.gmz.droidremote.plan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ModelClient(private val config: AgentConfig) {

    data class Reply(val text: String, val promptTokens: Int, val completionTokens: Int)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        system: String,
        user: String,
        jpeg: ByteArray? = null,
    ): Result<Reply> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", userContent(user, jpeg)))

            val payload = JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .put("temperature", 0.0)
                .put("max_tokens", 400)
                .put("stream", false)

            val request = Request.Builder()
                .url(config.chatUrl())
                .post(payload.toString().toRequestBody(JSON))
                .apply { if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}") }
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(300)}")
                val json = JSONObject(body)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
                val usage = json.optJSONObject("usage")
                Reply(
                    text = content,
                    promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
                    completionTokens = usage?.optInt("completion_tokens", 0) ?: 0,
                )
            }
        }
    }

    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(config.modelsUrl())
                .apply { if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}") }
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(200)}")
                val data = JSONObject(body).getJSONArray("data")
                (0 until data.length()).mapNotNull { index ->
                    data.getJSONObject(index).optString("id").takeIf { it.isNotBlank() }
                }
            }
        }
    }

    private fun userContent(text: String, jpeg: ByteArray?): Any {
        if (jpeg == null) return text
        val base64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        return JSONArray()
            .put(JSONObject().put("type", "text").put("text", text))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
            )
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
