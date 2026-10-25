package hu.gmz.droidremote.observe

import android.view.accessibility.AccessibilityNodeInfo

class Observation(
    val treeText: String,
    val nodes: Map<Int, AccessibilityNodeInfo>,
    val screen: Bounds,
    val packageName: String,
    val windowTitle: String?,
    val nodeCount: Int,
    val capturedAt: Long,
)
