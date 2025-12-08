package com.miniPE.photoediting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GradientMaskBSFragment : BottomSheetDialogFragment() {

    interface GradientMaskListener {
        fun onGradientMaskApplied(angle: Float, softnessPercent: Float)
    }

    private var listener: GradientMaskListener? = null
    private var angleValue: Float = 45f
    private var softnessValue: Float = 50f

    fun setGradientMaskListener(listener: GradientMaskListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_bottom_gradient_mask_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sliderAngle: SeekBar = view.findViewById(R.id.sliderAngle)
        val sliderSoftness: SeekBar = view.findViewById(R.id.sliderSoftness)
        val btnApply: Button = view.findViewById(R.id.btnApply)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)

        // 初始化�?
        angleValue = sliderAngle.progress.toFloat()
        softnessValue = (sliderSoftness.progress + 10).toFloat() // 10-90范围

        sliderAngle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                angleValue = progress.toFloat()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sliderSoftness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                softnessValue = (progress + 10).toFloat() // 10-90范围
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnApply.setOnClickListener {
            listener?.onGradientMaskApplied(angleValue, softnessValue / 100f)
            dismissAllowingStateLoss()
        }

        btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }
}

