package com.gs.payment.plugin.http;

/**
 * Created by LiBaoZhi
 * on 2025/7/3
 */

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class OkHttpClientSingle {
    private static volatile OkHttpClient clientInstance;

    private OkHttpClientSingle() {
    }

    public static OkHttpClient getClientInstance() {
        if (clientInstance == null) {
            synchronized (OkHttpClientSingle.class) {
                if (clientInstance == null) {
                    clientInstance = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return clientInstance;
    }
}

