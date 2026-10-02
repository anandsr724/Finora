package com.example.expensetracker.ui.common

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.hypot
import kotlin.math.min

/**
 * Pinch-to-zoom, pan, double-tap-to-zoom, and swipe-to-dismiss ImageView for the full-screen
 * receipt viewer. Self-contained (no external photo-viewer library).
 *
 * Zoom/pan applies via its own working [Matrix] (drawing-only — it doesn't move the View
 * itself). Swipe-to-dismiss (only active at 1x, matching the Google Photos/iOS Photos
 * convention that a zoomed-in image pans instead) is tracked manually from each touch event's
 * *raw screen* coordinates rather than through GestureDetector.onScroll/onFling. That distinction
 * matters: this feature drags the view's own translationX/Y, a real View transform, and Android
 * computes each subsequent MotionEvent's local x/y *through* whatever transform the view
 * currently has. Accumulating GestureDetector's per-event dx/dy into translationX/Y therefore
 * feeds each frame's already-applied movement back into the next frame's reported delta — a
 * runaway feedback loop (confirmed empirically: deltas exploded from ~30px to ~50,000px within
 * twenty frames of a single slow swipe). Computing displacement as (rawX - startRawX) each move,
 * relative to a fixed start point in screen space, sidesteps this entirely.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 5f
        private const val DOUBLE_TAP_SCALE = 2.5f

        // Fraction of the view's shorter side a non-zoomed drag must cross to count as a
        // dismiss instead of springing back.
        private const val DISMISS_DRAG_FRACTION = 0.28f
        private const val FLING_DISMISS_VELOCITY = 1000f // px/sec
        private const val MAX_DRAG_SHRINK = 0.35f
        // The shrink reaches its floor (1 - MAX_DRAG_SHRINK) once the drag covers this fraction
        // of the dismiss threshold, then holds there — matching Google Photos, where the photo
        // shrinks a bit and stops, tracking the finger at that fixed size for the rest of the
        // drag, rather than continuing to shrink the further you drag.
        private const val SHRINK_SATURATION_FRACTION = 0.4f
    }

    /** Invoked on a single tap that isn't part of a double-tap or drag (e.g. to dismiss). */
    var onSingleTap: (() -> Unit)? = null

    /** Continuous during an active non-zoomed drag: 0f at rest, approaching 1f at the dismiss
     *  threshold. Use to dim/reveal a background scrim in lockstep with the drag — this view's
     *  own opacity never changes, only its position and (capped) scale do. */
    var onDragProgress: ((Float) -> Unit)? = null

    /** A non-zoomed drag was released before crossing the dismiss threshold — this view is
     *  springing back to rest; restore any scrim/chrome alpha over the same duration. */
    var onDragCancelled: ((durationMs: Long) -> Unit)? = null

    /** A non-zoomed drag was released past the dismiss threshold (or flung fast enough) and
     *  this view is about to fly off-screen — fade out any scrim/chrome over the same duration
     *  in lockstep. */
    var onDragCommitted: ((durationMs: Long) -> Unit)? = null

    /** The fly-off-and-fade triggered by [onDragCommitted] has finished — safe to dismiss. */
    var onDismiss: (() -> Unit)? = null

    private val workingMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var pinchScale = 1f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var isDragging = false
    private var dragResolved = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastMoveTimeMs = 0L
    private var velocityX = 0f
    private var velocityY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = (pinchScale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            val factor = target / pinchScale
            pinchScale = target
            workingMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            constrainAndApply()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (pinchScale > MIN_SCALE + 0.01f) MIN_SCALE else DOUBLE_TAP_SCALE
            val factor = target / pinchScale
            pinchScale = target
            workingMatrix.postScale(factor, factor, e.x, e.y)
            constrainAndApply()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            // Only used for panning a zoomed-in image (a Matrix transform, not a View transform
            // — safe to drive from GestureDetector's per-event delta here). Non-zoomed dragging
            // is handled manually in onTouchEvent — see the class doc for why.
            if (pinchScale <= MIN_SCALE + 0.01f) return false
            workingMatrix.postTranslate(-dx, -dy)
            constrainAndApply()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragResolved = false
                isDragging = false
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                lastMoveTimeMs = System.currentTimeMillis()
                velocityX = 0f
                velocityY = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinchScale > MIN_SCALE + 0.01f || event.pointerCount > 1) {
                    // Zoomed pan (Matrix-based) or a pinch in progress — not a dismiss-drag.
                } else {
                    val totalDx = event.rawX - dragStartRawX
                    val totalDy = event.rawY - dragStartRawY
                    if (!isDragging && hypot(totalDx, totalDy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val now = System.currentTimeMillis()
                        val dt = (now - lastMoveTimeMs).coerceAtLeast(1)
                        velocityX = (event.rawX - lastRawX) / dt * 1000f
                        velocityY = (event.rawY - lastRawY) / dt * 1000f
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        lastMoveTimeMs = now
                        translationX = totalDx
                        translationY = totalDy
                        updateDragVisuals()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && !dragResolved) {
                    isDragging = false
                    resolveDragRelease()
                }
            }
        }
        return true
    }

    private fun dismissThreshold() = min(width, height) * DISMISS_DRAG_FRACTION

    private fun updateDragVisuals() {
        val threshold = dismissThreshold()
        if (threshold <= 0f) return
        val dragDistance = hypot(translationX, translationY)
        val progress = (dragDistance / threshold).coerceIn(0f, 1f)
        // Shrink saturates quickly and then holds at its floor — see SHRINK_SATURATION_FRACTION
        // — while translation keeps tracking the finger 1:1 for the whole drag, unsaturated.
        val shrinkProgress = (progress / SHRINK_SATURATION_FRACTION).coerceIn(0f, 1f)
        val scale = 1f - shrinkProgress * MAX_DRAG_SHRINK
        scaleX = scale
        scaleY = scale
        onDragProgress?.invoke(progress)
    }

    private fun resolveDragRelease() {
        val threshold = dismissThreshold()
        val dragDistance = hypot(translationX, translationY)
        val flung = hypot(velocityX, velocityY) > FLING_DISMISS_VELOCITY
        if (threshold > 0f && (dragDistance > threshold || flung)) {
            commitDismiss()
        } else {
            dragResolved = true
            onDragCancelled?.invoke(220)
            animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun commitDismiss() {
        if (dragResolved) return
        dragResolved = true
        isDragging = false
        val dragDistance = hypot(translationX, translationY).coerceAtLeast(1f)
        // Continue in the same direction the user was already dragging/flinging, out past the
        // edge of the screen, rather than snapping to a fixed exit point. No alpha fade here —
        // by the time this finishes the view is scaled down and translated well off-screen, so
        // the lack of a fade isn't noticeable, and the image itself never changes opacity.
        val overshoot = (min(width, height) * 1.2f) / dragDistance
        onDragCommitted?.invoke(200)
        animate()
            .translationX(translationX * overshoot)
            .translationY(translationY * overshoot)
            .scaleX(0.5f).scaleY(0.5f)
            .setDuration(200)
            .withEndAction { onDismiss?.invoke() }
            .start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetTransform()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetTransform()
    }

    /** Re-centers and re-fits the image at 1x scale (matches `fitCenter`). */
    fun resetTransform() {
        pinchScale = 1f
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (vw <= 0f || vh <= 0f || dw <= 0f || dh <= 0f) return
        val scale = min(vw / dw, vh / dh)
        val dx = (vw - dw * scale) / 2f
        val dy = (vh - dh * scale) / 2f
        workingMatrix.reset()
        workingMatrix.postScale(scale, scale)
        workingMatrix.postTranslate(dx, dy)
        imageMatrix = workingMatrix
    }

    private fun constrainAndApply() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        workingMatrix.getValues(matrixValues)
        val scaledW = dw * matrixValues[Matrix.MSCALE_X]
        val scaledH = dh * matrixValues[Matrix.MSCALE_Y]

        matrixValues[Matrix.MTRANS_X] = if (scaledW <= vw) {
            (vw - scaledW) / 2f
        } else {
            matrixValues[Matrix.MTRANS_X].coerceIn(vw - scaledW, 0f)
        }
        matrixValues[Matrix.MTRANS_Y] = if (scaledH <= vh) {
            (vh - scaledH) / 2f
        } else {
            matrixValues[Matrix.MTRANS_Y].coerceIn(vh - scaledH, 0f)
        }
        workingMatrix.setValues(matrixValues)
        imageMatrix = workingMatrix
    }
}
