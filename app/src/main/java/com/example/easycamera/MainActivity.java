package com.example.easycamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;



public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private TextureView mTextureView;

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;

    /*请求抓取相机图像帧的会话，会话的建立主要会建立起一个通道。一个CameraDevice一次只能开启一个CameraCaptureSession。
    源端是相机，另一端是 Target，Target可以是Preview，也可以是ImageReader。*/
    private CameraCaptureSession mCameraCaptureSession;

    //相机捕获图像的设置请求，包含传感器，镜头，闪光灯等
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;

    //用于从相机打开的通道中读取需要的格式的原始图像数据，可以设置多个ImageReader。
    //private ImageReader mImageReader;

    private Boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };

    View.OnClickListener mOnClickListener = view -> {
        if (view.getId() == R.id.start_pre_btn) {
            openCamera();
        } else if (view.getId() == R.id.stop_pre_btn) {
            releaseCamera();
        }
    };

    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.texture);
        assert mTextureView != null;
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        Button mBtnStartPreview =  findViewById(R.id.start_pre_btn);
        Button mBtnStopPreview =  findViewById(R.id.stop_pre_btn);
        mBtnStartPreview.setOnClickListener(mOnClickListener);
        mBtnStopPreview.setOnClickListener(mOnClickListener);
    }

    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CAMERA_PERMISSION);
            return;
        }

        setUpCameraOutputs();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            for (String cameraId : cameraIdList) {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                //使用后置摄像头
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                //获取分辨率信息
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));

                Log.d("previewing", "width: "+mPreviewSize.getWidth()+"  height: "+mPreviewSize.getHeight());
                //检查闪光灯是否支持
                Boolean available = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available != null && available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size chooseOptimalSize(Size[] outputSizes) {
        Size largest = Collections.max(
                Arrays.asList(outputSizes), new CompareSizesByArea());

        return new Size(720*largest.getWidth()/largest.getHeight(),720);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "没有权限", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            //设置缓冲区大小
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            //用来输出的surface
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (mCameraDevice == null) {
                        return;
                    }
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        setAutoFlash(mPreviewRequestBuilder);

                        mPreviewRequest = mPreviewRequestBuilder.build();
                        //此处只有预览，没有CaptureCallback参数
                        mCameraCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder captureRequestBuilder) {
        if (mFlashSupported) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }


    private void releaseCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        releaseCamera();
        super.onPause();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera_Background");
        mBackgroundThread.start();
        // 在Android开发中，不熟悉多线程开发的人一想到要使用线程，可能就用new Thread(){…}.start()这样的方式。
        //实质上在只有单个耗时任务时用这种方式是可以的，但若是有多个耗时任务要串行执行呢？
        //那不得要多次创建多次销毁线程，这样导致的代价是很耗系统资源，容易存在性能问题。那么，怎么解决呢？
        //我们可以只创建一个工作线程，然后在里面循环处理耗时任务
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}