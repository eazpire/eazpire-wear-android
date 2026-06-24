package com.eazpire.wear.discovery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable

fun createArtifactMarkerDrawable(context: Context, inRange: Boolean): BitmapDrawable {
    val sizePx = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (inRange) 0xFFFF7A1A.toInt() else 0xFF9E9E9E.toInt()
        style = Paint.Style.FILL
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (inRange) 0x66FF7A1A else 0x449E9E9E
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx, ring)
    canvas.drawCircle(cx, cy, cx * 0.72f, outer)
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx * 0.28f, inner)
    return BitmapDrawable(context.resources, bitmap)
}
