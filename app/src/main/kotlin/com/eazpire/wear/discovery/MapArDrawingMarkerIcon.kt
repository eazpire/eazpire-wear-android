package com.eazpire.wear.discovery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable

/** Purple brush marker for saved AR drawings on the discovery map. */
fun createArDrawingMarkerDrawable(context: Context, inRange: Boolean): BitmapDrawable {
    val sizePx = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val purple = if (inRange) 0xFF9B59B6.toInt() else 0xFF7A6A8A.toInt()
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (inRange) 0x669B59B6 else 0x447A6A8A
        style = Paint.Style.FILL
    }
    val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = purple
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, cx, ring)
    canvas.drawCircle(cx, cy, cx * 0.72f, outer)
    val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.08f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx * 0.55f, cy * 1.15f, cx * 1.35f, cy * 0.35f, brush)
    canvas.drawCircle(cx * 1.35f, cy * 0.35f, sizePx * 0.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    })
    return BitmapDrawable(context.resources, bitmap)
}
