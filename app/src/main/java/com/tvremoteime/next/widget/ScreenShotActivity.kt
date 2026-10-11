package com.tvremoteime.next.widget

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.view.Window
import com.tvremoteime.next.core.Env
import com.tvremoteime.next.server.ServerFileManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * MediaProjection 截屏（对应旧版 widget/ScreenShotActivity.java）
 *
 * 无 UI 透明 Activity：启动即请求录屏授权，成功后截一帧存 dataDir/screenshot.png。
 * root 通道（阶段 4 RootChannel.screencap）之外的截屏保底方案。
 */
class ScreenShotActivity : Activity() {

    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mImageReader: ImageReader? = null
    private var mMediaProjectionManager: MediaProjectionManager? = null

    interface OnSavedListener {
        fun onSaved(success: Boolean)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setDimAmount(0f)

        requestScreenShot()
    }

    fun requestScreenShot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                startActivityForResult(
                    mMediaProjectionManager?.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION
                )
            } catch (e: Exception) {
                finish()
            }
        } else {
            finish()
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    mMediaProjection = mMediaProjectionManager?.getMediaProjection(resultCode, data)
                    startVirtualDisplay()
                } catch (e: Exception) {
                    finish()
                }
            } else {
                onSavedListener?.onSaved(false)
                finish()
            }
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.LOLLIPOP)
    private fun startVirtualDisplay() {
        val metrics = Resources.getSystem().displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        mMediaProjection?.createVirtualDisplay(
            "screen_shot", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mImageReader!!.surface, null, null
        )
        Handler().postDelayed({ saveImage() }, 300)
    }

    @TargetApi(android.os.Build.VERSION_CODES.LOLLIPOP)
    private fun saveImage() {
        val image: Image? = try {
            mImageReader?.acquireLatestImage()
        } catch (e: Exception) {
            null
        }
        if (image == null) {
            // 首帧未就绪，延迟重试
            Handler().postDelayed({ saveImage() }, 200)
            return
        }
        val width = image.width
        val height = image.height
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        // 裁掉 rowPadding
        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        image.close()

        val outputFile = ServerFileManager.getScreenShotFile()
        var success = false
        try {
            outputFile.parentFile?.mkdirs()
            val fos = FileOutputStream(outputFile)
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
            success = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        finalBitmap.recycle()

        onSavedListener?.onSaved(success)
        stopProjection()
        finish()
    }

    @TargetApi(android.os.Build.VERSION_CODES.LOLLIPOP)
    private fun stopProjection() {
        try {
            mVirtualDisplay?.release()
            mMediaProjection?.stop()
        } catch (ignored: Exception) {
        }
    }

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 0x8000
        var onSavedListener: OnSavedListener? = null
    }
}
