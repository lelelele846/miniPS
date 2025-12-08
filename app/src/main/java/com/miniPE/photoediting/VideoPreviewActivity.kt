package com.miniPE.photoediting

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/**
 * 视频预览Activity
 * 用于预览MP4等视频文�?
 */
class VideoPreviewActivity : AppCompatActivity() {
    
    private lateinit var videoView: VideoView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        videoView = VideoView(this)
        setContentView(videoView)
        
        val videoUri: Uri? = intent.data
        if (videoUri != null) {
            setupVideoPlayer(videoUri)
        } else {
            finish()
        }
    }
    
    private fun setupVideoPlayer(uri: Uri) {
        // 设置媒体控制�?
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        
        // 设置视频URI
        videoView.setVideoURI(uri)
        
        // 开始播�?
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            videoView.start()
        }
        
        // 错误处理
        videoView.setOnErrorListener { _, what, extra ->
            finish()
            true
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!videoView.isPlaying) {
            videoView.start()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
    }
}

