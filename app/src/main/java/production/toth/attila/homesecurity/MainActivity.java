package production.toth.attila.homesecurity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import production.toth.attila.homesecurity.Kryonet.SignalServer;
import production.toth.attila.homesecurity.WifiP2P.WifiP2PActivity;


//JAVA concurrency API
//Threadpool,Threadexecutor
//TimeLogger
//UDP broadcast vagy Ad-hoc wifi hálózat, Wifi-Direct
//Kryonet library javas objektumok broadcastelésére


//Este milyen módon lehetne használni az applikációt? negatív effekt a kamera képen
//Milyen objektumokat kell elküldeni a Wi-fi Direct kapcsolaton keresztül? jelzést küldeni
//Pontosan milyen backend szükséges ehhez? távolról státuszt lekérni vagy szerverre képet feltölteni
//Melyik ponton kapcsolódjon a két most még külön lévő applikáció?
//Szakmai gyakorlat lehetséges-e az AutSoft-nál? Írni mailt Eklernek
//hangérzékelés, settingsbe, pl 1 másodperces hangmintákat összehasonlítani 

public class MainActivity extends AppCompatActivity implements ImageConsumer.IRingtoneCallback{

    private static final String TAG = "AndroidCameraApi";
    private Button takePictureButton;
    private Button wifip2pSetup;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    //private BlockingQueue<Image> queue = new LinkedBlockingDeque<>(15);
    private BlockingQueue<Bitmap> queue = new LinkedBlockingDeque<>(15);
    ImageConsumer imageConsumer;
    Thread imageConsumerThread;
    private long starttime, difference;

    private BroadcastReceiver ringtoneReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        wifip2pSetup = (Button) findViewById(R.id.btn_wifip2p);
        wifip2pSetup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentToWifiActivity = new Intent(MainActivity.this, WifiP2PActivity.class);
                //esetleges adatokat továbbküldeni putExtra-val
                intentToWifiActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intentToWifiActivity);
            }
        });
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //takePicture();
                //stopBackgroundThread();
                starttime = System.currentTimeMillis();
                try {
                    cameraCaptureSessions.stopRepeating();
                } catch (CameraAccessException cae) {
                    cae.printStackTrace();
                }
                ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
                executorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        capturePreviewFrame();
                        //Log.i("homesecurity", "Működik a 500mses logolás");
                    }
                }, 0, 500, TimeUnit.MILLISECONDS);
                //capturePreviewFrame();
                imageConsumer /*ImageConsumer.instance*/ = new ImageConsumer(queue,MainActivity.this);
                imageConsumerThread = new Thread(imageConsumer /*ImageConsumer.getInstance()*/);
                imageConsumerThread.start();
                //takePictureButton.setClickable(false);
            }
        });
    }
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
            //getCurrentPreview();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
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
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    protected void takePicture() {
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                //width = jpegSizes[0].getWidth();
                //height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
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
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        startPreviewThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        ringtoneReceiver = new RingtoneSignalReceiver();
        IntentFilter intentFilterRingtone = new IntentFilter("sentRingtoneSignal");
        registerReceiver(ringtoneReceiver,intentFilterRingtone);

        try{
            SignalServer server = new SignalServer();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        stopPreviewThread();
        try {
            if(imageConsumerThread!=null){
                imageConsumerThread.join();
            }
        }catch (InterruptedException ie){
            ie.printStackTrace();
        }
        super.onPause();
    }

    Handler mPreviewHandler;
    HandlerThread mPreviewHandlerThread;

    protected void startPreviewThread() {
        mPreviewHandlerThread = new HandlerThread("Preview Background Thread");
        mPreviewHandlerThread.start();
        mPreviewHandler = new Handler(mPreviewHandlerThread.getLooper());
    }
    protected void stopPreviewThread() {
        mPreviewHandlerThread.quitSafely();
        try {
            mPreviewHandlerThread.join();
            mPreviewHandlerThread = null;
            mPreviewHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void capturePreviewFrame(){
        if(cameraDevice == null){
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                //width = jpegSizes[0].getWidth();
                //height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);  //JPEG helyett
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null; Bitmap bitmap = null;
                    try {
                        //ide tenni egy időzítőt hogy csak bizonyos időnként fusson le
                        difference = System.currentTimeMillis() - starttime;
                        if(difference >= 500){
                            image = reader.acquireLatestImage();
                            if(image != null){
                                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                                byte[] bytes = new byte[byteBuffer.capacity()];
                                byteBuffer.get(bytes);
                                bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);
                                queue.put(bitmap);
                                Log.e("homesecurity", "Image reached and put bitmap in the queue");
                                starttime = System.currentTimeMillis();
                            }
                            /*try {
                                cameraCaptureSessions.stopRepeating();
                            } catch (CameraAccessException cae) {
                                cae.printStackTrace();
                            }
                            createCameraPreview();*/
                        }
                        //queue.put(image);
                        /*  Ezek se kellenek ezt majd a consumer megcsinálja hogy átalakítja
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);*/
                        //save(bytes);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mPreviewHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    Log.e("homesecurity", "Lefutott az onCaptureCompleted");
                    /*try{
                        session.stopRepeating();
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                    createCameraPreview();*/
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces /*Arrays.asList(new Surface(textureView.getSurfaceTexture()))*/, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        // kipróbálni a setRepeatingBurst módot is
                        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        //cameraCaptureSessions = session;
                        session.setRepeatingRequest(captureBuilder.build(), null, mPreviewHandler);  // session.capture helyett, mert TEMPLATE_PREVIEW-t ezzel kell hívni
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void playRingtone(){
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            ringtone.play();
            //TODO: Valahol leállítani is a ringtonet stop()-al
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendRingtoneSignal() {
        Intent ringtoneIntent = new Intent();
        ringtoneIntent.putExtra(ImageConsumer.ImageConsumerTAG,"ringtoneIntent");
        sendBroadcast(ringtoneIntent);
    }


    /*########################################################################################################## */

    /*Handler mPreviewHandler;
    HandlerThread mPreviewHandlerThread;
    Image firstimage = null;
    Image secondimage = null;
    Bitmap firstbitmap;
    Bitmap secondbitmap;
    public static int previewcounter = 0;

    protected void startPreviewThread() {
        mPreviewHandlerThread = new HandlerThread("Preview Background Thread");
        mPreviewHandlerThread.start();
        mPreviewHandler = new Handler(mPreviewHandlerThread.getLooper());
        mPreviewHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getCurrentPreview();
            }
        },500);
        //getCurrentPreview();
    }
    protected void stopPreviewThread() {
        mPreviewHandlerThread.quitSafely();
        try {
            mPreviewHandlerThread.join();
            mPreviewHandlerThread = null;
            mPreviewHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    *
     * Lekéri az éppen aktuális previewt mindezt bizonyos időközönként
     * Itt ez most fél másodperc
     * TODO: kitesztelni mennyi idő a két preview bitmappá konvertálása majd összehasonlítása

    public void getCurrentPreview(){

        previewcounter++;
        if(previewcounter == 4){
            previewcounter++; previewcounter--;
        }
        //secondimage = firstimage;
        //createPreviewImage();         // ROHADTUL NEM ITT KELL HÍVNI HANEM A HANDLER POST FÜGGVÉNYÉBEN....
        convertImageToBitmap(firstimage, secondimage);
        compareTheDifferenceBetweenBitmaps(firstbitmap, secondbitmap);
        secondimage = firstimage;
        secondbitmap = firstbitmap;
        mPreviewHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(previewcounter == 2){
                    previewcounter++; previewcounter--;
                }
                createPreviewImage();
                //convertImageToBitmap(firstimage, secondimage);
                //secondimage = firstimage;
                //compareTheDifferenceBetwennBitmaps(firstbitmap, secondbitmap);
                getCurrentPreview();
                //previewcounter++;
                //takePicture();
            }
        }, 3000);
    }

    *
     *Ez a fgv fog 500 msként elkészíteni egy képet

    public void createPreviewImage(){
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);  //ImageFormaton kell állítani majd
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);  //STILL_CAPTURE-ről
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    //Image image = null;
                    try {
                        firstimage = reader.acquireLatestImage();
                        //ByteBuffer buffer = firstimage.getPlanes()[0].getBuffer();
                        //byte[] bytes = new byte[buffer.capacity()];
                        //buffer.get(bytes);
                        //save(bytes);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (firstimage != null) {
                            firstimage.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mPreviewHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //A Toast miatt dobja a MessageQueue a Handler on  dead thread errort
                    Toast.makeText(MainActivity.this, previewcounter+". previewkep" , Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mPreviewHandler); // mBackgroundHandler
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mPreviewHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    *
     * Ez a fgv konvertálja a mindenkori két képet Bitmappá

    public void convertImageToBitmap(Image first, Image second){
        if(first != null){
            ByteBuffer buffer = first.getPlanes()[0].getBuffer();
            byte[] firstbytes = new byte[buffer.remaining()];
            buffer.get(firstbytes);
            firstbitmap = BitmapFactory.decodeByteArray(firstbytes,0,firstbytes.length,null);
        }

        if(second != null){
            ByteBuffer buffersec = second.getPlanes()[0].getBuffer();
            byte[] secondbytes = new byte[buffersec.remaining()];
            buffersec.get(secondbytes);
            secondbitmap = BitmapFactory.decodeByteArray(secondbytes,0,secondbytes.length,null);
        }
    }

    *
     * Ez a fgv fogja összehasonlítani a két kép között lévő eltérést és valahogy jelezni/figyelmeztetni
     * Ha az eltérés jelentős
     * @param first
     * @param second

    public void compareTheDifferenceBetweenBitmaps(Bitmap first, Bitmap second){
        if(first!=null && second!=null && first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight()){
            int fsumA = 0;int fsumR = 0; int fsumG= 0; int fsumB=0;
            int ssumA = 0;int ssumR = 0; int ssumG= 0; int ssumB=0;
            for(int height = 0;height < first.getHeight(); height++){
                for (int width = 0; width < first.getWidth(); width++){
                    int firstcolor = first.getPixel(width,height);
                    int A = (firstcolor >> 24) & 0xff; fsumA+=A; // or color >>> 24
                    int R = (firstcolor >> 16) & 0xff; fsumR+=R;
                    int G = (firstcolor >>  8) & 0xff; fsumG+=G;
                    int B = (firstcolor      ) & 0xff; fsumB+=B;

                    int secondcolor = second.getPixel(width,height);
                    int a = (secondcolor >> 24) & 0xff; ssumA+=a; // or color >>> 24
                    int r = (secondcolor >> 16) & 0xff; ssumR+=r;
                    int g = (secondcolor >>  8) & 0xff; ssumG+=g;
                    int b = (secondcolor      ) & 0xff; ssumB+=b;


                    if( width == first.getWidth()-1 && (height+1) % 20 == 0){
                        if(isDifferent(fsumR,fsumG,fsumB,ssumR,ssumG,ssumB)){
                            playRingtone();
                            //sendEmailNotification();
                        }
                        height++;
                        width = 0;
                    }
                    else if( (width+1) % 20 == 0 && (height+1) % 20  == 0){
                        if(isDifferent(fsumR,fsumG,fsumB,ssumR,ssumG,ssumB)){
                            playRingtone();
                            //sendEmailNotification();
                        }
                        width++;
                        height = height - 19;
                    }
                    else if( (width+1) % 20 == 0){
                        height++;
                        width = width - 19;
                    }
                }
            }
        }
    }

    public boolean isDifferent(int firstRed, int firstGreen, int firstBlue, int secondRed, int secondGreen, int secondBlue){
        double firstAverageR = firstRed/400; double firstAverageG = firstGreen/400; double firstAverageB = firstBlue/400;
        double secondAverageR = secondRed/400; double secondAverageG = secondGreen/400; double secondAverageB = secondBlue/400;
        return Math.abs((firstAverageR/secondAverageR) - 1) > 0.15 ||
               Math.abs((firstAverageG/secondAverageG) - 1) > 0.15 ||
               Math.abs((firstAverageB/secondAverageB) - 1) > 0.15;

    }
    public void playRingtone(){
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            ringtone.play();
            //TODO: Valahol leállítani is a ringtonet stop()-al
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendEmailNotification(){
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmail");
        i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"toth.attila9704@gmail.com"});
        i.putExtra(Intent.EXTRA_SUBJECT, "HomeSecurity");
        i.putExtra(Intent.EXTRA_TEXT   , "Teszt email az android önlabhoz");
        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }*/
}
