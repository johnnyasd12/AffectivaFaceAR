package com.yutouch.affectivafacear;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * Created by Lee on 2017/6/22.
 */

public class DrawingView extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder holder;
    DrawingThread drawingThread;

    private final static String LOG_TAG = "FaceAR/DrawingView";
    private int imageWidth = 1;
    private int imageHeight = 1;
    private int surfaceViewWidth = 0;
    private int surfaceViewHeight = 0;
    private float screenToImageRatio = 0;
    private int drawThickness = 0;

    private DrawingThreadEventListener listener; // 拍照用的吧

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    public void setEventListener(DrawingThreadEventListener listener) {
        this.listener = listener;

        if (drawingThread != null) {
            drawingThread.setEventListener(listener);
        }
    }
    public void requestBitmap() {
        Log.d("DrawingView","requestBitmap() called");
        if (listener == null) {
            String msg = "Attempted to request screenshot without first attaching event listener";
            Log.e(LOG_TAG, msg);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            return;
        }
        if (drawingThread == null || drawingThread.isStopped()) {
            String msg = "Attempted to request screenshot without a running drawing thread";
            Log.e(LOG_TAG, msg);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("drawingThread","requestCaptureBitmap, T");
        drawingThread.requestCaptureBitmap = true;
    }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        drawingThread = new DrawingThread(holder,listener);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        drawingThread = new DrawingThread(holder,listener);
        drawingThread.setRunning(true);
        drawingThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        boolean retry = true;
        drawingThread.setRunning(false);
        while (retry) {
            try {
                drawingThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }

    public void updateViewDimensions(int surfaceViewWidth, int surfaceViewHeight, int imageWidth, int imageHeight) {// surfaceView在screen上的寬高, 以及畫圖(畫板)的寬高
        if (surfaceViewWidth <= 0 || surfaceViewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("All dimensions submitted to updateViewDimensions() must be positive");
        }
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.surfaceViewWidth = surfaceViewWidth;
        this.surfaceViewHeight = surfaceViewHeight;
        screenToImageRatio = (float) surfaceViewWidth / imageWidth;
    }

    public void setThickness(int t) {
        if (t <= 0) {
            throw new IllegalArgumentException("Thickness must be positive.");
        }
        drawThickness = t;
    }

    public void updatePoints(List<FaceObj> faces, boolean isPointsMirrored) {
        drawingThread.updatePoints(faces, isPointsMirrored);
    }

    public void invalidatePoints() {
        drawingThread.invalidatePoints();
    }

    interface DrawingThreadEventListener { // main要用的
        void onBitmapGenerated(Bitmap bitmap);
    }
    public int getImageWidth(){return imageWidth;}
    public boolean getIsMirror(){return drawingThread.sharer.isPointsMirrored;}
    public float getScreenToImageRatio(){return screenToImageRatio;}
    public int getSurfaceWidth(){return drawingThread.holder.getSurfaceFrame().width();}
    public int getSurfaceHeight(){return drawingThread.holder.getSurfaceFrame().height();}

    class DrawingThread extends Thread {
        private final FacesSharer sharer;
        private SurfaceHolder holder;
        private boolean running = true;

        private static final float ID_TEXT_SIZE = 40.0f;
        private static final float X_OFFSET = 10.0f;
        private static final float Y_OFFSET = 10.0f;
        private boolean isDrawPointsEnabled = true;
        // 拍照用
        private DrawingThreadEventListener listener; // 應該是拍照用的
        private volatile boolean requestCaptureBitmap = false; //從main通知DrawingThread要不要取得截圖

        Paint idPaint, boxPaint, trackingPointsPaint;

        public DrawingThread(SurfaceHolder holder,DrawingThreadEventListener listener) {
            this.holder = holder;
            this.listener = listener;
            sharer = new FacesSharer();

            idPaint = new Paint();
            idPaint.setColor(Color.WHITE);
            idPaint.setTextSize(ID_TEXT_SIZE);

            boxPaint = new Paint();
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(drawThickness);

            trackingPointsPaint = new Paint();
            trackingPointsPaint.setColor(Color.WHITE);
        }


        //Updates thread with latest faces returned by the onImageResults() event.
        public void updatePoints(List<FaceObj> faces, boolean isPointsMirrored) {
            synchronized (sharer) { // 隨時更新sharer的狀態及資料
                sharer.facesToDraw.clear();
                if (faces != null) {
                    sharer.facesToDraw.addAll(faces);
                }
                sharer.isPointsMirrored = isPointsMirrored;
            }
        }

        @Override
        public void run() {
            // this is where the animation will occur
            while(running) {
                Canvas canvas = null;
                Canvas screenshotCanvas = null;// 拍照用 會把AR畫上去, 不含照片 吧
                Bitmap screenshotBitmap = null;
                try {
                    canvas = holder.lockCanvas();

                    if (requestCaptureBitmap) { // 根據SurfaceView的大小製造一個空的canvas也就是screenshotCanvas
                        Log.d("DrawingThread","requestCaptureBitmap detected true");
                        Rect surfaceBounds = holder.getSurfaceFrame();
                        screenshotBitmap = Bitmap.createBitmap(surfaceBounds.width(), surfaceBounds.height(), Bitmap.Config.ARGB_8888);
                        screenshotCanvas = new Canvas(screenshotBitmap);
                        requestCaptureBitmap = false;
                    }

                    if (canvas != null) {
                        synchronized (holder) {
                            // draw
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            synchronized (sharer) {
                                for (int i = 0; i < sharer.facesToDraw.size(); i++) {
                                    FaceObj face = sharer.facesToDraw.get(i);

                                    //Rect rect = getFaceRect(canvas, face);
                                    // 基本 + 拍照用
                                    //draw(canvas,screenshotCanvas);
                                    drawFaceAttributes(canvas, face); // 畫AR在相機畫面上
                                    if(screenshotCanvas!=null){
                                        Log.d("DrawinThread/run()","screenshotCanvas detected not null");
                                        drawFaceAttributesForScreenshot(screenshotCanvas,face); // 畫AR給截圖使用
                                    }
                                }

                            }
                        }
                    }
                }
                finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                    if (screenshotBitmap != null && listener != null) { // 拍照畫圖
                        listener.onBitmapGenerated(Bitmap.createBitmap(screenshotBitmap));
                        screenshotBitmap.recycle();
                    }
                }
            }
        }

        public void setRunning(boolean runTag) {
            running = runTag;
        }


        private void drawFaceAttributes(Canvas canvas, FaceObj face) { // 畫出臉部AR
            //canvas.drawRect(rect, boxPaint);
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
            if(sharer.isPointsMirrored){drawEarX = DrawingView.this.imageWidth - drawEarX;}
            drawEarX *= DrawingView.this.screenToImageRatio;
            drawEarY *= DrawingView.this.screenToImageRatio;
//            Log.d("Draw/rootX",rootX+"");
//            Log.d("Draw/rootY",rootY+"");
//            Log.d("Draw/faceWidth",faceWidth+"");
//            Log.d("Draw/faceHeight",faceHeight+"");
            earWidth *= DrawingView.this.screenToImageRatio;
            earHeight *= DrawingView.this.screenToImageRatio;
            Bitmap earBitmap = BitmapFactory.decodeResource(DrawingView.this.getResources(),R.drawable.rbear);
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
            if(sharer.isPointsMirrored){drawNoseX = DrawingView.this.imageWidth - drawNoseX;}
            drawNoseX *= DrawingView.this.screenToImageRatio;
            drawNoseY *= DrawingView.this.screenToImageRatio;
            noseWidth *= DrawingView.this.screenToImageRatio;
            noseHeight *= DrawingView.this.screenToImageRatio;
            Bitmap noseBitmap = BitmapFactory.decodeResource(DrawingView.this.getResources(),R.drawable.rbnose);
            Bitmap noseBitmap2 = Bitmap.createScaledBitmap(noseBitmap, noseWidth, noseHeight, false);
            //image(鏡像) x向左遞增, y向下遞增, 原點在右上
            // 計算傾斜角度
            float chinX = facePoints[2].x;
            float chinY = facePoints[2].y;
            float noseX = facePoints[14].x; // 鼻子底部
            float noseY = facePoints[14].y;
            if(sharer.isPointsMirrored){
                chinX = DrawingView.this.imageWidth-chinX;
                noseX = DrawingView.this.imageWidth-noseX;
            }
            chinX*=DrawingView.this.screenToImageRatio;
            chinY*=DrawingView.this.screenToImageRatio;
            noseX*=DrawingView.this.screenToImageRatio;
            noseY*=DrawingView.this.screenToImageRatio;
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
            drawRotateBitmapByCenter(canvas,trackingPointsPaint,earBitmap2,angle,drawEarX,drawEarY,noseX,noseY);
            drawRotateBitmapByCenter(canvas,trackingPointsPaint,noseBitmap2,angle,drawNoseX,drawNoseY,noseX,noseY);
            earBitmap.recycle();
            earBitmap2.recycle();
            noseBitmap.recycle();
            noseBitmap2.recycle();
        }

        private void drawFaceAttributesForScreenshot(Canvas canvas, FaceObj face) { // 畫出臉部AR, 給截圖用
            //canvas.drawRect(rect, boxPaint);
            boolean isMirror = sharer.isPointsMirrored;
            //isMirror = false;
            Log.d("dShot","drawAttr4ScreenShot called");
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
                //drawEarX = DrawingView.this.imageWidth - drawEarX;
                //drawEarX = -drawEarX;
                drawEarX = drawEarX - DrawingView.this.imageWidth/2;
                //drawEarX = 0;
            }
            drawEarX *= DrawingView.this.screenToImageRatio;
            drawEarY *= DrawingView.this.screenToImageRatio;
//            Log.d("Draw/rootX",rootX+"");
//            Log.d("Draw/rootY",rootY+"");
//            Log.d("Draw/faceWidth",faceWidth+"");
//            Log.d("Draw/faceHeight",faceHeight+"");
            earWidth *= DrawingView.this.screenToImageRatio;
            earHeight *= DrawingView.this.screenToImageRatio;
            Bitmap earBitmap = BitmapFactory.decodeResource(DrawingView.this.getResources(),R.drawable.rbear);
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
                //drawNoseX = DrawingView.this.imageWidth - drawNoseX;
                //drawNoseX = -drawNoseX;
                drawNoseX = drawNoseX - DrawingView.this.imageWidth/2;
            }
            drawNoseX *= DrawingView.this.screenToImageRatio;
            drawNoseY *= DrawingView.this.screenToImageRatio;
            noseWidth *= DrawingView.this.screenToImageRatio;
            noseHeight *= DrawingView.this.screenToImageRatio;
            Bitmap noseBitmap = BitmapFactory.decodeResource(DrawingView.this.getResources(),R.drawable.rbnose);
            Bitmap noseBitmap2 = Bitmap.createScaledBitmap(noseBitmap, noseWidth, noseHeight, false);
            //image(鏡像) x向左遞增, y向下遞增, 原點在右上
            // 計算傾斜角度
            float chinX = facePoints[2].x;
            float chinY = facePoints[2].y;
            float noseX = facePoints[14].x; // 鼻子底部
            float noseY = facePoints[14].y;
            if(isMirror){
                //chinX = DrawingView.this.imageWidth-chinX;
                //noseX = DrawingView.this.imageWidth-noseX;
                //chinX = -chinX;
                //noseX = -noseX;
                chinX = chinX - DrawingView.this.imageWidth/2;
                noseX = noseX - DrawingView.this.imageWidth/2;
            }
            chinX*=DrawingView.this.screenToImageRatio;
            chinY*=DrawingView.this.screenToImageRatio;
            noseX*=DrawingView.this.screenToImageRatio;
            noseY*=DrawingView.this.screenToImageRatio;
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
            drawRotateBitmapByCenter(canvas,trackingPointsPaint,earBitmap2,angle,drawEarX,drawEarY,noseX,noseY);
            drawRotateBitmapByCenter(canvas,trackingPointsPaint,noseBitmap2,angle,drawNoseX,drawNoseY,noseX,noseY);
            Log.d("dShot","drawAttr4Screenshot finished");
        }
        private void drawRotateBitmapByCenter(Canvas canvas, Paint paint, Bitmap bitmap,
                                              float rotation, float posX, float posY, float centerX, float centerY) {// 旋轉bitmap本身
            //if(mirrorPoints){posX=config.imageWidth-posX;}
            Matrix matrix = new Matrix();
            matrix.postTranslate(posX, posY);
            matrix.postRotate(rotation,centerX,centerY);
            canvas.drawBitmap(bitmap, matrix, paint);
        }

        //Inform thread face detection has stopped, so pending faces are no longer valid.
        public void invalidatePoints() {
            synchronized (sharer) {
                sharer.facesToDraw.clear();
            }
        }
        public void setEventListener(DrawingThreadEventListener listener) {
            this.listener = listener;
        }
        public boolean isStopped(){return !running;}
    }


    class FacesSharer {
        boolean isPointsMirrored;
        List<FaceObj> facesToDraw;

        public FacesSharer() {
            isPointsMirrored = false;
            facesToDraw = new ArrayList<FaceObj>();
        }
    }

}

