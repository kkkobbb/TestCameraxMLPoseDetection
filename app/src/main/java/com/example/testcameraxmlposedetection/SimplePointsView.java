package com.example.testcameraxmlposedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.List;

public class SimplePointsView extends View {
    private final Paint paint = new Paint();
    private List<PoseLandmark> poseLandmarks;
    private float inputRatioX; // 表示画面と入力画像の幅の比
    private float inputRatioY; // 表示画面と入力画像の高さの比

    public SimplePointsView(Context context) {
        super(context);
        init();
    }

    public SimplePointsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimplePointsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(Color.argb(255, 255, 0, 0));
        paint.setStrokeWidth(10);
    }

    /**
     * 体の位置を更新する
     * @param poseLandmarks 体の各部位の位置
     * @param rx 表示画面と入力画像の幅の比
     * @param ry 表示画面と入力画面の高さの比
     */
    public void updatePose(List<PoseLandmark> poseLandmarks, final float rx, final float ry) {
        this.poseLandmarks = poseLandmarks;
        inputRatioX = rx;
        inputRatioY = ry;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (poseLandmarks != null && poseLandmarks.size() > 0) {
            drawPoints(canvas, poseLandmarks);
        }
    }

    /**
     * 体の各部位の上に点を描画する
     * @param canvas 描画用
     * @param poseLandmarks 体の各部位の位置
     */
    private void drawPoints(Canvas canvas, List<PoseLandmark> poseLandmarks) {
        for (PoseLandmark pl : poseLandmarks) {
            PointF p = pl.getPosition();
            canvas.drawPoint(p.x * inputRatioX, p.y * inputRatioY, paint);
        }
    }
}
