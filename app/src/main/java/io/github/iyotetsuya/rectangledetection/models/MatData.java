package io.github.iyotetsuya.rectangledetection.models;

import android.graphics.Path;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.List;

public class MatData {
    public Mat resizeMat;
    public Mat monoChrome;
    public List<Point> points;
    public Mat oriMat;
    public float resizeRatio;
    public float cameraRatio;
    //        public List<Point> fixPoints;
//    public Mat fixMat;
//    public String content;
//    public String setId;
//    public String pageId;
//    public Mat resultMat;
//    public Mat scoreMat;
//    public ArrayList<Integer> scores;
//    public String path;
//    public byte[] data;
    public Path cameraPath;
//    public Mat qrcodeMat;
//    public Pair<LinkedList<Integer>, LinkedList<Integer>> grid;

}
