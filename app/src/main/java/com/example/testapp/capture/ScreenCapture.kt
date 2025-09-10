package com.example.testapp.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.view.Surface
import java.nio.ByteBuffer

class ScreenCapture(private val context: Context) {
    private var imageReader: ImageReader? = null
    private var vd: android.hardware.display.VirtualDisplay? = null
    private var projection: MediaProjection? = null
    private var handlerThread: android.os.HandlerThread? = null
    private var handler: android.os.Handler? = null

    fun start(mp: MediaProjection, width: Int, height: Int, dpi: Int, onFrame: (Bitmap) -> Unit) {
        stop()
        projection = mp
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        handlerThread = android.os.HandlerThread("SCapture").apply { start() }
        handler = android.os.Handler(handlerThread!!.looper)

        imageReader?.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { imageToBitmap(img)?.let(onFrame) } catch (_: Throwable) {} finally { img.close() }
        }, handler)

        vd = projection?.createVirtualDisplay(
            "SCAPTURE", width, height, dpi, 0, imageReader?.surface as Surface, null, null
        )
    }

    fun stop() {
        try { vd?.release() } catch (_: Throwable) {}
        vd = null
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
        try { handlerThread?.quitSafely() } catch (_: Throwable) {}
        handler = null; handlerThread = null
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes[0]
        val ps = plane.pixelStride
        val rs = plane.rowStride
        val pad = rs - ps * image.width
        val tmp = Bitmap.createBitmap(image.width + maxOf(0, pad / ps), image.height, Bitmap.Config.ARGB_8888)
        val buf: ByteBuffer = plane.buffer
        buf.rewind()
        tmp.copyPixelsFromBuffer(buf)
        val out = Bitmap.createBitmap(tmp, 0, 0, image.width, image.height)
        if (out !== tmp) tmp.recycle()
        return out
    }
}
