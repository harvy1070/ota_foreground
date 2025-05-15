package com.example.ota_service.download;

import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;

import com.example.ota_service.model.DownloadState;
import com.example.ota_service.model.DownloadStateManager;
import com.example.ota_service.network.ConnectionManager;
import com.example.ota_service.download.DownloadTask;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager implements DownloadTask.DownloadTaskListener {
    private static final String TAG = DownloadManager.class.getSimpleName();
    private static final String DOWNLOAD_URL = "https://s3.ap-southeast-2.amazonaws.com/avn.directed.kr/firmware/TEST/random_file_1GB.bin";

    private final Context context;
    private final File downloadDir;
    private final DownloadStateManager stateManager;
    private final ConnectionManager connectionManager;

    private File downloadFile;
    private File tempFile;
    private DownloadTask downloadTask;
    private DownloadManagerListener listener;

    private long downloadStartTime;
    private DownloadState currentState;
    private DownloadProgressInfo progressInfo;

    private ExecutorService executorService;

    /**
     * DownloadManager 생성자
     *
     * @param context 앱 컨텍스트
     * @param downloadDir 다운로드 디렉토리
     */
    public DownloadManager(Context context, File downloadDir) {
        this.context = context;
        this.downloadDir = downloadDir;

        // 네트워크 연결 관리자 초기화
        this.connectionManager = new ConnectionManager();

        // 파일 경로 및 이름 설정
        downloadFile = new File(downloadDir, "update.bin");
        tempFile = new File(downloadDir, "update.bin.tmp");

        // 다운로드 상태 관리자 초기화
        stateManager = new DownloadStateManager(tempFile);

        // 현재 상태 및 진행 정보 초기화
        currentState = new DownloadState();
        progressInfo = new DownloadProgressInfo();

        // ExecutorService 초기화
        executorService = Executors.newSingleThreadExecutor();
    }

    // 다운로드 매니저 리스너 설정
    public void setListener(DownloadManagerListener listener) {
        this.listener = listener;
    }

    // 이전 다운로드 확인
    public DownloadProgressInfo checkPreviousDownload() {
        DownloadState state = stateManager.loadState();
        if (state != null && state.getDownloadedBytes() > 0 && state.getTotalBytes() > 0 &&
        tempFile.exists() && tempFile.length() == state.getDownloadedBytes()) {
            // 현재 상태 업데이트
            currentState = state;

            int progress = state.getProgress();

            // 진행 정보 생성
            progressInfo = new DownloadProgressInfo();
            progressInfo.setStatus(DownloadProgressInfo.STATUS_PAUSED);
            progressInfo.setProgress(progress);
            progressInfo.setDownloadedBytes(state.getDownloadedBytes());
            progressInfo.setTotalBytes(state.getTotalBytes());

            Log.d(TAG, "이전 다운로드 파일 발견 ▶ " + state.getDownloadedBytes() + "/" + state.getTotalBytes());

            return progressInfo;
        }
        return null;
    }

    // 다운로드 시작
    public void startDownload() {
        if (isDownloading()) {
            return;
        }

        downloadStartTime = System.currentTimeMillis();

        // 진행 정보 초기화
        progressInfo = DownloadProgressInfo.createStarting();

        // 리스너 알림
        if (listener != null) {
            listener.onStatusChanged(progressInfo);
        }

        try {
            // 현재 다운로드 상태 가져오기
            DownloadState state = stateManager.loadState();
            if (state == null) {
                state = new DownloadState();
                state.setDownloadId(UUID.randomUUID().toString());
            }
            currentState = state;

            // 이미 다운로드된 바이트 수 확인
            long downloadedBytes = 0;
            if (tempFile.exists()) {
                downloadedBytes = tempFile.length();
                Log.d(TAG, "이전 다운로드 내역 확인 ▶ " + downloadedBytes + " bytes");
            }

            // 다운로드 작업 초기화
            downloadTask = new DownloadTask(
                    connectionManager,
                    tempFile,
                    downloadFile
            );
            downloadTask.setListener(this);

            // 다운로드 작업 실행
            executeDownload(state, downloadedBytes);
        } catch (Exception e) {
            Log.e(TAG, "다운로드 시작 중 예외 상황 발생함", e);

            // 실패 상태 업데이트
            progressInfo = DownloadProgressInfo.createFailed(e.getMessage());
            if  (listener != null) {
                listener.onStatusChanged(progressInfo);
            }
        }
    }

    /**
     * 다운로드 작업 실행
     *
     * @param state 다운로드 상태 객체
     * @param downloadedBytes 이미 다운로드된 바이트 수
     */
    private void executeDownload(final DownloadState state, final long downloadedBytes) {
        executorService.execute(() -> {
            boolean success = downloadTask.startDownload(DOWNLOAD_URL, downloadedBytes, state);

            if (success) {
                stateManager.clearState();
                currentState.setCompleted(true);
            } else if (downloadTask.isDownloading()) {
                // 상태 저장
                saveDownloadState();
            }
        });
    }

    // 리소스 해제
    public void shutdown() {
        if (isDownloading()) {
            cancelDownload();
        }

        // Executor 종료
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // 다운로드 상태 저장
    public void saveDownloadState() {
        if (tempFile.exists() && (isDownloading() || currentState.getDownloadedBytes() > 0)) {
            long currentSize = tempFile.length();

            if (currentState.getTotalBytes() > 0) {
                currentState.setDownloadedBytes(currentSize);
                stateManager.saveState(currentState);

                Log.d(TAG, "다운로드 상태 저장 ▶ " + currentSize + "/" + currentState.getTotalBytes());
            }
        }
    }

    // 다운로드 상태 취소
    public void cancelDownload() {
        if (isDownloading()) {
            if (downloadTask != null) {
                downloadTask.cancelDownload();
            }
        }
    }

    // 다운로드 상태 확인
    // true = 다운로드 진행 중, false = 다운로드 중 아님
    public boolean isDownloading() {
        return downloadTask != null && downloadTask.isDownloading();
    }

    // 현재 진행 정보 반환
    public DownloadProgressInfo getCurrentProgress() {
        return progressInfo;
    }

    // DownloadTaskListener 구현
    @Override
    public void onStart(long totalBytes, long downloadedBytes) {
        currentState.setTotalBytes(totalBytes);
        currentState.setDownloadedBytes(downloadedBytes);

        // 진행 정보 업데이트
        progressInfo = new DownloadProgressInfo();
        progressInfo.setStatus(DownloadProgressInfo.STATUS_DOWNLOADING);
        progressInfo.setTotalBytes(totalBytes);
        progressInfo.setDownloadedBytes(downloadedBytes);
        progressInfo.setProgress((int)(downloadedBytes * 100 / totalBytes));

        // 리스너 알림
        if (listener != null) {
            listener.onStatusChanged(progressInfo);
        }
    }

    @Override
    public void onProgress(long currentBytes, long totalBytes, long speed) {
        currentState.setDownloadedBytes(currentBytes);

        // 30초마다 혹은 10% 진행 마다 상태 저장
        if (currentBytes % (totalBytes / 10) < 1024 * 1024) { // 약 1MB 단위로 체크함
            saveDownloadState();
        }

        // 진행 정보 업데이트
        progressInfo = DownloadProgressInfo.createDownloading(currentBytes, totalBytes, speed);

        // 리스너 알림
        if (listener != null) {
            listener.onStatusChanged(progressInfo);
        }
    }

    @Override
    public void onComplete(long fileSize) {
        // 소요 시간 계산
        long downloadEndTime = System.currentTimeMillis();
        long downloadDuration = downloadEndTime - downloadStartTime;

        // 상태 업데이트
        currentState.setCompleted(true);
        currentState.setDownloadedBytes(fileSize);
        currentState.setTotalBytes(fileSize);

        // 진행 정보 업데이트
        progressInfo = DownloadProgressInfo.createCompleted(fileSize, downloadDuration);

        // 상태 정보 삭제
        stateManager.clearState();

        // 리스너 알림
        if (listener != null) {
            listener.onStatusChanged(progressInfo);
        }
    }

    @Override
    public void onFailure(String errorMessage) {
        // 실패 정보 업데이트
        progressInfo = DownloadProgressInfo.createFailed(errorMessage);

        // 상태 저장 (재시도 가능하도록)
        saveDownloadState();

        // 리스너 알림
        if (listener != null) {
            listener.onStatusChanged(progressInfo);
        }
    }

    @Override
    public void onCancelled() {
        // 취소 정보 업데이트
        progressInfo = DownloadProgressInfo.createCancelled();

        // 상태 저장 추가(이어받기용도), (5. 12.)
        saveDownloadState();

        // 리스너 알림
        if (listener != null) {
            listener.onStatusChanged(progressInfo);
        }
    }

    // 다운로드 매니저 리스너 인터페이스
    public interface DownloadManagerListener {
        void onStatusChanged(DownloadProgressInfo progress);
    }
}
