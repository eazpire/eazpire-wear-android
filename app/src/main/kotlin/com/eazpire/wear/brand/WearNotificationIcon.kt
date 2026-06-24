package com.eazpire.wear.brand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eazpire.wear.R
import com.eazpire.wear.core.brand.BrandAssetSlots
import com.eazpire.wear.core.brand.BrandAssetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Notification small icons must be white alpha silhouettes.
 * Build-time [R.drawable.ic_notification_wear] is synced from admin on deploy;
 * this helper optionally refreshes from the live manifest URL and caches a bitmap.
 */
object WearNotificationIcon {
    @DrawableRes
    val fallbackRes: Int = R.drawable.ic_notification_wear

    @Volatile
    private var cachedSmallIcon: Bitmap? = null

    @DrawableRes
    fun smallIconRes(): Int = fallbackRes

    suspend fun warmFromAdmin(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val repo = BrandAssetsRepository.get(app)
        repo.refreshIfStale(force = false)
        val url = repo.urlFor(BrandAssetSlots.EAZ_APP_FAVICON) ?: return@withContext
        try {
            val request = ImageRequest.Builder(app).data(url).allowHardware(false).build()
            when (val result = app.imageLoader.execute(request)) {
                is SuccessResult -> {
                    val bmp = drawableToBitmap(result.drawable) ?: return@withContext
                    cachedSmallIcon = toMonochrome(bmp)
                }
                else -> Unit
            }
        } catch (_: Exception) {
        }
    }

    fun cachedSmallIconBitmap(): Bitmap? = cachedSmallIcon

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    private fun toMonochrome(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            pixels[i] = if (r < 40 && g < 40 && b < 40) 0 else 0xFFFFFFFF.toInt()
        }
        out.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return out
    }

    fun fallbackBitmap(context: Context): Bitmap? {
        return try {
            val d = ContextCompat.getDrawable(context, fallbackRes) ?: return null
            drawableToBitmap(d)
        } catch (_: Exception) {
            null
        }
    }
}
