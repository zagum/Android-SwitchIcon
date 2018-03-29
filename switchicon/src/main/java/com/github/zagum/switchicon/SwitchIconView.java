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

package com.github.zagum.switchicon;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Region;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

public class SwitchIconView extends AppCompatImageView {
  private static final int DEFAULT_ANIMATION_DURATION = 300;
  private static final float DASH_THICKNESS_PART = 1f / 12f;
  private static final float DEFAULT_DISABLED_ALPHA = .5f;
  private static final float SIN_45 = (float) Math.sin(Math.toRadians(45));

  private final long animationDuration;
  @FloatRange(from = 0f, to = 1f)
  private final float disabledStateAlpha;
  private final int dashXStart;
  private final int dashYStart;
  private final Path clipPath;
  private final int iconTintColor;
  private final int disabledStateColor;
  private final boolean noDash;
  private int dashThickness;
  private int dashLengthXProjection;
  private int dashLengthYProjection;
  private PorterDuffColorFilter colorFilter;
  private final ArgbEvaluator colorEvaluator = new ArgbEvaluator();

  @FloatRange(from = 0f, to = 1f)
  private float fraction = 0f;
  private boolean enabled;

  @NonNull
  private final Paint dashPaint;
  @NonNull
  private final Point dashStart = new Point();
  @NonNull
  private final Point dashEnd = new Point();

  public SwitchIconView(@NonNull Context context) {
    this(context, null);
  }

  public SwitchIconView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SwitchIconView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    setLayerType(LAYER_TYPE_SOFTWARE, null);

    TypedArray array = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SwitchIconView, 0, 0);

    try {
      iconTintColor = array.getColor(R.styleable.SwitchIconView_si_tint_color, Color.BLACK);
      animationDuration = array.getInteger(R.styleable.SwitchIconView_si_animation_duration, DEFAULT_ANIMATION_DURATION);
      disabledStateAlpha = array.getFloat(R.styleable.SwitchIconView_si_disabled_alpha, DEFAULT_DISABLED_ALPHA);
      disabledStateColor = array.getColor(R.styleable.SwitchIconView_si_disabled_color, iconTintColor);
      enabled = array.getBoolean(R.styleable.SwitchIconView_si_enabled, true);
      noDash = array.getBoolean(R.styleable.SwitchIconView_si_no_dash, false);
    } finally {
      array.recycle();
    }

    if (disabledStateAlpha < 0f || disabledStateAlpha > 1f) {
      throw new IllegalArgumentException("Wrong value for si_disabled_alpha [" + disabledStateAlpha + "]. "
          + "Must be value from range [0, 1]");
    }

    colorFilter = new PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN);
    setColorFilter(colorFilter);

    dashXStart = getPaddingLeft();
    dashYStart = getPaddingTop();

    dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dashPaint.setStyle(Paint.Style.STROKE);
    dashPaint.setColor(iconTintColor);

    clipPath = new Path();

    initDashCoordinates();
    setFraction(enabled ? 0f : 1f);
  }

  /**
   * Changes state with animation
   *
   * @param enabled If TRUE - icon is enabled
   */
  public void setIconEnabled(boolean enabled) {
    setIconEnabled(enabled, true);
  }

  /**
   * Changes state
   *
   * @param enabled If TRUE - icon is enabled
   */
  public void setIconEnabled(boolean enabled, boolean animate) {
    if (this.enabled == enabled) return;
    switchState(animate);
  }

  /**
   * Check state
   *
   * @return TRUE if icon is enabled, otherwise FALSE
   */
  public boolean isIconEnabled() {
    return enabled;
  }

  /**
   * Switches icon state with animation
   */
  public void switchState() {
    switchState(true);
  }

  /**
   * Switches icon state
   *
   * @param animate Indicates that state will be changed with or without animation
   */
  public void switchState(boolean animate) {
    float newFraction;
    if (enabled) {
      newFraction = 1f;
    } else {
      newFraction = 0f;
    }
    enabled = !enabled;
    if (animate) {
      animateToFraction(newFraction);
    } else {
      setFraction(newFraction);
      invalidate();
    }
  }

  @Override
  public Parcelable onSaveInstanceState() {
    Parcelable superState = super.onSaveInstanceState();
    SwitchIconSavedState savedState = new SwitchIconSavedState(superState);
    savedState.iconEnabled = enabled;
    return savedState;
  }

  @Override
  public void onRestoreInstanceState(Parcelable state) {
    if (!(state instanceof SwitchIconSavedState)) {
      super.onRestoreInstanceState(state);
      return;
    }
    SwitchIconSavedState savedState = (SwitchIconSavedState) state;
    super.onRestoreInstanceState(savedState.getSuperState());
    enabled = savedState.iconEnabled;
    setFraction(enabled ? 0f : 1f);
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    dashLengthXProjection = width - getPaddingLeft() - getPaddingRight();
    dashLengthYProjection = height - getPaddingTop() - getPaddingBottom();
    dashThickness = (int) (DASH_THICKNESS_PART * (dashLengthXProjection + dashLengthYProjection) / 2f);
    dashPaint.setStrokeWidth(dashThickness);
    initDashCoordinates();
    updateClipPath();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (!noDash) {
      drawDash(canvas);
      canvas.clipPath(clipPath, Region.Op.XOR);
    }
    super.onDraw(canvas);
  }

  private void animateToFraction(float toFraction) {
    ValueAnimator animator = ValueAnimator.ofFloat(fraction, toFraction);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        setFraction((float) animation.getAnimatedValue());
      }
    });
    animator.setInterpolator(new DecelerateInterpolator());
    animator.setDuration(animationDuration);
    animator.start();
  }

  private void setFraction(float fraction) {
    this.fraction = fraction;
    updateColor(fraction);
    updateAlpha(fraction);
    updateClipPath();
    postInvalidateOnAnimationCompat();
  }

  private void initDashCoordinates() {
    float delta1 = 1.5f * SIN_45 * dashThickness;
    float delta2 = 0.5f * SIN_45 * dashThickness;
    dashStart.x = (int) (dashXStart + delta2);
    dashStart.y = dashYStart + (int) (delta1);
    dashEnd.x = (int) (dashXStart + dashLengthXProjection - delta1);
    dashEnd.y = (int) (dashYStart + dashLengthYProjection - delta2);
  }

  private void updateClipPath() {
    float delta = dashThickness / SIN_45;
    clipPath.reset();
    clipPath.moveTo(dashXStart, dashYStart + delta);
    clipPath.lineTo(dashXStart + delta, dashYStart);
    clipPath.lineTo(dashXStart + dashLengthXProjection * fraction, dashYStart + dashLengthYProjection * fraction - delta);
    clipPath.lineTo(dashXStart + dashLengthXProjection * fraction - delta, dashYStart + dashLengthYProjection * fraction);
  }

  private void drawDash(Canvas canvas) {
    float x = fraction * (dashEnd.x - dashStart.x) + dashStart.x;
    float y = fraction * (dashEnd.y - dashStart.y) + dashStart.y;
    canvas.drawLine(dashStart.x, dashStart.y, x, y, dashPaint);
  }

  private void updateColor(float fraction) {
    if (iconTintColor != disabledStateColor) {
      final int color = (int) colorEvaluator.evaluate(fraction, iconTintColor, disabledStateColor);
      updateImageColor(color);
      dashPaint.setColor(color);
    }
  }

  private void updateAlpha(float fraction) {
    int alpha = (int) ((disabledStateAlpha + (1f - fraction) * (1f - disabledStateAlpha)) * 255);
    updateImageAlpha(alpha);
    dashPaint.setAlpha(alpha);
  }

  private void updateImageColor(int color) {
    colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
    setColorFilter(colorFilter);
  }

  @SuppressWarnings("deprecation")
  private void updateImageAlpha(int alpha) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      setImageAlpha(alpha);
    } else {
      setAlpha(alpha);
    }
  }

  private void postInvalidateOnAnimationCompat() {
    final long fakeFrameTime = 10;
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
      postInvalidateOnAnimation();
    } else {
      postInvalidateDelayed(fakeFrameTime);
    }
  }

  static class SwitchIconSavedState extends BaseSavedState {
    boolean iconEnabled;

    SwitchIconSavedState(Parcelable superState) {
      super(superState);
    }

    private SwitchIconSavedState(Parcel in) {
      super(in);
      final int enabled = in.readInt();
      iconEnabled = enabled == 1;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeInt(iconEnabled ? 1 : 0);
    }

    public static final Parcelable.Creator<SwitchIconSavedState> CREATOR =
        new Parcelable.Creator<SwitchIconSavedState>() {
          public SwitchIconSavedState createFromParcel(Parcel in) {
            return new SwitchIconSavedState(in);
          }

          public SwitchIconSavedState[] newArray(int size) {
            return new SwitchIconSavedState[size];
          }
        };
  }
}
