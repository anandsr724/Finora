package com.example.expensetracker.ui.common

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
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

    init {
        var level = 2
        val defaultRadius = resources.getDimension(R.dimen.glass_card_corner_radius)
        cornerRadiusPx = defaultRadius
        context.withStyledAttributes(attrs, R.styleable.GlassCardView) {
            level = getInt(R.styleable.GlassCardView_glassLevel, 2)
            cornerRadiusPx = getDimension(R.styleable.GlassCardView_glassCornerRadius, defaultRadius)
        }
        applyGlassBackground(level)

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = true
    }

    private fun applyGlassBackground(level: Int) {
        val fillColorRes = if (level == 3) R.color.color_glass_fill_l3 else R.color.color_glass_fill_l2
        val borderColorRes = if (level == 3) R.color.color_glass_border_bright else R.color.color_glass_border
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(ContextCompat.getColor(context, fillColorRes))
            setStroke(resources.getDimensionPixelSize(R.dimen.glass_card_border_width), ContextCompat.getColor(context, borderColorRes))
        }
    }
}
