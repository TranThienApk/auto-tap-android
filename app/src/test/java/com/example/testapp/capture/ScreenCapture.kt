package com.example.testapp.capture

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class ScreenCapture(private val context: Context) {

    private var imageReader: ImageReader? = null
    private var vd: android.hardware.display.VirtualDisplay? = null
    private var projection: MediaProjection? = null

    private val executor = Executors.newSingleThreadExecutor()

    fun start(
        mediaProjection: MediaProjection,
        width: Int, height: Int, densityDpi: Int,
        onFrame: (Bitmap) -> Unit
    ) {
        stop()

        projection = mediaProjection
        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bmp = imageToBitmap(image)
                if (bmp != null) onFrame(bmp)
            } catch (_: Throwable) {
                // ignore
            } finally {
                image.close()
            }
        }, android.os.Handler(android.os.Looper.getMainLooper())) // main looper đủ dùng

        vd = projection?.createVirtualDisplay(
            "SC_CAPTURE",
            width, height, densityDpi,
            0,
            imageReader?.surface as Surface,
            null, null
        )
    }

    fun stop() {
        try { vd?.release() } catch (_: Throwable) {}
        vd = null
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
        // Không stop projection ở đây; để MainActivity quản lý lifecycle
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        // Tạo bitmap có tính đến rowPadding, sau đó crop về width thật
        val tmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        val buffer: ByteBuffer = plane.buffer
        tmp.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(tmp, 0, 0, image.width, image.height).also {
            if (tmp !== it) tmp.recycle()
        }
    }
}
