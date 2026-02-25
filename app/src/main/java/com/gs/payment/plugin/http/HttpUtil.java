package com.gs.payment.plugin.http;

/**
 * Created by LiBaoZhi
 * on 2025/7/3
 */

import java.util.Map;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class HttpUtil {

    //异步get 拼接字符串
    public static void asyncGet(String url, String data, Callback callback) {
        OkHttpClient client = OkHttpClientSingle.getClientInstance();
        Request.Builder builder = new Request.Builder().url(url + "?" + data);
        Request request = builder.build();
        client.newCall(request).enqueue(callback);
    }

    //异步 POST
    public static void asyncPostJson(String url, Map<String, String> headers, String json, Callback callback) {
        OkHttpClient client = OkHttpClientSingle.getClientInstance();
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder().url(url).post(body);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        client.newCall(builder.build()).enqueue(callback);
    }
}

