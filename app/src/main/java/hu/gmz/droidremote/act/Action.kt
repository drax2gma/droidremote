package hu.gmz.droidremote.act

sealed interface Action {
    data class Tap(val node: Int) : Action
    data class TapAt(val x: Int, val y: Int) : Action
    data class LongPress(val x: Int, val y: Int, val ms: Long = 600) : Action
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val ms: Long = 300) : Action
    data class Type(val node: Int?, val text: String) : Action
    data class Scroll(val node: Int, val forward: Boolean) : Action
    data class Key(val code: String) : Action
    data class OpenApp(val query: String) : Action
    data object Screenshot : Action
    data class Wait(val ms: Long) : Action
    data class Done(val summary: String) : Action
    data class Fail(val why: String) : Action
}
