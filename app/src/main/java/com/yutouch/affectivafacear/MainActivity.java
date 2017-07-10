package com.yutouch.affectivafacear;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.yutouch.affectivafacear.ImageHelper.getBitmapFromFrame;
import static java.lang.Math.sqrt;

/**
 * This is a very bare sample app to demonstrate the usage of the CameraDetector object from Affectiva.
 * It displays statistics on frames per second, percentage of time a face was detected, and the user's smile score.
 *
 * The app shows off the maneuverability of the SDK by allowing the user to start and stop the SDK and also hide the camera SurfaceView.
 *
 * For use with SDK 2.02
 */
public class MainActivity extends Activity implements Detector.ImageListener, CameraDetector.CameraEventListener,
    DrawingView.DrawingThreadEventListener{

    final String LOG_TAG = "CameraDetectorDemo";
    // 拍照用參數
    private boolean storagePermissionsAvailable = false;
    private static final int EXTERNAL_STORAGE_PERMISSIONS_REQUEST = 73;// 介於0~255的任意數
    private Frame mostRecentFrame;
    // 改過snapshot架構後另外使用的參數
    boolean isRequestScreenshot = false;
    //ArrayList<FaceObj> listFaces;

    Button startSDKButton;
    Button screenshotButton;
    RelativeLayout mainLayout;
    LinearLayout childLayout1;
    SurfaceView cameraPreview;
    DrawingView drawingView;

    boolean isCameraBack = false;
    boolean isSDKStarted = true;


    CameraDetector detector;

    int previewWidth = 0;
    int previewHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainLayout = (RelativeLayout) findViewById(R.id.mainLayout);
        childLayout1 = (LinearLayout)findViewById(R.id.childLayout1);
        drawingView = (DrawingView)findViewById(R.id.drawingView);
        startSDKButton = (Button) findViewById(R.id.sdk_start_button);
        screenshotButton = (Button)findViewById(R.id.screenshot_button);

        startSDKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSDKStarted) {
                    isSDKStarted = false;
                    stopDetector();
                    startSDKButton.setText("Start Camera");
                } else {
                    isSDKStarted = true;
                    startDetector();
                    startSDKButton.setText("Stop Camera");
                }
            }
        });
        startSDKButton.setText(isSDKStarted?"Stop Camera":"Start Camera");

        //We create a custom SurfaceView that resizes itself to match the aspect ratio of the incoming camera frames
        cameraPreview = new SurfaceView(this) {
            @Override
            public void onMeasure(int widthSpec, int heightSpec) {
                int measureWidth = MeasureSpec.getSize(widthSpec);
                int measureHeight = MeasureSpec.getSize(heightSpec);
                int width;
                int height;
                if (previewHeight == 0 || previewWidth == 0) {
                    width = measureWidth;
                    height = measureHeight;
                } else {
                    float viewAspectRatio = (float)measureWidth/measureHeight; // 這個view的寬高比
                    float cameraPreviewAspectRatio = (float) previewWidth/previewHeight; // 相機畫面寬高比

                    if (cameraPreviewAspectRatio > viewAspectRatio) {// 若相機畫面較寬
                        width = measureWidth; // 寬度縮為view的寬度
                        height =(int) (measureWidth / cameraPreviewAspectRatio);
                    } else { // 若相機畫面較高
                        width = (int) (measureHeight * cameraPreviewAspectRatio);
                        height = measureHeight; // 高度縮為view的高度
                    }
                }
                setMeasuredDimension(width,height);
            }
        };
//        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        params.addRule(RelativeLayout.CENTER_IN_PARENT,RelativeLayout.TRUE);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(mainLayout.getLayoutParams());
        params = new RelativeLayout.LayoutParams(drawingView.getLayoutParams());
        //LinearLayout.LayoutParams params =  new LinearLayout.LayoutParams(childLayout1.getLayoutParams());
        cameraPreview.setLayoutParams(params);
        mainLayout.addView(cameraPreview,0);
        //childLayout1.addView(cameraPreview,0);
        drawingView.setZOrderMediaOverlay(true); // 讓他在別的surfaceView上層(但還是在window下層
        cameraPreview.setZOrderMediaOverlay(false);
        drawingView.setZOrderOnTop(true); // 讓他在window上層
        drawingView.getHolder().setFormat(PixelFormat.TRANSPARENT); // 將這個view的背景設成透明
        setDetector(); // detector設定

        drawingView.setEventListener(this);
        isSDKStarted = true; // 這行必須
        //startDetector();
        // 截圖用
        //listFaces = new ArrayList<FaceObj>();
    }
    private void setDetector(){
        detector = new CameraDetector(this, CameraDetector.CameraType.CAMERA_FRONT, cameraPreview);
        detector.setDetectSmile(false);
        detector.setDetectAge(false);
        detector.setDetectEthnicity(false);
        detector.setDetectAllAppearances(true);
        detector.setImageListener(this);
        detector.setOnCameraEventListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isSDKStarted) {
            startDetector(); // 必須有
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopDetector();
    }

    void startDetector() {
        if (!detector.isRunning()) {
            detector.start();
        }
    }

    void stopDetector() {
        if (detector.isRunning()) {
            detector.stop();
        }
    }

    void switchCamera(CameraDetector.CameraType type) {
        detector.setCameraType(type);
    }

    @Override
    public void onImageResults(List<Face> list, Frame frame, float v) {//只要有畫面就會call
        //Log.d("dShot:","onImageResults called");
        mostRecentFrame = frame;// 照片(拍照截圖用
        List<FaceObj> faces = new ArrayList<>();
        if (list == null)
            return;
        if (list.size() == 0) {// 若無affectiva faces
            drawingView.invalidatePoints(); // 清除DrawingThread.sharer的資料
            drawingView.updatePoints(new ArrayList<FaceObj>(), true); // 給一個空的ArrayFace到sharer

        } else { // 若有偵測到faces, 則更新drawingThread.sharer的臉資料
            for (int i = 0; i < list.size(); i++) {
                Face face = list.get(i);
                int faceId = face.getId();
                FaceObj mFaceObj = new FaceObj(this, faceId, face);
                faces.add(mFaceObj);
                //listFaces.add(mFaceObj);
            }
            drawingView.updatePoints(faces, true);
        }
        if(isRequestScreenshot){ // 如果按下了拍照紐
            isRequestScreenshot=false;
            Bitmap drawViewBitmap;
            Canvas screenshotCanvas;
            drawViewBitmap = Bitmap.createBitmap(drawingView.getSurfaceWidth(), drawingView.getSurfaceHeight(), Bitmap.Config.ARGB_8888);
            screenshotCanvas = new Canvas(drawViewBitmap);
            screenshotCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            // 畫 臉部AR
            if(faces.size()>0) { // 若有偵測到臉
                for (FaceObj eachFace : faces) {
                    drawFaceAR(screenshotCanvas, eachFace);
                }
                processScreenshot(drawViewBitmap);
            }else { // 沒偵測到臉就只拍照片
                processScreenshot();
            }
            drawViewBitmap.recycle();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public void onCameraSizeSelected(int width, int height, Frame.ROTATE rotate) {
        if (rotate == Frame.ROTATE.BY_90_CCW || rotate == Frame.ROTATE.BY_90_CW) {
            previewWidth = height;
            previewHeight = width;
        } else {
            previewHeight = height;
            previewWidth = width;
        }
        cameraPreview.requestLayout(); // 重 call measure(), onMeasure()....

        // 要更改drawingView的size
        drawingView.setThickness((int) (previewWidth / 100f)); // 設定畫筆粗度?
        mainLayout.post(new Runnable() {
            @Override
            public void run() {// 將drawingView的size和cameraPreview同化
                //Get the screen width and height, and calculate the new app width/height based on the surfaceview aspect ratio.
                DisplayMetrics displaymetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
                int layoutWidth = displaymetrics.widthPixels;
                int layoutHeight = displaymetrics.heightPixels;

                if (previewWidth == 0 || previewHeight == 0 || layoutWidth == 0 || layoutHeight == 0)
                    return;

                float layoutAspectRatio = (float) layoutWidth / layoutHeight; // layout寬高比
                float cameraPreviewAspectRatio = (float) previewWidth / previewHeight; // 相機畫面寬高比

                int newWidth; // 設定mainLayout大小
                int newHeight;

                if (cameraPreviewAspectRatio > layoutAspectRatio) { // 若相機畫面比較寬
                    newWidth = layoutWidth; // 寬度縮至layout寬度
                    newHeight = (int) (layoutWidth / cameraPreviewAspectRatio);
                } else { // 若相機畫面比較高
                    newWidth = (int) (layoutHeight * cameraPreviewAspectRatio);
                    newHeight = layoutHeight; // 高度縮至layout高度
                }

                drawingView.updateViewDimensions(newWidth, newHeight, previewWidth, previewHeight); // 設定畫圖view的寬高, 前兩參數為surfaceView的寬高, 後兩項為畫板的寬高, 所以在draw的時候座標會轉換

                ViewGroup.LayoutParams params = mainLayout.getLayoutParams();
                params.height = newHeight;
                params.width = newWidth;
                mainLayout.setLayoutParams(params); // 設定主layout的寬高

                //Now that our main layout has been resized, we can remove the progress bar that was obscuring the screen (its purpose was to obscure the resizing of the SurfaceView)

            }
        });
    }
    public void takeScreenshot(View view) {
        // Check the permissions to see if we are allowed to save the screenshot
        Log.d("dShot:takeScreenshot","called");
        if (!storagePermissionsAvailable) {
            checkForStoragePermissions();
            return;
        }
        isRequestScreenshot = true;//drawingView.requestBitmap();

        /**
         * A screenshot of the drawing view is generated and processing continues via the callback
         * onBitmapGenerated() which calls processScreenshot().
         */
    }
    private void processScreenshot(Bitmap drawingViewBitmap) { // drawingViewBitmap應該是畫AR的Bitmap
        Log.d("dShot:processScreenshot","called");
        if (mostRecentFrame == null) {
            Toast.makeText(getApplicationContext(), "No frame detected, aborting screenshot", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!storagePermissionsAvailable) {
            checkForStoragePermissions();
            return;
        }

        Bitmap faceBitmap = getBitmapFromFrame(mostRecentFrame); // 原始照片

        if (faceBitmap == null) {
            Log.e(LOG_TAG, "Unable to generate bitmap for frame, aborting screenshot");
            return;
        }else{Log.d("Main/processScreenshot","mostRecentFrame get");}

        Bitmap finalScreenshot = Bitmap.createBitmap(faceBitmap.getWidth(), faceBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalScreenshot); // 畫在canvas上的東西會進finalScreenshot這個Bitmap
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

        canvas.drawBitmap(faceBitmap, 0, 0, paint); // 畫拍好的照片上去 (0,0)是座標

        // 應該是在調整作畫比例
        float scaleFactor = ((float) faceBitmap.getWidth()) / ((float) drawingViewBitmap.getWidth());
        int scaledHeight = Math.round(drawingViewBitmap.getHeight() * scaleFactor);
        canvas.drawBitmap(drawingViewBitmap, null, new Rect(0, 0, faceBitmap.getWidth(), scaledHeight), paint); // 畫AR上去

        drawingViewBitmap.recycle();

        Date now = new Date();
        String timestamp = DateFormat.format("yyyy-MM-dd_HH-mm-ss", now).toString();
        File pictureFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AffdexMe");
        if (!pictureFolder.exists()) {
            if (!pictureFolder.mkdir()) {
                Log.e(LOG_TAG, "Unable to create directory: " + pictureFolder.getAbsolutePath());
                return;
            }
        }

        String screenshotFileName = timestamp + ".png";
        File screenshotFile = new File(pictureFolder, screenshotFileName);

        try {
            ImageHelper.saveBitmapToFileAsPng(finalScreenshot, screenshotFile);
        } catch (IOException e) {
            String msg = "Unable to save screenshot";
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            Log.e(LOG_TAG, msg, e);
            return;
        }
        ImageHelper.addPngToGallery(getApplicationContext(), screenshotFile);

        faceBitmap.recycle();
        finalScreenshot.recycle();

        String fileSavedMessage = "Screenshot saved to: " + screenshotFile.getPath();
        Toast.makeText(getApplicationContext(), fileSavedMessage, Toast.LENGTH_SHORT).show();
        Log.d(LOG_TAG, fileSavedMessage);
        Log.d("dShot:processScreenshot","finished");
    }

    private void processScreenshot() { // drawingViewBitmap應該是畫AR的Bitmap
        Log.d("dShot:processScreenshot","called");
        if (mostRecentFrame == null) {
            Toast.makeText(getApplicationContext(), "No frame detected, aborting screenshot", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!storagePermissionsAvailable) {
            checkForStoragePermissions();
            return;
        }

        Bitmap faceBitmap = getBitmapFromFrame(mostRecentFrame); // 原始照片

        if (faceBitmap == null) {
            Log.e(LOG_TAG, "Unable to generate bitmap for frame, aborting screenshot");
            return;
        }else{Log.d("Main/processScreenshot","mostRecentFrame get");}

        Bitmap finalScreenshot = Bitmap.createBitmap(faceBitmap.getWidth(), faceBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalScreenshot); // 畫在canvas上的東西會進finalScreenshot這個Bitmap
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

        canvas.drawBitmap(faceBitmap, 0, 0, paint); // 畫拍好的照片上去 (0,0)是座標

        //float scaleFactor = ((float) faceBitmap.getWidth()) / ((float) drawingViewBitmap.getWidth());
        //int scaledHeight = Math.round(drawingViewBitmap.getHeight() * scaleFactor);
        //canvas.drawBitmap(drawingViewBitmap, null, new Rect(0, 0, faceBitmap.getWidth(), scaledHeight), paint); // 畫AR上去

        //drawingViewBitmap.recycle();

        Date now = new Date();
        String timestamp = DateFormat.format("yyyy-MM-dd_hh-mm-ss", now).toString();
        File pictureFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AffdexMe");
        if (!pictureFolder.exists()) {
            if (!pictureFolder.mkdir()) {
                Log.e(LOG_TAG, "Unable to create directory: " + pictureFolder.getAbsolutePath());
                return;
            }
        }

        String screenshotFileName = timestamp + ".png";
        File screenshotFile = new File(pictureFolder, screenshotFileName);

        try {
            ImageHelper.saveBitmapToFileAsPng(finalScreenshot, screenshotFile);
        } catch (IOException e) {
            String msg = "Unable to save screenshot";
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            Log.e(LOG_TAG, msg, e);
            return;
        }
        ImageHelper.addPngToGallery(getApplicationContext(), screenshotFile);

        faceBitmap.recycle();
        finalScreenshot.recycle();

        String fileSavedMessage = "Screenshot saved to: " + screenshotFile.getPath();
        Toast.makeText(getApplicationContext(), fileSavedMessage, Toast.LENGTH_SHORT).show();
        Log.d(LOG_TAG, fileSavedMessage);
        Log.d("dShot:processScreenshot","finished");
    }
    private void drawFaceAR(Canvas canvas, FaceObj face) { // 畫出臉部AR
        //canvas.drawRect(rect, boxPaint);
        boolean isMirror = drawingView.getIsMirror();
        //isMirror = false;
        Log.d("drawFaceAR","isMirror = "+isMirror);
        PointF[] facePoints = face.getFacePoints();
        int drawEarX,drawEarY,drawNoseX,drawNoseY;
        //  faceWidth和faceHeight應該用距離來算 不是用x或y來算 因為要考慮傾斜
        int faceWidth = (int)(sqrt((facePoints[10].x - facePoints[5].x)*(facePoints[10].x - facePoints[5].x)+
                (facePoints[10].y - facePoints[5].y)*(facePoints[10].y - facePoints[5].y))); // 左眉外側 - 右眉外側
        int faceHeight = (int)(sqrt((facePoints[2].y - facePoints[11].y)*(facePoints[2].y - facePoints[11].y)+
                (facePoints[2].x - facePoints[11].x)*(facePoints[2].x - facePoints[11].x)))*3/2; // (下巴 - 鼻根) *3/2

        PointF pointNoseRoot = facePoints[11];// 耳朵位置參考 鼻根
        int rootX = (int)pointNoseRoot.x;
        int rootY = (int)pointNoseRoot.y;
        int earWidth = faceWidth*6/5;
        int earHeight = faceHeight;
        drawEarX = rootX + earWidth/2;
        drawEarY = rootY - earHeight*4/3;
        if(isMirror){
            drawEarX = drawingView.getImageWidth() - drawEarX;
            drawEarX*=-1;
        }
        drawEarX *= drawingView.getScreenToImageRatio();
        drawEarY *= drawingView.getScreenToImageRatio();
//            Log.d("Draw/rootX",rootX+"");
//            Log.d("Draw/rootY",rootY+"");
//            Log.d("Draw/faceWidth",faceWidth+"");
//            Log.d("Draw/faceHeight",faceHeight+"");
        earWidth *= drawingView.getScreenToImageRatio();
        earHeight *= drawingView.getScreenToImageRatio();
        Bitmap earBitmap = BitmapFactory.decodeResource(this.getResources(),R.drawable.rbear);
        Bitmap earBitmap2 = Bitmap.createScaledBitmap(earBitmap, earWidth, earHeight, false);

        // 12:nose tip 鼻尖, 14: nose bottom boundary 鼻底
        PointF pointNoseTip = facePoints[12];
        int tipX = (int)pointNoseTip.x;
        int tipY = (int)pointNoseTip.y;
        int noseWidth = faceWidth;
        int noseHeight = faceHeight/2;
        drawNoseX = tipX + noseWidth/2 ;
        //drawNoseY = tipY - noseHeight/2;
        drawNoseY = (int)(face.getFacePoints()[12].y+face.getFacePoints()[14].y)/2 - noseHeight/2; // 鼻尖和鼻底之間
        if(isMirror){
            drawNoseX = drawingView.getImageWidth() - drawNoseX;
            drawNoseX*=-1;
        }
        drawNoseX *= drawingView.getScreenToImageRatio();
        drawNoseY *= drawingView.getScreenToImageRatio();
        noseWidth *= drawingView.getScreenToImageRatio();
        noseHeight *= drawingView.getScreenToImageRatio();
        Bitmap noseBitmap = BitmapFactory.decodeResource(this.getResources(),R.drawable.rbnose);
        Bitmap noseBitmap2 = Bitmap.createScaledBitmap(noseBitmap, noseWidth, noseHeight, false);
        //image(鏡像) x向左遞增, y向下遞增, 原點在右上
        // 計算傾斜角度
        float chinX = facePoints[2].x;
        float chinY = facePoints[2].y;
        float noseX = facePoints[14].x; // 鼻子底部
        float noseY = facePoints[14].y;
//        if(isMirror){
//            chinX = drawingView.getImageWidth()-chinX;
//            noseX = drawingView.getImageWidth()-noseX;
//            chinX*=-1;
//            noseX*=-1;
//        }
//        chinX*=drawingView.getScreenToImageRatio();
//        chinY*=drawingView.getScreenToImageRatio();
//        noseX*=drawingView.getScreenToImageRatio();
//        noseY*=drawingView.getScreenToImageRatio();
        float lenChinToNose = (float)sqrt((chinX-noseX)*(chinX-noseX)+(chinY-noseY)*(chinY-noseY)); // 下巴和鼻子的距離
        float sinAngle = (noseX-chinX)/lenChinToNose; // 大概是這樣算吧
//            Log.d("Draw/chinX-drawNoseX",(chinX-noseX)+"");
//            Log.d("Draw/x_chin",chinX+"");
//            Log.d("Draw/x_nose",noseX+"");
//            Log.d("Draw/sin",sinAngle+"");
        float angle = (float)Math.toDegrees(Math.asin(sinAngle)); //Log.d("Draw/angle",angle+"");


        //c.drawBitmap(earBitmap2, drawEarX, drawEarY, trackingPointsPaint);// 耳朵放在額頭 並置中
        //c.drawBitmap(noseBitmap2, drawNoseX, drawNoseY, trackingPointsPaint);// 鼻子放在鼻頭 並置中
        //畫 經過旋轉的 耳朵&鼻子
        Paint trackingPointsPaint;
        trackingPointsPaint = new Paint();
        trackingPointsPaint.setColor(Color.WHITE);
        drawRotateBitmapByCenter(canvas,trackingPointsPaint,earBitmap2,angle,drawEarX,drawEarY,noseX,noseY);
        drawRotateBitmapByCenter(canvas,trackingPointsPaint,noseBitmap2,angle,drawNoseX,drawNoseY,noseX,noseY);
        earBitmap.recycle();
        earBitmap2.recycle();
        noseBitmap.recycle();
        noseBitmap2.recycle();
    }
    private void drawRotateBitmapByCenter(Canvas canvas, Paint paint, Bitmap bitmap,
                                          float rotation, float posX, float posY, float centerX, float centerY) {// 旋轉bitmap本身
        //if(mirrorPoints){posX=config.imageWidth-posX;}
        Matrix matrix = new Matrix();
        matrix.postTranslate(posX, posY);
        matrix.postRotate(rotation,centerX,centerY);
        canvas.drawBitmap(bitmap, matrix, paint);
        //canvas.drawBitmap(bitmap,posX,posY,null); 怒不旋轉
    }
    private void checkForStoragePermissions() {
        storagePermissionsAvailable =
                ContextCompat.checkSelfPermission(
                        getApplicationContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        if (!storagePermissionsAvailable) {
            Log.d("Main/checkForStoragePer","notAvailable, resolving...");
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                // showPermissionExplanationDialog(EXTERNAL_STORAGE_PERMISSIONS_REQUEST); // im not going to show explanation, sorry
                Log.d("Main/checkForStoragePer","show explanation WRITE_EXTERNAL");
            } else {
                // No explanation needed, we can request the permission.
                Log.d("Main/checkForStoragePer","cannot write, requesting permissions");
                requestStoragePermissions();
            }
        } else {
            Log.d("Main/checkForStoragePer","Available, takeScreenshot()...");
            takeScreenshot(screenshotButton);
        }
    }
    private void requestStoragePermissions() {
        if (!storagePermissionsAvailable) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    EXTERNAL_STORAGE_PERMISSIONS_REQUEST);

            // EXTERNAL_STORAGE_PERMISSIONS_REQUEST is an app-defined int constant that must be between 0 and 255.
            // The callback method gets the result of the request.
        }
    }
    @Override
    public void onBitmapGenerated(@NonNull final Bitmap bitmap) { // 處理request後的bitmap
        Log.d("Main/onBitmapGenerated","called");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                processScreenshot(bitmap);
            }
        });
    }


}
