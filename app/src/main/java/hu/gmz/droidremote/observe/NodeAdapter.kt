package hu.gmz.droidremote.observe

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class NodeAdapter(
    private val maxDepth: Int = 30,
    private val maxNodes: Int = 600,
) {
    private var counter = 0

    val nodeById = HashMap<Int, AccessibilityNodeInfo>()

    fun adapt(root: AccessibilityNodeInfo): ObservedNode? {
        counter = 0
        nodeById.clear()
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val screen = Bounds(rect.left, rect.top, rect.right, rect.bottom)
        val children = ArrayList<ObservedNode>()
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            children.addAll(adaptOne(child, 1))
        }
        return ObservedNode(index = ROOT_INDEX, cls = "root", bounds = screen, children = children)
    }

    private fun adaptOne(node: AccessibilityNodeInfo, depth: Int): List<ObservedNode> {
        if (depth > maxDepth || counter >= maxNodes) return emptyList()

        val children = ArrayList<ObservedNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            children.addAll(adaptOne(child, depth + 1))
        }

        if (!node.isVisibleToUser) return children

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom)
        if (bounds.width <= 0 || bounds.height <= 0) return children

        val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val label = text ?: desc
        val interactive = node.isClickable || node.isLongClickable || node.isEditable ||
            node.isScrollable || node.isCheckable

        if (!interactive && label == null) return children

        val index = counter++
        nodeById[index] = node

        return listOf(
            ObservedNode(
                index = index,
                cls = simpleClass(node),
                text = text,
                desc = desc,
                viewId = node.viewIdResourceName,
                bounds = bounds,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                checked = node.isChecked,
                focusable = node.isFocusable,
                enabled = node.isEnabled,
                selected = node.isSelected,
                children = children,
            )
        )
    }

    private fun simpleClass(node: AccessibilityNodeInfo): String {
        val raw = node.className?.toString() ?: return "View"
        return raw.substringAfterLast('.')
    }

    companion object {
        const val ROOT_INDEX = -1
    }
}
