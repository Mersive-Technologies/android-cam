package com.example.dannycam;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DannyCam";
    private Button takePictureButton;
    private TextureView textureView;
    private String cameraId;
    protected CameraDevice cameraDevice;
    private Surface surface;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION_200 = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(v -> startCapturingImage());
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {  return false;   }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
//            outputUSBDevicesToLogs();
//            outputCameraDevicesToLogs();
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION_200) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();// close the app
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
//        if (textureView.isAvailable()) {
//            openCamera();
//        } else {
//            textureView.setSurfaceTextureListener(textureListener);
//        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
//        stopBackgroundThread();
        super.onPause();
    }

    protected void outputUSBDevicesToLogs() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {

            Log.i(TAG,
                    "USB Device: " + device.getDeviceName() + " perm: " + usbManager.hasPermission(device) + " has vendor ID: " + device.getVendorId() + " and product ID: " + device.getProductId()
            );
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                Log.i(TAG, "    interface " + i);
                for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                    UsbEndpoint usbEndpoint = usbInterface.getEndpoint(j);
                    Log.i(TAG, "        endpoint " + j + " has direction " + usbEndpoint.getDirection() + " and type " + usbEndpoint.getType());
//                    log out the endpoints descriptions in english

                }
            }
        }
    }

    protected void outputCameraDevicesToLogs(){
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.i(TAG, "CameraManager " + manager.toString());
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Log.i(TAG, "Camera " + cameraId + " has characteristics " + characteristics.toString());
                Integer hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                Log.i(TAG, "Camera " + cameraId + " has characteristics " + hardwareLevel.toString() );
//                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED = 0;
//                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL = 1;
//                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 = 3;
//                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL = 4;
                Log.i(TAG, "Camera " + cameraId + " has system feature FEATURE_CAMERA_EXTERNAL " + getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL));
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager cameraManagerSystemService = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            Integer numberOfCameras = cameraManagerSystemService.getCameraIdList().length;
            Log.e(TAG, "openCamera() from list of cameras: " + numberOfCameras);
            if(numberOfCameras < 1){
                Log.e(TAG, "openCamera() no cameras found");
                return;
            }
            cameraId = cameraManagerSystemService.getCameraIdList()[0];

            CameraCharacteristics cameraCharacteristics = cameraManagerSystemService.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            Log.e(TAG, "openCamera() imageDimensions: " + map.getOutputSizes(SurfaceTexture.class).toString());
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION_200);
                return;
            }
            cameraManagerSystemService.openCamera(
                    cameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice cameraDevice) {
                            Log.i(TAG, "onOpened cameraDevice.getId() " + cameraDevice.getId() );
                            MainActivity.this.cameraDevice = cameraDevice;
                            createCameraPreview();
                        }

                        @Override
                        public void onDisconnected(CameraDevice camera) {
                            cameraDevice.close();
                        }

                        @Override
                        public void onError(CameraDevice camera, int error) {
                            cameraDevice.close();
                            cameraDevice = null;
                        }
                    },
                    null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.wtf(TAG, "openCamera X");
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            surface = new Surface(texture);

            imageReader = ImageReader.newInstance(
                    imageDimension.getWidth(),
                    imageDimension.getHeight(),
                    ImageFormat.YUV_420_888,
                    5);

            surface = imageReader.getSurface();
            imageReader.setOnImageAvailableListener(
                    reader -> {
                        try (Image image = reader.acquireLatestImage()) {
                            Log.i(TAG, "createCameraPreview: " + imageDimension.getWidth() + ", " + imageDimension.getHeight() +
                                    ": " + image.getPlanes().length + " planes");
                            // Get the internal storage directory
                            Context context = getApplicationContext();
                            File internalStorageDir = context.getFilesDir();
                            Log.i(TAG, "createCameraPreview: internalStorageDir " + internalStorageDir.toString());

                            // Create a new file in the internal storage directory
                            File outputFile = new File(internalStorageDir, "rawPrivate.raw");

                            // Write the bytes to the file using FileOutputStream
                            FileOutputStream fos = new FileOutputStream(outputFile);

                            // loop over the planes of the image and save each sequentially the file
                            int i = 0;
                            for (Image.Plane plane : image.getPlanes()) {
//                                    if(i>=2) break;
                                ByteBuffer buffer = plane.getBuffer();
                                byte[] bytes = new byte[buffer.capacity()];
                                buffer.get(bytes);
                                Log.i(TAG, "onImageAvailable: save this image" + plane.getPixelStride() + " : " + plane.getRowStride());
                                fos.write(bytes);
                                i++;
                            }
                            fos.close();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {

                        }
                    },
                    mBackgroundHandler
            );

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (cameraDevice == null) return; //The camera is already closed
                            // When the session is ready, we start displaying the preview.
                            cameraCaptureSessions = cameraCaptureSession;
                            updatePreview();
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
                            Log.i(TAG, "onSurfacePrepared: ");
                        }
                    },
                    null);
        } catch (CameraAccessException e) {e.printStackTrace();}
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        try {
            cameraCaptureSessions.prepare(surface); // should we be preparing our surface here?
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startCapturingImage(){
        if(cameraId == null) {
            Toast.makeText(MainActivity.this, "No Cameras", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequest = captureRequestBuilder.build();
//            cameraCaptureSessions.capture(captureRequest, captureCallbackListener, mBackgroundHandler);
//            cameraCaptureSessions.setRepeatingRequest(captureRequest, null, mBackgroundHandler);
//            cameraCaptureSessions.stopRepeating();
//            cameraCaptureSessions.captureSingleRequest(captureRequest, null, captureCallbackListener);
            cameraCaptureSessions.captureBurst(Arrays.asList(captureRequest), captureCallbackListener, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.i(TAG, "Pic took");
            Toast.makeText(MainActivity.this, "Capture Completed: " + result.getFrameNumber(), Toast.LENGTH_SHORT).show();

        }
        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.i(TAG, "Pic failed " + failure.getReason() + " " + failure.wasImageCaptured());
            Toast.makeText(MainActivity.this, "Capture Failed: " + failure.getFrameNumber(), Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
            Log.i(TAG, "Pic buffer lost");
            Toast.makeText(MainActivity.this, "Buffer Lost: " + target.describeContents(), Toast.LENGTH_SHORT).show();
        }
    };

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
}
