package com.example.ota_service.network;

import android.util.Log;

import com.example.ota_service.R;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

// HTTP 통신 처리용 네트워크 클래스
public class ConnectionManager {
    private static final String TAG = ConnectionManager.class.getSimpleName();
    private final OkHttpClient client;

    // ConnectionManager 생성자
    public ConnectionManager() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.v(TAG, "OKHTTP ▶ " + message));
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        // Okhttp 클라이언트 생성 - 타임아웃 설정
        client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * 서버에 연결해서 응답을 받아오는 메서드
     *
     * @param url 연결할 url
     * @param rangeStart 이어받기를 위한 시작 위치(0이면 처음부터)
     * @return 서버 응답
     * @throws java.io.IOException 연결 오류 발생 시
     */

    public Response connect(String url, long rangeStart) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);

        // range 헤더 추가
        if (rangeStart > 0) {
            requestBuilder.addHeader("Range", "bytes=" + rangeStart + "-");
            Log.d(TAG, "이어받기 요청 ▶ " + rangeStart + " 바이트부터");
        }

        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    /**
     * HEAD 요청을 보내 파일 크기 등의 정보 확인
     *
     * @param url 확인할 URL
     * @return 서버 응답
     * @throws IOException 연결 오류 발생 시
     */

    public Response checkFileInfo(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();
        return client.newCall(request).execute();
    }

    /**
     * 서버 상태 및 가용성 체크(Availability)
     *
     * @param url 체크할 URL
     * @return 서버가 정상이면 true, 아니면 false
     */

    public boolean isServerAvailable(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head() // HEAD 요청
                    .build();

            Response response = client.newCall(request).execute();
            boolean isSuccess = response.isSuccessful();
            response.close();

            return isSuccess;
        } catch (IOException e) {
            Log.e(TAG, "서버 연결 확인 중 오류", e);
            return false;
        }
    }

    /**
     * 네트워크 연결 상태 확인
     *
     * @param url 체크할 URL
     * @return 응답 코드(200:정상, 이외 오류)
     */
    public int checkNetworkStatus(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .build();

            Response response = client.newCall(request).execute();
            int code = response.code();
            response.close();

            return code;
        } catch (IOException e) {
            Log.e(TAG, "네트워크 상태 확인 중 오류 발생", e);
            return -1; // 연결 실패(-1 반환)
        }
    }

    /**
     * OkHttpClient 인스턴스 반환
     *
     * @return OkHttpClient 인스턴스
     */
    public OkHttpClient getClient() {
        return client;
    }
}
