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
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.FloatRange
import androidx.appcompat.widget.AppCompatImageView

class SwitchIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatImageView(context, attrs, defStyleAttr) {

    @FloatRange(from = 0.0, to = 1.0)
    private var fraction = 0f
    private var dashThickness: Int = 0
    private var dashLengthXProjection: Int = 0
    private var dashLengthYProjection: Int = 0
    private var colorFilter: PorterDuffColorFilter

    @FloatRange(from = 0.0, to = 1.0)
    private val disabledStateAlpha: Float
    private val animationDuration: Long
    private val dashXStart: Int
    private val dashYStart: Int
    private val iconTintColor: Int
    private val disabledStateColor: Int
    private val noDash: Boolean
    private val colorEvaluator = ArgbEvaluator()
    private val clipPath = Path()
    private val dashStart = Point()
    private val dashEnd = Point()
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /**
     * Check state
     *
     * @return TRUE if icon is enabled, otherwise FALSE
     */
    var isIconEnabled = false
        private set

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        context.theme.obtainStyledAttributes(attrs, R.styleable.SwitchIconView, 0, 0).apply {
            try {
                iconTintColor = getColor(R.styleable.SwitchIconView_si_tint_color, Color.BLACK)
                animationDuration = getInteger(R.styleable.SwitchIconView_si_animation_duration, DEFAULT_ANIMATION_DURATION).toLong()
                disabledStateAlpha = getFloat(R.styleable.SwitchIconView_si_disabled_alpha, DEFAULT_DISABLED_ALPHA)
                disabledStateColor = getColor(R.styleable.SwitchIconView_si_disabled_color, iconTintColor)
                isIconEnabled = getBoolean(R.styleable.SwitchIconView_si_enabled, true)
                noDash = getBoolean(R.styleable.SwitchIconView_si_no_dash, false)
            } finally {
                recycle()
            }
        }

        if (disabledStateAlpha < 0f || disabledStateAlpha > 1f) {
            throw IllegalArgumentException("Wrong value for si_disabled_alpha [" + disabledStateAlpha + "]. "
                    + "Must be value from range [0, 1]")
        }

        colorFilter = PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN)
        setColorFilter(colorFilter)

        dashXStart = paddingLeft
        dashYStart = paddingTop

        dashPaint.color = iconTintColor

        initDashCoordinates()
        setFraction(if (isIconEnabled) 0f else 1f)
    }

    /**
     * Changes state
     *
     * @param enabled If TRUE - icon is enabled
     */
    @JvmOverloads
    fun setIconEnabled(enabled: Boolean, animate: Boolean = true) {
        if (isIconEnabled == enabled) return
        switchState(animate)
    }

    /**
     * Switches icon state
     *
     * @param animate Indicates that state will be changed with or without animation
     * default value = TRUE
     */
    @JvmOverloads
    fun switchState(animate: Boolean = true) {
        val newFraction = if (isIconEnabled) 1f else 0f
        isIconEnabled = !isIconEnabled
        if (animate) {
            animateToFraction(newFraction)
        } else {
            setFraction(newFraction)
            invalidate()
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SwitchIconSavedState(superState)
        savedState.iconEnabled = isIconEnabled
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SwitchIconSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        isIconEnabled = state.iconEnabled
        setFraction(if (isIconEnabled) 0f else 1f)
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
                canvas.clipPath(clipPath, Region.Op.XOR)
            }
        }
        super.onDraw(canvas)
    }

    private fun animateToFraction(toFraction: Float) {
        ValueAnimator.ofFloat(fraction, toFraction).apply {
            addUpdateListener { animation -> setFraction(animation.animatedValue as Float) }
            interpolator = DecelerateInterpolator()
            duration = animationDuration
            start()
        }
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
        clipPath.apply {
            reset()
            moveTo(dashXStart.toFloat(), dashYStart + delta)
            lineTo(dashXStart + delta, dashYStart.toFloat())
            lineTo(dashXStart + dashLengthXProjection * fraction, dashYStart + dashLengthYProjection * fraction - delta)
            lineTo(dashXStart + dashLengthXProjection * fraction - delta, dashYStart + dashLengthYProjection * fraction)
        }
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
            setAlpha(alpha)
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

    internal class SwitchIconSavedState : BaseSavedState {
        var iconEnabled: Boolean = false

        constructor(superState: Parcelable) : super(superState) {}

        private constructor(parcel: Parcel) : super(parcel) {
            val enabled = parcel.readInt()
            iconEnabled = enabled == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (iconEnabled) 1 else 0)
        }

        companion object {

            @JvmField
            val CREATOR: Parcelable.Creator<SwitchIconSavedState> = object : Parcelable.Creator<SwitchIconSavedState> {
                override fun createFromParcel(parcel: Parcel): SwitchIconSavedState {
                    return SwitchIconSavedState(parcel)
                }

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
