package io.github.iyotetsuya.rectangledetection.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class DrawView : View {
    private var paint: Paint = Paint()
    private var path: Path? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        paint.color = Color.BLUE
        paint.strokeWidth = 10f
        paint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        this.path?.let {
            canvas.drawPath(it, paint)
        }
    }

    fun setPath(path: Path?) {
        this.path = path
    }
}
