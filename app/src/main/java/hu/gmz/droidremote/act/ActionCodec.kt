package hu.gmz.droidremote.act

import org.json.JSONArray
import org.json.JSONObject

object ActionCodec {

    fun parse(raw: String): Result<Action> {
        val json = extract(raw) ?: return Result.failure(IllegalArgumentException("nincs JSON objektum a válaszban"))
        return runCatching {
            val obj = JSONObject(json)
            when (val kind = obj.optString("a").lowercase()) {
                "tap" -> if (obj.has("n")) {
                    Action.Tap(obj.getInt("n"))
                } else {
                    Action.TapAt(obj.getInt("x"), obj.getInt("y"))
                }
                "long" -> Action.LongPress(obj.getInt("x"), obj.getInt("y"), obj.optLong("ms", 600))
                "swipe" -> Action.Swipe(
                    obj.getInt("x1"),
                    obj.getInt("y1"),
                    obj.getInt("x2"),
                    obj.getInt("y2"),
                    obj.optLong("ms", 300),
                )
                "type" -> Action.Type(
                    if (obj.has("n")) obj.getInt("n") else null,
                    obj.getString("t"),
                )
                "scroll" -> Action.Scroll(obj.getInt("n"), !obj.optString("dir", "down").equals("up", true))
                "key" -> Action.Key(obj.getString("k").uppercase())
                "shot" -> Action.Screenshot
                "wait" -> Action.Wait(obj.optLong("ms", 500))
                "done" -> Action.Done(obj.optString("summary", ""))
                "fail" -> Action.Fail(obj.optString("why", ""))
                else -> error("ismeretlen akció: $kind")
            }
        }
    }

    fun parseArray(raw: String): Result<List<Action>> {
        val text = stripFences(raw)
        val start = text.indexOf('[')
        if (start < 0) return Result.failure(IllegalArgumentException("nincs JSON tömb a bemenetben"))
        val end = text.lastIndexOf(']')
        if (end <= start) return Result.failure(IllegalArgumentException("záratlan JSON tömb"))
        return runCatching {
            val array = JSONArray(text.substring(start, end + 1))
            (0 until array.length()).map { index ->
                parse(array.get(index).toString()).getOrThrow()
            }
        }
    }

    fun toJson(action: Action): JSONObject = when (action) {
        is Action.Tap -> JSONObject().put("a", "tap").put("n", action.node)
        is Action.TapAt -> JSONObject().put("a", "tap").put("x", action.x).put("y", action.y)
        is Action.LongPress -> JSONObject().put("a", "long").put("x", action.x).put("y", action.y).put("ms", action.ms)
        is Action.Swipe -> JSONObject().put("a", "swipe").put("x1", action.x1).put("y1", action.y1)
            .put("x2", action.x2).put("y2", action.y2).put("ms", action.ms)
        is Action.Type -> JSONObject().put("a", "type").put("t", action.text)
            .apply { action.node?.let { node -> put("n", node) } }
        is Action.Scroll -> JSONObject().put("a", "scroll").put("n", action.node)
            .put("dir", if (action.forward) "down" else "up")
        is Action.Key -> JSONObject().put("a", "key").put("k", action.code)
        Action.Screenshot -> JSONObject().put("a", "shot")
        is Action.Wait -> JSONObject().put("a", "wait").put("ms", action.ms)
        is Action.Done -> JSONObject().put("a", "done").put("summary", action.summary)
        is Action.Fail -> JSONObject().put("a", "fail").put("why", action.why)
    }

    fun describe(action: Action): String = when (action) {
        is Action.Tap -> "tap @${action.node}"
        is Action.TapAt -> "tap ${action.x},${action.y}"
        is Action.LongPress -> "long ${action.x},${action.y}"
        is Action.Swipe -> "swipe ${action.x1},${action.y1}->${action.x2},${action.y2}"
        is Action.Type -> "type ${action.node?.let { "@$it" } ?: "fókusz"} (${action.text.length} kar.)"
        is Action.Scroll -> "scroll @${action.node} ${if (action.forward) "le" else "fel"}"
        is Action.Key -> "key ${action.code}"
        Action.Screenshot -> "shot"
        is Action.Wait -> "wait ${action.ms}ms"
        is Action.Done -> "done"
        is Action.Fail -> "fail"
    }

    private fun stripFences(raw: String): String =
        raw.replace("```json", "").replace("```JSON", "").replace("```", "")

    private fun extract(raw: String): String? {
        val text = stripFences(raw)
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (inString) {
                when (ch) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
