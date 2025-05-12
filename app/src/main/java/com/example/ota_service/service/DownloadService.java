package com.example.ota_service.service;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.ota_service.download.DownloadManager;
import com.example.ota_service.download.DownloadProgressInfo;
import com.example.ota_service.ui.DownloadStatusView;
import com.example.ota_service.utils.NotificationUtils;

import java.io.File;

// OTA Download를 위한 Foreground Service 구축 부분
public class DownloadService extends Service implements DownloadManager.DownloadManagerListener {
    private static final String TAG = DownloadService.class.getSimpleName();

    // 인텐트 액션 정의 부분
    public static final String ACTION_START_DOWNLOAD = "com.example.ota_service.START_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "com.example.ota_service.CANCEL_DOWNLOAD";
    public static final String ACTION_REQUEST_STATUS = "com.example.ota_service.REQUEST_STATUS";
    public static final String ACTION_SET_FOREGROUND_STATE = "SET_FOREGROUND_STATE";

    private DownloadManager downloadManager;
    private NotificationManagerCompat notificationManager;
    private boolean isServiceRunning = false;

    private DownloadStatusView floatingView;

    // 활성화 여부
    private boolean isAppInForeground = true;

    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "서비스 생성");

        // Handler 추가 5. 12.
        mainHandler = new Handler(Looper.getMainLooper());

        // 알림 채널 생성
        NotificationUtils.createNotificationChannel(this);
        notificationManager = NotificationManagerCompat.from(this);

        // 다운로드 매니저 초기화
        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        downloadManager = new DownloadManager(this, downloadDir);
        downloadManager.setListener(this);

        isServiceRunning = true;

        // 플로팅 뷰 초기화(5. 9.)
        // 플로팅 뷰 수정(5. 12.)
        floatingView = new DownloadStatusView(this);
        floatingView.setListener(new DownloadStatusView.DownloadStatusListener() {
            @Override
            public void onDownloadClicked() {
                if (!downloadManager.isDownloading()) {
                    downloadManager.startDownload();
                }
            }

            @Override
            public void onCancelClicked() {
                if (downloadManager.isDownloading()) {
                    downloadManager.cancelDownload();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "서비스 시작 명령 - Action ▶ " + (intent != null ? intent.getAction() : "null"));

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_DOWNLOAD:
                    handleStartDownload();
                    break;

                case ACTION_CANCEL_DOWNLOAD:
                    handleCancelDownload();
                    break;

                case ACTION_REQUEST_STATUS:
                    handleRequestStatus();
                    break;
                case ACTION_SET_FOREGROUND_STATE:
                    isAppInForeground = intent.getBooleanExtra("isInForeground", true);
                    Log.d(TAG, "포그라운드 상태 변경 ▶ " + isAppInForeground);
                    break;
            }
        } else {
            // 서비스가 시스템에 의해 재시작된 경우
            restoreDownloadState();
        }

        // 서비스가 종료되면 자동으로 다시 시작 (Intent 포함)
        return START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "서비스 종료");

        // 플로팅 뷰 제거
        if (floatingView != null) {
            floatingView.hide();
        }

        // 다운로드가 진행 중이면 상태 저장
        if (downloadManager != null && downloadManager.isDownloading()) {
            downloadManager.saveDownloadState();
        }

        // 리소스 정리
        if (downloadManager != null) {
            downloadManager.shutdown();
        }

        isServiceRunning = false;
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "앱이 최근 목록에서 제거됨");

        // 앱이 제거되면 백그라운드 상태로 변경(5. 12.)
        isAppInForeground = false;

        // 다운로드가 진행 중이면 상태 저장
        if (downloadManager != null && downloadManager.isDownloading()) {
            downloadManager.saveDownloadState();
        }

        super.onTaskRemoved(rootIntent);
    }

    // 다운로드 시작 처리
    private void handleStartDownload() {
        if (downloadManager != null && !downloadManager.isDownloading()) {
            // Foreground 서비스로 시작
            startForegroundWithNotification(DownloadProgressInfo.createStarting());

            // 다운로드 시작
            downloadManager.startDownload();
        }
    }

    // 다운로드 취소 처리
    private void handleCancelDownload() {
        if (downloadManager != null && downloadManager.isDownloading()) {
            downloadManager.cancelDownload();
        }
    }

    // 상태 요청 처리
    private void handleRequestStatus() {
        if (downloadManager != null) {
            DownloadProgressInfo progress = downloadManager.getCurrentProgress();
            broadcastProgressUpdate(progress);

            // 다운로드 중이던 Foreground 서비스 유지
            if (downloadManager.isDownloading()) {
                updateNotification(progress);
            }
        }
    }

    // 다운로드 상태 복원
    private void restoreDownloadState() {
        if (downloadManager != null) {
            DownloadProgressInfo previousState = downloadManager.checkPreviousDownload();

            if (previousState != null) {
                Log.d(TAG, "이전 다운로드 상태 복원");
                broadcastProgressUpdate(previousState);

                // 사용자에게 복원된 상태 알림
                updateNotification(previousState);
            }
        }
    }

    // Foreground 서비스 시작
    private void startForegroundWithNotification(DownloadProgressInfo progress) {
        Notification notification = NotificationUtils.createDownloadNotification(
                this, progress.getProgress(), progress.getStatusMessage()
        );
        startForeground(NotificationUtils.NOTIFICATION_ID, notification);
    }

    // 알림 업데이트
    private void updateNotification(DownloadProgressInfo progress) {
        switch (progress.getStatus()) {
            case DownloadProgressInfo.STATUS_DOWNLOADING:
                Notification progressNotification = NotificationUtils.createDownloadNotification(
                        this, progress.getProgress(), progress.getStatusMessage()
                );
                notificationManager.notify(NotificationUtils.NOTIFICATION_ID, progressNotification);
                break;

            case DownloadProgressInfo.STATUS_COMPLETED:
                // 다운로드 완료 알림
                Notification completeNotification = NotificationUtils.createCompletionNotification(
                        this, progress.getStatusMessage()
                );
                notificationManager.notify(NotificationUtils.NOTIFICATION_ID, completeNotification);

                // Foreground 서비스 종료
                stopForeground(false);
                stopSelf();
                break;

            case DownloadProgressInfo.STATUS_FAILED:
                // 실패 알림
                Notification failureNotification = NotificationUtils.createFailureNotification(
                        this, progress.getStatusMessage()
                );
                notificationManager.notify(NotificationUtils.NOTIFICATION_ID, failureNotification);

                // Foreground 서비스 종료
                stopForeground(false);
                stopSelf();
                break;

            case DownloadProgressInfo.STATUS_CANCELLED:
                // Foreground 서비스 종료
                stopForeground(false);
                stopSelf();
                break;
        }
    }
    
    // 상태 업데이트 브로드캐스트
    private void broadcastProgressUpdate(DownloadProgressInfo progress) {
        Intent intent = new Intent(ServiceManager.ACTION_STATUS_UPDATE);
        intent.putExtra(ServiceManager.EXTRA_PROGRESS_INFO, progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    // 다운로드매니저 리스너 구현 파트
    @Override
    public void onStatusChanged(DownloadProgressInfo progress) {
        // 상태 브로드캐스트
        broadcastProgressUpdate(progress);

        // 알림 업데이트
        updateNotification(progress);

        // 플로팅 뷰 업데이트(5. 9.) - 메인 스레드에서 실행
        // Foreground 사용으로 수정(5. 12.)
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAppInForeground) {
                    if (progress.getStatus() == DownloadProgressInfo.STATUS_DOWNLOADING) {
                        if (!floatingView.isShowing()) {
                            Log.d(TAG, "플로팅 뷰 표시 - 백그라운드 상태");
                            floatingView.show();
                        }
                        floatingView.updateStatus(progress);
                    } else if (progress.getStatus() == DownloadProgressInfo.STATUS_COMPLETED ||
                            progress.getStatus() == DownloadProgressInfo.STATUS_FAILED ||
                            progress.getStatus() == DownloadProgressInfo.STATUS_CANCELLED) {
                        floatingView.hide();
                    }
                } else {
                    // 포그라운드면 플로팅 뷰 숨김
                    if (floatingView.isShowing()) {
                        Log.d(TAG, "플로팅 뷰 숨김 - 포그라운드 상태");
                        floatingView.hide();
                    }
                }
            }
        });
    }

    // 상태 감지 메서드 추가 5. 12.
    private void registerActivityLifecycleCallbacks() {
        ((Application) getApplication()).registerActivityLifecycleCallbacks(
            new Application.ActivityLifecycleCallbacks() {
                private int activityReferences = 0;

                @Override
                public void onActivityStarted(Activity activity) {
                    if (++activityReferences == 1) {
                        // 포그라운드로 전환시킴
                        isAppInForeground = true;
                        if (floatingView != null && floatingView.isShowing()) {
                            floatingView.hide();
                        }
                    }
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    if (--activityReferences == 0) {
                        // 앱이 백그라운드로 전환됨
                        isAppInForeground = false;
                        if (downloadManager.isDownloading() && floatingView != null) {
                            floatingView.show();
                        }
                    }
                }

                // 나머지 콜백 메서드는 빈 구현
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
                @Override
                public void onActivityResumed(Activity activity) {}
                @Override
                public void onActivityPaused(Activity activity) {}
                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override
                public void onActivityDestroyed(Activity activity) {}
            }
        );
    }
}
