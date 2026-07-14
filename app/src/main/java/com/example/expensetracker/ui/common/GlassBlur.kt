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
 * Also strips two opaque layers BottomSheetDialog paints behind the content by default: the
 * window background, and — separately — the internal `design_bottom_sheet` container's own
 * MaterialShapeDrawable background (a plain colorSurface rectangle, unrounded, sized to the
 * full sheet). Left in place, that rectangle sits directly behind our rounded GlassCardView,
 * showing as a flat-cornered box peeking out from behind the card and flattening the glass
 * fill into a matte panel. findViewById is a no-op (null) for non-bottom-sheet dialogs.
 *
 * PixelFormat.TRANSLUCENT is set explicitly (rather than relying on it being inferred from the
 * transparent background drawable) since a transparent ColorDrawable was otherwise leaving the
 * window at PixelFormat.TRANSPARENT — a distinct "hole-punch" format, not the alpha-blended
 * format cross-window blur expects.
 */
fun Dialog.applyGlassBlur(radiusPx: Int = 48) {
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
    // Legacy blur-behind fallback (pre-dates cross-window blur, API 1) — tried alongside the
    // modern API since setBackgroundBlurRadius() was confirmed (via live SurfaceFlinger
    // inspection) to never reach the compositor's layer state on this build despite every
    // precondition checking out. Distinct mechanism: blurs via a dedicated blur-behind surface
    // rather than a per-layer compositor blur region, so it's independent of whatever is
    // blocking the modern path.
    window?.let { win ->
        win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val lp = win.attributes
        lp.setBlurBehindRadius(radiusPx)
        win.attributes = lp
        // Stitch's own scrim is `bg-black/40` (40% black); BottomSheetDialog's inherited
        // default dim (~60%) was crushing the blurred backdrop to near-black before it ever
        // reached the card, leaving the card's translucent fill nothing to visibly reveal.
        win.setDimAmount(0.4f)
    }
}
