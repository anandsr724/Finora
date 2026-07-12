package com.example.expensetracker.ui.common

import android.app.Dialog
import android.os.Build

/**
 * Applies real frosted-glass blur to whatever is behind this dialog/bottom sheet window,
 * via the officially-supported Window.setBackgroundBlurRadius (API 31+). Below API 31 this
 * is a no-op — the sheet's own glass fill (set in its layout, level3) must stay opaque enough
 * on its own since there's no blur to lean on there.
 */
fun Dialog.applyGlassBlur(radiusPx: Int = 48) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window?.setBackgroundBlurRadius(radiusPx)
    }
}
