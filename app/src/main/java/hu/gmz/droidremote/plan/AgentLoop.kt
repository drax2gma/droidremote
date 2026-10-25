package hu.gmz.droidremote.plan

import hu.gmz.droidremote.act.Action
import hu.gmz.droidremote.act.ActionCodec
import hu.gmz.droidremote.observe.Observation
import hu.gmz.droidremote.observe.ScreenCapture.CaptureResult
import hu.gmz.droidremote.observe.SettleDetector
import hu.gmz.droidremote.service.ObserverService
import hu.gmz.droidremote.trace.RunStore

class AgentLoop(
    private val service: ObserverService,
    private val config: AgentConfig,
    private val prompts: PromptBuilder,
    private val client: ModelClient,
    private val store: RunStore,
) {

    data class Report(val ok: Boolean, val summary: String, val steps: Int, val runId: String)

    @Volatile
    private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    suspend fun run(goal: String, onEvent: (String) -> Unit): Report {
        stopRequested = false
        val session = store.start(goal, config.model)
        val system = prompts.systemPrompt()

        var observation: Observation? = service.observe()
        if (observation == null) {
            session.finish("error", "nem olvasható képernyő")
            return Report(false, "nem olvasható képernyő (a11y szolgáltatás?)", 0, session.id)
        }

        var steps = 0
        var lastAction = "(kezdés)"
        var lastChange = "(nincs)"
        var pendingShot: ByteArray? = null
        var stall = 0

        fun emit(line: String) = onEvent(line)

        while (steps < config.maxSteps) {
            if (stopRequested) {
                session.finish("stopped", "leállítva a $steps. lépésnél")
                return Report(false, "leállítva", steps, session.id)
            }

            val current = observation ?: break
            steps++

            val user = prompts.actorPrompt(
                goal = goal,
                step = steps,
                maxSteps = config.maxSteps,
                lastAction = lastAction,
                lastChange = lastChange,
                screen = current.treeText,
            )

            emit("→ modell ($steps. lépés)")
            val replyResult = client.complete(system, user, pendingShot)
            pendingShot = null
            if (replyResult.isFailure) {
                val message = replyResult.exceptionOrNull()?.message ?: "ismeretlen hiba"
                session.finish("error", "modell hiba: $message")
                return Report(false, "modell hiba: $message", steps, session.id)
            }
            val reply = replyResult.getOrThrow()

            val parsed = ActionCodec.parse(reply.text)
            if (parsed.isFailure) {
                lastAction = "parse hiba: ${parsed.exceptionOrNull()?.message}"
                lastChange = "nem"
                session.step(steps, lastAction, reply.text, current.treeText, false, null)
                emit("$steps. $lastAction")
                continue
            }
            val action = parsed.getOrThrow()

            when (action) {
                is Action.Done -> {
                    session.finish("done", action.summary)
                    return Report(true, action.summary.ifBlank { "kész" }, steps, session.id)
                }
                is Action.Fail -> {
                    session.finish("failed", action.why)
                    return Report(false, action.why.ifBlank { "feladva" }, steps, session.id)
                }
                else -> Unit
            }

            if (action is Action.Screenshot) {
                val jpeg = (service.capture.capture() as? CaptureResult.Success)?.jpeg
                pendingShot = jpeg
                lastAction = "shot"
                lastChange = if (jpeg != null) "kép csatolva" else "kép nem sikerült"
                session.step(steps, lastAction, reply.text, current.treeText, jpeg != null, null)
                emit("$steps. kép: ${(jpeg?.size ?: 0) / 1024} KB")
                continue
            }

            val before = current.treeText
            val result = service.act(action, current)
            val settle = SettleDetector.wait(
                before = before,
                observe = { service.observe()?.treeText ?: "" },
            )
            lastAction = "${ActionCodec.describe(action)} → ${result.detail}"
            lastChange = if (settle.changed) "igen" else "nem"
            stall = if (settle.changed) 0 else stall + 1
            observation = service.observe() ?: observation

            session.step(
                index = steps,
                action = lastAction,
                rawReply = reply.text,
                tree = observation?.treeText ?: before,
                ok = result.ok,
                screenshot = null,
            )
            emit("$steps. $lastAction | változott: $lastChange (${settle.waitedMs} ms)")

            if (stall == 3) {
                pendingShot = (service.capture.capture() as? CaptureResult.Success)?.jpeg
                emit("$steps. nincs változás 3x → kép a következő lépéshez")
            }
            if (stall >= 6) {
                val summary = "6 lépés óta nincs változás"
                session.finish("stalled", summary)
                return Report(false, summary, steps, session.id)
            }
        }

        val summary = "elértem a lépés-plafont (${config.maxSteps})"
        session.finish("limit", summary)
        return Report(false, summary, steps, session.id)
    }
}
