package com.miniPE.photoediting

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class StickerBSFragment : BottomSheetDialogFragment() {
    private var mStickerListener: StickerListener? = null
    fun setStickerListener(stickerListener: StickerListener?) {
        mStickerListener = stickerListener
    }

    interface StickerListener {
        fun onStickerClick(bitmap: Bitmap)
    }

    private val mBottomSheetBehaviorCallback: BottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val contentView = View.inflate(context, R.layout.fragment_bottom_sticker_emoji_dialog, null)
        dialog.setContentView(contentView)
        val params = (contentView.parent as View).layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior
        if (behavior != null && behavior is BottomSheetBehavior<*>) {
            behavior.setBottomSheetCallback(mBottomSheetBehaviorCallback)
        }
        (contentView.parent as View).setBackgroundColor(resources.getColor(android.R.color.transparent))
        val rvEmoji: RecyclerView = contentView.findViewById(R.id.rvEmoji)
        val gridLayoutManager = GridLayoutManager(activity, 3)
        rvEmoji.layoutManager = gridLayoutManager

        // 获取 Unicode 表情列表，并与贴纸列表合并显�?        val emojiList = EmojiBSFragment.getEmojis(context)
        val stickerAdapter = StickerAdapter(emojiList)
        rvEmoji.adapter = stickerAdapter
        rvEmoji.setHasFixedSize(true)
        rvEmoji.setItemViewCacheSize(emojiList.size + stickerPathList.size)
    }

    /**
     * 统一的贴纸适配器：
     * - 前半部分：Unicode 表情（渲染成位图�?     * - 后半部分：网络贴纸图�?     */
    inner class StickerAdapter(
        private val emojis: List<String>
    ) : RecyclerView.Adapter<StickerAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.row_sticker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (position < emojis.size) {
                // 表情：将 Unicode 表情渲染为位图显�?                val emoji = emojis[position]
                val emojiBitmap = createEmojiBitmap(emoji, 256)
                holder.imgSticker.setImageBitmap(emojiBitmap)
            } else {
                // 贴纸：从远程 URL 加载图片
                val stickerIndex = position - emojis.size
                Glide.with(requireContext())
                    .asBitmap()
                    .load(stickerPathList[stickerIndex])
                    .into(holder.imgSticker)
            }
        }

        override fun getItemCount(): Int {
            return emojis.size + stickerPathList.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgSticker: ImageView = itemView.findViewById(R.id.imgSticker)

            init {
                itemView.setOnClickListener {
                    if (mStickerListener != null) {
                        val pos = layoutPosition
                        if (pos == RecyclerView.NO_POSITION) {
                            return@setOnClickListener
                        }

                        if (adapterPosition < emojis.size) {
                            // 点击的是表情：生成表情位图回调出�?                            val emoji = emojis[adapterPosition]
                            val emojiBitmap = createEmojiBitmap(emoji, 256)
                            mStickerListener!!.onStickerClick(emojiBitmap)
                        } else {
                            // 点击的是贴纸：保持原有逻辑，从 URL 加载位图
                            val stickerIndex = adapterPosition - emojis.size
                            Glide.with(requireContext())
                                .asBitmap()
                                .load(stickerPathList[stickerIndex])
                                .into(object : CustomTarget<Bitmap?>(256, 256) {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                                        mStickerListener!!.onStickerClick(resource)
                                    }

                                    override fun onLoadCleared(placeholder: Drawable?) {}
                                })
                        }
                    }
                    dismiss()
                }
            }
        }
    }

    companion object {
        // Image Urls from flaticon(https://www.flaticon.com/stickers-pack/food-289)
        private val stickerPathList = arrayOf(
                "https://cdn-icons-png.flaticon.com/256/4392/4392452.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392455.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392459.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392462.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392465.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392467.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392469.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392471.png",
                "https://cdn-icons-png.flaticon.com/256/4392/4392522.png",
        )

        /**
         * �?Unicode 表情渲染为位图，用于在贴纸面板中与图片贴纸统一显示/回调�?         */
        fun createEmojiBitmap(emoji: String, size: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 透明背景
            canvas.drawARGB(0, 0, 0, 0)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = size * 0.6f
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.DEFAULT

            // 计算文字居中
            val textBounds = Rect()
            paint.getTextBounds(emoji, 0, emoji.length, textBounds)
            val x = (size - textBounds.width()) / 2f - textBounds.left
            val y = (size + textBounds.height()) / 2f - textBounds.bottom

            canvas.drawText(emoji, x, y, paint)

            return bitmap
        }
    }
}