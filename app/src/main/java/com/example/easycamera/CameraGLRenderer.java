package com.example.easycamera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.TextureView;



import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Created by lb6905 on 2017/6/28.
 */

public class CameraGLRenderer implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "Camera_GLRenderer";
    private Context mContext;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private TextureView mTextureView;
    private int mOESTextureId;
    private FilterEngine mFilterEngine;
    /**
     * 变换矩阵
     */
    private final float[] transformMatrix = new float[16];

    private EGL10 mEgl = null;
    private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
    private final EGLConfig[] mEGLConfig = new EGLConfig[1];
    private EGLSurface mEglSurface;

    private static final int MSG_INIT = 1;
    private static final int MSG_RENDER = 2;
    private static final int MSG_DINIT = 3;
    private SurfaceTexture mOESSurfaceTexture;

    public void init(TextureView textureView, int oesTextureId, Context context) {
        Log.d(TAG, "init: in");
        mContext = context;
        mTextureView = textureView;
        mOESTextureId = oesTextureId;

        mHandlerThread = new HandlerThread("Renderer Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INIT:
                        initEGL();
                        return;
                    case MSG_RENDER:
                        drawFrame();
                        return;
                    case MSG_DINIT:
                        deinitEGL();
                    default:
                        return;
                }
            }
        };
        mHandler.sendEmptyMessage(MSG_INIT);
        Log.d(TAG, "init: out");
    }

    //初始化EGL
    private void initEGL() {
        Log.d(TAG, "initEGL: in");
        //获得EGL句柄
        mEgl = (EGL10) EGLContext.getEGL();

        //获取显示设备
        mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed! " + mEgl.eglGetError());
        }

        //version中存放EGL 版本号，int[0]为主版本号，int[1]为子版本号
        int[] version = new int[2];

        //初始化EGL
        if (!mEgl.eglInitialize(mEGLDisplay, version)) {
            throw new RuntimeException("eglInitialize failed! " + mEgl.eglGetError());
        }

        //构造需要的配置列表
        int[] attributes = {
                EGL10.EGL_RED_SIZE, 8,//分别表示EGL帧缓冲中的颜色缓冲一个颜色通道用多少位表示。
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_BUFFER_SIZE, 32,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,//指定渲染api类别
                EGL10.EGL_NONE  //总是以EGL10.EGL_NONE结尾
        };
        int[] configsNum = new int[1];

        //EGL选择配置
        if (!mEgl.eglChooseConfig(mEGLDisplay, attributes, mEGLConfig, 1, configsNum)) {
            throw new RuntimeException("eglChooseConfig failed! " + mEgl.eglGetError());
        }
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture == null)
            return;

        Log.d(TAG, "initEGL: eglCreateWindowSurface in");

        //创建EGL 的window surface 并且返回它的handles(eslSurface)，最后一个参数为属性信息，0表示不需要属性
        mEglSurface = mEgl.eglCreateWindowSurface(mEGLDisplay, mEGLConfig[0], surfaceTexture, null);
        Log.d(TAG, "initEGL: eglCreateWindowSurface out");
        //渲染上下文EGLContext关联的帧缓冲配置列表，EGL_CONTEXT_CLIENT_VERSION表示这里是配置EGLContext的版本
        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
        };

        //通过Display和上面获取到的的EGL帧缓存配置列表创建一个EGLContext， EGL_NO_CONTEXT表示不需要多个设备共享上下文
        mEGLContext = mEgl.eglCreateContext(mEGLDisplay, mEGLConfig[0], EGL10.EGL_NO_CONTEXT, contextAttributes);

        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY || mEGLContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext fail failed! " + mEgl.eglGetError());
        }
        //将EGLContext和当前线程以及draw和read的EGLSurface关联，关联之后，当前线程就成为了OpenGL ES的渲染线程
        //绑定context到当前渲染线程并且去绘制（通过opengl去绘制）和读取surface（通过eglSwapBuffers（EGLDisplay dpy, EGLContext ctx）来显示）

        if (!mEgl.eglMakeCurrent(mEGLDisplay, mEglSurface, mEglSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed! " + mEgl.eglGetError());
        }

        mFilterEngine = new FilterEngine(mOESTextureId, mContext);
        Log.d(TAG, "initEGL: out");
    }

    //主要的绘制函数，实现绘制
    private void drawFrame() {
        long t1, t2;
        t1 = System.currentTimeMillis();
        if (mOESSurfaceTexture != null) {
            mOESSurfaceTexture.updateTexImage();
            mOESSurfaceTexture.getTransformMatrix(transformMatrix);
        }
        mEgl.eglMakeCurrent(mEGLDisplay, mEglSurface, mEglSurface, mEGLContext);
        GLES20.glViewport(0, 0, mTextureView.getWidth(), mTextureView.getHeight());
        // 使用glClearColor设置的颜色，刷新Surface
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 设置刷新屏幕时候使用的颜色值,顺序是RGBA，值的范围从0~1。这里不会立刻刷新，只有在GLES20.glClear调用时使用该颜色值才刷新。
        GLES20.glClearColor(1f, 1f, 0f, 0f);
        mFilterEngine.drawTexture(transformMatrix);
        mEgl.eglSwapBuffers(mEGLDisplay, mEglSurface);
        t2 = System.currentTimeMillis();
        Log.i(TAG, "drawFrame: time = " + (t2 - t1));
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(MSG_RENDER);
        }
    }

    //加载自定义的SurfaceTexture传递给相机
    public SurfaceTexture initOESTexture() {
        Log.d(TAG, "initOESTexture: in");
        mOESSurfaceTexture = new SurfaceTexture(mOESTextureId);
        mOESSurfaceTexture.setOnFrameAvailableListener(this);
        Log.d(TAG, "initOESTexture: out");
        return mOESSurfaceTexture;
    }

    public void onPause() {
        mHandler.sendEmptyMessage(MSG_DINIT);
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void deinitEGL() {
        mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        mEgl.eglDestroySurface(mEGLDisplay, mEglSurface);
        mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
        mEgl.eglTerminate(mEGLDisplay);
    }

}