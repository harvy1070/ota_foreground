package com.example.ota_service.download;

import android.util.Log;

import com.example.ota_service.model.DownloadState;
import com.example.ota_service.network.ConnectionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;

import okhttp3.CipherSuite;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

// 다운로드 작업 처리 클래스
public class DownloadTask {
    private static final String TAG = DownloadTask.class.getSimpleName();

    private final ConnectionManager connectionManager;
    private final File tempFile;
    private final File downloadFile;

    private boolean isDownloading = false;
    private long lastProgressUpdateTime = 0;
    private long lastBytesDownloaded = 0;
    private DownloadTaskListener listener;

    /**
     * DownloadTask 생성자
     *
     * @param connectionManager 네트워크 연결 관리
     * @param tempFile 임시 저장 파일
     * @param downloadFile 최종 다운로드 파일
     */
    public DownloadTask(ConnectionManager connectionManager, File tempFile, File downloadFile) {
        this.connectionManager = connectionManager;
        this.tempFile = tempFile;
        this.downloadFile = downloadFile;
    }

    // 다운로드 태스크 리스너 설정
    public void setListener(DownloadTaskListener listener) {
        this.listener = listener;
    }

    /**
     * 다운로드 작업 영역
     *
     * @param url 다운로드할 파일의 URL
     * @param downloadedBytes 이미 다운로드된 바이트 수
     * @param state 다운로드 상태 객체
     * @return 다운로드 성공 여부 반환
     */
    public boolean startDownload(String url, long downloadedBytes, DownloadState state) {
        isDownloading = true;
        lastProgressUpdateTime = System.currentTimeMillis();
        lastBytesDownloaded = downloadedBytes;

        try {
            // 서버 가용성 확인
            if (!connectionManager.isServerAvailable(url)) {
                if (listener != null) {
                    listener.onFailure("서버에 연결할 수 없습니다.");
                }
                return false;
            }

            // 서버에 연결
            Response response = connectionManager.connect(url, downloadedBytes);

            if(!response.isSuccessful()) {
                if (listener != null) {
                    listener.onFailure("서버 오류 ▶ " + response.code());
                }
                return false;
            }

            // HTTPS 연결 정보 로깅
            logConnectionInfo(response);

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                if (listener != null) {
                    listener.onFailure("응답 데이터가 없음");
                }
                return false;
            }

            // 전체 파일 크기 확인
            long totalBytes = getTotalBytes(response, responseBody, downloadedBytes);

            // 상태 업데이트
            state.setTotalBytes(totalBytes);
            state.setDownloadedBytes(downloadedBytes);

            // 다운로드 시작 로그
            Log.d(TAG, "다운로드 시작 ... 총 파일 크기 ▶ " + totalBytes + ", 기존 다운로드 ▶ " + downloadedBytes);

            // 시작 알림
            if (listener != null) {
                listener.onStart(totalBytes, downloadedBytes);
            }

            // 파일 다운로드 및 저장
            if (!downloadFile(responseBody, totalBytes, downloadedBytes)) {
                return false;
            }

            // 파일 이름 변경(임시 -> 최종)
            finalizeDownload();

            return true;
        } catch (IOException e) {
            Log.e(TAG, "다운로드 중 오류 발생", e);
            if (listener != null) {
                listener.onFailure(e.getMessage());
            }
            return false;
        } finally {
            isDownloading = false;
        }
    }

    // 다운로드 취소 시
    public void cancelDownload() {
        isDownloading = false;
        if (listener != null) {
            listener.onCancelled();
        }
    }

    // 현재 상태 체크
    public boolean isDownloading() {
        return isDownloading;
    }

    // 연결 정보 로깅
    public void logConnectionInfo(Response response) {
        String protocol = response.protocol().toString();
        String cipher = response.handshake() != null ?
                response.handshake().cipherSuite().toString() : "알 수 없음";

        Log.d(TAG, "HTTP 연결 성공");
        Log.d(TAG, "프로토콜 ▶ " + protocol);
        Log.d(TAG, "암호화 스위트 ▶ " + cipher);
    }

    // 전체 파일 크기 확인 영역
    private long getTotalBytes(Response response, ResponseBody responseBody, long downloadBytes) {
        long totalBytes;
        if (response.code() == 206) {
            String contentRange = response.header("Content-Range");
            if (contentRange != null && contentRange.startsWith("bytes ")) {
                String[] parts = contentRange.substring(6).split("/");
                if (parts.length == 2) {
                    totalBytes = Long.parseLong(parts[1]);
                } else {
                    totalBytes = downloadBytes + responseBody.contentLength();
                }
            } else {
                totalBytes = downloadBytes + responseBody.contentLength();
            }
        } else {
            totalBytes = downloadBytes + responseBody.contentLength();
            //  새 다운로드인 경우 이전 임시 파일 삭제
            if (tempFile.exists()) {
                tempFile.delete();
                downloadBytes = 0;
            }
        }
        return totalBytes;
    }

    // 파일 다운로드 및 저장
    private boolean downloadFile(ResponseBody responseBody, long totalBytes, long downloadedBytes) throws IOException {
        BufferedSink sink = null;
        try {
            // 이어 쓰기 모드로 파일 엶
            sink = OKio.buffer(Okio.appendingSink(tempFile));

            // 버퍼 설정
            Buffer buffer = new Buffer();
            long bytesReadThisSession = 0;
            int bufferSize = 8 * 1024; // 8kb

            // 스트리밍 방식으로 다운로드 진행
            BufferedSource source = responseBody.source();

            // 다운로드 속도 계산용 변수 설정
            long currentTime;
            long timeDifference;
            long downloadSpeed;

            while (isDownloading) {
                long read = source.read(buffer, bufferSize);
                if (read == -1) break;

                sink.write(buffer, read);
                bytesReadThisSession += read;
                long totalBytesDownloaded = downloadedBytes + bytesReadThisSession;

                // 속도 계산(1초마다 혹은 10%마다)
                currentTime = System.currentTimeMillis();
                timeDifference = currentTime - lastProgressUpdateTime;

                if (timeDifference >= 1000 || (totalBytes > 0 && (totalBytesDownloaded * 100 / totalBytes)
                        >= (lastBytesDownloaded * 100 / totalBytes) + 10)) {
                    // 다운로드 속도 계산 (bytes/second)
                    downloadSpeed = (totalBytesDownloaded - lastBytesDownloaded) * 1000 / Math.max(timeDifference, 1);

                    // 진행 상황 업데이트
                    if (listener != null) {
                        listener.onProgress(totalBytesDownloaded, totalBytes, downloadSpeed);
                    }

                    // 마지막 업데이트 시간 및 다운로드 크기 갱신
                    lastProgressUpdateTime = currentTime;
                    lastBytesDownloaded = totalBytesDownloaded;
                }
            }

            // 다운로드 취소 확인
            if (!isDownloading) {
                Log.d(TAG, "다운로드 취소됨");
                return false;
            }

            sink.flush();
            return true;
        } finally {
            if (sink != null) {
                try {
                    sink.close();
                } catch (IOException e) {
                    Log.e(TAG, "리소스 정리 오류", e);
                }
            }
            responseBody.close()
        }
    }

    // 다운로드 완료 후 파일 이름 변경
    private void finalizeDownload() throws IOException {
        // 임시 파일을 실제 파일로 이동
        if (downloadFile.exists()) {
            downloadFile.delete();
        }

        if (!tempFile.renameTo(downloadFile)) {
            throw new IOException("파일 이름 변경 실패");
        }

        Log.d(TAG, "다운로드 완료, 파일 저장 위치 ▶ " + downloadFile.getAbsolutePath());
        Log.d(TAG, "파일 크기 ▶ " + downloadFile.length());

        if (listener != null) {
            listener.onComplete(downloadFile.length());
        }
    }

    // 다운로드 태스크 리스너 인터페이스
    public interface DownloadTaskListener {
        void onStart(long totalBytes, long downloadedBytes);
        void onProgress(long currentBytes, long totalBytes, long speed);
        void onComplete(long filesize);
        void onFailure(String errorMessage);
        void onCancelled();
    }
}
