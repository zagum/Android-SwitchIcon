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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class SwitchIconView extends View {

  public static final int ENABLED = 0;
  public static final int DISABLED = 1;

  private static final int DEFAULT_ANIMATION_DURATION = 300;
  private static final int DASH_THICKNESS_DP = 3;
  private static final float DEFAULT_DISABLED_ALPHA = .5f;
  private static final float SIN_45 = (float) Math.sin(Math.toRadians(45));

  @IntDef({
      ENABLED,
      DISABLED
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {
  }

  private final long animationDuration;
  @FloatRange(from = 0f, to = 1f)
  private final float disabledStateAlpha;
  private final int dashThickness;
  private final int padding;
  private final int dashXStart;
  private final int dashYStart;
  private final int dashLengthXProjection;
  private final int dashLengthYProjection;
  private final Path path;
  private Bitmap icon;

  @State
  private int currentState = ENABLED;
  @FloatRange(from = 0f, to = 1f)
  private float fraction = 0f;

  @NonNull
  private final Paint dashPaint;
  @NonNull
  private final Paint iconPaint;
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

    TypedArray array = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SwitchIconView, 0, 0);

    int iconTintColor;
    int iconResId;
    try {
      iconResId = array.getResourceId(R.styleable.SwitchIconView_si_image, 0);
      iconTintColor = array.getColor(R.styleable.SwitchIconView_si_tint_color, 0);
      padding = array.getDimensionPixelSize(R.styleable.SwitchIconView_si_padding, 0);
      animationDuration = array.getInteger(R.styleable.SwitchIconView_si_animation_duration, DEFAULT_ANIMATION_DURATION);
      disabledStateAlpha = array.getFloat(R.styleable.SwitchIconView_si_disabled_alpha, DEFAULT_DISABLED_ALPHA);
    } finally {
      array.recycle();
    }

    if (disabledStateAlpha < 0f || disabledStateAlpha > 1f) {
      throw new IllegalArgumentException("Wrong value for si_disabled_alpha [" + disabledStateAlpha + "]. "
          + "Must be value from range [0, 1]");
    }

    if (iconResId == 0) {
      throw new IllegalArgumentException("You must set correct icon id");
    }

    icon = getBitmapFromDrawable(iconResId);

    final float density = getResources().getDisplayMetrics().density;
    dashThickness = (int) (DASH_THICKNESS_DP * density);

    dashXStart = -icon.getWidth() / 2;
    dashYStart = -icon.getHeight() / 2;
    dashLengthXProjection = icon.getWidth();
    dashLengthYProjection = icon.getHeight();

    if (iconTintColor == 0) {
      iconTintColor = getBitmapColor(icon);
    }

    iconPaint = new Paint();
    iconPaint.setColorFilter(new PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN));

    dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dashPaint.setStyle(Paint.Style.STROKE);
    dashPaint.setColorFilter(new PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN));
    dashPaint.setStrokeWidth(dashThickness);

    path = new Path();

    initDashCoordinates();
  }

  /**
   * Changes state with animation
   *
   * @param state {@link #ENABLED} or {@link #DISABLED}
   * @throws IllegalArgumentException if {@param state} is invalid
   */
  public void setState(@State int state) {
    setState(state, true);
  }

  /**
   * Changes state
   *
   * @param state {@link #ENABLED} or {@link #DISABLED}
   * @param animate Indicates that state will be changed with or without animation
   * @throws IllegalArgumentException if {@param state} is invalid
   */
  public void setState(@State int state, boolean animate) {
    if (state == currentState) return;
    if (state != ENABLED && state != DISABLED) {
      throw new IllegalArgumentException("Unknown state [" + state + "]");
    }
    switchState(animate);
  }

  /**
   * Switches state between values {@link #ENABLED} or {@link #DISABLED}
   * with animation
   */
  public void switchState() {
    switchState(true);
  }

  /**
   * Switches state between values {@link #ENABLED} or {@link #DISABLED}
   * with animation
   *
   * @param animate Indicates that state will be changed with or without animation
   */
  public void switchState(boolean animate) {
    float newFraction;
    if (currentState == ENABLED) {
      newFraction = 1f;
      currentState = DISABLED;
    } else {
      newFraction = 0f;
      currentState = ENABLED;
    }
    if (animate) {
      animateToFraction(newFraction);
    } else {
      setFraction(newFraction);
      invalidate();
    }
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
    int alpha = (int) ((disabledStateAlpha + (1f - fraction) * (1f - disabledStateAlpha)) * 255);
    iconPaint.setAlpha(alpha);
    dashPaint.setAlpha(alpha);
    updatePath();
    postInvalidateOnAnimationCompat();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
    final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);

    final int requiredWidth = icon.getWidth() + 2 * padding;
    final int requiredHeight = icon.getHeight() + 2 * padding;

    if (widthSpecMode != MeasureSpec.EXACTLY) {
      widthMeasureSpec = MeasureSpec.makeMeasureSpec(requiredWidth, MeasureSpec.EXACTLY);
    }

    if (heightSpecMode != MeasureSpec.EXACTLY) {
      heightMeasureSpec = MeasureSpec.makeMeasureSpec(requiredHeight, MeasureSpec.EXACTLY);
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (icon != null) {
      canvas.translate(getWidth() / 2, getHeight() / 2);
      drawDash(canvas);
      canvas.clipPath(path, Region.Op.XOR);
      canvas.drawBitmap(icon, -icon.getWidth() / 2, -icon.getHeight() / 2, iconPaint);
    }
  }

  private void initDashCoordinates() {
    float delta1 = 1.5f * SIN_45 * dashThickness;
    float delta2 = 0.5f * SIN_45 * dashThickness;
    dashStart.x = (int) (dashXStart + delta2);
    dashStart.y = dashYStart + (int) (delta1);
    dashEnd.x = (int) (dashLengthXProjection / 2 - delta1);
    dashEnd.y = (int) (dashLengthYProjection / 2 - delta2);
  }

  private void updatePath() {
    float delta = dashThickness / SIN_45;
    path.reset();
    path.moveTo(dashXStart, dashYStart + delta);
    path.lineTo(dashXStart + delta, dashYStart);
    path.lineTo(dashXStart + dashLengthXProjection * fraction, dashYStart + dashLengthYProjection * fraction - delta);
    path.lineTo(dashXStart + dashLengthXProjection * fraction - delta, dashYStart + dashLengthYProjection * fraction);
  }

  private void drawDash(Canvas canvas) {
    float x = fraction * (dashEnd.x - dashStart.x) + dashStart.x;
    float y = fraction * (dashEnd.y - dashStart.y) + dashStart.y;
    canvas.drawLine(dashStart.x, dashStart.y, x, y, dashPaint);
  }

  @ColorInt
  private int getBitmapColor(final Bitmap bitmap) {
    int maxAlpha = 0;
    int color = Color.TRANSPARENT;
    for (int y = 0; y < bitmap.getHeight(); y++) {
      for (int x = 0; x < bitmap.getWidth(); x++) {
        final int pixel = bitmap.getPixel(x, y);
        if (pixel != Color.TRANSPARENT) {
          final int alpha = Color.alpha(pixel);
          if (alpha > maxAlpha) {
            maxAlpha = alpha;
            color = pixel;
          }
        }
      }
    }
    return color;
  }

  private Bitmap getBitmapFromDrawable(@DrawableRes int drawableId) {
    Drawable drawable = ContextCompat.getDrawable(getContext(), drawableId);
    if (drawable instanceof BitmapDrawable) {
      return ((BitmapDrawable) drawable).getBitmap();
    } else if (drawable instanceof VectorDrawable || drawable instanceof VectorDrawableCompat) {
      Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
      drawable.draw(canvas);
      return bitmap;
    } else {
      throw new IllegalArgumentException("unsupported drawable type");
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

  private void resizeIcon(int newWidth, int newHeight) {
    if (newWidth < 0 || newHeight < 0) return;
    float scale = Math.min((float) newWidth / (float) icon.getWidth(), (float) newHeight / (float) icon.getHeight());
    icon = Bitmap.createScaledBitmap(icon, (int) (icon.getWidth() * scale), (int) (icon.getHeight() * scale), false);
  }
}
