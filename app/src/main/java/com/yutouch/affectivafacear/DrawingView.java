package com.yutouch.affectivafacear;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Lee on 2017/6/22.
 */

public class DrawingView extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder holder;
    DrawingThread drawingThread;

    private int imageWidth = 1;
    private int imageHeight = 1;
    private int surfaceViewWidth = 0;
    private int surfaceViewHeight = 0;
    private float screenToImageRatio = 0;
    private int drawThickness = 0;

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

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        drawingThread = new DrawingThread(holder);
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

    public void updateViewDimensions(int surfaceViewWidth, int surfaceViewHeight, int imageWidth, int imageHeight) {
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

    class DrawingThread extends Thread {
        private final FacesSharer sharer;
        private SurfaceHolder holder;
        private boolean running = true;

        private static final float ID_TEXT_SIZE = 40.0f;
        private static final float X_OFFSET = 10.0f;
        private static final float Y_OFFSET = 10.0f;
        private boolean isDrawPointsEnabled = true;

        Paint googlePaint, idPaint, boxPaint, trackingPointsPaint;

        public DrawingThread(SurfaceHolder holder) {
            this.holder = holder;
            sharer = new FacesSharer();

            googlePaint = new Paint();
            googlePaint.setColor(Color.GREEN);
            googlePaint.setStyle(Paint.Style.STROKE);
            googlePaint.setStrokeWidth(drawThickness);

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
            synchronized (sharer) {
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

                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        synchronized (holder) {
                            // draw
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            synchronized (sharer) {
                                for (int i = 0; i < sharer.facesToDraw.size(); i++) {
                                    FaceObj face = sharer.facesToDraw.get(i);

                                    Rect rect = getFaceRect(canvas, face);
                                    drawFaceRect(canvas, face, rect);
                                }

                            }
                        }
                    }
                }
                finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }

        public void setRunning(boolean runTag) {
            running = runTag;
        }


        private void drawFaceRect(Canvas canvas, FaceObj face, Rect rect) {
            canvas.drawRect(rect, boxPaint);
        }

        private Rect getFaceRect(Canvas canvas, FaceObj face) {
            float left = 1080;
            float top = 1920;
            float right = 0;
            float bottom = 0;

            for (PointF point : face.points) {
                float x;
                if (true) {
                    x = (imageWidth - point.x) * screenToImageRatio;
                } else {
                    x = (point.x) * screenToImageRatio;
                }
                float y = (point.y) * screenToImageRatio;

                if (isDrawPointsEnabled) {
                    canvas.drawCircle(x, y, drawThickness, trackingPointsPaint);
                }

                if (x < left) left = x;
                if (y < top) top = y;
                if (x > right) right = x;
                if (y > bottom) bottom = y;
            }
            return new Rect((int)left,(int)top,(int)right,(int)bottom);
        }

        //Inform thread face detection has stopped, so pending faces are no longer valid.
        public void invalidatePoints() {
            synchronized (sharer) {
                sharer.facesToDraw.clear();
            }
        }
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

