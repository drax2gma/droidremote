package hu.gmz.droidremote.trace

import android.content.Context
import java.io.File

object DebugExport {

    fun dir(context: Context): File = File(context.filesDir, "debug").apply { mkdirs() }

    fun text(context: Context, name: String, value: String) {
        runCatching { File(dir(context), name).writeText(value) }
    }

    fun bytes(context: Context, name: String, value: ByteArray) {
        runCatching { File(dir(context), name).writeBytes(value) }
    }
}
