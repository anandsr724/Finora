package com.example.expensetracker.ui.common

import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView

/**
 * Renders this ImageView as a flat colored dot with a soft colored glow behind it, matching
 * the Stitch reference's `shadow-[0_0_8px_rgba(color,0.6)]` category-dot treatment. Uses a
 * colored elevation shadow (API 28+) since Android has no direct CSS box-shadow equivalent;
 * below API 28 it falls back to the flat dot with no glow.
 */
fun ImageView.applyCategoryDotGlow(color: Int, elevationDp: Float = 6f) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
    clipToOutline = false
    elevation = elevationDp * resources.displayMetrics.density
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        outlineSpotShadowColor = color
        outlineAmbientShadowColor = color
    }
}

/**
 * Same colored-elevation-shadow technique as [applyCategoryDotGlow], but for a rounded-square
 * badge instead of a circle — matches the "Vivid Glass Variant" reference's
 * `vivid-glow-shadow` halo applied to Category icon badges and the Export CSV button.
 */
fun View.applyVividGlow(color: Int, cornerRadiusDp: Float = 12f, elevationDp: Float = 6f) {
    val density = resources.displayMetrics.density
    val cornerRadiusPx = cornerRadiusDp * density
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
        }
    }
    clipToOutline = false
    elevation = elevationDp * density
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        outlineSpotShadowColor = color
        outlineAmbientShadowColor = color
    }
}
