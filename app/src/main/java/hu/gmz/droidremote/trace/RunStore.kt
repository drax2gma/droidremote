package hu.gmz.droidremote.trace

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunStore(context: Context) {

    data class Summary(
        val id: String,
        val goal: String,
        val status: String,
        val steps: Int,
        val startedAt: Long,
    )

    private val root = File(context.filesDir, "runs").apply { mkdirs() }

    fun start(goal: String, model: String): Session {
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(root, id).apply { mkdirs() }
        val session = Session(id, dir, goal, model)
        session.begin()
        return session
    }

    fun list(limit: Int = 20): List<Summary> = runCatching {
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.take(limit)
            ?.mapNotNull { dir ->
                val meta = File(dir, "meta.json").takeIf { it.exists() } ?: return@mapNotNull null
                runCatching {
                    val json = JSONObject(meta.readText())
                    Summary(
                        id = json.optString("id", dir.name),
                        goal = json.optString("goal"),
                        status = json.optString("status", "?"),
                        steps = json.optInt("steps", 0),
                        startedAt = json.optLong("startedAt", 0),
                    )
                }.getOrNull()
            }
            .orEmpty()
    }.getOrDefault(emptyList())

    class Session(
        val id: String,
        private val dir: File,
        private val goal: String,
        private val model: String,
    ) {
        private val startedAt = System.currentTimeMillis()
        private val stepsFile = File(dir, "steps.jsonl")
        private var stepCount = 0
        private var closed = false

        fun begin() {
            runCatching {
                File(dir, "meta.json").writeText(
                    JSONObject()
                        .put("id", id)
                        .put("goal", goal)
                        .put("model", model)
                        .put("startedAt", startedAt)
                        .put("status", "running")
                        .put("steps", 0)
                        .toString()
                )
            }
        }

        fun step(
            index: Int,
            action: String,
            rawReply: String,
            tree: String,
            ok: Boolean?,
            screenshot: ByteArray?,
        ) {
            runCatching {
                val entry = JSONObject()
                    .put("i", index)
                    .put("action", action)
                    .put("raw", rawReply.take(1500))
                    .put("tree", tree.take(4000))
                if (ok != null) entry.put("ok", ok)
                if (screenshot != null) {
                    val name = "shot-%03d.jpg".format(index)
                    File(dir, name).writeBytes(screenshot)
                    entry.put("shot", name)
                }
                stepsFile.appendText(entry.toString() + "\n")
                stepCount = maxOf(stepCount, index)
            }
        }

        fun finish(status: String, summary: String) {
            if (closed) return
            closed = true
            runCatching {
                File(dir, "meta.json").writeText(
                    JSONObject()
                        .put("id", id)
                        .put("goal", goal)
                        .put("model", model)
                        .put("startedAt", startedAt)
                        .put("endedAt", System.currentTimeMillis())
                        .put("status", status)
                        .put("steps", stepCount)
                        .put("summary", summary)
                        .toString()
                )
            }
        }
    }
}
