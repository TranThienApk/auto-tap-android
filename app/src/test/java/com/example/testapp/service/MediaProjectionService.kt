package com.example.testapp.service

import android.app.Service import android.content.Context import android.content.Intent import android.graphics.Bitmap import android.graphics.Color import android.graphics.PixelFormat import android.hardware.display.DisplayManager import android.hardware.display.VirtualDisplay import android.media.ImageReader import android.media.projection.MediaProjection import android.media.projection.MediaProjectionManager import android.os.Handler import android.os.IBinder import android.os.Looper import android.util.Log import android.util.Resources import android.view.WindowManager

class MediaProjectionService : Service() {

companion object {
    const val TAG = "MediaProjectionService"
    const val ACTION_START = "START_CAPTURE"
    const val EXTRA_RESULT_CODE = "RESULT_CODE"
    const val EXTRA_DATA = "RESULT_DATA"
}

private var mediaProjection: MediaProjection? = null
private var virtualDisplay: VirtualDisplay? = null
private var imageReader: ImageReader? = null

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_START) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
        startCapture()
    }
    return START_NOT_STICKY
}

private fun startCapture() {
    val metrics = resources.displayMetrics
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val density = metrics.densityDpi

    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

    virtualDisplay = mediaProjection?.createVirtualDisplay(
        "ScreenCapture",
        width, height, density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader?.surface, null, null
    )

    imageReader?.setOnImageAvailableListener({ reader ->
        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        detectHp(bitmap)
    }, Handler(Looper.getMainLooper()))
}

private fun detectHp(bitmap: Bitmap) {
    val x = 100
    val y = 200
    if (x >= bitmap.width || y >= bitmap.height) return

    val pixel = bitmap.getPixel(x, y)
    val red = Color.red(pixel)
    val green = Color.green(pixel)
    val blue = Color.blue(pixel)

    Log.d(TAG, "Pixel at ($x,$y): R=$red, G=$green, B=$blue")

    if (red > 200 && green < 100 && blue < 100) {
        Log.d(TAG, "HP còn nhiều")
    } else {
        Log.d(TAG, "HP thấp, cần xử lý!")
        // TODO: gửi intent tới AutoAccessibilityService để tap hồi máu
    }
}

override fun onDestroy() {
    super.onDestroy()
    virtualDisplay?.release()
    imageReader?.close()
    mediaProjection?.stop()
}

override fun onBind(intent: Intent?): IBinder? = null

}

