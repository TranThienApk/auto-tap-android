package com.example.testapp.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer

class ScreenCapture(private val context: Context) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null

    fun start(
        projection: MediaProjection,
        width: Int,
        height: Int,
        density: Int,
        onFrame: (Bitmap) -> Unit
    ) {
        stop()

        handlerThread = HandlerThread("SC-Capture").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "AutoTapActivityCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bmp = image.toBitmapSafely()
            image.close()
            if (bmp != null) onFrame(bmp)
        }, handler)
    }

    fun stop() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        handlerThread?.quitSafely(); handlerThread = null
    }
}

private fun Image.toBitmapSafely(): Bitmap? {
    val plane = planes[0]
    val buffer: ByteBuffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width

    val tmp = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888
    )
    tmp.copyPixelsFromBuffer(buffer)
    return if (rowPadding != 0) Bitmap.createBitmap(tmp, 0, 0, width, height) else tmp
}
