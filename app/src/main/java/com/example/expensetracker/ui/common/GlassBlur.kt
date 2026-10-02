package com.example.expensetracker.ui.common

import android.app.Dialog
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import com.google.android.material.R as MaterialR

/**
 * Applies real frosted-glass blur to whatever is behind this dialog/bottom sheet window,
 * via the officially-supported Window.setBackgroundBlurRadius (API 31+). Below API 31 this
 * is a no-op — the sheet's own glass fill (set in its layout, level3) must stay opaque enough
 * on its own since there's no blur to lean on there.
 *
 * Also applies a legacy FLAG_BLUR_BEHIND fallback (pre-dates cross-window blur, API 1) since
 * setBackgroundBlurRadius() was confirmed (via live SurfaceFlinger inspection, and again by
 * A/B screenshot comparison) to never reach the compositor on this test build, despite every
 * precondition checking out. That legacy path's blur is visibly lower-quality than the modern
 * one — it shows 8-bit color-banding when blurring the app's near-black gradients — but a soft,
 * slightly-banded glow reads as intentional "glass catching light" at the small on-screen size
 * these dialogs render at, whereas disabling it entirely was a worse regression: it deletes the
 * whole soft-color-bleed-through look these dialogs are designed around (confirmed by A/B — see
 * Transaction Details' `glassTranslucent` card, which has nothing to reveal without this).
 * radiusLegacyPx is intentionally smaller than the modern radiusPx: a smaller blur kernel visibly
 * reduces the banding's step size without losing the soft-glow effect.
 *
 * PixelFormat.TRANSLUCENT is set explicitly (rather than relying on it being inferred from the
 * transparent background drawable) since a transparent ColorDrawable was otherwise leaving the
 * window at PixelFormat.TRANSPARENT — a distinct "hole-punch" format, not the alpha-blended
 * format cross-window blur expects.
 */
fun Dialog.applyGlassBlur(radiusPx: Int = 48, radiusLegacyPx: Int = 20) {
    window?.setBackgroundDrawableResource(android.R.color.transparent)
    window?.setFormat(PixelFormat.TRANSLUCENT)
    findViewById<android.view.View>(MaterialR.id.design_bottom_sheet)?.apply {
        background = null
        elevation = 0f
        // BottomSheetBehavior re-asserts its own MaterialShapeDrawable background on this view
        // during its internal layout callbacks, undoing a one-time `background = null` shortly
        // after it's set (confirmed empirically: the unrounded rectangle sliver persisted even
        // after nulling this at dialog-show time). Re-nulling on every layout pass keeps it from
        // ever being visible again, however many times Material reassigns it.
        viewTreeObserver.addOnGlobalLayoutListener {
            if (background != null) background = null
        }
    }
    window?.setElevation(0f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window?.setBackgroundBlurRadius(radiusPx)
    }
    window?.let { win ->
        win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val lp = win.attributes
        lp.setBlurBehindRadius(radiusLegacyPx)
        win.attributes = lp
        // Stitch's own scrim is `bg-black/40` (40% black); BottomSheetDialog's inherited
        // default dim (~60%) was crushing the blurred backdrop to near-black before it ever
        // reached the card, leaving the card's translucent fill nothing to visibly reveal.
        win.setDimAmount(0.4f)
    }
}
