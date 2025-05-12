package com.example.ota_service.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.telephony.mbms.DownloadStatusListener;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.ota_service.R;
import com.example.ota_service.download.DownloadProgressInfo;
import com.example.ota_service.network.ConnectionManager;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.AttributedCharacterIterator;

// 다운로드 상태 표시하는 플로팅 뷰 컴포넌트
public class DownloadStatusView extends LinearLayout {
    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnAction;

    private DownloadStatusListener listener;
    private boolean isDownloading = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;

    // 뷰 이동을 위한 변수
    private float initialX, initialY;
    private float initialTouchX, initialTouchY;

    public DownloadStatusView(Context context) {
        super(context);
        init(context);
    }

    public DownloadStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DownloadStatusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // XML 레이아웃 inflate
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.view_download_status, this, true);

        // UI 요소 초기화
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        btnAction = findViewById(R.id.btnAction);

        // 버튼 클릭 리스너 설정
        btnAction.setOnClickListener(v -> {
            if (listener != null) {
                if (isDownloading) {
                    listener.onCancelClicked();
                } else {
                    listener.onDownloadClicked();
                }
            }
        });

        // 윈도우 매니저 초기화
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        // 레이아웃 파라미터 설정
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        // 드래그 이동 처리
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (initialX + (event.getRawX() - initialTouchX));
                        params.y = (int) (initialY + (event.getRawY() - initialTouchY));
                        if (isShowing) {
                            windowManager.updateViewLayout(DownloadStatusView.this, params);
                        }
                        return true;
                    default:return false;
                }
            }
        });
    }

    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    // 플로팅 뷰 표시
    public void show() {
        if (!isShowing) {
            try {
                windowManager.addView(this, params);
                isShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 플로팅 뷰 숨기기
    public void hide() {
        if (isShowing) {
            try {
                windowManager.removeView(this);
                isShowing = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // isShowing 상태 확인 메서드
    public boolean isShowing() {
        return isShowing;
    }

    // 다운로드 상태 업데이트
    public void updateStatus(DownloadProgressInfo progressInfo) {
        switch (progressInfo.getStatus()) {
            case DownloadProgressInfo.STATUS_IDLE:
                progressBar.setVisibility(View.INVISIBLE);
                tvStatus.setText("대기 중");
                btnAction.setText("다운로드");
                isDownloading = false;
                break;

            case DownloadProgressInfo.STATUS_CONNECTING:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                tvStatus.setText(progressInfo.getStatusMessage());
                btnAction.setText("취소");
                isDownloading = true;
                break;

            case DownloadProgressInfo.STATUS_DOWNLOADING:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progressInfo.getProgress());
                tvStatus.setText(progressInfo.getStatusMessage());
                btnAction.setText("취소");
                isDownloading = true;
                break;

            case DownloadProgressInfo.STATUS_PAUSED:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progressInfo.getProgress());
                tvStatus.setText(progressInfo.getStatusMessage());
                btnAction.setText("다운로드");
                isDownloading = false;
                break;

            case DownloadProgressInfo.STATUS_COMPLETED:
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
                tvStatus.setText(progressInfo.getStatusMessage());
                btnAction.setText("다운로드");
                isDownloading = false;
                break;

            case DownloadProgressInfo.STATUS_FAILED:
                progressBar.setVisibility(View.INVISIBLE);
                tvStatus.setText(progressInfo.getStatusMessage());
                btnAction.setText("다운로드");
                isDownloading = false;
                break;

            case DownloadProgressInfo.STATUS_CANCELLED:
                progressBar.setVisibility(View.INVISIBLE);
                tvStatus.setText(progressInfo.getStatusMessage());
                btnAction.setText("다운로드");
                isDownloading = false;
                break;
        }
    }

    // 리스너 설정
    public void setListener(DownloadStatusListener listener) {
        this.listener = listener;
    }

    // 다운로드 상태 뷰 리스너 인터페이스
    public interface DownloadStatusListener {
        void onDownloadClicked();
        void onCancelClicked();
    }
}
