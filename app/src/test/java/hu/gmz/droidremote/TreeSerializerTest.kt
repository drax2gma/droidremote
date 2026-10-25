package hu.gmz.droidremote

import hu.gmz.droidremote.observe.Bounds
import hu.gmz.droidremote.observe.ObservedNode
import hu.gmz.droidremote.observe.TreeSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSerializerTest {

    private fun node(
        index: Int,
        cls: String = "TextView",
        text: String? = null,
        desc: String? = null,
        bounds: Bounds = Bounds(0, 0, 100, 50),
        clickable: Boolean = false,
        editable: Boolean = false,
        children: List<ObservedNode> = emptyList(),
    ) = ObservedNode(
        index = index,
        cls = cls,
        text = text,
        desc = desc,
        bounds = bounds,
        clickable = clickable,
        editable = editable,
        children = children,
    )

    @Test
    fun headerContainsScreenAndPackage() {
        val root = node(-1, cls = "root", bounds = Bounds(0, 0, 1080, 1920))
        val out = TreeSerializer.serialize(root, packageName = "com.android.settings", windowTitle = "Beállítások")
        assertTrue(out.startsWith("pkg=com.android.settings"))
        assertTrue(out.contains("screen=1080x1920"))
        assertTrue(out.contains("nodes=0"))
    }

    @Test
    fun rendersLabelsFlagsAndBounds() {
        val root = node(
            -1,
            cls = "root",
            bounds = Bounds(0, 0, 1080, 1920),
            children = listOf(
                node(3, "Button", text = "Wi-Fi", bounds = Bounds(40, 300, 1040, 420), clickable = true),
                node(4, "EditText", text = "", bounds = Bounds(40, 500, 1040, 580), editable = true),
            ),
        )
        val out = TreeSerializer.serialize(root)

        assertTrue(out.contains("@3 Button \"Wi-Fi\" (40,300-1040,420) c"))
        assertTrue(out.contains("@4 EditText (40,500-1040,580) e"))
    }

    @Test
    fun skipsNodesWithoutLabelOrFlags() {
        val root = node(
            -1,
            cls = "root",
            bounds = Bounds(0, 0, 1080, 1920),
            children = listOf(node(1, "FrameLayout", bounds = Bounds(0, 0, 10, 10))),
        )
        val out = TreeSerializer.serialize(root)
        assertTrue(!out.contains("@1"))
    }

    @Test
    fun truncatesLongLabelsAndKeepsOneLine() {
        val long = "a".repeat(200) + "\nb"
        val root = node(
            -1,
            cls = "root",
            bounds = Bounds(0, 0, 1080, 1920),
            children = listOf(node(2, "TextView", text = long, clickable = true)),
        )
        val out = TreeSerializer.serialize(root, maxText = 20)
        assertTrue(!out.contains("\n\n"))
        assertTrue(out.contains("…"))
    }

    @Test
    fun reportsTruncationWhenNodeBudgetExceeded() {
        val children = (0 until 30).map {
            node(it, "TextView", text = "item $it", bounds = Bounds(0, it * 10, 100, it * 10 + 10))
        }
        val root = node(-1, cls = "root", bounds = Bounds(0, 0, 1080, 1920), children = children)
        val out = TreeSerializer.serialize(root, maxNodes = 5)
        assertTrue(out.contains("more nodes"))
        assertEquals(5, out.lines().count { it.startsWith("@") })
    }

    @Test
    fun descriptionIsUsedWhenTextIsBlank() {
        val root = node(
            -1,
            cls = "root",
            bounds = Bounds(0, 0, 1080, 1920),
            children = listOf(node(9, "ImageButton", text = "   ", desc = "Keresés", clickable = true)),
        )
        val out = TreeSerializer.serialize(root)
        assertTrue(out.contains("\"Keresés\""))
    }
}
