package com.example.capston;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;


import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class CamService extends Service {

    public native int detect(long cascadeClassifier_face, long cascadeClassifier_side_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult);
    public long cascadeClassifier_face = MainActivity.cascadeClassifier_face;
    public long cascadeClassifier_side_face = MainActivity.cascadeClassifier_side_face;
    public long cascadeClassifier_eye = MainActivity.cascadeClassifier_eye;
    private int before_speed = 0;
    private boolean not_found = false;

    private WindowManager wm;
    private View rootView;
    private TextureView textureView;

    private static final String TAG = "CamService";
    private static final String ACTION_START = "START";
    private static final String ACTION_START_WITH_PREVIEW = "START_WITH_PREVIEW";
    private static final String ACTION_STOPPED = "STOPPED";
    private static final int ONGOING_NOTIFICATION_ID = 6660;
    private static final String CHANNEL_ID = "cam_service_channel_id";
    private static final String CHANNEL_NAME = "cam_service_channel_name";

    private Mat matInput;
    private Mat matResult;


    //Camera2 related stop
    private CameraManager cameraManager;
    private Size previewSize;
    private CameraDevice cameraDevice;
    private CaptureRequest captureRequest;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;

    private boolean shouldShowPreview = true;

    private final Semaphore writeLock = new Semaphore(1);

    public void getWriteLock() throws InterruptedException {
        writeLock.acquire();
    }

    public void releaseWriteLock() {
        writeLock.release();
    }

    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            try {
                initCam(width, height);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private ImageReader.OnImageAvailableListener imageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                getWriteLock();
                Image image = imageReader.acquireLatestImage();
                if(image!=null) {
                    Mat buf = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
                    ByteBuffer bb = image.getPlanes()[0].getBuffer();
                    byte[] data = new byte[bb.remaining()];
                    bb.get(data);
                    buf.put(0, 0, data);
                    matInput = buf.clone();
                    matResult = matInput.clone();
                    int point = detect(cascadeClassifier_face, cascadeClassifier_side_face, cascadeClassifier_eye, matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
                    startForeground(point);
                    image.close();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            releaseWriteLock();

        }
    };
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };


    public CamService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action.equals("ACTION_START")) {
            try {
                start();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        else if (action.equals("ACTION_START_WITH_PREVIEW")) {
            try {
                startWithPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        else if(action.equals(ACTION_STOPPED)){
            stopCamera();
            if (rootView != null)
                wm.removeView(rootView);
            sendBroadcast(new Intent(ACTION_STOPPED));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCamera();
        if (rootView != null)
            wm.removeView(rootView);
        sendBroadcast(new Intent(ACTION_STOPPED));
    }

    private void start() throws CameraAccessException {
        shouldShowPreview = false;
        initCam(320, 200);
    }

    private void startWithPreview() throws CameraAccessException {
        shouldShowPreview = true;
        initOverlay();
        if (textureView.isAvailable()) {
            initCam(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    private void initOverlay() {
        LayoutInflater li = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        rootView = li.inflate(R.layout.overlay, null);
        textureView = rootView.findViewById(R.id.texPreview);
        int type;
        if (Build.VERSION.SDK_INT < 26) {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(type, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.addView(rootView, params);
    }

    private void initCam(int width, int height) throws CameraAccessException {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String camId = null;
        String[] cameraIdList = cameraManager.getCameraIdList();
        for (int i = 0; i < cameraIdList.length; ++i) {
            String id = cameraIdList[i];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
            Integer facing = (Integer) cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                camId = id;
                break;
            }
        }
        previewSize = chooseSupportedSize(camId, width, height);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        cameraManager.openCamera(camId, stateCallback, null);
        Log.d("aaaaaaaaaa", "real");
    }
    private Size chooseSupportedSize(String camId, int textureViewWidth, int textureViewheight) throws CameraAccessException {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedSizes = map.getOutputSizes(SurfaceTexture.class);
        int texViewArea = textureViewWidth*textureViewheight;
        float texViewAspect = (float)textureViewWidth/(float)textureViewheight;
        Size[] nearestToFurthestSz = supportedSizes;
        if(nearestToFurthestSz.length!=0){
            return nearestToFurthestSz[0];
        }
        return new Size(320,200);
    }

    private void startForeground(int detection) {
        Intent intent = new Intent((Context)this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        String title = "BackGroundCam";
        String text = "BackGroundCam";

        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
            channel.setLightColor(Color.BLUE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
        int bias = detection /100000;
        int is_awake = detection%10;
        detection = (detection % 100000 - 50000)/10;
//        if(detection == 1000){
////            text = "Not Found";
//            detection = not_found? 1000 : before_speed;
//            not_found = true;
//        }
//        else if(detection>-5 && detection <5){
//            text = "Front";
//            before_speed = detection;
//            not_found = false;
//        }
//        else  if(detection >= 5){
////            text = "Right";
//            before_speed = detection;
//            not_found = false;
//        }
//        else{
////            text = "Left";
//            before_speed = detection;
//            not_found = false;
//        }
//        int n_final = Math.abs(detection - before_speed)<15 ? detection : before_speed;
        String msg = "Tracking 1 "+detection + " "+bias+" "+is_awake;
        text = Integer.toString(detection) + " " + bias + " " + is_awake;
        Intent stopIntent = new Intent(this, MainActivity.class);
        stopIntent.setAction(ACTION_STOPPED);
        stopIntent.putExtra("STOP", 0);
        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(this, 0, stopIntent, 0);

        Notification notification = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            //noinspection deprecation
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.arm)
                    .setContentIntent(pendingIntent)
                    .setTicker("BackGroundCam")
                    .addAction(R.drawable.arm, "STOP", stopPendingIntent)
                    .setAutoCancel(true)
                    .build();
        }
        startForeground(ONGOING_NOTIFICATION_ID, notification);
        Bluetooth.sendData(msg);
    }
    private void createCaptureSession(){
        try{
            ArrayList targetSurfaces = new ArrayList();
            final CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(cameraDevice.TEMPLATE_PREVIEW);
            if(shouldShowPreview){
                SurfaceTexture texture = textureView.getSurfaceTexture();
                texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                Surface previewSurface = new Surface(texture);
                targetSurfaces.add(previewSurface);
                requestBuilder.addTarget(previewSurface);
            }
            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(imageListener, null);
            targetSurfaces.add(imageReader.getSurface());
            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            cameraDevice.createCaptureSession(targetSurfaces, (CameraCaptureSession.StateCallback) new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    captureSession = cameraCaptureSession;
                    try{
                        captureRequest = requestBuilder.build();
                        captureSession.setRepeatingRequest(captureRequest, captureCallback, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "CREATE CAPTURE SESSION");
                }}, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void stopCamera() {
        try{
            captureSession.close();
            captureSession = null;
            cameraDevice.close();
            cameraDevice = null;
            imageReader.close();
            imageReader = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}