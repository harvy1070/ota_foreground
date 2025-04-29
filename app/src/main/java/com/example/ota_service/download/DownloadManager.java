package com.example.ota_service.download;

import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;

import com.example.ota_service.model.DownloadState;
import com.example.ota_service.model.DownloadStateManager;
import com.example.ota_service.network.ConnectionManager;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {
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
    public DownloadManager(Context context File downloadDir) {
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
}
