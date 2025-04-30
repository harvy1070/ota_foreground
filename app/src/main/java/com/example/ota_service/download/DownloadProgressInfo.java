package com.example.ota_service.download;

//import android.os.FileUtils;
import android.os.Parcel;
import android.os.Parcelable;
import com.example.ota_service.utils.FileUtils;

import java.io.File;

// 다운로드 진행 상태 정보 저장용 클래스
// Activity와 Service간의 전달을 위해 Parcelable 구현
public class DownloadProgressInfo implements Parcelable {
    // 상태 코드 정의
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_DOWNLOADING = 2;
    public static final int STATUS_PAUSED = 3;
    public static final int STATUS_COMPLETED = 4;
    public static final int STATUS_FAILED = 5;
    public static final int STATUS_CANCELLED = 6;

    private int status;                     // 현재 상태
    private int progress;                   // 진행률 0 - 100
    private long downloadedBytes;           // 다운로드된 바이트 수
    private long totalBytes;                // 총 바이트 수
    private long speed;                     // 다운로드 속도 bytes/s
    private String errorMessage;            // 오류 메세지
    private long estimatedTimeRemaining;    // 남은 예상 시간(ms)

    // 기본 생성자
    public DownloadProgressInfo() {
        this.status = STATUS_IDLE;
        this.progress = 0;
        this.downloadedBytes = 0;
        this.totalBytes = 0;
        this.speed = 0;
        this.errorMessage = "";
        this.estimatedTimeRemaining = 0;
    }

    // 다운로드 시작 상태 생성
    public static DownloadProgressInfo createStarting() {
        DownloadProgressInfo info = new DownloadProgressInfo();
        info.status = STATUS_CONNECTING;
        info.errorMessage = "다운로드 준비 중...";
        return info;
    }

    // 다운로드 진행 상태 생성
    public static DownloadProgressInfo createDownloading(long downloaded, long total, long speed) {
        DownloadProgressInfo info = new DownloadProgressInfo();
        info.status = STATUS_DOWNLOADING;
        info.downloadedBytes = downloaded;
        info.totalBytes = total;
        info.speed = speed;

        // 진행률 계산
        if (total > 0) {
            info.progress = (int) (downloaded * 100 / total);
        }

        // 예상 시간 계산
        if (speed > 0) {
            info.estimatedTimeRemaining = (total - downloaded) * 1000 / speed;
        }

        return info;
    }

    // 다운로드 완료 상태 생성
    public static DownloadProgressInfo createCompleted(long fileSize, long duration) {
        DownloadProgressInfo info = new DownloadProgressInfo();
        info.status = STATUS_COMPLETED;
        info.progress = 100;
        info.downloadedBytes = fileSize;
        info.totalBytes = fileSize;
        info.errorMessage = "다운로드 완료 ▶ " + FileUtils.formatFileSize(fileSize) +
                " (소요 시간 ▶ " + FileUtils.formatDownloadTime(duration) + ")";

        return info;
    }

    // 다운로드 실패 상태 생성
    public static DownloadProgressInfo createFailed(String message) {
        DownloadProgressInfo info = new DownloadProgressInfo();
        info.status = STATUS_FAILED;
        info.errorMessage = "다운로드 실패 ▶ " + message;
        return info;
    }

    // 다운로드 취소 상태 생성
    public static DownloadProgressInfo createCancelled() {
        DownloadProgressInfo info = new DownloadProgressInfo();
        info.status = STATUS_CANCELLED;
        info.errorMessage = "다운로드 취소됨";
        return info;
    }

    // 상태 메시지 생성
    public String getStatusMessage() {
        switch (status) {
            case STATUS_IDLE:
                return "대기 중";
            case STATUS_CONNECTING:
                return errorMessage;
            case STATUS_DOWNLOADING:
                String speedStr = speed > 0 ?
                        FileUtils.formatFileSize(speed) + "/s" : "계산 중...";
                String timeStr = estimatedTimeRemaining > 0 ?
                        "남은 시간 ▶ " + FileUtils.formatDownloadTime(estimatedTimeRemaining) : "";

                return String.format("다운로드 진행 중 %d%% (%s / %s) - %s %s",
                        progress,
                        FileUtils.formatFileSize(downloadedBytes),
                        FileUtils.formatFileSize(totalBytes),
                        speedStr,
                        timeStr);
            case STATUS_PAUSED:
                return String.format("다운로드 일시 중단 ▶ %d%% (%s/%s)",
                        progress,
                        FileUtils.formatFileSize(downloadedBytes),
                        FileUtils.formatFileSize(totalBytes));
            case STATUS_COMPLETED:
            case STATUS_FAILED:
            case STATUS_CANCELLED:return errorMessage;
            default:return "알 수 없는 상태";
        }
    }

    // Getter 및 Setter
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage() {
        this.errorMessage = errorMessage;
    }

    public long getEstimatedTimeRemaining() {
        return estimatedTimeRemaining;
    }

    public void setEstimatedTimeRemaining(long estimatedTimeRemaining) {
        this.estimatedTimeRemaining = estimatedTimeRemaining;
    }

    // ParceLable 구현
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest,int flags) {
        dest.writeInt(status);
        dest.writeInt(progress);
        dest.writeLong(downloadedBytes);
        dest.writeLong(totalBytes);
        dest.writeLong(speed);
        dest.writeString(errorMessage);
        dest.writeLong(estimatedTimeRemaining);
    }

    protected DownloadProgressInfo(Parcel in) {
        status = in.readInt();
        progress = in.readInt();
        downloadedBytes = in.readLong();
        totalBytes = in.readLong();
        speed = in.readLong();
        errorMessage = in.readString();
        estimatedTimeRemaining = in.readLong();
    }

    public static final Creator<DownloadProgressInfo> CREATOR = new Creator<DownloadProgressInfo>() {
        @Override
        public DownloadProgressInfo createFromParcel(Parcel in) {
            return new DownloadProgressInfo(in);
        }

        @Override
        public DownloadProgressInfo[] newArray(int size) {
            return new DownloadProgressInfo[size];
        }
    };

}
