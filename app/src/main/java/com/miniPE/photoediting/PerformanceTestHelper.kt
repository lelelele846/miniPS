package com.miniPE.photoediting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Debug
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 性能测试工具�?
 * 用于测试图片加载性能，包括：
 * - 加载时间
 * - 内存占用
 * - 图片尺寸
 * - 采样�?
 */
object PerformanceTestHelper {
    
    private const val TAG = "PerformanceTest"
    private val testResults = mutableListOf<TestResult>()
    
    /**
     * 测试结果数据�?
     */
    data class TestResult(
        val timestamp: String,
        val imageUri: String,
        val imageSize: Long, // 文件大小（字节）
        val originalWidth: Int,
        val originalHeight: Int,
        val loadedWidth: Int,
        val loadedHeight: Int,
        val sampleSize: Int,
        val loadTime: Long, // 加载时间（毫秒）
        val memoryBefore: Long, // 加载前内存（字节�?
        val memoryAfter: Long, // 加载后内存（字节�?
        val memoryUsed: Long, // 内存增量（字节）
        val bitmapConfig: String,
        val isOptimized: Boolean // 是否使用优化版本
    )
    
    /**
     * 测试优化后的图片加载性能
     */
    fun testOptimizedLoad(context: Context, uri: Uri): TestResult {
        val startTime = System.currentTimeMillis()
        val memoryBefore = getMemoryUsage()
        
        val bitmap = MediaLoader.getBitmapFromUri(context, uri)
        
        val endTime = System.currentTimeMillis()
        val memoryAfter = getMemoryUsage()
        
        val imageInfo = getImageInfo(context, uri)
        
        val result = TestResult(
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            imageUri = uri.toString(),
            imageSize = imageInfo.fileSize,
            originalWidth = imageInfo.originalWidth,
            originalHeight = imageInfo.originalHeight,
            loadedWidth = bitmap?.width ?: 0,
            loadedHeight = bitmap?.height ?: 0,
            sampleSize = imageInfo.sampleSize,
            loadTime = endTime - startTime,
            memoryBefore = memoryBefore,
            memoryAfter = memoryAfter,
            memoryUsed = memoryAfter - memoryBefore,
            bitmapConfig = bitmap?.config?.name ?: "null",
            isOptimized = true
        )
        
        testResults.add(result)
        logResult(result, "优化�?)
        
        // 释放bitmap
        bitmap?.recycle()
        
        return result
    }
    
    /**
     * 测试未优化的图片加载性能（用于对比）
     */
    fun testUnoptimizedLoad(context: Context, uri: Uri): TestResult {
        val startTime = System.currentTimeMillis()
        val memoryBefore = getMemoryUsage()
        
        val bitmap = loadUnoptimized(context, uri)
        
        val endTime = System.currentTimeMillis()
        val memoryAfter = getMemoryUsage()
        
        val imageInfo = getImageInfo(context, uri)
        
        val result = TestResult(
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            imageUri = uri.toString(),
            imageSize = imageInfo.fileSize,
            originalWidth = imageInfo.originalWidth,
            originalHeight = imageInfo.originalHeight,
            loadedWidth = bitmap?.width ?: 0,
            loadedHeight = bitmap?.height ?: 0,
            sampleSize = 1, // 未优化版本不使用采样
            loadTime = endTime - startTime,
            memoryBefore = memoryBefore,
            memoryAfter = memoryAfter,
            memoryUsed = memoryAfter - memoryBefore,
            bitmapConfig = bitmap?.config?.name ?: "null",
            isOptimized = false
        )
        
        testResults.add(result)
        logResult(result, "优化�?)
        
        // 释放bitmap
        bitmap?.recycle()
        
        return result
    }
    
    /**
     * 未优化的加载方法（用于对比测试）
     */
    private fun loadUnoptimized(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return null
            }
            
            // 直接加载，不使用采样
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: IOException) {
            Log.e(TAG, "未优化加载失�?, e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "未优化加载OOM", e)
            null
        }
    }
    
    /**
     * 获取图片信息
     */
    private fun getImageInfo(context: Context, uri: Uri): ImageInfo {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return ImageInfo(0, 0, 0, 1)
            }
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // 获取文件大小
            val fileSize = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    pfd.statSize
                } ?: 0
            } catch (e: Exception) {
                0
            }
            
            // 计算采样�?
            val sampleSize = calculateInSampleSize(options, 2048, 2048)
            
            ImageInfo(options.outWidth, options.outHeight, fileSize, sampleSize)
        } catch (e: Exception) {
            Log.e(TAG, "获取图片信息失败", e)
            ImageInfo(0, 0, 0, 1)
        }
    }
    
    private data class ImageInfo(
        val originalWidth: Int,
        val originalHeight: Int,
        val fileSize: Long,
        val sampleSize: Int
    )
    
    /**
     * 计算采样�?
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
            
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * 获取当前内存使用�?
     */
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    /**
     * 获取Native内存使用量（更准确）
     */
    fun getNativeMemoryUsage(): Long {
        return Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize()
    }
    
    /**
     * 记录测试结果
     */
    private fun logResult(result: TestResult, label: String) {
        Log.d(TAG, "========== $label 测试结果 ==========")
        Log.d(TAG, "时间�? ${result.timestamp}")
        Log.d(TAG, "图片URI: ${result.imageUri}")
        Log.d(TAG, "文件大小: ${formatSize(result.imageSize)}")
        Log.d(TAG, "原始尺寸: ${result.originalWidth} x ${result.originalHeight}")
        Log.d(TAG, "加载尺寸: ${result.loadedWidth} x ${result.loadedHeight}")
        Log.d(TAG, "采样�? ${result.sampleSize}")
        Log.d(TAG, "加载时间: ${result.loadTime} ms")
        Log.d(TAG, "内存使用: ${formatSize(result.memoryUsed)}")
        Log.d(TAG, "Bitmap配置: ${result.bitmapConfig}")
        Log.d(TAG, "=====================================")
    }
    
    /**
     * 格式化文件大�?
     */
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes} B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${String.format("%.2f", bytes / (1024.0 * 1024))} MB"
        return "${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024))} GB"
    }
    
    /**
     * 生成测试报告
     */
    fun generateReport(): String {
        val sb = StringBuilder()
        sb.append("========== 性能测试报告 ==========\n\n")
        sb.append("测试时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("测试次数: ${testResults.size}\n\n")
        
        val optimizedResults = testResults.filter { it.isOptimized }
        val unoptimizedResults = testResults.filter { !it.isOptimized }
        
        if (optimizedResults.isNotEmpty()) {
            sb.append("【优化后测试结果】\n")
            sb.append("平均加载时间: ${optimizedResults.map { it.loadTime }.average().toLong()} ms\n")
            sb.append("平均内存使用: ${formatSize(optimizedResults.map { it.memoryUsed }.average().toLong())}\n")
            sb.append("最大内存使�? ${formatSize(optimizedResults.maxOfOrNull { it.memoryUsed } ?: 0)}\n")
            sb.append("最小内存使�? ${formatSize(optimizedResults.minOfOrNull { it.memoryUsed } ?: 0)}\n\n")
        }
        
        if (unoptimizedResults.isNotEmpty()) {
            sb.append("【优化前测试结果】\n")
            sb.append("平均加载时间: ${unoptimizedResults.map { it.loadTime }.average().toLong()} ms\n")
            sb.append("平均内存使用: ${formatSize(unoptimizedResults.map { it.memoryUsed }.average().toLong())}\n")
            sb.append("最大内存使�? ${formatSize(unoptimizedResults.maxOfOrNull { it.memoryUsed } ?: 0)}\n")
            sb.append("最小内存使�? ${formatSize(unoptimizedResults.minOfOrNull { it.memoryUsed } ?: 0)}\n\n")
        }
        
        if (optimizedResults.isNotEmpty() && unoptimizedResults.isNotEmpty()) {
            val avgTimeOptimized = optimizedResults.map { it.loadTime }.average()
            val avgTimeUnoptimized = unoptimizedResults.map { it.loadTime }.average()
            val avgMemoryOptimized = optimizedResults.map { it.memoryUsed }.average()
            val avgMemoryUnoptimized = unoptimizedResults.map { it.memoryUsed }.average()
            
            val timeImprovement = ((avgTimeUnoptimized - avgTimeOptimized) / avgTimeUnoptimized * 100).toInt()
            val memoryImprovement = ((avgMemoryUnoptimized - avgMemoryOptimized) / avgMemoryUnoptimized * 100).toInt()
            
            sb.append("【性能提升】\n")
            sb.append("加载时间提升: $timeImprovement%\n")
            sb.append("内存占用减少: $memoryImprovement%\n\n")
        }
        
        sb.append("【详细测试记录】\n")
        testResults.forEachIndexed { index, result ->
            sb.append("\n测试 #${index + 1} (${if (result.isOptimized) "优化�? else "优化�?})\n")
            sb.append("  文件大小: ${formatSize(result.imageSize)}\n")
            sb.append("  原始尺寸: ${result.originalWidth} x ${result.originalHeight}\n")
            sb.append("  加载尺寸: ${result.loadedWidth} x ${result.loadedHeight}\n")
            sb.append("  采样�? ${result.sampleSize}\n")
            sb.append("  加载时间: ${result.loadTime} ms\n")
            sb.append("  内存使用: ${formatSize(result.memoryUsed)}\n")
        }
        
        sb.append("\n=====================================\n")
        
        return sb.toString()
    }
    
    /**
     * 清除测试结果
     */
    fun clearResults() {
        testResults.clear()
    }
    
    /**
     * 获取所有测试结�?
     */
    fun getAllResults(): List<TestResult> {
        return testResults.toList()
    }
}

