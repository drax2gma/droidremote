package hu.gmz.droidremote.observe

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.SystemClock
import android.view.Display
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

class ScreenCapture(private val service: AccessibilityService) {

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var lastCaptureAt = 0L

    suspend fun capture(maxLongEdge: Int = 1280, quality: Int = 70): CaptureResult {
        val since = SystemClock.elapsedRealtime() - lastCaptureAt
        if (since in 0 until MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - since)
        val result = request(maxLongEdge, quality)
        lastCaptureAt = SystemClock.elapsedRealtime()
        return result
    }

    private suspend fun request(maxLongEdge: Int, quality: Int): CaptureResult =
        suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (!cont.isActive) return
                        val outcome = runCatching { toJpeg(screenshot, maxLongEdge, quality) }
                        cont.resume(
                            outcome.fold(
                                onSuccess = { CaptureResult.Success(it) },
                                onFailure = { CaptureResult.Failure("feldolgozási hiba: ${it.message}") },
                            )
                        )
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(CaptureResult.Failure(describe(errorCode)))
                    }
                },
            )
        }

    private fun toJpeg(
        screenshot: AccessibilityService.ScreenshotResult,
        maxLongEdge: Int,
        quality: Int,
    ): ByteArray {
        val buffer = screenshot.hardwareBuffer
        val wrapped = try {
            Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
        } finally {
            buffer.close()
        }
        requireNotNull(wrapped) { "hardware buffer nem dekódolható" }

        val soft = wrapped.copy(Bitmap.Config.ARGB_8888, false)
        val longest = max(soft.width, soft.height)
        val scaled = if (longest > maxLongEdge) {
            val scale = maxLongEdge.toFloat() / longest
            Bitmap.createScaledBitmap(
                soft,
                (soft.width * scale).roundToInt().coerceAtLeast(1),
                (soft.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            soft
        }

        val out = ByteArrayOutputStream(128 * 1024)
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun describe(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "túl gyors kérés (throttle)"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "nincs a11y hozzáférés"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "érvénytelen display"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "belső hiba"
        -5 -> "FLAG_SECURE képernyő (nem olvasható)"
        else -> "ismeretlen hiba ($code)"
    }

    sealed interface CaptureResult {
        data class Success(val jpeg: ByteArray) : CaptureResult
        data class Failure(val reason: String) : CaptureResult
    }

    companion object {
        private const val MIN_INTERVAL_MS = 350L
    }
}
