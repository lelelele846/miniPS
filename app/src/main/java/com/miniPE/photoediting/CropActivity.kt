package com.miniPE.photoediting

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.miniPE.photoediting.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import java.io.File
import java.io.FileOutputStream

/**
 * 图片裁剪 Activity
 * 整合裁剪、旋转、比例调整功�?
 */
class CropActivity : AppCompatActivity() {
    
    private lateinit var cropImageView: ImageView
    private lateinit var cropOverlayView: CropImageView
    private lateinit var btnRotate: Button
    private lateinit var btnCrop: Button
    private lateinit var btnCancel: Button
    private lateinit var btnRatioFree: Button
    private lateinit var btnRatio1_1: Button
    private lateinit var btnRatio4_3: Button
    private lateinit var btnRatio16_9: Button
    private lateinit var btnRatio3_4: Button
    private lateinit var btnRatio9_16: Button
    private lateinit var tvAspectRatio: TextView
    
    private var sourceBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var currentAspectX = 0
    private var currentAspectY = 0
    
    // 图片�?ImageView 中的显示信息
    private val imageMatrix = Matrix()
    private var imageDisplayRect = RectF()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        
        initViews()
        loadImage()
        setupListeners()
    }
    
    private fun initViews() {
        cropImageView = findViewById(R.id.cropImageView)
        cropOverlayView = findViewById(R.id.cropOverlayView)
        btnRotate = findViewById(R.id.btnRotate)
        btnCrop = findViewById(R.id.btnCrop)
        btnCancel = findViewById(R.id.btnCancel)
        btnRatioFree = findViewById(R.id.btnRatioFree)
        btnRatio1_1 = findViewById(R.id.btnRatio1_1)
        btnRatio4_3 = findViewById(R.id.btnRatio4_3)
        btnRatio16_9 = findViewById(R.id.btnRatio16_9)
        btnRatio3_4 = findViewById(R.id.btnRatio3_4)
        btnRatio9_16 = findViewById(R.id.btnRatio9_16)
        tvAspectRatio = findViewById(R.id.tvAspectRatio)
        
        // 获取初始裁剪参数
        currentAspectX = intent.getIntExtra("aspect_x", 0)
        currentAspectY = intent.getIntExtra("aspect_y", 0)
        
        // 设置初始比例
        if (currentAspectX > 0 && currentAspectY > 0) {
            tvAspectRatio.text = "比例: ${currentAspectX}:${currentAspectY}"
            cropOverlayView.setAspectRatio(currentAspectX, currentAspectY)
            updateRatioButtonState(currentAspectX, currentAspectY)
        } else {
            tvAspectRatio.text = "比例: 自由"
            updateRatioButtonState(0, 0)
        }
    }
    
    /**
     * 更新比例按钮的选中状�?
     */
    private fun updateRatioButtonState(aspectX: Int, aspectY: Int) {
        // 重置所有按钮状�?
        val buttons = listOf(btnRatioFree, btnRatio1_1, btnRatio4_3, btnRatio16_9, btnRatio3_4, btnRatio9_16)
        buttons.forEach { 
            it.isSelected = false
            it.setBackgroundColor(0xFF333333.toInt()) // 未选中背景�?
        }
        
        // 设置当前选中的按�?
        val selectedButton = when {
            aspectX == 0 && aspectY == 0 -> btnRatioFree
            aspectX == 1 && aspectY == 1 -> btnRatio1_1
            aspectX == 4 && aspectY == 3 -> btnRatio4_3
            aspectX == 16 && aspectY == 9 -> btnRatio16_9
            aspectX == 3 && aspectY == 4 -> btnRatio3_4
            aspectX == 9 && aspectY == 16 -> btnRatio9_16
            else -> null
        }
        
        selectedButton?.let {
            it.isSelected = true
            it.setBackgroundColor(0xFF4CAF50.toInt()) // 选中背景色（绿色�?
        }
    }
    
    private fun loadImage() {
        val imageUriString = intent.getStringExtra("image_uri") ?: return
        val imageUri = Uri.parse(imageUriString)
        
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
                
                if (bitmap != null) {
                    sourceBitmap = bitmap
                    currentBitmap = bitmap
                    withContext(Dispatchers.Main) {
                        cropImageView.setImageBitmap(bitmap)
                        // 设置图片显示方式
                        cropImageView.scaleType = ImageView.ScaleType.MATRIX
                        updateImageDisplayInfo()
                        // 图片加载完成后，初始化裁剪框
                        resetCropRectToImageBounds()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
            }
        }
    }
    
    private fun setupListeners() {
        // 旋转按钮：每次点击稳定旋�?90°
        btnRotate.setOnClickListener {
            rotateCurrentBitmap(90f)
        }
        
        // 比例选择按钮
        btnRatioFree.setOnClickListener {
            setAspectRatio(0, 0)
        }
        
        btnRatio1_1.setOnClickListener {
            setAspectRatio(1, 1)
        }
        
        btnRatio4_3.setOnClickListener {
            setAspectRatio(4, 3)
        }
        
        btnRatio16_9.setOnClickListener {
            setAspectRatio(16, 9)
        }
        
        btnRatio3_4.setOnClickListener {
            setAspectRatio(3, 4)
        }
        
        btnRatio9_16.setOnClickListener {
            setAspectRatio(9, 16)
        }
        
        // 裁剪按钮
        btnCrop.setOnClickListener {
            saveCroppedImage()
        }
        
        // 取消按钮
        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
    
    /**
     * 设置裁剪比例
     */
    private fun setAspectRatio(aspectX: Int, aspectY: Int) {
        currentAspectX = aspectX
        currentAspectY = aspectY
        
        if (aspectX > 0 && aspectY > 0) {
            tvAspectRatio.text = "比例: ${aspectX}:${aspectY}"
        } else {
            tvAspectRatio.text = "比例: 自由"
        }
        
        // 先设置比例约�?
        cropOverlayView.setAspectRatio(aspectX, aspectY, resetToFull = false)
        
        // 然后重置裁剪框以适应图片显示区域（基于原图，而不是在现有裁剪框基础上调整）
        resetCropRectToImageBounds()
        
        updateRatioButtonState(aspectX, aspectY)
    }
    
    /**
     * 将当前图片按指定角度旋转（相对当前状态，每次点击 +degrees�?
     */
    private fun rotateCurrentBitmap(degrees: Float) {
        val bitmap = currentBitmap ?: sourceBitmap ?: return

        lifecycleScope.launch {
            val transformed = withContext(Dispatchers.Default) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
            }

            // 更新当前显示的位�?
            currentBitmap = transformed

            withContext(Dispatchers.Main) {
                cropImageView.setImageBitmap(transformed)
                cropImageView.scaleType = ImageView.ScaleType.MATRIX
                updateImageDisplayInfo()
                // 更新图片显示后，重置裁剪框以适应新的图片尺寸
                resetCropRectToImageBounds()
            }
        }
    }
    
    /**
     * 重置裁剪框以适应图片显示区域
     */
    private fun resetCropRectToImageBounds() {
        if (imageDisplayRect.isEmpty) return
        
        val padding = 20f
        val availableWidth = imageDisplayRect.width() - padding * 2
        val availableHeight = imageDisplayRect.height() - padding * 2
        
        val centerX = imageDisplayRect.centerX()
        val centerY = imageDisplayRect.centerY()
        
        if (currentAspectX > 0 && currentAspectY > 0) {
            // 有比例约�?
            val ratio = currentAspectX.toFloat() / currentAspectY.toFloat()
            val newWidth: Float
            val newHeight: Float
            
            if (availableWidth / availableHeight > ratio) {
                // 可用区域更宽，以高度为准
                newHeight = availableHeight
                newWidth = newHeight * ratio
            } else {
                // 可用区域更高，以宽度为准
                newWidth = availableWidth
                newHeight = newWidth / ratio
            }
            
            cropOverlayView.setCropRect(
                centerX - newWidth / 2,
                centerY - newHeight / 2,
                centerX + newWidth / 2,
                centerY + newHeight / 2
            )
        } else {
            // 自由比例，使用整个图片显示区�?
            cropOverlayView.setCropRect(
                imageDisplayRect.left + padding,
                imageDisplayRect.top + padding,
                imageDisplayRect.right - padding,
                imageDisplayRect.bottom - padding
            )
        }
    }
    
    /**
     * 更新图片显示信息，用于计算裁剪区�?
     */
    private fun updateImageDisplayInfo() {
        val bitmap = currentBitmap ?: return
        val imageView = cropImageView
        
        if (imageView.width == 0 || imageView.height == 0) {
            // 视图尚未测量，延迟更�?
            imageView.post { updateImageDisplayInfo() }
            return
        }
        
        // 获取 ImageView 的边�?
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        
        // 计算图片�?ImageView 中的实际显示区域（centerInside 模式�?
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        
        val scale = min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        
        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2
        
        imageDisplayRect = RectF(
            left,
            top,
            left + scaledWidth,
            top + scaledHeight
        )
        
        // 设置 ImageView �?Matrix 以正确显示图�?
        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(left, top)
        imageView.imageMatrix = imageMatrix
    }
    
    private fun saveCroppedImage() {
        val bitmap = currentBitmap ?: return
        
        lifecycleScope.launch {
            try {
                // 获取裁剪框区域（相对于视图）
                val cropRect = cropOverlayView.getCropRect()
                
                // 将视图坐标转换为图片坐标
                val bitmap = currentBitmap ?: return@launch
                val bitmapWidth = bitmap.width.toFloat()
                val bitmapHeight = bitmap.height.toFloat()
                
                // 计算裁剪框在图片中的实际位置
                val scaleX = bitmapWidth / imageDisplayRect.width()
                val scaleY = bitmapHeight / imageDisplayRect.height()
                
                val cropLeft = (cropRect.left - imageDisplayRect.left) * scaleX
                val cropTop = (cropRect.top - imageDisplayRect.top) * scaleY
                val cropRight = (cropRect.right - imageDisplayRect.left) * scaleX
                val cropBottom = (cropRect.bottom - imageDisplayRect.top) * scaleY
                
                // 确保裁剪区域在图片范围内
                val finalLeft = max(0f, min(cropLeft, bitmapWidth))
                val finalTop = max(0f, min(cropTop, bitmapHeight))
                val finalRight = max(finalLeft, min(cropRight, bitmapWidth))
                val finalBottom = max(finalTop, min(cropBottom, bitmapHeight))
                
                // 执行裁剪
                val croppedBitmap = withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(
                        bitmap,
                        finalLeft.toInt(),
                        finalTop.toInt(),
                        (finalRight - finalLeft).toInt(),
                        (finalBottom - finalTop).toInt()
                    )
                }
                
                // 保存裁剪后的图片
                val croppedFile = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    FileOutputStream(croppedFile).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }
                
                croppedBitmap.recycle()
                
                // 使用 FileProvider 生成 URI
                val croppedUri = FileProvider.getUriForFile(
                    this@CropActivity,
                    EditImageActivity.FILE_PROVIDER_AUTHORITY,
                    croppedFile
                )
                val resultIntent = Intent().apply {
                    putExtra("cropped_uri", croppedUri)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sourceBitmap?.recycle()
        currentBitmap?.recycle()
    }
}
