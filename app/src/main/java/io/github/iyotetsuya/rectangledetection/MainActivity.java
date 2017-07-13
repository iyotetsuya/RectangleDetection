package io.github.iyotetsuya.rectangledetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import io.github.iyotetsuya.rectangledetection.models.CameraData;
import io.github.iyotetsuya.rectangledetection.models.MatData;
import io.github.iyotetsuya.rectangledetection.utils.OpenCVHelper;
import io.github.iyotetsuya.rectangledetection.views.CameraPreview;
import io.github.iyotetsuya.rectangledetection.views.DrawView;
import org.opencv.android.OpenCVLoader;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = MainActivity.class.getSimpleName();
  private static final int REQUEST_CAMERA = 1;

  static {
    if (!OpenCVLoader.initDebug()) {
      Log.v(TAG, "init OpenCV");
    }
  }

  private PublishSubject<CameraData> subject = PublishSubject.create();

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA },
          REQUEST_CAMERA);
    } else {
      init();
    }
  }

  @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
      case REQUEST_CAMERA:
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          this.runOnUiThread(this::init);
        } else {
          finish();
        }
        break;
    }
  }

  private void init() {
    CameraPreview cameraPreview = new CameraPreview(this);
    FrameLayout layout = (FrameLayout) findViewById(R.id.root_view);
    cameraPreview.init();
    layout.addView(cameraPreview, 0,
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    cameraPreview.setCallback((data, camera) -> {
      CameraData cameraData = new CameraData();
      cameraData.data = data;
      cameraData.camera = camera;
      subject.onNext(cameraData);
    });
    cameraPreview.setOnClickListener(v -> cameraPreview.focus());
    DrawView drawView = (DrawView) findViewById(R.id.draw_layout);
    subject.concatMap(
        cameraData -> OpenCVHelper.getRgbMat(new MatData(), cameraData.data, cameraData.camera))
        .concatMap(matData -> OpenCVHelper.resize(matData, 400, 400))
        .map(matData -> {
          matData.resizeRatio = (float) matData.oriMat.height() / matData.resizeMat.height();
          matData.cameraRatio = (float) cameraPreview.getHeight() / matData.oriMat.height();
          return matData;
        })
        .concatMap(this::detectRect)
        .compose(mainAsync())
        .subscribe(matData -> {
          if (drawView != null) {
            if (matData.cameraPath != null) {
              drawView.setPath(matData.cameraPath);
            } else {
              drawView.setPath(null);
            }
            drawView.invalidate();
          }
        });
  }

  private Observable<MatData> detectRect(MatData mataData) {
    return Observable.just(mataData)
        .concatMap(OpenCVHelper::getMonochromeMat)
        .concatMap(OpenCVHelper::getContoursMat)
        .concatMap(OpenCVHelper::getPath);
  }

  private static <T> Observable.Transformer<T, T> mainAsync() {
    return obs -> obs.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread());
  }
}
