package io.github.iyotetsuya.rectangledetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import io.github.iyotetsuya.rectangledetection.models.CameraData;
import io.github.iyotetsuya.rectangledetection.utils.OpenCVHelper;
import io.github.iyotetsuya.rectangledetection.views.CameraPreview;
import io.github.iyotetsuya.rectangledetection.views.DrawView;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CAMERA = 1;
    private static final int SIZE = 400;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.v(TAG, "init OpenCV");
        }
    }

    private PublishSubject<CameraData> subject = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        } else {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                this.runOnUiThread(this::init);
            } else {
                finish();
            }
        }
    }

    private void init() {
        CameraPreview cameraPreview = new CameraPreview(this);
        FrameLayout layout = findViewById(R.id.root_view);
        cameraPreview.init();
        layout.addView(cameraPreview, 0,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        cameraPreview.setCallback((data, camera) -> {
            Camera.Size size = camera.getParameters().getPreviewSize();
            CameraData cameraData = new CameraData(data, size.width, size.height);
            subject.onNext(cameraData);
        });
        cameraPreview.setOnClickListener(v -> cameraPreview.focus());
        DrawView drawView = findViewById(R.id.draw_layout);
        Disposable disposable = subject.concatMap(
                cameraData -> OpenCVHelper.getRgbMat(cameraData.getData(), cameraData.getWidth(), cameraData.getHeight()))
                .concatMap(rgbMat -> OpenCVHelper.resize(rgbMat, SIZE, SIZE))
                .concatMap(mat -> {
                    float ratio = (float) cameraPreview.getHeight() / mat.height();
                    return detectRect(mat, ratio);
                })
                .compose(mainAsync())
                .subscribe(path -> {
                    if (drawView != null) {
                        drawView.setPath(path);
                        drawView.invalidate();
                    }
                });
    }

    private Observable<Path> detectRect(Mat mat, float ratio) {
        return Observable.just(mat)
                .concatMap(resizeMat -> OpenCVHelper.getMonochromeMat(resizeMat)
                        .concatMap(monoChromeMat -> OpenCVHelper.getContoursMat(monoChromeMat, resizeMat))
                        .flatMap(points -> Observable.just(points).flatMapIterable(e -> e).map(e -> new Point(e.x * ratio, e.y * ratio)).toList().toObservable())
                        .concatMap(OpenCVHelper::getPath));
    }

    private static <T> ObservableTransformer<T, T> mainAsync() {
        return obs -> obs.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread());
    }
}
