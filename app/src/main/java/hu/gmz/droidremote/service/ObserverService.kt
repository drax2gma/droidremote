package hu.gmz.droidremote.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hu.gmz.droidremote.act.Action
import hu.gmz.droidremote.act.Actuator
import hu.gmz.droidremote.observe.NodeAdapter
import hu.gmz.droidremote.observe.Observation
import hu.gmz.droidremote.observe.ScreenCapture
import hu.gmz.droidremote.observe.TreeSerializer

class ObserverService : AccessibilityService() {

    val capture: ScreenCapture by lazy { ScreenCapture(this) }
    private val actuator: Actuator by lazy { Actuator(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun observe(maxNodes: Int = 140): Observation? {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return null
        val adapter = NodeAdapter()
        val tree = adapter.adapt(root) ?: return null
        val title = runCatching {
            windows?.firstOrNull { it.id == root.windowId }?.title?.toString()
        }.getOrNull()
        val packageName = root.packageName?.toString() ?: "?"
        val text = TreeSerializer.serialize(
            root = tree,
            maxNodes = maxNodes,
            maxText = 80,
            packageName = packageName,
            windowTitle = title,
        )
        return Observation(
            treeText = text,
            nodes = adapter.nodeById,
            screen = tree.bounds,
            packageName = packageName,
            windowTitle = title,
            nodeCount = tree.walk().count() - 1,
            capturedAt = System.currentTimeMillis(),
        )
    }

    suspend fun act(action: Action, observation: Observation): Actuator.Result =
        actuator.perform(action, observation.nodes, observation.screen)

    companion object {
        @Volatile
        var instance: ObserverService? = null
            private set

        fun enabled(context: Context): Boolean {
            val expected = ComponentName(context, ObserverService::class.java).flattenToString()
            val stored = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return stored.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
