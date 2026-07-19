package com.example.expensetracker.ui.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.pow

/**
 * Paints [fill] over a soft halo, approximating Stitch's `box-shadow: 0 4px 15px
 * rgba(255,180,171,0.3)` — a shadow offset downward, at a modest 30% peak opacity, blurred
 * outward from the shape's own edge (not from a single center point, which a Paint
 * RadialGradient would use and which decays at different real-pixel rates on a wide, short
 * pill's short vs. long axis).
 *
 * Draws ~30 concentric copies of the pill's own rounded-rect shape, each dilated outward by
 * a fraction of [bleedPx]. Each ring is clipped to only the *annular band* between it and the
 * next-smaller ring (via [Path.Op.DIFFERENCE]) rather than a full filled shape — stacking full
 * filled shapes on top of each other, tried first, made the overlapping interior compound far
 * past any single ring's nominal alpha, reading as a much more saturated "neon" halo than
 * Stitch's restrained shadow.
 *
 * [yOffsetPx] ramps in proportionally to each ring's distance from the fill (0 at the
 * innermost ring, full offset at the outermost) rather than shifting every ring uniformly.
 * Shifting all rings — including the one touching the fill's own edge — by the full offset,
 * tried first, left a hard-edged strip of bare background between the fill and where the
 * (now-displaced) glow began on the side opposite the shift.
 */
class GlowPillDrawable(
    private val glowColor: Int,
    private val bleedPx: Float,
    private val yOffsetPx: Float,
    private val fill: Drawable
) : Drawable() {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        fill.setBounds(bounds)
    }

    private fun ringPath(inset: Float, t: Float, b: Rect): Path {
        val offset = yOffsetPx * t
        val rect = RectF(
            b.left - inset,
            b.top - inset + offset,
            b.right + inset,
            b.bottom + inset + offset
        )
        return Path().apply { addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW) }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = Color.red(glowColor)
        val g = Color.green(glowColor)
        val bl = Color.blue(glowColor)

        val steps = 30
        val peakAlpha = 76f // 0x4D — matches Stitch's box-shadow alpha of 0.3
        for (i in 0 until steps) {
            val tInner = i / steps.toFloat()
            val tOuter = (i + 1) / steps.toFloat()
            val innerInset = bleedPx * tInner
            val outerInset = bleedPx * tOuter
            val closeness = 1f - tInner  // 1 nearest the fill -> ~0 at the outer edge
            val alpha = (peakAlpha * closeness.pow(1.6f)).toInt().coerceIn(0, 255)
            if (alpha <= 0) continue
            val annulus = Path()
            annulus.op(ringPath(outerInset, tOuter, b), ringPath(innerInset, tInner, b), Path.Op.DIFFERENCE)
            ringPaint.color = Color.argb(alpha, r, g, bl)
            canvas.drawPath(annulus, ringPaint)
        }
        fill.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
