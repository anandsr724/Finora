package com.example.expensetracker.ui.common

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * Resolves a theme-variant color attribute (attrs.xml) against the calling context's current
 * theme — the runtime equivalent of `?attr/colorX` in XML, needed anywhere a color is built in
 * code (GlassCardView, chart colors, drawables assembled at runtime) so it also switches between
 * Theme.Finora.Luminous and Theme.Finora.Onyx.
 */
@ColorInt
fun Context.themeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
