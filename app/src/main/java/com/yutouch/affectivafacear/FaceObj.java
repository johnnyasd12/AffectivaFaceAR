package com.yutouch.affectivafacear;

/**
 * Created by johnnytu on 2017/6/30.
 */


import android.content.Context;
import android.graphics.PointF;

import com.affectiva.android.affdex.sdk.detector.Face;

/**
 * Created by Lee on 2017/6/15.
 */

public class FaceObj {
    private Face mFace;
    private Context mContext;
    private int faceId;

    //Face feature points coordinates
    PointF[] points ;

    public FaceObj(Context c, int fid, Face face) {
        mFace = face;
        faceId = fid;
        mContext = c;
        //Face feature points coordinates
        points = face.getFacePoints();
    }

    public int getID() {
        return faceId;
    }
    public PointF[] getFacePoints(){
        return points;
    }



}

