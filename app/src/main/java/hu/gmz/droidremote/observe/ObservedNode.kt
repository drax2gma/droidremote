package hu.gmz.droidremote.observe

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

data class ObservedNode(
    val index: Int,
    val cls: String,
    val text: String? = null,
    val desc: String? = null,
    val viewId: String? = null,
    val bounds: Bounds = Bounds.EMPTY,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focusable: Boolean = false,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val children: List<ObservedNode> = emptyList(),
) {
    val label: String
        get() = text?.takeIf { it.isNotBlank() } ?: desc?.takeIf { it.isNotBlank() } ?: ""

    val actionable: Boolean
        get() = clickable || longClickable || editable || scrollable || checkable

    fun walk(): Sequence<ObservedNode> = sequence {
        yield(this@ObservedNode)
        for (child in children) yieldAll(child.walk())
    }
}
