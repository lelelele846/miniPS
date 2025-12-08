package com.miniPE.photoediting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import android.widget.VideoView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.request.RequestOptions
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ExecutionException

/**
 * 媒体加载工具类（优化版）
 * 支持加载和预览多种格式的媒体文件：WebP、GIF、MP4
 * 
 * 优化策略�? * 1. 大图片采样加载，避免OOM
 * 2. 异步加载，不阻塞UI线程
 * 3. 使用Glide缓存机制
 * 4. 内存优化，及时释放资�? */
object MediaLoader {
    
    private const val TAG = "MediaLoader"
    private const val MAX_IMAGE_SIZE = 2048 // 最大图片尺寸（像素�?    private const val MAX_MEMORY_SIZE = 10 * 1024 * 1024 // 最大内存占�?10MB

    /**
     * 检测媒体文件类�?     */
    fun getMediaType(context: Context, uri: Uri): MediaType {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        return when {
            mimeType.startsWith("image/gif") -> MediaType.GIF
            mimeType.startsWith("image/webp") -> MediaType.WEBP
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("image/") -> MediaType.IMAGE
            else -> {
                // 通过文件扩展名判�?                val path = uri.path ?: ""
                when {
                    path.endsWith(".gif", ignoreCase = true) -> MediaType.GIF
                    path.endsWith(".webp", ignoreCase = true) -> MediaType.WEBP
                    path.endsWith(".mp4", ignoreCase = true) -> MediaType.VIDEO
                    path.endsWith(".mov", ignoreCase = true) -> MediaType.VIDEO
                    path.endsWith(".avi", ignoreCase = true) -> MediaType.VIDEO
                    else -> MediaType.IMAGE
                }
            }
        }
    }

    /**
     * 加载图片到ImageView（优化版 - 支持大图片）
     * 使用Glide自动处理动画GIF和WebP
     * 优化：大图片采样加载，避免OOM
     */
    fun loadImage(
        context: Context,
        uri: Uri,
        imageView: ImageView,
        onLoadComplete: ((Bitmap?) -> Unit)? = null,
        onLoadError: ((Exception?) -> Unit)? = null
    ) {
        if (onLoadComplete != null || onLoadError != null) {
            // 如果需要回调，使用 FutureTarget 异步加载，并应用采样优化
            Thread {
                try {
                    // 先加载缩略图用于快速预�?                    val thumbnailTarget: FutureTarget<Bitmap> = Glide.with(context)
                        .asBitmap()
                        .load(uri)
                        .apply(
                            RequestOptions()
                                .override(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
                                .format(DecodeFormat.PREFER_RGB_565) // 使用RGB_565减少内存
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                        )
                        .submit()
                    
                    val thumbnail = thumbnailTarget.get()
                    
                    // 在主线程更新 UI（快速显示缩略图�?                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        imageView.setImageBitmap(thumbnail)
                    }
                    
                    // 异步加载完整图片（如果需要）
                    val fullTarget: FutureTarget<Bitmap> = Glide.with(context)
                        .asBitmap()
                        .load(uri)
                        .apply(
                            RequestOptions()
                                .format(DecodeFormat.PREFER_RGB_565)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                        )
                        .submit()
                    
                    val fullBitmap = fullTarget.get()
                    
                    // 在主线程更新 UI
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        imageView.setImageBitmap(fullBitmap)
                        onLoadComplete?.invoke(fullBitmap)
                        Glide.with(context).clear(thumbnailTarget)
                        Glide.with(context).clear(fullTarget)
                    }
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    Log.e(TAG, "加载图片失败", cause ?: e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onLoadError?.invoke(cause as? Exception ?: e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载图片失败", e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onLoadError?.invoke(e)
                    }
                }
            }.start()
        } else {
            // 如果不需要回调，直接加载（优化版�?            Glide.with(context)
                .asBitmap()
                .load(uri)
                .apply(
                    RequestOptions()
                        .override(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                )
                .into(imageView)
        }
    }

    /**
     * 加载GIF动画到ImageView
     * Glide会自动播放GIF动画
     */
    fun loadGif(context: Context, uri: Uri, imageView: ImageView) {
        Glide.with(context)
            .asGif()
            .load(uri)
            .into(imageView)
    }

    /**
     * 加载WebP到ImageView（支持动画WebP�?     */
    fun loadWebP(context: Context, uri: Uri, imageView: ImageView) {
        Glide.with(context)
            .load(uri)
            .into(imageView)
    }

    /**
     * 加载视频到VideoView
     */
    fun loadVideo(context: Context, uri: Uri, videoView: VideoView) {
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            false
        }
    }

    /**
     * 从URI获取Bitmap（优化版 - 用于编辑功能�?     * 对于GIF和动画WebP，返回第一�?     * 优化：大图片采样加载，避免OOM
     */
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.w(TAG, "无法打开输入�? $uri")
                return null
            }
            
            // 先获取图片尺寸，不加载到内存
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // 计算采样�?            val sampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
            
            // 使用采样率加载图�?            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 使用RGB_565减少内存
                inJustDecodeBounds = false
            }
            
            val newInputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(newInputStream, null, decodeOptions)
            newInputStream?.close()
            
            bitmap
        } catch (e: IOException) {
            Log.e(TAG, "获取Bitmap失败", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "内存不足，无法加载图�?, e)
            // 尝试更小的采样率
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 8 // 强制使用更大的采样率
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()
                bitmap
            } catch (e2: Exception) {
                Log.e(TAG, "重试加载失败", e2)
                null
            }
        }
    }
    
    /**
     * 计算采样率，使图片尺寸不超过指定大小
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // 计算最大的采样率，使图片尺寸不超过要求
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }

    enum class MediaType {
        IMAGE,      // 静态图片（JPG, PNG等）
        GIF,        // GIF动画
        WEBP,       // WebP（静态或动画�?        VIDEO       // 视频（MP4等）
    }
}

