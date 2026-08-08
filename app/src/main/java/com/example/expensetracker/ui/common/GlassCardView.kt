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
        var translucent = false
        val defaultRadius = resources.getDimension(R.dimen.glass_card_corner_radius)
        cornerRadiusPx = defaultRadius
        context.withStyledAttributes(attrs, R.styleable.GlassCardView) {
            level = getInt(R.styleable.GlassCardView_glassLevel, 2)
            cornerRadiusPx = getDimension(R.styleable.GlassCardView_glassCornerRadius, defaultRadius)
            topCornersOnly = getBoolean(R.styleable.GlassCardView_glassTopCornersOnly, false)
            translucent = getBoolean(R.styleable.GlassCardView_glassTranslucent, false)
        }
        applyGlassBackground(level, translucent)

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

    private fun applyGlassBackground(level: Int, translucent: Boolean = false) {
        val borderColorAttr = if (level == 3) R.attr.colorGlassBorderBright else R.attr.colorGlassBorder
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
            // Blur alone read as too see-through for most level-3 sheets (busy content behind
            // stayed legible enough to be distracting), so the default base is a solid, mostly
            // opaque fill. glassTranslucent opts a specific sheet (Transaction Details) out of
            // that into a much lower-alpha base instead, so the real window blur (applyGlassBlur)
            // actually reads through it — matching Stitch's lighter "glass-modal" reference.
            val baseAttr = if (translucent) R.attr.colorGlassFillL3TranslucentBase else R.attr.colorGlassFillL3Base
            val base = roundedRect().apply {
                setColor(context.themeColor(baseAttr))
            }
            val highlight = roundedRect().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    context.themeColor(R.attr.colorGlassFillL3Bright),
                    context.themeColor(R.attr.colorGlassFillL3Dim)
                )
                setStroke(resources.getDimensionPixelSize(R.dimen.glass_card_border_width), context.themeColor(borderColorAttr))
            }
            background = LayerDrawable(arrayOf(base, highlight))
        } else {
            background = roundedRect().apply {
                setColor(context.themeColor(R.attr.colorGlassFillL2))
                setStroke(resources.getDimensionPixelSize(R.dimen.glass_card_border_width), context.themeColor(borderColorAttr))
            }
        }
    }
}
