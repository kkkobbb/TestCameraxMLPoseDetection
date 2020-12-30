package com.example.testcameraxmlposedetection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "testcameraxmlpose";
    private static final int REQUEST_PERMISSION = 1;

    private PreviewView previewView;
    private SimplePointsView simplePointsView;
    private SimpleBoxesView simpleBoxesView;

    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private PoseDetector poseDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        simplePointsView = findViewById(R.id.simplePointsView);
        simpleBoxesView = findViewById(R.id.simpleBoxesView);

        // Base pose detector with streaming frames, when depending on the pose-detection sdk
        final PoseDetectorOptions options =
                new PoseDetectorOptions.Builder()
                        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                        .build();
        poseDetector = PoseDetection.getClient(options);

        previewView.setVisibility(View.INVISIBLE);

        // 権限があれば処理開始、なければ権限のリクエスト
        String[] permissions = {Manifest.permission.CAMERA};
        if (hasPermissions(permissions)) {
            startCameraOnLayout();
        } else {
            requestPermissions(permissions, REQUEST_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 権限が付加された場合、処理開始
                startCamera();
            }
        }
    }

    /**
     * 権限があるか確認する
     * @param permissions 確認する権限の一覧
     * @return 全ての権限がある場合、真
     */
    private boolean hasPermissions(final String[] permissions) {
        for (String permission : permissions) {
            final int permissionGranted = ContextCompat.checkSelfPermission(this,
                    permission);
            if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * previewViewのレイアウトが確定してからカメラを開始する
     */
    private void startCameraOnLayout() {
        final ViewTreeObserver viewTreeObserver = previewView.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                startCamera();
                previewView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    // カメラ初期化、開始
    private void startCamera() {
        previewView.setVisibility(View.VISIBLE);

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        final Context context = this;
        cameraProviderFuture.addListener(() -> {
            try {
                // カメラ選択
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                // 表示
                preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                // 画像解析
                // 解像度は端末毎に決められたリストからsetTargetResolution()で指定したサイズに近い値が選択される
                // imageAnalysisに渡される画像のサイズはpreviewに表示される画像と同じとは限らない
                // サイズが大きいと遅くなる
                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(previewView.getWidth(), previewView.getHeight()))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new PoseDetectionAnalyzer());

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle((LifecycleOwner)context, cameraSelector, preview, imageAnalysis);

                Log.d(LOG_TAG, "previewView Size: " + previewView.getWidth() + ", " + previewView.getHeight());
                @SuppressLint("RestrictedApi") final Size imageAnalysisSize = imageAnalysis.getAttachedSurfaceResolution();
                Log.d(LOG_TAG, "imageAnalysis Size: " + imageAnalysisSize);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 画像解析処理
    private class PoseDetectionAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(ImageProxy imageProxy) {
            @SuppressLint("UnsafeExperimentalUsageError") final Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                return;
            }

            final InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // 表示している画面と内部で処理する画像のサイズの比を求める
            // (imageは90度回転している前提)
            final float ratioX = previewView.getWidth() / (float)image.getHeight();
            final float ratioY = previewView.getHeight() / (float)image.getWidth();

            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {
                        // 検出位置取得
                        List<PoseLandmark> allPoseLandmarks = pose.getAllPoseLandmarks();
                        // 画面に表示
                        simpleBoxesView.updatePose(allPoseLandmarks, ratioX, ratioY);
                        simpleBoxesView.postInvalidate();
                        simplePointsView.updatePose(allPoseLandmarks, ratioX, ratioY);
                        simplePointsView.postInvalidate();
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close(); // クローズしないと次のanalyze()が呼び出されない
                    });
        }
    }
}