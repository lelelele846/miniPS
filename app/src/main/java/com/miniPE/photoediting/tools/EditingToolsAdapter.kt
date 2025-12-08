package com.miniPE.photoediting.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniPE.photoediting.R
import java.util.ArrayList


class EditingToolsAdapter(private val mOnItemSelected: OnItemSelected) :
    RecyclerView.Adapter<EditingToolsAdapter.ViewHolder>() {
    private val mToolList: MutableList<ToolModel> = ArrayList()

    interface OnItemSelected {
        fun onToolSelected(toolType: ToolType)
    }

    internal inner class ToolModel(
        val mToolName: String,
        val mToolIcon: Int,
        val mToolType: ToolType
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_editing_tools, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mToolList[position]
        holder.txtTool.text = item.mToolName
        holder.imgToolIcon.setImageResource(item.mToolIcon)
    }

    override fun getItemCount(): Int {
        return mToolList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgToolIcon: ImageView = itemView.findViewById(R.id.imgToolIcon)
        val txtTool: TextView = itemView.findViewById(R.id.txtTool)

        init {
            itemView.setOnClickListener { _: View? ->
                mOnItemSelected.onToolSelected(
                    mToolList[layoutPosition].mToolType
                )
            }
        }
    }

    init {
        mToolList.add(ToolModel("形状", R.drawable.ic_oval, ToolType.SHAPE))
        mToolList.add(ToolModel("文字", R.drawable.ic_text, ToolType.TEXT))
        mToolList.add(ToolModel("橡皮�?, R.drawable.ic_eraser, ToolType.ERASER))
        mToolList.add(ToolModel("滤镜", R.drawable.ic_photo_filter, ToolType.FILTER))
        // 合并表情与贴纸：这里只保留一个“贴纸”入�?        mToolList.add(ToolModel("贴纸", R.drawable.ic_sticker, ToolType.STICKER))
        mToolList.add(ToolModel("渐变", R.drawable.ic_gradient_mask, ToolType.GRADIENT_MASK))
        mToolList.add(ToolModel("裁剪", R.drawable.ic_crop, ToolType.CROP))
    }
}