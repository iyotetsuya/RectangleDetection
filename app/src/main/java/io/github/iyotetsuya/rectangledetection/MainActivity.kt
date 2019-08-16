package io.github.iyotetsuya.rectangledetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.iyotetsuya.rectangledetection.models.CameraData
import io.github.iyotetsuya.rectangledetection.utils.OpenCVHelper
import io.github.iyotetsuya.rectangledetection.views.CameraPreview
import io.github.iyotetsuya.rectangledetection.views.DrawView
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Point

class MainActivity : AppCompatActivity() {
    private var disposable: Disposable? = null

    private val subject = PublishSubject.create<CameraData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA)
        } else {
            init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        this.disposable?.dispose()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { init() }
            } else {
                finish()
            }
        }
    }

    private fun init() {
        val cameraPreview = CameraPreview(this)
        val layout = findViewById<FrameLayout>(R.id.root_view)
        cameraPreview.init()
        layout.addView(cameraPreview, 0,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
        cameraPreview.setCallback { data, camera ->
            val size = camera.parameters.previewSize
            val cameraData = CameraData(data, size.width, size.height)
            subject.onNext(cameraData)
        }
        cameraPreview.setOnClickListener { cameraPreview.focus() }
        val drawView = findViewById<DrawView>(R.id.draw_layout)
        disposable = subject.concatMap { (data, width, height) -> OpenCVHelper.getRgbMat(data, width, height) }
                .concatMap { rgbMat -> OpenCVHelper.resize(rgbMat, SIZE.toFloat(), SIZE.toFloat()) }
                .concatMap { mat ->
                    val ratio = cameraPreview.height.toFloat() / mat.height()
                    detectRect(mat, ratio)
                }
                .compose(mainAsync())
                .subscribe { path ->
                    if (drawView != null) {
                        drawView.setPath(path)
                        drawView.invalidate()
                    }
                }
    }

    private fun detectRect(mat: Mat, ratio: Float): Observable<Path> {
        return Observable.just(mat)
                .concatMap { resizeMat ->
                    OpenCVHelper.getMonochromeMat(resizeMat)
                            .flatMap { monoChromeMat -> OpenCVHelper.getContoursMat(monoChromeMat, resizeMat) }
                            .flatMap { points -> Observable.just(points).flatMapIterable { e -> e }.map { e -> Point(e.x * ratio, e.y * ratio) }.toList().toObservable() }
                            .flatMap { points -> OpenCVHelper.getPath(points) }
                }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val REQUEST_CAMERA = 1
        private const val SIZE = 400

        init {
            if (!OpenCVLoader.initDebug()) {
                Log.v(TAG, "init OpenCV")
            }
        }

        private fun <T> mainAsync(): ObservableTransformer<T, T> {
            return ObservableTransformer { obs ->
                obs.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
            }
        }
    }
}
