package io.github.iyotetsuya.rectangledetection.views;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraPreview extends SurfaceView
        implements SurfaceHolder.Callback {
    private static final String TAG = CameraPreview.class.getSimpleName();
    private SurfaceHolder mHolder;
    private Camera mCamera;

    private LayoutMode mLayoutMode;
    protected List<Camera.Size> mPreviewSizeList;
    protected List<Camera.Size> mPictureSizeList;
    private int mSurfaceChangedCallDepth = 0;
    protected boolean mSurfaceConfiguring = false;
    protected Camera.Size mPreviewSize;
    protected Camera.Size mPictureSize;
    private Camera.PreviewCallback callback;

    public void setCallback(Camera.PreviewCallback callback) {
        this.callback = callback;
    }

    public enum LayoutMode {
        FitToParent, // Scale to the size that no side is larger than the parent
        NoBlank // Scale to the size that no side is smaller than the parent
    }

    public CameraPreview(Context context) {
        super(context);
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        mLayoutMode = LayoutMode.NoBlank;
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        int cameraId = 0;
        int mCameraId;
        if (Camera.getNumberOfCameras() > cameraId) {
            mCameraId = cameraId;
        } else {
            mCameraId = 0;
        }

        mCamera = Camera.open(mCameraId);
        Camera.Parameters cameraParams = mCamera.getParameters();
        mPreviewSizeList = cameraParams.getSupportedPreviewSizes();
        mPictureSizeList = cameraParams.getSupportedPictureSizes();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.setPreviewCallback(callback);
            }
        } catch (IOException e) {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            mSurfaceChangedCallDepth++;
            doSurfaceChanged(width, height);
            mSurfaceChangedCallDepth--;
        }
    }

    private void doSurfaceChanged(int width, int height) {
        mCamera.stopPreview();

        Camera.Parameters cameraParams = mCamera.getParameters();

        // The code in this if-statement is prevented from executed again when surfaceChanged is
        // called again due to the change of the layout size in this if-statement.
        if (!mSurfaceConfiguring) {
            Camera.Size previewSize = determinePreviewSize(width, height);
            Camera.Size pictureSize = determinePictureSize(previewSize);
            Log.v(TAG, "Desired Preview Size - w: " + width + ", h: " + height);
            mPreviewSize = previewSize;
            mPictureSize = pictureSize;
            mSurfaceConfiguring = adjustSurfaceLayoutSize(previewSize, width, height);
            // Continue executing this method if this method is called recursively.
            // Recursive call of surfaceChanged is very special case, which is a path from
            // the catch clause at the end of this method.
            // The later part of this method should be executed as well in the recursive
            // invocation of this method, because the layout change made in this recursive
            // call will not trigger another invocation of this method.
            if (mSurfaceConfiguring && (mSurfaceChangedCallDepth <= 1)) {
                return;
            }
        }

        configureCameraParameters(cameraParams);
        mSurfaceConfiguring = false;

        try {
            byte[] previewBuffer = new byte[(mPreviewSize.height * mPreviewSize.width * 3) / 2];
            mCamera.addCallbackBuffer(previewBuffer);
            mCamera.setPreviewCallback(callback);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.w(TAG, "Failed to start preview: " + e.getMessage());

            // Remove failed size
            mPreviewSizeList.remove(mPreviewSize);
            mPreviewSize = null;

            // Reconfigure
            if (mPreviewSizeList.size() > 0) { // prevent infinite loop
                surfaceChanged(null, 0, width, height);
            } else {
                Log.w(TAG, "Gave up starting preview");
            }
        }
    }

    protected Camera.Size determinePreviewSize(int reqWidth, int reqHeight) {
        // Meaning of width and height is switched for preview when portrait,
        // while it is the same as user's view for surface and metrics.
        // That is, width must always be larger than height for setPreviewSize.

        Log.v(TAG, "Listing all supported preview sizes");
        for (Camera.Size size : mPreviewSizeList) {
            Log.v(TAG, "  w: " + size.width + ", h: " + size.height);
        }
        Log.v(TAG, "Listing all supported picture sizes");
        for (Camera.Size size : mPictureSizeList) {
            Log.v(TAG, "  w: " + size.width + ", h: " + size.height);
        }

        // Adjust surface size with the closest aspect-ratio
        float reqRatio = ((float) reqHeight) / reqWidth;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Camera.Size retSize = null;
        for (Camera.Size size : mPreviewSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    protected Camera.Size determinePictureSize(Camera.Size previewSize) {
        Camera.Size retSize = null;
        for (Camera.Size size : mPictureSizeList) {
            if (size.equals(previewSize)) {
                return size;
            }
        }

        Log.v(TAG, "Same picture size not found.");

        // if the preview size is not supported as a picture size
        float reqRatio = ((float) previewSize.width) / previewSize.height;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        for (Camera.Size size : mPictureSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    protected boolean adjustSurfaceLayoutSize(Camera.Size previewSize,
                                              int availableWidth, int availableHeight) {
        float tmpLayoutH = previewSize.width;
        float tmpLayoutW = previewSize.height;

        float factH, factW, fact;
        factH = availableHeight / tmpLayoutH;
        factW = availableWidth / tmpLayoutW;
        if (mLayoutMode == LayoutMode.FitToParent) {
            // Select smaller factor, because the surface cannot be set to the size larger than display metrics.
            if (factH < factW) {
                fact = factH;
            } else {
                fact = factW;
            }
        } else {
            if (factH < factW) {
                fact = factW;
            } else {
                fact = factH;
            }
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) this.getLayoutParams();

        int layoutHeight = (int) (tmpLayoutH * fact);
        int layoutWidth = (int) (tmpLayoutW * fact);
        Log.v(TAG, "Preview Layout Size - w: " + layoutWidth + ", h: " + layoutHeight);
        Log.v(TAG, "Scale factor: " + fact);

        boolean layoutChanged;
        if ((layoutWidth != this.getWidth()) || (layoutHeight != this.getHeight())) {
            layoutParams.height = layoutHeight;
            layoutParams.width = layoutWidth;
            this.setLayoutParams(layoutParams); // this will trigger another surfaceChanged invocation.
            layoutChanged = true;
        } else {
            layoutChanged = false;
        }

        return layoutChanged;
    }

    protected void configureCameraParameters(Camera.Parameters cameraParams) {
        // for 2.2 and later
        int angle;
        Display display = ((Activity) getContext()).getWindowManager().getDefaultDisplay();
        switch (display.getRotation()) {
            case Surface.ROTATION_0: // This is display orientation
                angle = 90; // This is camera orientation
                break;
            case Surface.ROTATION_90:
                angle = 0;
                break;
            case Surface.ROTATION_180:
                angle = 270;
                break;
            case Surface.ROTATION_270:
                angle = 180;
                break;
            default:
                angle = 90;
                break;
        }
        Log.v(TAG, "angle: " + angle);
        mCamera.setDisplayOrientation(angle);

        cameraParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        cameraParams.setPictureSize(mPictureSize.width, mPictureSize.height);
        Log.v(TAG, "Preview Actual Size - w: " + mPreviewSize.width + ", h: " + mPreviewSize.height);
        Log.v(TAG, "Picture Actual Size - w: " + mPictureSize.width + ", h: " + mPictureSize.height);

        mCamera.setParameters(cameraParams);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stop();
    }

    public void stop() {
        if (null == mCamera) {
            return;
        }
        mCamera.stopPreview();
        mCamera.setPreviewCallback(null);
        mCamera.release();
        mCamera = null;
    }

    public void focus() {
        if (mCamera != null) {
            mCamera.autoFocus((success, camera) -> camera.autoFocus(null));
        }
    }
}