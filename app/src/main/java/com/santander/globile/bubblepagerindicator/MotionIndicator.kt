package com.santander.globile.bubblepagerindicator

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * Created by Bogdan Kornev
 * on 11/7/2017, 5:01 PM.
 */
abstract class MotionIndicator @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr)
{
    private var lastMotionX = -1f
    private var activePointerId = INVALID_POINTER
    private val touchSlop: Int
    private var isDragging: Boolean = false

    var currentPage: Int = 0
    var viewPager: ViewPager? = null

    protected abstract val count: Int

    init {
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledPagingTouchSlop
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (super.onTouchEvent(ev)) return true
        if (viewPager == null || count == 0) return false

        when (val action = ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                lastMotionX = ev.x
            }

            MotionEvent.ACTION_MOVE -> {
                val activePointerIndex = ev.findPointerIndex(activePointerId)
                val x = ev.findPointerIndex(activePointerIndex).toFloat()
                val deltaX = x - lastMotionX

                if (!isDragging) {
                    if (abs(deltaX) > touchSlop) {
                        isDragging = true
                    }
                }

                if (isDragging) {
                    lastMotionX = x
                    if (viewPager!!.isFakeDragging || viewPager!!.beginFakeDrag()) {
                        viewPager!!.fakeDragBy(deltaX)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val count = count
                    val width = width
                    val halfWidth = width / 2f
                    val sixthWidth = width / 6f

                    if (currentPage > 0 && ev.x < halfWidth - sixthWidth) {
                        if (action != MotionEvent.ACTION_CANCEL) {
                            viewPager!!.currentItem = currentPage - 1
                        }

                        return true

                    } else if (currentPage < count - 1 && ev.x > halfWidth + sixthWidth) {
                        if (action != MotionEvent.ACTION_CANCEL) {
                            viewPager!!.currentItem = currentPage + 1
                        }

                        return true
                    }
                }

                isDragging = false
                activePointerId = INVALID_POINTER
                if (viewPager!!.isFakeDragging)
                    viewPager!!.endFakeDrag()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                lastMotionX = ev.getX(index)
                activePointerId = ev.getPointerId(index)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    activePointerId = ev.getPointerId(newPointerIndex)
                }

                lastMotionX = ev.getX(ev.findPointerIndex(activePointerId))
            }
        }

        return true
    }

    companion object {
        private val INVALID_POINTER = -1
    }
}