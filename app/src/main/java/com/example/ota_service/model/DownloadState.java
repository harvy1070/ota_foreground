package com.example.ota_service.model;

public class DownloadState {
    private String downloadId;      // 고유 ID
    private long downloadedBytes;   // 다운로드 바이트 수 체크
    private long totalBytes;        // 총 파일 크기
    private long lastUpdateTime;    // 마지막 업데이트 시간
    private boolean isCompleted;    // 완료 여부
    private boolean isCancelled;    // 취소 여부

    // 기본 생성자 시작
    public DownloadState() {
        this.downloadId = "";
        this.downloadedBytes = 0;
        this.totalBytes = 0;
        this.lastUpdateTime = System.currentTimeMillis();
        this.isCompleted = false;
        this.isCancelled = false;
    }

    // Download ID 반환
    public String getDownloadId() {
        return downloadId;
    }

    // Download ID 설정
    public void setDownloadId(String downloadId) {
        this.downloadId = downloadId;
    }

    // 현재까지 다운로드된 바이트 수 반환
    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    // Download Bytes 수 설정
    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // 전체 파일 크기 반환
    public long getTotalBytes() {
        return totalBytes;
    }

    // 전체 파일 크기 설정
    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    // 마지막 업데이트 시간 반환
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    // 완료 여부 반환
    public boolean isCompleted() {
        return isCompleted;
    }

    // 완료 여부 설정
    public void setCompleted(boolean completed) {
        isCompleted = completed;
        if (completed) {
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    // 취소 여부 반환
    public boolean isCancelled() {
        return isCancelled;
    }

    // 취소 여부 설정
    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
        if (cancelled) {
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    // 다운로드 진행 상태 반환
    public int getProgress() {
        if (totalBytes <= 0) return 0;
        return (int) (downloadedBytes * 100 / totalBytes);
    }
}
