/*
 * Copyright (C) 2016 Evgenii Zagumennyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.zagum.switchicon

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class SwitchIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val animationDuration: Long

    private val disabledStateAlpha: Float
    private val dashXStart: Int
    private val dashYStart: Int
    private val clipPath: Path
    private val iconTintColor: Int
    private val disabledStateColor: Int
    private val noDash: Boolean
    private var dashThickness: Int = 0
    private var dashLengthXProjection: Int = 0
    private var dashLengthYProjection: Int = 0
    private var colorFilter: PorterDuffColorFilter? = null
    private val colorEvaluator = ArgbEvaluator()

    private var fraction = 0f
    private var mEnabled: Boolean = false

    private val dashPaint: Paint
    private val dashStart = Point()
    private val dashEnd = Point()

    /**
     * Check state
     *
     * @return TRUE if icon is mEnabled, otherwise FALSE
     */
    /**
     * Changes state with animation
     *
     * @param mEnabled If TRUE - icon is mEnabled
     */
    @Suppress("unused")
    var isIconEnabled: Boolean
        get() = mEnabled
        set(enabled) = setIconEnabled(enabled, true)

    init {

        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        val array = getContext().theme.obtainStyledAttributes(attrs, R.styleable.SwitchIconView, 0, 0)

        try {
            iconTintColor = array.getColor(R.styleable.SwitchIconView_si_tint_color, Color.BLACK)
            animationDuration = array.getInteger(R.styleable.SwitchIconView_si_animation_duration, DEFAULT_ANIMATION_DURATION).toLong()
            disabledStateAlpha = array.getFloat(R.styleable.SwitchIconView_si_disabled_alpha, DEFAULT_DISABLED_ALPHA)
            disabledStateColor = array.getColor(R.styleable.SwitchIconView_si_disabled_color, iconTintColor)
            mEnabled = array.getBoolean(R.styleable.SwitchIconView_si_enabled, true)
            noDash = array.getBoolean(R.styleable.SwitchIconView_si_no_dash, false)
        } finally {
            array.recycle()
        }

        if (disabledStateAlpha < 0f || disabledStateAlpha > 1f) {
            throw IllegalArgumentException("Wrong value for si_disabled_alpha [" + disabledStateAlpha + "]. "
                    + "Must be value from range [0, 1]")
        }

        colorFilter = PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN)
        setColorFilter(colorFilter)

        dashXStart = paddingLeft
        dashYStart = paddingTop

        dashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        dashPaint.style = Paint.Style.STROKE
        dashPaint.color = iconTintColor

        clipPath = Path()

        initDashCoordinates()
        setFraction(if (mEnabled) 0f else 1f)
    }

    /**
     * Changes state
     *
     * @param enabled If TRUE - icon is mEnabled
     */
    fun setIconEnabled(enabled: Boolean, animate: Boolean) {
        if (this.mEnabled == enabled) return
        switchState(animate)
    }

    /**
     * Switches icon state
     *
     * @param animate Indicates that state will be changed with or without animation
     */
    @JvmOverloads
    fun switchState(animate: Boolean = true) {
        val newFraction: Float = if (mEnabled) {
            1f
        } else {
            0f
        }
        mEnabled = !mEnabled
        if (animate) {
            animateToFraction(newFraction)
        } else {
            setFraction(newFraction)
            invalidate()
        }
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val savedState = SwitchIconSavedState(superState)
        savedState.iconEnabled = mEnabled
        return savedState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SwitchIconSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        mEnabled = state.iconEnabled
        setFraction(if (mEnabled) 0f else 1f)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        dashLengthXProjection = width - paddingLeft - paddingRight
        dashLengthYProjection = height - paddingTop - paddingBottom
        dashThickness = (DASH_THICKNESS_PART * (dashLengthXProjection + dashLengthYProjection) / 2f).toInt()
        dashPaint.strokeWidth = dashThickness.toFloat()
        initDashCoordinates()
        updateClipPath()
    }

    override fun onDraw(canvas: Canvas) {
        if (!noDash) {
            drawDash(canvas)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(clipPath)
            } else {
                canvas.clipPath(clipPath)
                //canvas.clipPath(clipPath, Region.Op.XOR)
            }
        }
        super.onDraw(canvas)
    }

    private fun animateToFraction(toFraction: Float) {
        val animator = ValueAnimator.ofFloat(fraction, toFraction)
        animator.addUpdateListener { animation -> setFraction(animation.animatedValue as Float) }
        animator.interpolator = DecelerateInterpolator()
        animator.duration = animationDuration
        animator.start()
    }

    private fun setFraction(fraction: Float) {
        this.fraction = fraction
        updateColor(fraction)
        updateAlpha(fraction)
        updateClipPath()
        postInvalidateOnAnimationCompat()
    }

    private fun initDashCoordinates() {
        val delta1 = 1.5f * SIN_45 * dashThickness.toFloat()
        val delta2 = 0.5f * SIN_45 * dashThickness.toFloat()
        dashStart.x = (dashXStart + delta2).toInt()
        dashStart.y = dashYStart + delta1.toInt()
        dashEnd.x = (dashXStart + dashLengthXProjection - delta1).toInt()
        dashEnd.y = (dashYStart + dashLengthYProjection - delta2).toInt()
    }

    private fun updateClipPath() {
        val delta = dashThickness / SIN_45
        clipPath.reset()
        clipPath.moveTo(dashXStart.toFloat(), dashYStart + delta)
        clipPath.lineTo(dashXStart + delta, dashYStart.toFloat())
        clipPath.lineTo(dashXStart + dashLengthXProjection * fraction, dashYStart + dashLengthYProjection * fraction - delta)
        clipPath.lineTo(dashXStart + dashLengthXProjection * fraction - delta, dashYStart + dashLengthYProjection * fraction)
    }

    private fun drawDash(canvas: Canvas) {
        val x = fraction * (dashEnd.x - dashStart.x) + dashStart.x
        val y = fraction * (dashEnd.y - dashStart.y) + dashStart.y
        canvas.drawLine(dashStart.x.toFloat(), dashStart.y.toFloat(), x, y, dashPaint)
    }

    private fun updateColor(fraction: Float) {
        if (iconTintColor != disabledStateColor) {
            val color = colorEvaluator.evaluate(fraction, iconTintColor, disabledStateColor) as Int
            updateImageColor(color)
            dashPaint.color = color
        }
    }

    private fun updateAlpha(fraction: Float) {
        val alpha = ((disabledStateAlpha + (1f - fraction) * (1f - disabledStateAlpha)) * 255).toInt()
        updateImageAlpha(alpha)
        dashPaint.alpha = alpha
    }

    private fun updateImageColor(color: Int) {
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        setColorFilter(colorFilter)
    }

    private fun updateImageAlpha(alpha: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            imageAlpha = alpha
        } else {
            setAlpha(alpha.toFloat())
        }
    }

    private fun postInvalidateOnAnimationCompat() {
        val fakeFrameTime: Long = 10
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            postInvalidateOnAnimation()
        } else {
            postInvalidateDelayed(fakeFrameTime)
        }
    }


    class SwitchIconSavedState : BaseSavedState, Parcelable {
        var iconEnabled: Boolean = false

        constructor(superState: Parcelable) : super(superState)

        private constructor(`in`: Parcel) : super(`in`) {
            val enabled = `in`.readInt()
            iconEnabled = enabled == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            out.writeInt(if (iconEnabled) 1 else 0)
        }

        companion object {
            @Suppress("unused")
            @JvmField
            val CREATOR: Parcelable.Creator<SwitchIconSavedState> = object : Parcelable.Creator<SwitchIconSavedState> {

                override fun createFromParcel(`in`: Parcel) = SwitchIconSavedState(`in`)

                override fun newArray(size: Int): Array<SwitchIconSavedState?> = arrayOfNulls(size)

            }
        }
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 300
        private const val DASH_THICKNESS_PART = 1f / 12f
        private const val DEFAULT_DISABLED_ALPHA = .5f
        private val SIN_45 = Math.sin(Math.toRadians(45.0)).toFloat()
    }
}

