package com.example.ota_service.service;

import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.ota_service.download.DownloadManager;
import com.example.ota_service.download.DownloadProgressInfo;

// Activity - Service 간 통신을 관리하는 클래스
public class ServiceManager {
    // 브로드캐스트 액션 정의
    public static final String ACTION_STATUS_UPDATE = "com.example.ota_service.STATUS_UPDATE";
    public static final String EXTRA_PROGRESS_INFO = "progress_info";

    private final Context context;
    private final ServiceListener listener;
    private final BroadcastReceiver statusReceiver;
    private boolean receiverRegistered = false;

    /**
     * Service Manager 생성자
     *
     * @param context 애플리케이션 컨텍스트
     * @param listener 서비스 이벤트 리스너
     */
    public ServiceManager(Context context, ServiceListener listener) {
        this.context = context;
        this.listener = listener;

        // 브로드캐스트 리시버 초기화
        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_STATUS_UPDATE.equals(intent.getAction())) {
                    Bundle extras = intent.getExtras();
                    if (extras != null && extras.containsKey(EXTRA_PROGRESS_INFO)) {
                        DownloadProgressInfo progressInfo = extras.getParcelable(EXTRA_PROGRESS_INFO);
                        if (listener != null && progressInfo != null) {
                            listener.onStatusUpdate(progressInfo);
                        }
                    }
                }
            }
        };
    }

    // 서비스 연결 시작
    public void connect() {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_STATUS_UPDATE);
            LocalBroadcastManager.getInstance(context).registerReceiver(statusReceiver, filter);
            receiverRegistered = true;
        }

        // 서비스에 현재 상태 요청
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(DownloadService.ACTION_REQUEST_STATUS);
        context.startService(intent);

        // 서비스에 포그라운드 상태 알림(5. 12.)
        Intent foregroundIntent = new Intent(context, DownloadService.class);
        foregroundIntent.setAction(DownloadService.ACTION_SET_FOREGROUND_STATE); // 수정
        foregroundIntent.putExtra("isInForeground", true);
        context.startService(foregroundIntent);
    }

    // 서비스 연결 종료
    public void disconnect() {
        // 서비스에 백그라운드 상태 알림(5. 12.)
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(DownloadService.ACTION_SET_FOREGROUND_STATE); // 수정
        intent.putExtra("isInForeground", false);
        context.startService(intent);

        if (receiverRegistered) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
    }

    // 다운로드 시작
    public void startDownload() {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(DownloadService.ACTION_START_DOWNLOAD);
        context.startService(intent);
    }

    // 취소
    public void cancelDownload() {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(DownloadService.ACTION_CANCEL_DOWNLOAD);
        context.startService(intent);
    }

    // 확인
    public void checkServiceStatus() {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(DownloadService.ACTION_REQUEST_STATUS);
        context.startService(intent);
    }

    // 서비스 리스너 인터페이스
    public interface ServiceListener {
        void onStatusUpdate(DownloadProgressInfo progressInfo);
    }
}
