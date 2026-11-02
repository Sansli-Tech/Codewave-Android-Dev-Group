package com.example.mustudentsync.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // Set in local.properties as api.base.url (see app/build.gradle):
    //   USB phone:       http://localhost:5000/api/   (after: adb reverse tcp:5000 tcp:5000)
    //   Emulator:        http://10.0.2.2:5000/api/
    //   Hosted backend:  https://your-service.onrender.com/api/   (must be https)
    public static final String BASE_URL = com.example.mustudentsync.BuildConfig.API_BASE_URL;

    private static Retrofit retrofit;

    public static synchronized Retrofit getInstance() {
        if (retrofit == null) {
            // BASIC logs method, URL and status in Logcat (filter by tag "OkHttp").
            // Not BODY: the body of a sign-in request contains the password, and the
            // Authorization header carries the session token. Neither belongs in a log.
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            logging.redactHeader("Authorization");

            // A free hosted server sleeps when idle and takes up to a minute to wake,
            // so the first request is given time. A dead server still fails in about a minute.
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static ApiService getApiService() {
        return getInstance().create(ApiService.class);
    }
}
