package hu.gmz.droidremote

import hu.gmz.droidremote.act.Action
import hu.gmz.droidremote.act.ActionCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionCodecTest {

    @Test
    fun parsesTapByNode() {
        val action = ActionCodec.parse("""{"a":"tap","n":12}""").getOrThrow()
        assertEquals(Action.Tap(12), action)
    }

    @Test
    fun parsesTapByPixel() {
        val action = ActionCodec.parse("""{"a":"tap","x":540,"y":1200}""").getOrThrow()
        assertEquals(Action.TapAt(540, 1200), action)
    }

    @Test
    fun parsesTypeWithUnicode() {
        val action = ActionCodec.parse("""{"a":"type","n":7,"t":"Szia világ"}""").getOrThrow()
        assertEquals(Action.Type(7, "Szia világ"), action)
    }

    @Test
    fun parsesTypeWithoutNodeForFocusedField() {
        val action = ActionCodec.parse("""{"a":"type","t":"42"}""").getOrThrow()
        assertEquals(Action.Type(null, "42"), action)
    }

    @Test
    fun parsesScrollDirection() {
        assertEquals(Action.Scroll(3, true), ActionCodec.parse("""{"a":"scroll","n":3,"dir":"down"}""").getOrThrow())
        assertEquals(Action.Scroll(3, false), ActionCodec.parse("""{"a":"scroll","n":3,"dir":"up"}""").getOrThrow())
    }

    @Test
    fun toleratesCodeFencesAndProse() {
        val raw = "Sure, here you go:\n```json\n{\"a\":\"key\",\"k\":\"back\"}\n```\nDone."
        assertEquals(Action.Key("BACK"), ActionCodec.parse(raw).getOrThrow())
    }

    @Test
    fun rejectsUnknownAction() {
        assertTrue(ActionCodec.parse("""{"a":"teleport","n":1}""").isFailure)
    }

    @Test
    fun rejectsReplyWithoutJson() {
        assertTrue(ActionCodec.parse("Nem tudom.").isFailure)
    }

    @Test
    fun parsesOpenAppByLabelAndPackage() {
        assertEquals(Action.OpenApp("Settings"), ActionCodec.parse("""{"a":"open","q":"Settings"}""").getOrThrow())
        assertEquals(
            Action.OpenApp("com.android.settings"),
            ActionCodec.parse("""{"a":"open","pkg":"com.android.settings"}""").getOrThrow(),
        )
    }

    @Test
    fun rejectsOpenWithoutQuery() {
        assertTrue(ActionCodec.parse("""{"a":"open"}""").isFailure)
    }

    @Test
    fun parsesScriptArray() {
        val raw = """[{"a":"key","k":"HOME"},{"a":"wait","ms":800},{"a":"shot"}]"""
        val actions = ActionCodec.parseArray(raw).getOrThrow()
        assertEquals(3, actions.size)
        assertEquals(Action.Key("HOME"), actions[0])
        assertEquals(Action.Wait(800), actions[1])
        assertEquals(Action.Screenshot, actions[2])
    }

    @Test
    fun describeIsCompact() {
        assertEquals("tap @12", ActionCodec.describe(Action.Tap(12)))
        assertEquals("key HOME", ActionCodec.describe(Action.Key("HOME")))
    }
}
