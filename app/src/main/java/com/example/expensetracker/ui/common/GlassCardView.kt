package com.example.expensetracker.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.example.expensetracker.R

/**
 * Luminous Finance "glass" surface: a flat translucent fill + 1px border standing in for
 * frosted glass, per the app's blur-scoping decision — true backdrop blur isn't achievable
 * for opaque content in front of scrolling background, so every in-flow card (Home, History,
 * Analytics, Settings, transaction rows) uses this cheap approximation instead. It renders
 * identically on every API level, unlike bottom sheets/dialogs which use real window blur
 * (see [applyGlassBlur]).
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var cornerRadiusPx: Float
    private var topCornersOnly: Boolean = false
    private var topClipPath: Path? = null

    init {
        var level = 2
        val defaultRadius = resources.getDimension(R.dimen.glass_card_corner_radius)
        cornerRadiusPx = defaultRadius
        context.withStyledAttributes(attrs, R.styleable.GlassCardView) {
            level = getInt(R.styleable.GlassCardView_glassLevel, 2)
            cornerRadiusPx = getDimension(R.styleable.GlassCardView_glassCornerRadius, defaultRadius)
            topCornersOnly = getBoolean(R.styleable.GlassCardView_glassTopCornersOnly, false)
        }
        applyGlassBackground(level)

        if (topCornersOnly) {
            // Outline.setConvexPath() cannot clip (Outline.canClip() is only true for RECT/
            // ROUND_RECT) — it only affects shadow casting. A top-only rounded rect isn't a
            // ROUND_RECT as far as Outline is concerned, so clipToOutline silently did nothing
            // here and both the background and child content could draw past the rounded
            // corners. draw() below applies a manual Canvas.clipPath instead, which clips
            // unconditionally regardless of outline type.
            setWillNotDraw(false)
        } else {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            clipToOutline = true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (topCornersOnly && w > 0 && h > 0) {
            topClipPath = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, w.toFloat(), h.toFloat()),
                    floatArrayOf(
                        cornerRadiusPx, cornerRadiusPx,
                        cornerRadiusPx, cornerRadiusPx,
                        0f, 0f,
                        0f, 0f
                    ),
                    Path.Direction.CW
                )
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val path = topClipPath
        if (path != null) {
            val save = canvas.save()
            canvas.clipPath(path)
            super.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            super.draw(canvas)
        }
    }

    private fun applyGlassBackground(level: Int) {
        val borderColorRes = if (level == 3) R.color.color_glass_border_bright else R.color.color_glass_border
        val corners: FloatArray? = if (topCornersOnly) {
            floatArrayOf(
                cornerRadiusPx, cornerRadiusPx,
                cornerRadiusPx, cornerRadiusPx,
                0f, 0f,
                0f, 0f
            )
        } else null

        fun roundedRect() = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (corners != null) cornerRadii = corners else cornerRadius = cornerRadiusPx
        }

        if (level == 3) {
            // Blur alone read as too see-through — the busy content behind the sheet stayed
            // legible enough to be distracting. A solid dark base underneath the blur gives the
            // sheet real body/contrast, while the white highlight gradient on top (bright
            // top-left fading to dim bottom-right) keeps the "glass catching light" look instead
            // of turning it into a flat opaque panel.
            val base = roundedRect().apply {
                setColor(ContextCompat.getColor(context, R.color.color_glass_fill_l3_base))
            }
            val highlight = roundedRect().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    ContextCompat.getColor(context, R.color.color_glass_fill_l3_bright),
                    ContextCompat.getColor(context, R.color.color_glass_fill_l3_dim)
                )
                setStroke(resources.getDimensionPixelSize(R.dimen.glass_card_border_width), ContextCompat.getColor(context, borderColorRes))
            }
            background = LayerDrawable(arrayOf(base, highlight))
        } else {
            background = roundedRect().apply {
                setColor(ContextCompat.getColor(context, R.color.color_glass_fill_l2))
                setStroke(resources.getDimensionPixelSize(R.dimen.glass_card_border_width), ContextCompat.getColor(context, borderColorRes))
            }
        }
    }
}
