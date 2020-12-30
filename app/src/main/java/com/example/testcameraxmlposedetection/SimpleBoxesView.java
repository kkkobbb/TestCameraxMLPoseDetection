package com.example.testcameraxmlposedetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.List;

public class SimpleBoxesView extends View {
    private final Paint paint = new Paint();
    private List<PoseLandmark> poseLandmarks = null;
    private float inputRatioX = 0; // 表示画面と入力画像の幅の比
    private float inputRatioY = 0; // 表示画面と入力画像の高さの比
    // onDraw()時にメモリ確保しないためにここでメモリ確保
    private final Path boxPath = new Path();
    private final float[] boxPoints = new float[8];
    private final float[] pLine = new float[4];
    private final float[] qLine = new float[4];

    public SimpleBoxesView(Context context) {
        super(context);
        init();
    }

    public SimpleBoxesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimpleBoxesView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(Color.argb(100, 255, 0, 255));
        paint.setStrokeWidth(3);
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
            drawBoxes(canvas, poseLandmarks);
        }
    }

    /**
     * 体の各部位の上に長方形を描画する
     * @param canvas 描画用
     * @param poseLandmarks 体の各部位の位置
     */
    private void drawBoxes(Canvas canvas, List<PoseLandmark> poseLandmarks) {
        final PointF leftShoulder = poseLandmarks.get(PoseLandmark.LEFT_SHOULDER).getPosition();
        final PointF leftElbow = poseLandmarks.get(PoseLandmark.LEFT_ELBOW).getPosition();
        final PointF leftWrist = poseLandmarks.get(PoseLandmark.LEFT_WRIST).getPosition();
        final PointF rightShoulder = poseLandmarks.get(PoseLandmark.RIGHT_SHOULDER).getPosition();
        final PointF rightElbow = poseLandmarks.get(PoseLandmark.RIGHT_ELBOW).getPosition();
        final PointF rightWrist = poseLandmarks.get(PoseLandmark.RIGHT_WRIST).getPosition();
        final PointF leftHip = poseLandmarks.get(PoseLandmark.LEFT_HIP).getPosition();
        final PointF leftKnee = poseLandmarks.get(PoseLandmark.LEFT_KNEE).getPosition();
        final PointF leftAnkle = poseLandmarks.get(PoseLandmark.LEFT_ANKLE).getPosition();
        final PointF rightHip = poseLandmarks.get(PoseLandmark.RIGHT_HIP).getPosition();
        final PointF rightKnee = poseLandmarks.get(PoseLandmark.RIGHT_KNEE).getPosition();
        final PointF rightAnkle = poseLandmarks.get(PoseLandmark.RIGHT_ANKLE).getPosition();

        // 左腕
        drawBox(canvas, paint, leftShoulder, leftElbow, 0.5f);
        drawBox(canvas, paint, leftElbow, leftWrist, 0.5f);
        // 右腕
        drawBox(canvas, paint, rightShoulder, rightElbow, 0.5f);
        drawBox(canvas, paint, rightElbow, rightWrist, 0.5f);
        // 左足
        drawBox(canvas, paint, leftHip, leftKnee, 0.4f);
        drawBox(canvas, paint, leftKnee, leftAnkle, 0.4f);
        // 右足
        drawBox(canvas, paint, rightHip, rightKnee, 0.4f);
        drawBox(canvas, paint, rightKnee, rightAnkle, 0.4f);
    }

    /**
     * 線分pqを軸にした長方形を描画する (長辺:pqの長さ 短辺:pqの長さ*ratio)
     * <pre>
     *         length: L
     *    +-----------------+
     *    |                 |  length: L*ratio/2
     *   p*-----------------*q
     *    |                 |  length: L*ratio/2
     *    +-----------------+
     * </pre>
     *
     * @param canvas 描画用
     * @param paint 描画用
     * @param p 線分の端
     * @param q 線分の端
     * @param ratio 長方形の長辺と短辺の比
     */
    private void drawBox(Canvas canvas, Paint paint, final PointF p, final PointF q, final float ratio) {
        calculateBoxPoints(p, q, ratio, boxPoints);

        boxPath.reset();

        // 描画開始位置
        final float firstPointX = boxPoints[0] * inputRatioX;
        final float firstPointY = boxPoints[1] * inputRatioY;
        boxPath.moveTo(firstPointX, firstPointY);
        // 4隅の残りの位置
        for (int i = 1; i < boxPoints.length / 2; ++i) {
            final float pointX = boxPoints[i * 2] * inputRatioX;
            final float pointY = boxPoints[i * 2 + 1] * inputRatioY;
            boxPath.lineTo(pointX, pointY);
        }
        boxPath.close();

        canvas.drawPath(boxPath, paint);
    }

    /**
     * 線分pqを軸にした長方形の四隅の点を求める
     * @param p 線分の端
     * @param q 線分の端
     * @param ratio 長方形の長辺と短辺の比
     * @param result 結果格納先 (要素数が8以上であること)
     */
    private void calculateBoxPoints(final PointF p, final PointF q, final float ratio, final float[] result) {
        final float a = perpendicularSlope(p, q);

        // 短辺の長さ（の半分）を求める
        final float pqDistance = distance(p, q);
        final float d = pqDistance * ratio / 2;

        // 点pを通る線分pqの垂線上にある、点pから距離d離れた2点を求める
        calculateLinePoints(a, p, d, pLine);
        // 点qでも同様
        calculateLinePoints(a, q, d, qLine);

        // 長方形の四隅を一筆書きする順番に格納する
        result[0] = pLine[0];
        result[1] = pLine[1];
        result[2] = pLine[2];
        result[3] = pLine[3];
        result[4] = qLine[2];
        result[5] = qLine[3];
        result[6] = qLine[0];
        result[7] = qLine[1];
    }

    /**
     * 点pと点qの距離を求める
     * @param p 点
     * @param q 点
     * @return 距離
     */
    private float distance(final PointF p, final PointF q) {
        return (float) Math.sqrt(Math.pow(p.x - q.x, 2) + Math.pow(p.y - q.y, 2));
    }

    /**
     * 線分pqの垂線の傾きを求める
     * @param p 線分の端
     * @param q 線分の端
     * @return 傾き
     */
    private float perpendicularSlope(final PointF p, final PointF q) {
        if (Float.compare(p.y, q.y) == 0) {
            // この場合垂線はy軸と並行な直線になるが、近似値としてfloatの最大値を返す
            return Float.MAX_VALUE;
        }
        return (p.x - q.x) / (q.y - p.y);
    }

    /**
     * 点p(直線y=ax+b上の点)からdだけ離れた直線y=ax+b上の点(2個)を求める
     * (bは上記の条件を満たす任意の値:計算上不要なので省略)
     * @param a 傾き
     * @param p 点
     * @param d 距離
     * @param result 結果格納先 (要素数が4以上であること)
     */
    private void calculateLinePoints(final float a, final PointF p, final float d, final float[] result) {
        final float squaredA = a * a;
        if (Float.isInfinite(squaredA)) {
            // aの2乗が(オーバーフローして)無限大になる場合計算できないので、y軸に平行な直線として計算する
            result[0] = p.x;
            result[1] = p.y + d;
            result[2] = p.x;
            result[3] = p.y - d;
            return;
        }

        // 点pを原点とした座標系で直線y=axと半径dの円との交点を求める
        // y = a*x
        // x^2 + y^2 = d^2

        // x^2 + a^2*x^2 = d^2
        // (1+a^2)*x^2 = d^2
        // x^2 = d^2 / (1+a^2)
        final double x1 = Math.sqrt(d * d / (1 + squaredA));
        final double x2 = -x1;
        // y = a*x
        final double y1 = a * x1;
        final double y2 = a * x2;

        // 点pを原点とした座標系から元の座標系に変換する
        result[0] = (float)(x1 + p.x);
        result[1] = (float)(y1 + p.y);
        result[2] = (float)(x2 + p.x);
        result[3] = (float)(y2 + p.y);
    }
}
