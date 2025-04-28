package com.example.ota_service.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.ota_service.MainActivity;
import com.example.ota_service.service.DownloadService;

public class NotificationUtils {
    public static final String CHANNEL_ID = "download_channel";
    public static final int NOTIFICATION_ID = 1;

    // 알림 채널 생성
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.0) {
            CharSequence name = "다운로드 알림";
            String description = "OTA 다운로드 진행 상황을 표시합니다";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 다운로드 진행 알림 생성
     *
     * @param context 애플리케이션 컨텍스트
     * @param progress 진행률(0-100)
     * @param contentText 알림 내용
     * @return 생성된 알림 객체
     */
    public static Notification createDownloadNotification(Context context, int progress, String contentText) {
        // MainActivity로 이동하는 Intent
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        // 다운로드 취소 Intent
        Intent cancelIntent = new Intent(context, DownloadService.class);
        cancelIntent.setAction(DownloadService.ACTION_CANCEL_DOWNLOAD);
        PendingIntent cancelPendingIntent = PendingIntent.getService(
                context,
                0,
                cancelIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        // 알림 빌더 설정
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("OTA 다운로드")
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true);

        // 취소 액션 추가
        builder.addAction(android.R.drawable.ic_delete, "취소", cancelPendingIntent);

        // 진행 상황 표시
        if (progress >= 0) {
            builder.setProgress(100, progress, false);
        }

        return builder.build();
    }

    // 다운로드 완료 알림 생성
    public static Notification createCompletionNotification(Context context, String contentText) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("다운로드 완료")
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
    }

    // 다운로드 실패 알림 생성
    public static Notification createFailureNotification(Context context, String contentText) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("다운로드 실패")
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
    }
}
