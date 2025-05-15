package com.example.ota_service;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ota_service.download.DownloadProgressInfo;
import com.example.ota_service.service.ServiceManager;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ServiceManager.ServiceListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSION = 100;

    // UI 요소
    private TextView tvCurrentVersion;
    private TextView tvStatus;
    private Button btnDownload;
    private ProgressBar progressBar;

    // 버전 초기값
    private double currentVersion = 1.0;

    // 서비스 매니저
    private ServiceManager serviceManager;

    // 현재 다운로드 상태
    private boolean isDownloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // UI 요소 초기화
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion);
        tvStatus = findViewById(R.id.tvStatus);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);

        // 버전 표시
        tvCurrentVersion.setText("현재 버전 ▶ " + String.format("%.1f", currentVersion));

        // 다운로드 버튼 이벤트 설정
        btnDownload.setOnClickListener(this);

        // 서비스 매니저 초기화
        serviceManager = new ServiceManager(this, this);

        // 권한 확인
        checkPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 서비스 연결 시작
        serviceManager.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 서비스 연결 해제
        serviceManager.disconnect();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnDownload) {
            if (isDownloading) {
                // 이미 다운로드 중이라면 취소
                serviceManager.cancelDownload();
                Toast.makeText(this, "다운로드 취소 중...", Toast.LENGTH_SHORT).show();
            } else {
                // 다운로드 시작
                if (checkPermissions()) {
                    serviceManager.startDownload();
                }
            }
        }
    }

    // 권한 확인 메서드
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 알림 권한
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_PERMISSION);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한 승인
                Toast.makeText(this, "권한이 승인되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                // 권한 거부
                Toast.makeText(this, "알림 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ServiceManager.ServiceListener 구현
    @Override
    public void onStatusUpdate(DownloadProgressInfo progressInfo) {
        updateUI(progressInfo);
    }

    // UI 업데이트
    private void updateUI(DownloadProgressInfo progress) {
        switch (progress.getStatus()) {
            case DownloadProgressInfo.STATUS_IDLE:
                progressBar.setVisibility(View.INVISIBLE);
                tvStatus.setText("대기 중");
                btnDownload.setText("다운로드");
                isDownloading = false;
                break;

            case DownloadProgressInfo.STATUS_CONNECTING:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                tvStatus.setText(progress.getStatusMessage());
                btnDownload.setText("취소");
                isDownloading = true;
                break;

            case DownloadProgressInfo.STATUS_DOWNLOADING:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progress.getProgress());
                tvStatus.setText(progress.getStatusMessage());
                btnDownload.setText("취소");
                isDownloading = true;
                break;

            case DownloadProgressInfo.STATUS_PAUSED:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progress.getProgress());
                tvStatus.setText(progress.getStatusMessage());
                btnDownload.setText("다운로드");
                isDownloading = false;
                break;

            case DownloadProgressInfo.STATUS_COMPLETED:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
                tvStatus.setText(progress.getStatusMessage());
                btnDownload.setText("다운로드");
                isDownloading = false;

                // 버전 업데이트(테스트용)
                currentVersion += 0.1;
                tvCurrentVersion.setText("현재 버전 ▶ " + String.format("%.1f", currentVersion));

                Toast.makeText(this, "다운로드 완료", Toast.LENGTH_SHORT).show();
                break;

            case DownloadProgressInfo.STATUS_FAILED:
                progressBar.setVisibility(View.INVISIBLE);
                tvStatus.setText(progress.getStatusMessage());
                btnDownload.setText("다운로드");
                isDownloading = false;

                Toast.makeText(this, progress.getStatusMessage(), Toast.LENGTH_SHORT).show();
                break;

            case DownloadProgressInfo.STATUS_CANCELLED:
                progressBar.setVisibility(View.INVISIBLE);
                tvStatus.setText(progress.getStatusMessage());
                btnDownload.setText("다운로드");
                isDownloading = false;

                Toast.makeText(this, "다운로드 취소됨", Toast.LENGTH_SHORT).show();
                break;
        }
    }
    // MainActivity에 추가
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1234);
            Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1234) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "오버레이 권한이 거부되었습니다", Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}