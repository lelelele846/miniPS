package com.miniPE.photoediting

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 自定义裁剪视�?
 * 支持拖动和调整裁剪框大小
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(150, 0, 0, 0)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // 裁剪框边�?
    private var cropRect = RectF()
    private var minCropSize = 100f
    private var aspectRatio: Float? = null // null 表示自由比例

    // 触摸处理
    private var touchMode = TouchMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private enum class TouchMode {
        NONE,
        MOVE,           // 移动裁剪�?
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT,
        RESIZE_LEFT,
        RESIZE_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cropRect.isEmpty) {
            // 初始化裁剪框为中央区�?
            val padding = 50f
            cropRect = RectF(
                padding,
                padding,
                width - padding,
                height - padding
            )
            applyAspectRatio()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制遮罩�?
        val overlayPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(cropRect, Path.Direction.CCW)
        }
        canvas.drawPath(overlayPath, overlayPaint)

        // 绘制裁剪�?
        canvas.drawRect(cropRect, paint)

        // 绘制四个角的指示�?
        val cornerSize = 30f
        val cornerStroke = 6f

        // 左上�?
        canvas.drawRect(
            cropRect.left - cornerStroke / 2,
            cropRect.top - cornerStroke / 2,
            cropRect.left + cornerSize,
            cropRect.top + cornerStroke / 2,
            cornerPaint
        )
        canvas.drawRect(
            cropRect.left - cornerStroke / 2,
            cropRect.top - cornerStroke / 2,
            cropRect.left + cornerStroke / 2,
            cropRect.top + cornerSize,
            cornerPaint
        )

        // 右上�?
        canvas.drawRect(
            cropRect.right - cornerSize,
            cropRect.top - cornerStroke / 2,
            cropRect.right + cornerStroke / 2,
            cropRect.top + cornerStroke / 2,
            cornerPaint
        )
        canvas.drawRect(
            cropRect.right - cornerStroke / 2,
            cropRect.top - cornerStroke / 2,
            cropRect.right + cornerStroke / 2,
            cropRect.top + cornerSize,
            cornerPaint
        )

        // 左下�?
        canvas.drawRect(
            cropRect.left - cornerStroke / 2,
            cropRect.bottom - cornerStroke / 2,
            cropRect.left + cornerSize,
            cropRect.bottom + cornerStroke / 2,
            cornerPaint
        )
        canvas.drawRect(
            cropRect.left - cornerStroke / 2,
            cropRect.bottom - cornerSize,
            cropRect.left + cornerStroke / 2,
            cropRect.bottom + cornerStroke / 2,
            cornerPaint
        )

        // 右下�?
        canvas.drawRect(
            cropRect.right - cornerSize,
            cropRect.bottom - cornerStroke / 2,
            cropRect.right + cornerStroke / 2,
            cropRect.bottom + cornerStroke / 2,
            cornerPaint
        )
        canvas.drawRect(
            cropRect.right - cornerStroke / 2,
            cropRect.bottom - cornerSize,
            cropRect.right + cornerStroke / 2,
            cropRect.bottom + cornerStroke / 2,
            cornerPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchMode = getTouchMode(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (touchMode) {
                    TouchMode.MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        moveCropRect(dx, dy)
                    }
                    TouchMode.RESIZE_TOP_LEFT -> {
                        resizeCropRect(
                            event.x - lastTouchX,
                            event.y - lastTouchY,
                            0f, 0f
                        )
                    }
                    TouchMode.RESIZE_TOP_RIGHT -> {
                        resizeCropRect(
                            0f,
                            event.y - lastTouchY,
                            event.x - lastTouchX,
                            0f
                        )
                    }
                    TouchMode.RESIZE_BOTTOM_LEFT -> {
                        resizeCropRect(
                            event.x - lastTouchX,
                            0f,
                            0f,
                            event.y - lastTouchY
                        )
                    }
                    TouchMode.RESIZE_BOTTOM_RIGHT -> {
                        resizeCropRect(
                            0f,
                            0f,
                            event.x - lastTouchX,
                            event.y - lastTouchY
                        )
                    }
                    TouchMode.RESIZE_LEFT -> {
                        resizeCropRect(event.x - lastTouchX, 0f, 0f, 0f)
                    }
                    TouchMode.RESIZE_RIGHT -> {
                        resizeCropRect(0f, 0f, event.x - lastTouchX, 0f)
                    }
                    TouchMode.RESIZE_TOP -> {
                        resizeCropRect(0f, event.y - lastTouchY, 0f, 0f)
                    }
                    TouchMode.RESIZE_BOTTOM -> {
                        resizeCropRect(0f, 0f, 0f, event.y - lastTouchY)
                    }
                    else -> {}
                }
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getTouchMode(x: Float, y: Float): TouchMode {
        val touchTolerance = 50f

        // 检查是否在裁剪框内（用于移动）
        if (cropRect.contains(x, y)) {
            return TouchMode.MOVE
        }

        // 检查四个角
        if (distance(x, y, cropRect.left, cropRect.top) < touchTolerance) {
            return TouchMode.RESIZE_TOP_LEFT
        }
        if (distance(x, y, cropRect.right, cropRect.top) < touchTolerance) {
            return TouchMode.RESIZE_TOP_RIGHT
        }
        if (distance(x, y, cropRect.left, cropRect.bottom) < touchTolerance) {
            return TouchMode.RESIZE_BOTTOM_LEFT
        }
        if (distance(x, y, cropRect.right, cropRect.bottom) < touchTolerance) {
            return TouchMode.RESIZE_BOTTOM_RIGHT
        }

        // 检查四条边
        if (kotlin.math.abs(x - cropRect.left) < touchTolerance && y in cropRect.top..cropRect.bottom) {
            return TouchMode.RESIZE_LEFT
        }
        if (kotlin.math.abs(x - cropRect.right) < touchTolerance && y in cropRect.top..cropRect.bottom) {
            return TouchMode.RESIZE_RIGHT
        }
        if (kotlin.math.abs(y - cropRect.top) < touchTolerance && x in cropRect.left..cropRect.right) {
            return TouchMode.RESIZE_TOP
        }
        if (kotlin.math.abs(y - cropRect.bottom) < touchTolerance && x in cropRect.left..cropRect.right) {
            return TouchMode.RESIZE_BOTTOM
        }

        return TouchMode.NONE
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
    }

    private fun moveCropRect(dx: Float, dy: Float) {
        val newLeft = cropRect.left + dx
        val newTop = cropRect.top + dy
        val newRight = cropRect.right + dx
        val newBottom = cropRect.bottom + dy

        if (newLeft >= 0 && newRight <= width && newTop >= 0 && newBottom <= height) {
            cropRect.offset(dx, dy)
        }
    }

    private fun resizeCropRect(dLeft: Float, dTop: Float, dRight: Float, dBottom: Float) {
        var newLeft = cropRect.left + dLeft
        var newTop = cropRect.top + dTop
        var newRight = cropRect.right + dRight
        var newBottom = cropRect.bottom + dBottom

        // 确保裁剪框在视图范围�?
        newLeft = max(0f, min(newLeft, width - minCropSize))
        newTop = max(0f, min(newTop, height - minCropSize))
        newRight = max(newLeft + minCropSize, min(newRight, width.toFloat()))
        newBottom = max(newTop + minCropSize, min(newBottom, height.toFloat()))

        cropRect.set(newLeft, newTop, newRight, newBottom)
        applyAspectRatio()
    }

    private fun applyAspectRatio() {
        aspectRatio?.let { ratio ->
            val currentWidth = cropRect.width()
            val currentHeight = cropRect.height()
            val currentRatio = currentWidth / currentHeight

            if (kotlin.math.abs(currentRatio - ratio) > 0.01f) {
                // 需要调整以保持比例
                val centerX = cropRect.centerX()
                val centerY = cropRect.centerY()

                val newWidth: Float
                val newHeight: Float

                if (currentRatio > ratio) {
                    // 太宽了，以高度为�?
                    newHeight = currentHeight
                    newWidth = newHeight * ratio
                } else {
                    // 太高了，以宽度为�?
                    newWidth = currentWidth
                    newHeight = newWidth / ratio
                }

                cropRect.set(
                    centerX - newWidth / 2,
                    centerY - newHeight / 2,
                    centerX + newWidth / 2,
                    centerY + newHeight / 2
                )

                // 确保在边界内
                if (cropRect.left < 0) {
                    cropRect.offset(-cropRect.left, 0f)
                }
                if (cropRect.top < 0) {
                    cropRect.offset(0f, -cropRect.top)
                }
                if (cropRect.right > width) {
                    cropRect.offset(width - cropRect.right, 0f)
                }
                if (cropRect.bottom > height) {
                    cropRect.offset(0f, height - cropRect.bottom)
                }
            }
        }
    }

    /**
     * 设置宽高�?
     * @param aspectX 宽度比例
     * @param aspectY 高度比例
     * @param resetToFull 是否重置为全图（切换比例时使用）
     */
    fun setAspectRatio(aspectX: Int, aspectY: Int, resetToFull: Boolean = false) {
        aspectRatio = if (aspectX > 0 && aspectY > 0) {
            aspectX.toFloat() / aspectY.toFloat()
        } else {
            null
        }
        
        // 如果切换比例，重新初始化裁剪框为全图
        if (resetToFull && width > 0 && height > 0) {
            resetCropRectToFull()
        } else {
            applyAspectRatio()
        }
        
        invalidate()
    }
    
    /**
     * 重置裁剪框为全图
     */
    private fun resetCropRectToFull() {
        val padding = 50f
        val availableWidth = width - padding * 2
        val availableHeight = height - padding * 2
        
        if (aspectRatio != null) {
            // 有比例约束，计算合适的尺寸
            val ratio = aspectRatio!!
            val centerX = width / 2f
            val centerY = height / 2f
            
            val newWidth: Float
            val newHeight: Float
            
            if (availableWidth / availableHeight > ratio) {
                // 视图更宽，以高度为准
                newHeight = availableHeight
                newWidth = newHeight * ratio
            } else {
                // 视图更高，以宽度为准
                newWidth = availableWidth
                newHeight = newWidth / ratio
            }
            
            cropRect = RectF(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
            )
        } else {
            // 自由比例，使用整个可用区�?
            cropRect = RectF(
                padding,
                padding,
                width - padding,
                height - padding
            )
        }
    }

    /**
     * 获取裁剪区域（相对于视图的坐标）
     */
    fun getCropRect(): RectF {
        return RectF(cropRect)
    }
    
    /**
     * 设置裁剪框的位置和大�?
     */
    fun setCropRect(left: Float, top: Float, right: Float, bottom: Float) {
        cropRect.set(
            max(0f, min(left, width.toFloat())),
            max(0f, min(top, height.toFloat())),
            max(left, min(right, width.toFloat())),
            max(top, min(bottom, height.toFloat()))
        )
        applyAspectRatio()
        invalidate()
    }

    /**
     * 设置图片的实际尺寸（用于计算裁剪区域�?
     */
    fun setImageSize(imageWidth: Int, imageHeight: Int) {
        // 这里可以用于计算实际的裁剪区�?
        // 当前实现使用视图坐标
    }
}
