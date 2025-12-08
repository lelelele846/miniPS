package com.miniPE.photoediting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File

/**
 * 统一的图片裁剪管理器
 * 整合裁剪、旋转、自定义比例功能
 * 参�?lib-cropview 的设计思路
 */
class CropManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: CropManager? = null
        
        fun getInstance(): CropManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CropManager().also { INSTANCE = it }
            }
        }
    }
    
    private var activity: FragmentActivity? = null
    private var handler: CropperHandler? = null
    private var cameraLauncher: ActivityResultLauncher<Intent>? = null
    private var galleryLauncher: ActivityResultLauncher<Intent>? = null
    private var cropLauncher: ActivityResultLauncher<Intent>? = null
    private var cameraImageUri: Uri? = null
    
    /**
     * 初始�?CropManager
     */
    fun build(activity: FragmentActivity) {
        this.activity = activity
        this.handler = activity as? CropperHandler
        
        // 注册相机启动�?
        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                cameraImageUri?.let { uri ->
                    startCropActivity(uri)
                }
            }
        }
        
        // 注册图库启动�?
        galleryLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    startCropActivity(uri)
                }
            }
        }
        
        // 注册裁剪结果启动�?
        cropLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getParcelableExtra<Uri>("cropped_uri")?.let { uri ->
                    handler?.onCropped(uri)
                } ?: handler?.onCropFailed("裁剪结果为空")
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                handler?.onCropCancel()
            }
        }
    }
    
    /**
     * 从相机拍照并裁剪
     */
    fun pickFromCamera() {
        val activity = this.activity ?: return
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val imageFile = File(activity.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraImageUri = FileProvider.getUriForFile(
                activity,
                EditImageActivity.FILE_PROVIDER_AUTHORITY,
                imageFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            cameraLauncher?.launch(intent)
        } catch (e: Exception) {
            handler?.onCropFailed("启动相机失败: ${e.message}")
        }
    }
    
    /**
     * 从图库选择并裁�?
     */
    fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*"))
        }
        galleryLauncher?.launch(Intent.createChooser(intent, "选择图片"))
    }
    
    /**
     * 直接裁剪指定的图�?URI
     */
    fun cropImage(uri: Uri) {
        startCropActivity(uri)
    }
    
    /**
     * 启动裁剪 Activity
     */
    private fun startCropActivity(uri: Uri) {
        val activity = this.activity ?: return
        val params = handler?.getCropperParams() ?: CropperParams(0, 0)
        
        val intent = Intent(activity, CropActivity::class.java).apply {
            putExtra("image_uri", uri.toString())
            putExtra("aspect_x", params.aspectX)
            putExtra("aspect_y", params.aspectY)
        }
        cropLauncher?.launch(intent)
    }
    
    /**
     * 清理资源
     */
    fun destroy() {
        activity = null
        handler = null
        cameraLauncher = null
        galleryLauncher = null
        cropLauncher = null
        cameraImageUri = null
    }
}

/**
 * 裁剪参数
 */
data class CropperParams(
    val aspectX: Int = 0,  // 裁剪框宽高比 X�? 表示不约�?
    val aspectY: Int = 0   // 裁剪框宽高比 Y�? 表示不约�?
)

/**
 * 裁剪处理器接�?
 */
interface CropperHandler {
    fun getActivity(): Activity
    fun getCropperParams(): CropperParams
    fun onCropped(uri: Uri)
    fun onCropCancel()
    fun onCropFailed(msg: String)
}
