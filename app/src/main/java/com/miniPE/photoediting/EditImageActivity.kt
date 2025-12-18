package com.miniPE.photoediting

import android.app.Activity
import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.miniPE.photoediting.EmojiBSFragment.EmojiListener
import com.miniPE.photoediting.StickerBSFragment.StickerListener
import com.miniPE.photoediting.base.BaseActivity
import com.miniPE.photoediting.filters.FilterListener
import com.miniPE.photoediting.filters.FilterViewAdapter
import com.miniPE.photoediting.tools.EditingToolsAdapter
import com.miniPE.photoediting.tools.EditingToolsAdapter.OnItemSelected
import com.miniPE.photoediting.tools.ToolType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ja.miniPE.photoeditor.OnPhotoEditorListener
import ja.miniPE.photoeditor.PhotoEditor
import ja.miniPE.photoeditor.PhotoEditorView
import ja.miniPE.photoeditor.PhotoFilter
import ja.miniPE.photoeditor.SaveFileResult
import ja.miniPE.photoeditor.SaveSettings
import ja.miniPE.photoeditor.TextStyleBuilder
import ja.miniPE.photoeditor.ViewType
import ja.miniPE.photoeditor.shape.ShapeBuilder
import ja.miniPE.photoeditor.shape.ShapeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EditImageActivity : BaseActivity(), OnPhotoEditorListener, View.OnClickListener,
    PropertiesBSFragment.Properties, ShapeBSFragment.Properties, EmojiListener, StickerListener,
    OnItemSelected, FilterListener, GradientMaskBSFragment.GradientMaskListener, CropperHandler {

    lateinit var mPhotoEditor: PhotoEditor
    private lateinit var mPhotoEditorView: PhotoEditorView
    private lateinit var mPropertiesBSFragment: PropertiesBSFragment
    private lateinit var mShapeBSFragment: ShapeBSFragment
    private lateinit var mShapeBuilder: ShapeBuilder
    private lateinit var mEmojiBSFragment: EmojiBSFragment
    private lateinit var mStickerBSFragment: StickerBSFragment
    private lateinit var mGradientMaskFragment: GradientMaskBSFragment
    private lateinit var mTxtCurrentTool: TextView
    private lateinit var mWonderFont: Typeface
    private lateinit var mRvTools: RecyclerView
    private lateinit var mRvFilters: RecyclerView
    private lateinit var mImgUndo: View
    private lateinit var mImgRedo: View
    private val mEditingToolsAdapter = EditingToolsAdapter(this)
    private val mFilterViewAdapter = FilterViewAdapter(this)
    private lateinit var mRootView: ConstraintLayout
    private val mConstraintSet = ConstraintSet()
    private var mIsFilterVisible = false
    private var isPerformanceTestMode = false // 性能测试模式标志

    @VisibleForTesting
    var mSaveImageUri: Uri? = null

    private lateinit var mSaveFileHelper: FileSaveHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeFullScreen()
        setContentView(R.layout.activity_edit_image)

        initViews()

        handleIntentImage(mPhotoEditorView.source)

        mWonderFont = Typeface.createFromAsset(assets, "beyond_wonderland.ttf")

        mPropertiesBSFragment = PropertiesBSFragment()
        mEmojiBSFragment = EmojiBSFragment()
        mStickerBSFragment = StickerBSFragment()
        mShapeBSFragment = ShapeBSFragment()
        mGradientMaskFragment = GradientMaskBSFragment()

        mStickerBSFragment.setStickerListener(this)
        // Emoji 功能已合并到贴纸面板，不再单独弹�?        mPropertiesBSFragment.setPropertiesChangeListener(this)
        mShapeBSFragment.setPropertiesChangeListener(this)
        mGradientMaskFragment.setGradientMaskListener(this)

        val llmTools = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvTools.layoutManager = llmTools
        mRvTools.adapter = mEditingToolsAdapter

        val llmFilters = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvFilters.layoutManager = llmFilters
        mRvFilters.adapter = mFilterViewAdapter

        // NOTE(lucianocheng): Used to set integration testing parameters to PhotoEditor
        val pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true)

        // val mTextRobotoTf = ResourcesCompat.getFont(this, R.font.roboto_medium)
        // val mEmojiTypeFace = Typeface.createFromAsset(getAssets(), "emojione-android.ttf")

        mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
            .setPinchTextScalable(pinchTextScalable) // set flag to make text scalable when pinch
            //.setDefaultTextTypeface(mTextRobotoTf)
            //.setDefaultEmojiTypeface(mEmojiTypeFace)
            .build() // build photo editor sdk

        mPhotoEditor.setOnPhotoEditorListener(this)

        //Set Image Dynamically
        mPhotoEditorView.source.setImageResource(R.drawable.paris_tower)

        mSaveFileHelper = FileSaveHelper(this)
        
        // 初始化裁剪管理器
        CropManager.getInstance().build(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        CropManager.getInstance().destroy()
    }

    private fun handleIntentImage(source: ImageView) {
        if (intent == null) {
            return
        }

        when (intent.action) {
            Intent.ACTION_EDIT, ACTION_NEXTGEN_EDIT -> {
                try {
                    val uri = intent.data ?: return
                    loadMediaFromUri(uri, source)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            else -> {
                val imageUri = intent.data
                if (imageUri != null) {
                    loadMediaFromUri(imageUri, source)
                }
            }
        }
    }
    
    /**
     * 加载媒体文件（支持图片、GIF、WebP、MP4�?     */
    private fun loadMediaFromUri(uri: Uri, imageView: ImageView) {
        val mediaType = MediaLoader.getMediaType(this, uri)
        
        when (mediaType) {
            MediaLoader.MediaType.GIF -> {
                // 使用Glide加载GIF动画
                MediaLoader.loadGif(this, uri, imageView)
                // 同时获取第一帧用于编�?                MediaLoader.getBitmapFromUri(this, uri)?.let { bitmap ->
                    mPhotoEditorView.source.setImageBitmap(bitmap)
                }
                showSnackbar(getString(R.string.msg_gif_loaded))
            }
            MediaLoader.MediaType.WEBP -> {
                // 使用Glide加载WebP（支持动画WebP�?                MediaLoader.loadWebP(this, uri, imageView)
                // 同时获取第一帧用于编�?                MediaLoader.getBitmapFromUri(this, uri)?.let { bitmap ->
                    mPhotoEditorView.source.setImageBitmap(bitmap)
                }
                showSnackbar(getString(R.string.msg_webp_loaded))
            }
            MediaLoader.MediaType.VIDEO -> {
                // 视频需要特殊处理，显示视频预览对话�?                showVideoPreview(uri)
            }
            MediaLoader.MediaType.IMAGE -> {
                // 普通图片，使用优化的Glide加载
                // 显示加载提示
                showLoading(getString(R.string.msg_loading_image))
                
                MediaLoader.loadImage(
                    this, 
                    uri, 
                    imageView,
                    onLoadComplete = { bitmap ->
                        // 加载完成后设置到PhotoEditorView
                        hideLoading()
                        bitmap?.let {
                            mPhotoEditorView.source.setImageBitmap(it)
                        } ?: run {
                            showSnackbar(getString(R.string.msg_load_image_failed))
                        }
                    },
                    onLoadError = { exception ->
                        hideLoading()
                        exception?.printStackTrace()
                        showSnackbar(getString(R.string.msg_load_image_failed))
                    }
                )
            }
        }
    }
    
    /**
     * 显示视频预览
     */
    private fun showVideoPreview(videoUri: Uri) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_video_preview))
            .setMessage(getString(R.string.msg_video_cannot_edit))
            .setPositiveButton(getString(R.string.action_preview)) { _, _ ->
                openVideoPreviewActivity(videoUri)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()
        dialog.show()
    }
    
    /**
     * 打开视频预览Activity
     */
    private fun openVideoPreviewActivity(videoUri: Uri) {
        val intent = Intent(this, VideoPreviewActivity::class.java)
        intent.data = videoUri
        startActivity(intent)
    }

    private fun initViews() {
        mPhotoEditorView = findViewById(R.id.photoEditorView)
        mTxtCurrentTool = findViewById(R.id.txtCurrentTool)
        mRvTools = findViewById(R.id.rvConstraintTools)
        mRvFilters = findViewById(R.id.rvFilterView)
        mRootView = findViewById(R.id.rootView)

        mImgUndo = findViewById(R.id.imgUndo)
        mImgUndo.setOnClickListener(this)
        mImgUndo.isEnabled = false

        mImgRedo = findViewById(R.id.imgRedo)
        mImgRedo.setOnClickListener(this)
        mImgRedo.isEnabled = false

        val imgCamera: ImageView = findViewById(R.id.imgCamera)
        imgCamera.setOnClickListener(this)

        val imgGallery: ImageView = findViewById(R.id.imgGallery)
        
        // 长按相册图标进入性能测试模式
        imgGallery.setOnLongClickListener {
            Log.d(TAG, "长按相册图标，显示性能测试对话�?)
            showPerformanceTestDialog()
            true
        }
        
        // 单击打开相册
        imgGallery.setOnClickListener(this)

        val imgSave: ImageView = findViewById(R.id.imgSave)
        imgSave.setOnClickListener(this)

        val imgClose: ImageView = findViewById(R.id.imgClose)
        imgClose.setOnClickListener(this)

        val imgShare: ImageView = findViewById(R.id.imgShare)
        imgShare.setOnClickListener(this)
    }

    override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
        val textEditorDialogFragment =
            TextEditorDialogFragment.show(this, text.toString(), colorCode)
        textEditorDialogFragment.setOnTextEditorListener(object :
            TextEditorDialogFragment.TextEditorListener {
            override fun onDone(inputText: String, colorCode: Int) {
                val styleBuilder = TextStyleBuilder()
                styleBuilder.withTextColor(colorCode)
                mPhotoEditor.editText(rootView, inputText, styleBuilder)
                mTxtCurrentTool.setText(R.string.label_text)
            }
        })
    }

    override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        Log.d(
            TAG,
            "onAddViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
        )

        mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
        mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
    }

    override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        Log.d(
            TAG,
            "onRemoveViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
        )

        mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
        mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
    }

    override fun onStartViewChangeListener(viewType: ViewType) {
        Log.d(TAG, "onStartViewChangeListener() called with: viewType = [$viewType]")
    }

    override fun onStopViewChangeListener(viewType: ViewType) {
        Log.d(TAG, "onStopViewChangeListener() called with: viewType = [$viewType]")
    }

    override fun onTouchSourceImage(event: MotionEvent) {
        Log.d(TAG, "onTouchView() called with: event = [$event]")
    }

    @SuppressLint("NonConstantResourceId", "MissingPermission")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.imgUndo -> {
                mImgUndo.isEnabled = mPhotoEditor.undo()
                mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
            }

            R.id.imgRedo -> {
                mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
                mImgRedo.isEnabled = mPhotoEditor.redo()
            }

            R.id.imgSave -> saveImage()
            R.id.imgClose -> onBackPressed()
            R.id.imgShare -> shareImage()
            R.id.imgCamera -> {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, CAMERA_REQUEST)
            }

            R.id.imgGallery -> {
                val intent = Intent()
                // 支持图片和视频格�?                intent.type = "*/*"
                intent.action = Intent.ACTION_GET_CONTENT
                intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "image/*",
                    "video/mp4",
                    "video/quicktime"
                ))
                startActivityForResult(Intent.createChooser(intent, getString(R.string.title_select_media)), PICK_REQUEST)
            }
        }
    }

    private fun shareImage() {
        val saveImageUri = mSaveImageUri
        if (saveImageUri == null) {
            showSnackbar(getString(R.string.msg_save_image_to_share))
            return
        }

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_STREAM, buildFileProviderUri(saveImageUri))
        startActivity(Intent.createChooser(intent, getString(R.string.msg_share_image)))
    }

    private fun buildFileProviderUri(uri: Uri): Uri {
        if (FileSaveHelper.isSdkHigherThan28()) {
            return uri
        }
        val path: String = uri.path ?: throw IllegalArgumentException("URI Path Expected")

        return FileProvider.getUriForFile(
            this,
            FILE_PROVIDER_AUTHORITY,
            File(path)
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    private fun saveImage() {
        val fileName = System.currentTimeMillis().toString() + ".png"
        val hasStoragePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasStoragePermission || FileSaveHelper.isSdkHigherThan28()) {
            showLoading(getString(R.string.msg_saving))
            mSaveFileHelper.createFile(fileName, object : FileSaveHelper.OnFileCreateResult {

                @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
                override fun onFileCreateResult(
                    created: Boolean,
                    filePath: String?,
                    error: String?,
                    uri: Uri?
                ) {
                    lifecycleScope.launch {
                        if (created && filePath != null) {
                            val saveSettings = SaveSettings.Builder()
                                .setClearViewsEnabled(true)
                                .setTransparencyEnabled(true)
                                .build()

                            val result = mPhotoEditor.saveAsFile(filePath, saveSettings)

                            if (result is SaveFileResult.Success) {
                                mSaveFileHelper.notifyThatFileIsNowPubliclyAvailable(contentResolver)
                                hideLoading()
                                showSnackbar(getString(R.string.msg_image_saved))
                                mSaveImageUri = uri
                                mPhotoEditorView.source.setImageURI(mSaveImageUri)
                            } else {
                                hideLoading()
                                showSnackbar(getString(R.string.msg_image_save_failed))
                            }
                        } else {
                            hideLoading()
                            error?.let { showSnackbar(error) }
                        }
                    }
                }
            })
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // TODO(lucianocheng): Replace onActivityResult with Result API from Google
    //                     See https://developer.android.com/training/basics/intents/result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST -> {
                    mPhotoEditor.clearAllViews()
                    val photo = data?.extras?.get("data") as Bitmap?
                    mPhotoEditorView.source.setImageBitmap(photo)
                }

                PICK_REQUEST -> {
                    mPhotoEditor.clearAllViews()
                    val uri = data?.data
                    if (uri != null) {
                        // 检查是否在性能测试模式
                        if (isPerformanceTestMode) {
                            runPerformanceTest(uri)
                        } else {
                            loadMediaFromUri(uri, mPhotoEditorView.source)
                        }
                    }
                }
            }
        }
    }

    override fun onColorChanged(colorCode: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeColor(colorCode))
        mTxtCurrentTool.setText(R.string.label_brush)
    }

    override fun onOpacityChanged(opacity: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeOpacity(opacity))
        mTxtCurrentTool.setText(R.string.label_brush)
    }

    override fun onShapeSizeChanged(shapeSize: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeSize(shapeSize.toFloat()))
        mTxtCurrentTool.setText(R.string.label_brush)
    }

    override fun onShapePicked(shapeType: ShapeType) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeType(shapeType))
    }

    override fun onEmojiClick(emojiUnicode: String) {
        mPhotoEditor.addEmoji(emojiUnicode)
        mTxtCurrentTool.setText(R.string.label_emoji)
    }

    override fun onStickerClick(bitmap: Bitmap) {
        mPhotoEditor.addImage(bitmap)
        mTxtCurrentTool.setText(R.string.label_sticker)
    }

    @SuppressLint("MissingPermission")
    override fun isPermissionGranted(isGranted: Boolean, permission: String?) {
        if (isGranted) {
            saveImage()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showSaveDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.msg_save_image))
        builder.setPositiveButton(getString(R.string.action_save)) { _: DialogInterface?, _: Int -> saveImage() }
        builder.setNegativeButton(getString(R.string.action_cancel)) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setNeutralButton(getString(R.string.action_discard)) { _: DialogInterface?, _: Int -> finish() }
        builder.create().show()
    }

    override fun onFilterSelected(photoFilter: PhotoFilter) {
        mPhotoEditor.setFilterEffect(photoFilter)
    }

    override fun onToolSelected(toolType: ToolType) {
        when (toolType) {
            ToolType.SHAPE -> {
                mPhotoEditor.setBrushDrawingMode(true)
                mShapeBuilder = ShapeBuilder()
                mPhotoEditor.setShape(mShapeBuilder)
                mTxtCurrentTool.setText(R.string.label_shape)
                showBottomSheetDialogFragment(mShapeBSFragment)
            }

            ToolType.TEXT -> {
                val textEditorDialogFragment = TextEditorDialogFragment.show(this)
                textEditorDialogFragment.setOnTextEditorListener(object :
                    TextEditorDialogFragment.TextEditorListener {
                    override fun onDone(inputText: String, colorCode: Int) {
                        val styleBuilder = TextStyleBuilder()
                        styleBuilder.withTextColor(colorCode)
                        mPhotoEditor.addText(inputText, styleBuilder)
                        mTxtCurrentTool.setText(R.string.label_text)
                    }
                })
            }

            ToolType.ERASER -> {
                mPhotoEditor.brushEraser()
                mTxtCurrentTool.setText(R.string.label_eraser_mode)
            }

            ToolType.FILTER -> {
                mTxtCurrentTool.setText(R.string.label_filter)
                showFilter(true)
            }

            // Emoji 已合并到贴纸面板，这里只保留贴纸入口
            ToolType.EMOJI -> showBottomSheetDialogFragment(mStickerBSFragment)
            ToolType.STICKER -> showBottomSheetDialogFragment(mStickerBSFragment)
            ToolType.GRADIENT_MASK -> {
                mTxtCurrentTool.setText(R.string.label_gradient_mask)
                showGradientMaskDialog()
            }

            ToolType.CROP -> {
                mTxtCurrentTool.setText("裁剪")
                startCropOperation()
            }
        }
    }

    private fun showBottomSheetDialogFragment(fragment: BottomSheetDialogFragment?) {
        if (fragment == null || fragment.isAdded) {
            return
        }
        fragment.show(supportFragmentManager, fragment.tag)
    }

    private fun showGradientMaskDialog() {
        if (mGradientMaskFragment.isAdded) {
            return
        }
        mGradientMaskFragment.show(supportFragmentManager, mGradientMaskFragment.tag)
    }

    override fun onGradientMaskApplied(angle: Float, softnessPercent: Float) {
        applyGradientMask(angle, softnessPercent)
    }

    private fun applyGradientMask(angle: Float, softnessPercent: Float) {
        showLoading(getString(R.string.msg_applying_gradient_mask))
        lifecycleScope.launch {
            try {
                val saveSettings = SaveSettings.Builder()
                    .setClearViewsEnabled(true)
                    .setTransparencyEnabled(true)
                    .build()
                val flattenedBitmap = mPhotoEditor.saveAsBitmap(saveSettings)
                val maskedBitmap = withContext(Dispatchers.Default) {
                    GradientMaskProcessor.applyGradientMask(flattenedBitmap, angle, softnessPercent)
                }
                mPhotoEditorView.source.setImageBitmap(maskedBitmap)
                hideLoading()
                showSnackbar(getString(R.string.msg_gradient_mask_applied))
            } catch (e: Exception) {
                Log.e(TAG, "applyGradientMask failed", e)
                hideLoading()
                showSnackbar(getString(R.string.msg_gradient_mask_failed))
            }
        }
    }

    /**
     * 启动裁剪操作
     * 将当前编辑的图片保存为临时文件，然后启动裁剪 Activity
     */
    private fun startCropOperation() {
        lifecycleScope.launch {
            try {
                showLoading("准备图片...")
                val saveSettings = SaveSettings.Builder()
                    .setClearViewsEnabled(true)
                    .setTransparencyEnabled(true)
                    .build()
                val sourceBitmap = withContext(Dispatchers.Default) {
                    mPhotoEditor.saveAsBitmap(saveSettings)
                }
                
                // 保存为临时文�?                val tempFile = File(cacheDir, "crop_temp_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    FileOutputStream(tempFile).use { out ->
                        sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }
                
                hideLoading()
                
                // 启动裁剪 Activity
                val tempUri = Uri.fromFile(tempFile)
                CropManager.getInstance().cropImage(tempUri)
            } catch (e: Exception) {
                Log.e(TAG, "startCropOperation failed", e)
                hideLoading()
                showSnackbar("准备图片失败")
            }
        }
    }

    private fun showFilter(isVisible: Boolean) {
        mIsFilterVisible = isVisible
        mConstraintSet.clone(mRootView)

        val rvFilterId: Int = mRvFilters.id

        if (isVisible) {
            mConstraintSet.clear(rvFilterId, ConstraintSet.START)
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START,
                ConstraintSet.PARENT_ID, ConstraintSet.START
            )
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.END,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )
        } else {
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START,
                ConstraintSet.PARENT_ID, ConstraintSet.END
            )
            mConstraintSet.clear(rvFilterId, ConstraintSet.END)
        }

        val changeBounds = ChangeBounds()
        changeBounds.duration = 350
        changeBounds.interpolator = AnticipateOvershootInterpolator(1.0f)
        TransitionManager.beginDelayedTransition(mRootView, changeBounds)

        mConstraintSet.applyTo(mRootView)
    }

    // ==================== CropperHandler 接口实现 ====================
    
    override fun getActivity(): Activity {
        return this
    }
    
    override fun getCropperParams(): CropperParams {
        // 返回不约束比例的裁剪参数�?, 0 表示自由比例�?        // 如果需要固定比例，可以返回例如 CropperParams(1, 1) 表示 1:1
        return CropperParams(0, 0)
    }
    
    override fun onCropped(uri: Uri) {
        Log.d(TAG, "裁剪成功: $uri")
        // 加载裁剪后的图片
        lifecycleScope.launch {
            try {
                showLoading("加载裁剪后的图片...")
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
                
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        mPhotoEditor.clearAllViews()
                        mPhotoEditorView.source.setImageBitmap(bitmap)
                    }
                    hideLoading()
                    showSnackbar("裁剪完成")
                } else {
                    hideLoading()
                    showSnackbar("加载图片失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "onCropped failed", e)
                hideLoading()
                showSnackbar("加载图片失败")
            }
        }
    }
    
    override fun onCropCancel() {
        Log.d(TAG, "裁剪取消")
        showSnackbar("裁剪已取�?)
    }
    
    override fun onCropFailed(msg: String) {
        Log.e(TAG, "裁剪失败: $msg")
        showSnackbar("裁剪失败: $msg")
    }
    
    // ==================== 结束 CropperHandler 接口实现 ====================

    override fun onBackPressed() {
        if (mIsFilterVisible) {
            showFilter(false)
            mTxtCurrentTool.setText(R.string.app_name)
        } else if (!mPhotoEditor.isCacheEmpty) {
            showSaveDialog()
        } else {
            super.onBackPressed()
        }
    }
    
    /**
     * 显示性能测试对话�?     */
    private fun showPerformanceTestDialog() {
        val options = arrayOf(
            "测试优化后的性能",
            "对比测试（优化前 vs 优化后）",
            "查看测试报告",
            "清除测试记录"
        )
        
        AlertDialog.Builder(this)
            .setTitle("性能测试")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        isPerformanceTestMode = true
                        showSnackbar("已进入性能测试模式，请选择一张图�?)
                        // 打开相册
                        val intent = Intent().apply {
                            type = "image/*"
                            action = Intent.ACTION_GET_CONTENT
                        }
                        startActivityForResult(Intent.createChooser(intent, "选择测试图片"), PICK_REQUEST)
                    }
                    1 -> {
                        isPerformanceTestMode = true
                        showSnackbar("已进入对比测试模式，请选择一张图�?)
                        val intent = Intent().apply {
                            type = "image/*"
                            action = Intent.ACTION_GET_CONTENT
                        }
                        startActivityForResult(Intent.createChooser(intent, "选择测试图片"), PICK_REQUEST)
                    }
                    2 -> {
                        showPerformanceReport()
                    }
                    3 -> {
                        PerformanceTestHelper.clearResults()
                        showSnackbar("测试记录已清�?)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 运行性能测试
     */
    private fun runPerformanceTest(uri: Uri) {
        lifecycleScope.launch {
            try {
                showLoading("正在测试性能...")
                
                // 测试优化后的性能
                val optimizedResult = withContext(Dispatchers.IO) {
                    PerformanceTestHelper.testOptimizedLoad(this@EditImageActivity, uri)
                }
                
                // 等待一下，让内存稳�?                kotlinx.coroutines.delay(500)
                
                // 测试优化前的性能（用于对比）
                val unoptimizedResult = withContext(Dispatchers.IO) {
                    try {
                        PerformanceTestHelper.testUnoptimizedLoad(this@EditImageActivity, uri)
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "未优化版本OOM", e)
                        null
                    }
                }
                
                hideLoading()
                
                // 显示测试结果
                showTestResults(optimizedResult, unoptimizedResult)
                
                // 加载图片到编辑器
                loadMediaFromUri(uri, mPhotoEditorView.source)
                
            } catch (e: Exception) {
                hideLoading()
                Log.e(TAG, "性能测试失败", e)
                showSnackbar("性能测试失败: ${e.message}")
                // 即使测试失败，也加载图片
                loadMediaFromUri(uri, mPhotoEditorView.source)
            } finally {
                isPerformanceTestMode = false
            }
        }
    }
    
    /**
     * 显示测试结果
     */
    private fun showTestResults(optimized: PerformanceTestHelper.TestResult, unoptimized: PerformanceTestHelper.TestResult?) {
        val sb = StringBuilder()
        sb.append("【优化后测试结果】\n")
        sb.append("加载时间: ${optimized.loadTime} ms\n")
        sb.append("内存使用: ${formatSize(optimized.memoryUsed)}\n")
        sb.append("图片尺寸: ${optimized.loadedWidth} x ${optimized.loadedHeight}\n")
        sb.append("采样�? ${optimized.sampleSize}\n")
        sb.append("Bitmap配置: ${optimized.bitmapConfig}\n\n")
        
        if (unoptimized != null) {
            sb.append("【优化前测试结果】\n")
            sb.append("加载时间: ${unoptimized.loadTime} ms\n")
            sb.append("内存使用: ${formatSize(unoptimized.memoryUsed)}\n")
            sb.append("图片尺寸: ${unoptimized.loadedWidth} x ${unoptimized.loadedHeight}\n")
            sb.append("采样�? ${unoptimized.sampleSize}\n")
            sb.append("Bitmap配置: ${unoptimized.bitmapConfig}\n\n")
            
            val timeImprovement = ((unoptimized.loadTime - optimized.loadTime).toDouble() / unoptimized.loadTime * 100).toInt()
            val memoryImprovement = ((unoptimized.memoryUsed - optimized.memoryUsed).toDouble() / unoptimized.memoryUsed * 100).toInt()
            
            sb.append("【性能提升】\n")
            sb.append("加载时间提升: $timeImprovement%\n")
            sb.append("内存占用减少: $memoryImprovement%\n")
        } else {
            sb.append("【优化前测试】\n")
            sb.append("测试失败（可能因内存不足）\n")
        }
        
        AlertDialog.Builder(this)
            .setTitle("性能测试结果")
            .setMessage(sb.toString())
            .setPositiveButton("查看详细报告") { _, _ ->
                showPerformanceReport()
            }
            .setNegativeButton("确定", null)
            .show()
    }
    
    /**
     * 显示性能测试报告
     */
    private fun showPerformanceReport() {
        val report = PerformanceTestHelper.generateReport()
        
        AlertDialog.Builder(this)
            .setTitle("性能测试报告")
            .setMessage(report)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 格式化文件大�?     */
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes} B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${String.format("%.2f", bytes / (1024.0 * 1024))} MB"
        return "${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024))} GB"
    }

    companion object {

        private const val TAG = "EditImageActivity"

        const val FILE_PROVIDER_AUTHORITY = "com.miniPE.photoediting.fileprovider"
        private const val CAMERA_REQUEST = 52
        private const val PICK_REQUEST = 53
        const val ACTION_NEXTGEN_EDIT = "action_nextgen_edit"
        const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
    }
}