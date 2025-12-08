package com.miniPE.photoediting

import android.graphics.*
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object GradientMaskProcessor {

    fun applyGradientMask(
        source: Bitmap,
        angle: Float,
        softness: Float
    ): Bitmap {
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val angleRad = Math.toRadians(angle.toDouble())
        val diagonal = hypot(width.toDouble(), height.toDouble()).toFloat()
        val halfDiagonal = diagonal / 2f
        val dx = cos(angleRad).toFloat() * halfDiagonal
        val dy = sin(angleRad).toFloat() * halfDiagonal
        val centerX = width / 2f
        val centerY = height / 2f
        val startX = centerX - dx
        val startY = centerY - dy
        val endX = centerX + dx
        val endY = centerY + dy

        val fadeStart = (1f - softness).coerceIn(0.05f, 0.95f)
        val colors = intArrayOf(
            Color.argb(255, 255, 255, 255),
            Color.argb(255, 255, 255, 255),
            Color.argb(0, 255, 255, 255)
        )
        val positions = floatArrayOf(0f, fadeStart, 1f)

        val gradient = LinearGradient(
            startX,
            startY,
            endX,
            endY,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        source.recycle()
        return result
    }
}

