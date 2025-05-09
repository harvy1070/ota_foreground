package com.example.ota_service.model;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

// 다운로드 상태 저장 및 복원들 담당하는 클래스
public class DownloadStateManager {
    private static final String TAG = DownloadStateManager.class.getSimpleName();
    private final File tempFile;
    private final File stateFile;

    // DownloadStateManager 생성자, tempfile은 임시경로를 설정하도록 함
    public DownloadStateManager(File tempFile) {
        this.tempFile = tempFile;
        this.stateFile = new File(tempFile.getParentFile(), "download_state.dat");
    }

    // 다운로드 상태를 파일에 저장
    public synchronized void saveState(DownloadState state) {
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;

        try {
            SerializableDownloadState serializableState = new SerializableDownloadState(
                    state.getDownloadId(),
                    state.getDownloadedBytes(),
                    state.getTotalBytes(),
                    state.getLastUpdateTime(),
                    state.isCompleted(),
                    state.isCancelled()
            );

            fos = new FileOutputStream(stateFile);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(serializableState);

            Log.d(TAG, "다운로드 상태 저장 완료 ▶ " + state.getDownloadedBytes() + "/" + state.getTotalBytes());
        } catch (IOException e) {
            Log.e(TAG, "다운로드 상태 저장 중 오류 발생" , e);
        } finally {
            try {
                if (oos != null) oos.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                Log.e(TAG, "리소스 정리 중 오류 발생", e);
            }
        }
    }

    // 저장된 다운로드 상태 로드 시작, 실패하면 Null 반환
    public synchronized DownloadState loadState() {
        // 임시 파일이 없다면 상태 정보도 의미가 없기에 Null 처리
        if (!tempFile.exists()) {
            return null;
        }

        // 상태 파일 없으면 null
        if (!stateFile.exists()) {
            return null;
        }

        FileInputStream fis = null;
        ObjectInputStream ois = null;

        try {
            fis = new FileInputStream(stateFile);
            ois = new ObjectInputStream(fis);

            SerializableDownloadState serializableState = (SerializableDownloadState) ois.readObject();
            DownloadState state = new DownloadState();
            state.setDownloadId(serializableState.downloadId);
            state.setDownloadedBytes(serializableState.downloadedBytes);
            state.setTotalBytes(serializableState.totalBytes);
            // 추가 필드 복원
            state.setCompleted(serializableState.isCompleted);
            state.setCancelled(serializableState.isCancelled);

            // 임시 파일 크기와 저장된 크기가 다르면 파일 손상으로 간주
            if (tempFile.length() != state.getDownloadedBytes()) {
                Log.w(TAG, "임시 파일 크기가 불일치함 ▶ " + tempFile.length() +
                        ", 저장된 크기 ▶ " + state.getDownloadedBytes());
                return null;
            }

            Log.d(TAG, "다운로드 상태 로드 완료 ▶ " + state.getDownloadedBytes() + "/" + state.getTotalBytes());
            return state;
        } catch (Exception e) {
            Log.e(TAG, "다운로드 상태 로드 중 오류 발생", e);
            return null;
        } finally {
            try {
                if (ois != null) ois.close();
                if (fis != null) fis.close();
            } catch (IOException e) {
                Log.e(TAG, "리소스 정리 중 오류 발생", e);
            }
        }
    }

    // 저장된 다운로드 상태 삭제
    public synchronized void clearState() {
        if (stateFile.exists() && stateFile.delete()) {
            Log.d(TAG, "다운로드 상태 파일 삭제 완료");
        }
    }

    // 직렬화를 위한 내부 클래스
    private static class SerializableDownloadState implements Serializable {
        private static final long serialVersionUID = 2L; // 버전 증가

        String downloadId;
        long downloadedBytes;
        long totalBytes;
        long lastUpdateTime;
        boolean isCompleted;
        boolean isCancelled;

        SerializableDownloadState(String downloadId, long downloadedBytes, long totalBytes,
                                  long lastUpdateTime, boolean isCompleted, boolean isCancelled) {
            this.downloadId = downloadId;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.lastUpdateTime = lastUpdateTime;
            this.isCompleted = isCompleted;
            this.isCancelled = isCancelled;
        }
    }
}
