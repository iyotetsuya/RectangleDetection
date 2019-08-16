package io.github.iyotetsuya.rectangledetection.utils

import android.graphics.Path
import android.util.Log
import io.reactivex.Observable
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object OpenCVHelper {
    private val TAG = OpenCVHelper::class.java.simpleName

    fun resize(mat: Mat, requestWidth: Float, requestHeight: Float): Observable<Mat> {
        return Observable.create { sub ->
            val height = mat.height()
            val width = mat.width()
            val ratioW = width / requestWidth
            val ratioH = height / requestHeight
            val scaleRatio = if (ratioW > ratioH) ratioW else ratioH
            val size = Size((mat.width() / scaleRatio).toDouble(), (mat.height() / scaleRatio).toDouble())
            val resultMat = Mat(size, mat.type())
            Imgproc.resize(mat, resultMat, size)
            Log.v(TAG, "request size:" + requestWidth + "," + requestHeight +
                    " ,scale to:" + resultMat.width() + "," + resultMat.height())
            sub.onNext(resultMat)
            sub.onComplete()
        }
    }

    fun getRgbMat(data: ByteArray, width: Int, height: Int): Observable<Mat> {
        return Observable.create { sub ->
            try {
                val now = System.currentTimeMillis()
                val mYuv = Mat(height + height / 2, width, CvType.CV_8UC1)
                mYuv.put(0, 0, data)
                val mRGB = Mat()
                Imgproc.cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3)
                val dst = Mat()
                Core.flip(mRGB.t(), dst, 1)
                Log.v(TAG, "getRgbMat time:" + (System.currentTimeMillis() - now))
                sub.onNext(dst)
                sub.onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                sub.onError(e)
            }
        }
    }


    fun getMonochromeMat(mat: Mat): Observable<Mat> {
        return Observable.create { sub ->
            val now = System.currentTimeMillis()
            val edgeMat = getEdge(mat)
            val monoChrome = Mat()
            Imgproc.threshold(edgeMat, monoChrome, 127.0, 255.0, Imgproc.THRESH_BINARY)
            Log.v(TAG, "getMonochromeMat time:" + (System.currentTimeMillis() - now))
            sub.onNext(monoChrome)
            sub.onComplete()
        }
    }

    private fun getEdge(mat: Mat): Mat {
        val now = System.currentTimeMillis()
        val sobelX = Mat()
        val sobelY = Mat()
        val destination = Mat(mat.rows(), mat.cols(), mat.type())
        Imgproc.cvtColor(mat, destination, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.Sobel(destination, sobelX, CvType.CV_16S, 1, 0)
        Imgproc.Sobel(destination, sobelY, CvType.CV_16S, 0, 1)
        val absX = Mat()
        val absY = Mat()
        Core.convertScaleAbs(sobelX, absX)
        Core.convertScaleAbs(sobelY, absY)
        val result = Mat()
        Core.addWeighted(absX, 0.5, absY, 0.5, 0.0, result)
        Log.v(TAG, "getEdge time:" + (System.currentTimeMillis() - now))
        return result
    }

    fun getContoursMat(monoChrome: Mat, resizeMat: Mat): Observable<List<Point>> {
        return Observable.create { sub ->
            //特徵化
            val now = System.currentTimeMillis()
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(monoChrome.clone(), contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val width = monoChrome.rows()
            val height = monoChrome.cols()
            val matArea = width * height
            for (i in contours.indices) {
                val contoursArea = Imgproc.contourArea(contours[i])
                val approx = MatOfPoint2f()
                val contour = MatOfPoint2f(*contours[i].toArray())
                val epsilon = Imgproc.arcLength(contour, true) * 0.1
                Imgproc.approxPolyDP(contour, approx, epsilon, true)
                if (abs(contoursArea) < matArea * 0.01 || !Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                    continue
                }
                Imgproc.drawContours(resizeMat, contours, i, Scalar(0.0, 255.0, 0.0))

                val points = approx.toList()
                val pointCount = points.size
                val list = LinkedList<Double>()
                for (j in 2 until pointCount + 1) {
                    list.addLast(angle(points[j % pointCount], points[j - 2], points[j - 1]))
                }
                list.sortWith(Comparator { lhs, rhs -> lhs.toInt() - rhs.toInt() })
                val minCos = list.first
                val maxCos = list.last
                if (points.size == 4 && minCos >= -0.3 && maxCos <= 0.5) {
                    for (j in points.indices) {
                        Core.circle(resizeMat, points[j], 6, Scalar(255.0, 0.0, 0.0), 6)
                    }
                    sub.onNext(points)
                    break
                }

            }
            Log.v(TAG, "getContoursMat time:" + (System.currentTimeMillis() - now))
            sub.onNext(ArrayList())
            sub.onComplete()
        }
    }

    private fun angle(pt1: Point, pt2: Point, pt0: Point): Double {
        val dx1 = pt1.x - pt0.x
        val dy1 = pt1.y - pt0.y
        val dx2 = pt2.x - pt0.x
        val dy2 = pt2.y - pt0.y
        return (dx1 * dx2 + dy1 * dy2) / sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10)
    }

    fun getPath(list: List<Point>): Observable<Path> {
        return Observable.create { subscriber ->
            if (list.size == 4) {
                val path = Path()
                val points = list.sortedWith(Comparator { lhs: Point, rhs: Point -> getDistance(lhs) - getDistance(rhs) })
                path.moveTo(points[0].x.toFloat(),
                        points[0].y.toFloat())
                path.lineTo(points[1].x.toFloat(),
                        points[1].y.toFloat())
                path.lineTo(points[3].x.toFloat(),
                        points[3].y.toFloat())
                path.lineTo(points[2].x.toFloat(),
                        points[2].y.toFloat())
                path.lineTo(points[0].x.toFloat(),
                        points[0].y.toFloat())
                subscriber.onNext(path)
            }
            subscriber.onComplete()
        }
    }

    private fun getDistance(point: Point): Int {
        val x1 = 0.0
        val x2 = point.x
        val y1 = 0.0
        val y2 = point.y
        return sqrt((x1 - x2).pow(2.0) + (y1 - y2).pow(2.0)).toInt()
    }
}
