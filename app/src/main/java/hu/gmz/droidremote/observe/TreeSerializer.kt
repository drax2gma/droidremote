package hu.gmz.droidremote.observe

object TreeSerializer {

    fun serialize(
        root: ObservedNode,
        maxNodes: Int = 140,
        maxText: Int = 80,
        packageName: String? = null,
        windowTitle: String? = null,
    ): String {
        val sb = StringBuilder(4096)
        val total = root.walk().count() - 1
        sb.append("pkg=").append(packageName ?: "?")
        if (!windowTitle.isNullOrBlank()) {
            sb.append(" window=\"").append(oneLine(windowTitle, 40)).append('"')
        }
        sb.append(" nodes=").append(total)
        sb.append(" screen=").append(root.bounds.width).append('x').append(root.bounds.height)
        sb.append('\n')

        var emitted = 0
        var skipped = 0

        fun visit(node: ObservedNode, depth: Int) {
            if (emitted < maxNodes) {
                val line = render(node, depth, maxText)
                if (line == null) {
                    skipped++
                } else {
                    sb.append(line).append('\n')
                    emitted++
                }
            } else {
                skipped++
            }
            for (child in node.children) visit(child, depth + 1)
        }

        for (child in root.children) visit(child, 0)

        if (skipped > 0) sb.append("… ").append(skipped).append(" more nodes\n")
        return sb.toString()
    }

    fun render(node: ObservedNode, depth: Int, maxText: Int = 80): String? {
        val label = node.label
        val flags = flagsOf(node)
        if (label.isEmpty() && flags.isEmpty()) return null
        if (node.bounds.width <= 0 || node.bounds.height <= 0) return null

        val sb = StringBuilder(96)
        repeat(depth.coerceAtMost(6)) { sb.append("  ") }
        sb.append('@').append(node.index).append(' ').append(node.cls)
        if (label.isNotEmpty()) sb.append(" \"").append(oneLine(label, maxText)).append('"')
        sb.append(" (").append(node.bounds.left).append(',').append(node.bounds.top)
            .append('-').append(node.bounds.right).append(',').append(node.bounds.bottom).append(')')
        if (flags.isNotEmpty()) sb.append(' ').append(flags)
        val viewId = node.viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it.length < 40 }
        if (viewId != null) sb.append(" #").append(viewId)
        return sb.toString()
    }

    fun flagsOf(node: ObservedNode): String {
        val sb = StringBuilder(10)
        if (node.clickable) sb.append('c')
        if (node.longClickable) sb.append('l')
        if (node.editable) sb.append('e')
        if (node.scrollable) sb.append('s')
        if (node.checkable) sb.append('k')
        if (node.checked) sb.append('K')
        if (node.focusable) sb.append('f')
        if (node.selected) sb.append('o')
        if (!node.enabled) sb.append('d')
        return sb.toString()
    }

    fun oneLine(value: String, max: Int): String {
        val flat = value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'').trim()
        return if (flat.length <= max) flat else flat.take(max - 1) + "…"
    }
}
