package com.yutouch.affectivafacear;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;

public class MainActivity extends AppCompatActivity implements CameraDetector.CameraEventListener{

    LinearLayout mainLayout;
    SurfaceView camServiceView;// 顯示畫面
    private void initializeUI(){
        mainLayout = (LinearLayout)findViewById(R.id.linearLayout1);
        camServiceView = (SurfaceView)findViewById(R.id.surfaceView1);

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeUI();
        // 初始化 detector
        CameraDetector detector = new CameraDetector(this, // context
                CameraDetector.CameraType.CAMERA_FRONT, // 前鏡頭
                camServiceView, // 顯示畫面
                5, // 偵測臉的最多數量
                Detector.FaceDetectorMode.SMALL_FACES); // 臉部大小偵測偏好
        detector.setMaxProcessRate(5);// 每秒處理幾個frames, 最低建議設5
    }


    @Override
    public void onCameraSizeSelected(int cameraWidth, int cameraHeight, Frame.ROTATE rotation) { // 選定畫面SIZE?
        int cameraPreviewWidth;
        int cameraPreviewHeight;

//cameraWidth and cameraHeight report the unrotated dimensions of the camera frames, so switch the width and height if necessary

        if (rotation == Frame.ROTATE.BY_90_CCW || rotation == Frame.ROTATE.BY_90_CW) {
            cameraPreviewWidth = cameraHeight;
            cameraPreviewHeight = cameraWidth;
        } else {
            cameraPreviewWidth = cameraWidth;
            cameraPreviewHeight = cameraHeight;
        }

//retrieve the width and height of the ViewGroup object containing our SurfaceView (in an actual application, we would want to consider the possibility that the mainLayout object may not have been sized yet)

        int layoutWidth = mainLayout.getWidth();
        int layoutHeight = mainLayout.getHeight();

//compute the aspect Ratio of the ViewGroup object and the cameraPreview

        float layoutAspectRatio = (float)layoutWidth/layoutHeight;
        float cameraPreviewAspectRatio = (float)cameraWidth/cameraHeight;

        int newWidth;
        int newHeight;

        if (cameraPreviewAspectRatio > layoutAspectRatio) {
            newWidth = layoutWidth;
            newHeight =(int) (layoutWidth / cameraPreviewAspectRatio);
        } else {
            newWidth = (int) (layoutHeight * cameraPreviewAspectRatio);
            newHeight = layoutHeight;
        }

//size the SurfaceView

        ViewGroup.LayoutParams params = camServiceView.getLayoutParams();
        params.height = newHeight;
        params.width = newWidth;
        camServiceView.setLayoutParams(params);
    }









}
