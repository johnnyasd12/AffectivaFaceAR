package com.yutouch.affectivafacear;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a very bare sample app to demonstrate the usage of the CameraDetector object from Affectiva.
 * It displays statistics on frames per second, percentage of time a face was detected, and the user's smile score.
 *
 * The app shows off the maneuverability of the SDK by allowing the user to start and stop the SDK and also hide the camera SurfaceView.
 *
 * For use with SDK 2.02
 */
public class MainActivity extends Activity implements Detector.ImageListener, CameraDetector.CameraEventListener {

    final String LOG_TAG = "CameraDetectorDemo";

    Button startSDKButton;

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

        isSDKStarted = true; // 這行必須
        //startDetector();
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
    public void onImageResults(List<Face> list, Frame frame, float v) {// TODO 畫圖囉
        if (list == null)
            return;
        if (list.size() == 0) {// 若無affectiva faces
            drawingView.invalidatePoints(); // 清除DrawingThread.sharer的資料
            drawingView.updatePoints(new ArrayList<FaceObj>(), true); // 給一個空的ArrayFace到sharer

        } else { // 若有偵測到faces, 則更新drawingThread.sharer的臉資料
            List<FaceObj> faces = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Face face = list.get(i);
                int faceId = face.getId();
                FaceObj mFaceObj = new FaceObj(this, faceId, face);
                faces.add(mFaceObj);
            }
            drawingView.updatePoints(faces, true);
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
        //cameraPreview.requestLayout(); // 這行能幹嘛???

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
}
