package com.santander.globile.bubblepagerindicator

import android.animation.ValueAnimator
import android.content.Context
import android.animation.Animator
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator

import androidx.annotation.Dimension
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

import android.graphics.Paint.ANTI_ALIAS_FLAG
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
class BubblePageIndicator(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.bpi_indicatorStyle
) :
    MotionIndicator(context, attrs, defStyle),
    ViewPager.OnPageChangeListener,
    ViewPager.OnAdapterChangeListener
{
    private var onSurfaceCount: Int = 0
    private var risingCount:  Int = 0
    private var surfaceStart: Int = 0
    private var surfaceEnd: Int = 0
    private var radius: Float = 0.toFloat()
    private var marginBetweenCircles: Float = 0.toFloat()
    private val paintPageFill = Paint(ANTI_ALIAS_FLAG)
    private val paintFill = Paint(ANTI_ALIAS_FLAG)
    private var scrollState: Int = 0
    private var scaleRadiusCorrection = ADD_RADIUS_DEFAULT
    private var translationAnim: ValueAnimator? = null
    private var startX = Integer.MIN_VALUE
    private var offset: Float = 0.toFloat()
    private var swipeDirection: Int = 0
    private var animationState = ANIMATE_IDLE

    private val dataSetObserver = object : DataSetObserver() {
        override fun onChanged() {
            ensureState()
            forceLayoutChanges()
        }
    }

    var pageColor: Int
        get() = paintPageFill.color
        set(pageColor) {
            paintPageFill.color = pageColor
            invalidate()
        }

    var fillColor: Int
        get() = paintFill.color
        set(fillColor) {
            paintFill.color = fillColor
            invalidate()
        }

    override val count: Int
        get() {
            return if (viewPager == null || viewPager!!.adapter == null)
            { 0 } else { viewPager!!.adapter!!.count }
        }


    private val initialStartX: Int
        get() {
            val result = internalPaddingLeft
            if (count <= onSurfaceCount)
                return result

            val risingCount = internalRisingCount
            return if (risingCount == 0) {
                result
            } else {
                (result.toFloat() + risingCount.toFloat() * radius * 2f
                        + (risingCount - 1) * marginBetweenCircles).toInt()
            }
        }

    private val internalRisingCount: Int
        get() {
            return if (count < onSurfaceCount + this.risingCount)
                count - onSurfaceCount
            else
                this.risingCount
        }

    private val internalPaddingLeft: Int
        get() = (paddingLeft + radius).toInt()

    private fun ensureState() {
        correctSurfaceIfDataSetChanges()
        if (currentPage >= count && count != 0)
            currentPage = count - 1

        correctStartXOnDataSetChanges()
    }

    private fun correctSurfaceIfDataSetChanges() {
        if (onSurfaceCount != surfaceEnd - surfaceStart + 1) {
            surfaceStart = currentPage
            surfaceEnd = surfaceStart + onSurfaceCount - 1
        }

        if (count == 0) return
        if (surfaceEnd > count - 1) {
            if (count > onSurfaceCount) {
                surfaceEnd = count - 1
                surfaceStart = surfaceEnd - (onSurfaceCount - 1)
            } else {
                surfaceEnd = onSurfaceCount - 1
                surfaceStart = 0
            }
        }
    }

    private fun correctStartXOnDataSetChanges() {
        if (startX == Integer.MIN_VALUE) return

        var initial = initialStartX
        if (startX == initial) return

        if (surfaceEnd > onSurfaceCount - 1) {
            initial -= ((surfaceEnd - (onSurfaceCount - 1)) *
                    (marginBetweenCircles + radius * 2)).toInt()

            if (count - onSurfaceCount <= 1)
                initial -= (marginBetweenCircles + radius * 2).toInt()
        }

        startX = initial
    }

    init {
        if (!isInEditMode) {
            //Retrieve styles attributes
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.BubblePageIndicator, defStyle, 0
            )

            paintPageFill.style = Style.FILL
            paintPageFill.color = typedArray.getColor(
                R.styleable.BubblePageIndicator_bpi_pageColor, 0
            )

            paintFill.style = Style.FILL
            paintFill.color = typedArray.getColor(
                R.styleable.BubblePageIndicator_bpi_fillColor, 0
            )

            radius = typedArray.getDimensionPixelSize(
                R.styleable.BubblePageIndicator_bpi_radius,
                0
            ).toFloat()

            marginBetweenCircles = typedArray.getDimensionPixelSize(
                R.styleable.BubblePageIndicator_bpi_marginBetweenCircles,
                0
            ).toFloat()

            onSurfaceCount = typedArray.getInteger(
                R.styleable.BubblePageIndicator_bpi_onSurfaceCount, 0
            )

            risingCount = typedArray.getInteger(
                R.styleable.BubblePageIndicator_bpi_risingCount, 0
            )

            typedArray.recycle()

            surfaceStart = 0
            surfaceEnd = onSurfaceCount - 1
        }
    }

    fun setScaleRadiusCorrection(value: Float) {
        scaleRadiusCorrection = value
        forceLayoutChanges()
    }

    fun setOnSurfaceCount(onSurfaceCount: Int) {
        this.onSurfaceCount = onSurfaceCount
        ensureState()
        forceLayoutChanges()
    }

    fun setRisingCount(risingCount: Int) {
        this.risingCount = risingCount
        forceLayoutChanges()
    }

    fun setRadius(@Dimension radius: Float) {
        this.radius = radius
        forceLayoutChanges()
    }

    fun setMarginBetweenCircles(@Dimension margin: Float) {
        this.marginBetweenCircles = margin
        forceLayoutChanges()
    }

    fun getRadius(): Float {
        return radius
    }

    override fun getPaddingLeft(): Int {
        return max(
            super.getPaddingLeft().toFloat(),
            marginBetweenCircles
        ).toInt()
    }

    override fun getPaddingRight(): Int {
        return max(
            super.getPaddingRight().toFloat(),
            marginBetweenCircles
        ).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewPager == null) return

        val count = count
        if (count == 0 || count == 1) return

        val shortPaddingBefore = paddingTop
        val shortOffset = shortPaddingBefore.toFloat() + radius + 1f

        // Draw stroked circles
        drawStrokedCircles(
            canvas, count,
            shortOffset, startX.toFloat()
        )

        //Draw the filled circle according to the current scroll
        drawFilledCircle(canvas, shortOffset, startX.toFloat())
    }

    private fun drawStrokedCircles(
        canvas: Canvas,
        count: Int, shortOffset: Float, startX: Float
    ) {
        // Only paint fill if not completely transparent
        if (paintPageFill.alpha == 0) return

        var dX: Float
        for (iLoop in 0 until count) {
            if (iLoop < surfaceStart - risingCount) continue
            if (iLoop > surfaceEnd + risingCount) return

            dX = startX + iLoop * (radius * 2 + marginBetweenCircles)

            if (dX < 0 || dX > width) continue
            val scaledRadius = getScaledRadius(radius, iLoop)

            canvas.drawCircle(dX, shortOffset, scaledRadius, paintPageFill)
        }
    }

    private fun getScaledRadius(radius: Float, position: Int): Float {
        // circles to the left of the surface
        if (position < surfaceStart) {
            val add = if (surfaceStart - position == 1) scaleRadiusCorrection else 0f

            // swipe left
            if (swipeDirection == SWIPE_LEFT && animationState == ANIMATE_SHIFT_LEFT) {
                val finalRadius = radius / (2 shl surfaceStart - position - 1) + add
                val currentRadius = radius / (2 shl surfaceStart - position - 1) *
                        2 + if (surfaceStart - position - 1 == 1) 1 else 0

                return currentRadius - (1 - offset) * (currentRadius - finalRadius)

                // swipe right
            } else if (swipeDirection == SWIPE_RIGHT && animationState == ANIMATE_SHIFT_RIGHT) {
                val finalRadius = radius / (2 shl surfaceStart - position - 1) + add
                val currentRadius = radius / (2 shl surfaceStart - position)
                return currentRadius + (1 - offset) * (finalRadius - currentRadius)

            } else {
                return radius / (2 shl surfaceStart - position - 1) + add
            }

            // circles to the right of the surface
        } else if (position > surfaceEnd) {
            val add = if (position - surfaceEnd == 1) scaleRadiusCorrection else 0f

            // swipe left
            return if (swipeDirection == SWIPE_LEFT && animationState == ANIMATE_SHIFT_LEFT) {
                val finalRadius = radius / (2 shl position - surfaceEnd) * 2 + add
                val currentRadius = radius / (2 shl position - surfaceEnd)
                currentRadius + (1 - offset) * (finalRadius - currentRadius)

                // swipe right
            } else if (swipeDirection == SWIPE_RIGHT && animationState == ANIMATE_SHIFT_RIGHT) {
                val finalRadius = radius / (2 shl position - surfaceEnd - 1) + add
                finalRadius + offset * finalRadius

            } else {
                radius / (2 shl position - surfaceEnd - 1) + add
            }

        } else if (position == currentPage) {
            // swipe left
            return if (swipeDirection == SWIPE_LEFT && animationState == ANIMATE_SHIFT_LEFT) {
                val finalRadius = radius + scaleRadiusCorrection
                val currentRadius = radius / 2 + scaleRadiusCorrection
                currentRadius + (1 - offset) * (finalRadius - currentRadius)

                // swipe right
            } else if (swipeDirection == SWIPE_RIGHT && animationState == ANIMATE_SHIFT_RIGHT) {
                val finalRadius = radius + scaleRadiusCorrection
                val currentRadius = radius / 2 + scaleRadiusCorrection
                currentRadius + (1 - offset) * (finalRadius - currentRadius)

            } else {
                radius + scaleRadiusCorrection
            }
        }

        return radius
    }

    private fun drawFilledCircle(canvas: Canvas, shortOffset: Float, startX: Float) {
        val dX: Float
        val dY: Float = shortOffset
        val cx = currentPage * (radius *
                2 + marginBetweenCircles)

        dX = startX + cx
        canvas.drawCircle(
            dX, dY, getScaledRadius(
                radius, currentPage
            ), paintFill
        )
    }

    fun setPager(view: ViewPager) {
        if (viewPager != null) {
            viewPager!!.removeOnPageChangeListener(this)
            viewPager!!.removeOnAdapterChangeListener(this)

            try {
                viewPager!!.adapter!!.unregisterDataSetObserver(dataSetObserver)
            } catch (ignore: Exception) {}
        }

        checkNotNull(view.adapter) {
            "ViewPager does not have adapter instance."
        }

        viewPager = view
        viewPager!!.adapter!!.registerDataSetObserver(dataSetObserver)
        viewPager!!.addOnAdapterChangeListener(this)
        viewPager!!.addOnPageChangeListener(this)
        forceLayoutChanges()
    }

    fun setViewPager(view: ViewPager, initialPosition: Int) {
        setPager(view)
        setCurrentItem(initialPosition)
    }

    override fun onAdapterChanged(
        viewPager: ViewPager,
        oldAdapter: PagerAdapter?, newAdapter: PagerAdapter?
    ) {
        startX = Integer.MIN_VALUE
        forceLayoutChanges()
    }

    private fun forceLayoutChanges() {
        requestLayout()
        invalidate()
    }

    fun setCurrentItem(item: Int) {
        checkNotNull(viewPager) { "ViewPager has not been bound." }

        if (item < 0 || item > count) return

        viewPager!!.currentItem = item
    }

    fun notifyDataSetChanged() {
        ensureState()
        forceLayoutChanges()
    }

    override fun onPageScrollStateChanged(state: Int) {
        scrollState = state
    }

    override fun onPageScrolled(position: Int,
                                positionOffset: Float, positionOffsetPixels: Int)
    {
        if (abs(viewPager!!.currentItem - position) > 1) {
            // Inconsistency detected.
            // Probably we changed a page manually
            onPageManuallyChanged(viewPager!!.currentItem)
            return
        }

        if (position == currentPage) {
            if (positionOffset >= 0.5 && currentPage + 1 < count) {
                swipeDirection = SWIPE_LEFT
                currentPage += 1

                if (currentPage > surfaceEnd) {
                    animationState = ANIMATE_SHIFT_LEFT
                    correctSurface()
                    invalidate()
                    animateShifting(startX, (startX -
                            (marginBetweenCircles + radius * 2)).toInt()
                    )

                } else {
                    animationState = ANIMATE_IDLE
                    invalidate()
                }
            }

        } else if (position < currentPage) {
            if (positionOffset <= 0.5) {
                swipeDirection = SWIPE_RIGHT
                currentPage = position

                if (currentPage < surfaceStart) {
                    animationState = ANIMATE_SHIFT_RIGHT
                    correctSurface()
                    invalidate()
                    animateShifting(startX, (startX +
                            (marginBetweenCircles + radius * 2)).toInt()
                    )

                } else {
                    animationState = ANIMATE_IDLE
                    invalidate()
                }
            }
        }
    }

    private fun animateShifting(from: Int, to: Int) {
        if (translationAnim != null && translationAnim!!.isRunning) translationAnim!!.end()

        translationAnim = ValueAnimator.ofInt(from, to)
        translationAnim!!.duration = ANIMATION_TIME
        translationAnim!!.interpolator = AccelerateDecelerateInterpolator()
        translationAnim!!.addUpdateListener { valueAnimator ->
            val `val` = valueAnimator.animatedValue as Int
            offset = (`val` - to) * 1f / (from - to)
            startX = `val`
            invalidate()
        }

        translationAnim!!.addListener(object : AnimatorListener() {
            override fun onAnimationEnd(animator: Animator) {
                animationState = ANIMATE_IDLE
                startX = to
                offset = 0f
                invalidate()
            }
        })

        translationAnim!!.start()
    }

    private fun correctSurface() {
        if (currentPage > surfaceEnd) {
            surfaceEnd = currentPage
            surfaceStart = surfaceEnd - (onSurfaceCount - 1)
        } else if (currentPage < surfaceStart) {
            surfaceStart = currentPage
            surfaceEnd = surfaceStart + (onSurfaceCount - 1)
        }
    }

    override fun onPageSelected(position: Int) {
        if (scrollState == ViewPager.SCROLL_STATE_IDLE) {
            if (startX == Integer.MIN_VALUE) {
                post { onPageManuallyChanged(position) }
            } else {
                onPageManuallyChanged(position)
            }
        }
    }

    private fun onPageManuallyChanged(position: Int) {
        currentPage = position
        val oldSurfaceStart = surfaceStart
        val oldSurfaceEnd = surfaceEnd

        correctSurface()
        correctStartXOnPageManuallyChanged(
            oldSurfaceStart, oldSurfaceEnd)

        invalidate()
    }

    private fun correctStartXOnPageManuallyChanged(
        oldSurfaceStart: Int, oldSurfaceEnd: Int)
    {
        if (currentPage in oldSurfaceStart..oldSurfaceEnd) {
            // startX is not changed
            return
        }

        var corrected = startX
        if (currentPage < oldSurfaceStart) {
            corrected += ((oldSurfaceStart - currentPage) *
                    (marginBetweenCircles + radius * 2)).toInt()
        } else {
            corrected -= ((currentPage - oldSurfaceEnd) *
                    (marginBetweenCircles + radius * 2)).toInt()
        }

        startX = corrected
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(measureWidth(widthMeasureSpec),
            measureHeight(heightMeasureSpec))

        measureStartX()
    }

    private fun measureStartX() {
        if (startX == Integer.MIN_VALUE) {
            startX = initialStartX
        }
    }

    private fun measureWidth(measureSpec: Int): Int {
        var result: Int
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)

        if (specMode == MeasureSpec.EXACTLY || viewPager == null) {
            //We were told how big to be
            result = specSize

        } else {
            //Calculate the width according the views count
            result = calculateExactWidth()

            //Respect AT_MOST value if that was what is called for by measureSpec
            if (specMode == MeasureSpec.AT_MOST) {
                result = min(result, specSize)
            }
        }

        return result
    }

    private fun calculateExactWidth(): Int {
        val maxSurfaceCount = min(count, onSurfaceCount)
        val risingCount = internalRisingCount
        var result = (paddingLeft.toFloat() + paddingRight.toFloat()
                + maxSurfaceCount.toFloat() * 2f * radius +
                (maxSurfaceCount - 1) * marginBetweenCircles).toInt()

        if (risingCount > 0) {
            result += ((risingCount.toFloat() * radius * 2f +
                    (risingCount - 1) * marginBetweenCircles +
                    initialStartX.toFloat()) - internalPaddingLeft).toInt()
        }

        return result
    }

    private fun measureHeight(measureSpec: Int): Int {
        var result: Int
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)

        if (specMode == MeasureSpec.EXACTLY) {
            //We were told how big to be
            result = specSize

        } else {
            //Measure the height
            result = (2 * (radius + scaleRadiusCorrection)
                    + paddingTop.toFloat() +
                    paddingBottom.toFloat()).toInt()

            //Respect AT_MOST value if that was what is called for by measureSpec
            if (specMode == MeasureSpec.AT_MOST) {
                result = min(result, specSize)
            }
        }

        return result
    }

    companion object {
        private val ANIMATION_TIME: Long = 300
        private val SWIPE_RIGHT = 1000
        private val SWIPE_LEFT = 1001
        private val ANIMATE_SHIFT_LEFT = 2000
        private val ANIMATE_SHIFT_RIGHT = 2001
        private val ANIMATE_IDLE = 2002
        private val ADD_RADIUS_DEFAULT = 1f
    }
}