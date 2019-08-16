package io.github.iyotetsuya.rectangledetection.utils;

import android.graphics.Path;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.reactivex.Observable;

public class OpenCVHelper {
    private static final String TAG = OpenCVHelper.class.getSimpleName();

    public static Observable<Mat> resize(Mat mat, float requestWidth, float requestHeight) {
        return Observable.create(sub -> {
            final int height = mat.height();
            final int width = mat.width();
            float ratioW = width / requestWidth;
            float ratioH = height / requestHeight;
            float scaleRatio = ratioW > ratioH ? ratioW : ratioH;
            Size size = new Size(mat.width() / scaleRatio, mat.height() / scaleRatio);
            Mat resultMat = new Mat(size, mat.type());
            Imgproc.resize(mat, resultMat, size);
            Log.v(TAG, "request size:" + requestWidth + "," + requestHeight +
                    " ,scale to:" + resultMat.width() + "," + resultMat.height());
            sub.onNext(resultMat);
            sub.onComplete();
        });
    }

    public static Observable<Mat> getRgbMat(byte[] data, int width, int height) {
        return Observable.create(sub -> {
            try {
                long now = System.currentTimeMillis();
                Mat mYuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
                mYuv.put(0, 0, data);
                Mat mRGB = new Mat();
                Imgproc.cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3);
                Mat dst = new Mat();
                Core.flip(mRGB.t(), dst, 1);
                Log.v(TAG, "getRgbMat time:" + (System.currentTimeMillis() - now));
                sub.onNext(dst);
                sub.onComplete();
            } catch (Exception e) {
                e.printStackTrace();
                sub.onError(e);
            }
        });
    }


    public static Observable<Mat> getMonochromeMat(Mat mat) {
        return Observable.create(sub -> {
            long now = System.currentTimeMillis();
            Mat edgeMat = getEdge(mat);
            Mat monoChrome = new Mat();
            Imgproc.threshold(edgeMat, monoChrome, 127, 255, Imgproc.THRESH_BINARY);
            Log.v(TAG, "getMonochromeMat time:" + (System.currentTimeMillis() - now));
            sub.onNext(monoChrome);
            sub.onComplete();
        });
    }

    private static Mat getEdge(Mat mat) {
        long now = System.currentTimeMillis();
        Mat sobelX = new Mat();
        Mat sobelY = new Mat();
        Mat destination = new Mat(mat.rows(), mat.cols(), mat.type());
        Imgproc.cvtColor(mat, destination, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.Sobel(destination, sobelX, CvType.CV_16S, 1, 0);
        Imgproc.Sobel(destination, sobelY, CvType.CV_16S, 0, 1);
        Mat absX = new Mat();
        Mat absY = new Mat();
        Core.convertScaleAbs(sobelX, absX);
        Core.convertScaleAbs(sobelY, absY);
        Mat result = new Mat();
        Core.addWeighted(absX, 0.5, absY, 0.5, 0, result);
        Log.v(TAG, "getEdge time:" + (System.currentTimeMillis() - now));
        return result;
    }

    public static Observable<List<Point>> getContoursMat(Mat monoChrome, Mat resizeMat) {
        return Observable.create(sub -> {
            //特徵化
            long now = System.currentTimeMillis();
            ArrayList<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(monoChrome.clone(), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            final int width = monoChrome.rows();
            final int height = monoChrome.cols();
            int matArea = width * height;
            for (int i = 0; i < contours.size(); i++) {
                double contoursArea = Imgproc.contourArea(contours.get(i));
                MatOfPoint2f approx = new MatOfPoint2f();
                MatOfPoint2f contour = new MatOfPoint2f(contours.get(i).toArray());
                double epsilon = Imgproc.arcLength(contour, true) * 0.1;
                Imgproc.approxPolyDP(contour, approx, epsilon, true);
                if (Math.abs(contoursArea) < matArea * 0.01 ||
                        !Imgproc.isContourConvex(new MatOfPoint(approx.toArray()))) {
                    continue;
                }
                Imgproc.drawContours(resizeMat, contours, i, new Scalar(0, 255, 0));

                List<Point> points = approx.toList();
                int pointCount = points.size();
                LinkedList<Double> cos = new LinkedList<>();
                for (int j = 2; j < pointCount + 1; j++) {
                    cos.addLast(angle(points.get(j % pointCount), points.get(j - 2), points.get(j - 1)));
                }
                Collections.sort(cos, (lhs, rhs) -> lhs.intValue() - rhs.intValue());
                double mincos = cos.getFirst();
                double maxcos = cos.getLast();
                if (points.size() == 4 && mincos >= -0.3 && maxcos <= 0.5) {
                    for (int j = 0; j < points.size(); j++) {
                        Core.circle(resizeMat, points.get(j), 6, new Scalar(255, 0, 0), 6);
                    }
                    sub.onNext(points);
                    break;
                }

            }
            Log.v(TAG, "getContoursMat time:" + (System.currentTimeMillis() - now));
            sub.onNext(new ArrayList<>());
            sub.onComplete();
        });
    }

    private static double angle(Point pt1, Point pt2, Point pt0) {
        double dx1 = pt1.x - pt0.x;
        double dy1 = pt1.y - pt0.y;
        double dx2 = pt2.x - pt0.x;
        double dy2 = pt2.y - pt0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    public static Observable<Path> getPath(List<Point> points) {
        return Observable.create(subscriber -> {
            if (points != null && points.size() == 4) {
                Path path = new Path();
                Collections.sort(points, (lhs, rhs) ->
                        OpenCVHelper.getDistance(lhs) - OpenCVHelper.getDistance(rhs));

                path.moveTo((float) points.get(0).x,
                        (float) points.get(0).y);
                path.lineTo((float) points.get(1).x,
                        (float) points.get(1).y);
                path.lineTo((float) points.get(3).x,
                        (float) points.get(3).y);
                path.lineTo((float) points.get(2).x,
                        (float) points.get(2).y);
                path.lineTo((float) points.get(0).x,
                        (float) points.get(0).y);
                subscriber.onNext(path);
            }
            subscriber.onComplete();
        });
    }

    private static int getDistance(Point point) {
        double x1 = 0;
        double x2 = point.x;
        double y1 = 0;
        double y2 = point.y;
        return (int) Math.sqrt(
                Math.pow(x1 - x2, 2) +
                        Math.pow(y1 - y2, 2));
    }
}
