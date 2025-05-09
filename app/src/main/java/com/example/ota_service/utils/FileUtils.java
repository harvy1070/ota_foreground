package com.example.ota_service.utils;

public class FileUtils {
    // 파일 크기를 사람이 읽기 쉬운 형태로 변환(B, KB, GB, MB) 기존 코드와 같음
    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // 시간을 사람이 읽기 쉬운 형식으로 변환함(ms -> h:m:s)
    public static String formatDownloadTime(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d시간 %d분 %d초", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds % 60);
        } else {
            return String.format("%.1f초", millis / 1000.0);
        }
    }

    // 남은 예상 시간 계산
    public static String formatEstimatedTimeRemaining(long totalBytes, long downloadedBytes, long bytesPerSecond) {
        if (bytesPerSecond <= 0) return "계산 진행 중...";

        long remainingBytes = totalBytes - downloadedBytes;
        long estimatedMillis = (remainingBytes * 1000) / bytesPerSecond;

        return formatDownloadTime(estimatedMillis);
    }
}
