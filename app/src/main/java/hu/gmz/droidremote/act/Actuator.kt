package hu.gmz.droidremote.act

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import hu.gmz.droidremote.observe.Bounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class Actuator(private val service: AccessibilityService) {

    data class Result(val ok: Boolean, val detail: String)

    suspend fun perform(
        action: Action,
        nodes: Map<Int, AccessibilityNodeInfo>,
        screen: Bounds,
    ): Result = when (action) {
        is Action.Tap -> tapNode(action.node, nodes, screen)
        is Action.TapAt -> tapAt(action.x, action.y)
        is Action.LongPress -> longPress(action.x, action.y, action.ms)
        is Action.Swipe -> swipe(action.x1, action.y1, action.x2, action.y2, action.ms)
        is Action.Type -> type(action.node, action.text, nodes)
        is Action.Scroll -> scroll(action.node, action.forward, nodes)
        is Action.Key -> key(action.code)
        is Action.Wait -> {
            delay(action.ms.coerceIn(0, 10_000))
            Result(true, "várakozás ${action.ms} ms")
        }
        Action.Screenshot -> Result(true, "képernyőkép kérés")
        is Action.Done -> Result(true, action.summary.ifBlank { "kész" })
        is Action.Fail -> Result(false, action.why.ifBlank { "feladva" })
    }

    private suspend fun tapNode(
        index: Int,
        nodes: Map<Int, AccessibilityNodeInfo>,
        screen: Bounds,
    ): Result {
        val node = nodes[index] ?: return tapAt(screen.centerX, screen.centerY)
        val clickable = clickableAncestor(node)
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return Result(true, "ACTION_CLICK @$index")
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return Result(false, "nulla méretű node @$index")
        return tapAt(rect.centerX(), rect.centerY())
    }

    private suspend fun tapAt(x: Int, y: Int): Result {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val ok = gesture(path, 60)
        return Result(ok, if (ok) "tap $x,$y" else "tap eldobva ($x,$y)")
    }

    private suspend fun longPress(x: Int, y: Int, ms: Long): Result {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val ok = gesture(path, ms.coerceIn(300, 4000))
        return Result(ok, if (ok) "long press $x,$y" else "long press eldobva")
    }

    private suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long): Result {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val ok = gesture(path, ms.coerceIn(80, 3000))
        return Result(ok, if (ok) "swipe $x1,$y1 -> $x2,$y2" else "swipe eldobva")
    }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(40))
        val description = GestureDescription.Builder().addStroke(stroke).build()
        return withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { continuation ->
                val dispatched = runCatching {
                    service.dispatchGesture(
                        description,
                        object : AccessibilityService.GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (continuation.isActive) continuation.resume(false)
                            }
                        },
                        null,
                    )
                }.getOrDefault(false)
                if (!dispatched && continuation.isActive) continuation.resume(false)
            }
        } ?: false
    }

    private fun type(index: Int?, text: String, nodes: Map<Int, AccessibilityNodeInfo>): Result {
        val start = if (index == null) {
            service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } else {
            nodes[index]
        } ?: return Result(
            false,
            if (index == null) "nincs fókuszált beviteli mező" else "ismeretlen node @$index",
        )
        val target = editableTarget(start) ?: return Result(false, "nem szerkeszthető mező")
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Result(true, "${text.length} karakter beírva")
        } else {
            Result(false, "ACTION_SET_TEXT elutasítva")
        }
    }

    private fun editableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(node to 0)
        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (current.isEditable) return current
            if (depth >= 3) continue
            for (i in 0 until current.childCount) {
                val child = current.getChild(i) ?: continue
                queue.addLast(child to depth + 1)
            }
        }
        return null
    }

    private fun scroll(index: Int, forward: Boolean, nodes: Map<Int, AccessibilityNodeInfo>): Result {
        var current = nodes[index] ?: return Result(false, "ismeretlen node @$index")
        repeat(8) {
            if (current.isScrollable) {
                val action = if (forward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                return if (current.performAction(action)) {
                    Result(true, "scroll @$index")
                } else {
                    Result(false, "scroll elutasítva")
                }
            }
            current = current.parent ?: return Result(false, "nincs görgethető node @$index")
        }
        return Result(false, "nincs görgethető node @$index")
    }

    private fun key(code: String): Result {
        val globalAction = when (code.uppercase()) {
            "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
            "HOME" -> AccessibilityService.GLOBAL_ACTION_HOME
            "RECENTS" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> null
        } ?: return Result(false, "nem támogatott billentyű: $code")
        return if (service.performGlobalAction(globalAction)) {
            Result(true, code.uppercase())
        } else {
            Result(false, "global action elutasítva")
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        repeat(8) {
            val candidate = current ?: return null
            if (candidate.isClickable) return candidate
            current = candidate.parent
        }
        return null
    }
}
