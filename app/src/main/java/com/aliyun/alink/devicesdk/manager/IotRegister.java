package com.aliyun.alink.devicesdk.manager;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.alibaba.fastjson.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 设备动态注册。
 * 使用OkHttp进行网络请求，支持异步操作
 * 单例模式实现
 */
public class IotRegister {
    private static final String TAG = "DynamicRegisterByHttps";
    private static final String HMAC_ALGORITHM = "hmacsha1";
    
    private static volatile IotRegister instance;
    private final OkHttpClient client;

    private IotRegister() {
        this.client = new OkHttpClient.Builder()
                .build();
    }

    /**
     * 获取单例实例
     */
    public static IotRegister getInstance() {
        if (instance == null) {
            synchronized (IotRegister.class) {
                if (instance == null) {
                    instance = new IotRegister();
                }
            }
        }
        return instance;
    }

    /**
     * 动态注册。
     *
     * @param productKey 产品key
     * @param productSecret 产品密钥
     * @param deviceName 设备名称
     * @param callback 注册回调接口
     */
    public void register(String productKey, String productSecret, String deviceName, RegisterCallback callback) {
        if (callback == null) {
            Log.w(TAG, "Callback is null, registration result will be ignored");
        }
        
        JSONObject params = createRegisterParams(productKey, productSecret, deviceName);
        RequestBody formBody = createFormBody(params);
        
        Request request = new Request.Builder()
                .url("http://iot.iwillcloud.com:10081/auth/register/device")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Register failed", e);
                if (callback != null) {
                    callback.onError(e);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (callback != null) {
                        callback.onError(new IOException("Unexpected response code: " + response.code()));
                    }
                    return;
                }

                String result = response.body().string();
                Log.d(TAG, "Register result: " + result);
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }
        });
    }

    /**
     * 注册回调接口
     */
    public interface RegisterCallback {
        /**
         * 注册成功回调
         * @param response 服务器响应
         */
        void onSuccess(String response);

        /**
         * 注册失败回调
         * @param e 异常信息
         */
        void onError(Exception e);
    }

    private JSONObject createRegisterParams(String productKey, String productSecret, String deviceName) {
        Random r = new Random();
        int random = r.nextInt(1000000);

        JSONObject params = new JSONObject();
        params.put("productKey", productKey);
        params.put("deviceName", deviceName);
        params.put("random", random);
        params.put("signMethod", HMAC_ALGORITHM);
        params.put("sign", sign(params, productSecret));

        return params;
    }

    private RequestBody createFormBody(JSONObject params) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (String key : params.keySet()) {
            formBuilder.add(key, params.getString(key));
        }
        return formBuilder.build();
    }

    private String sign(JSONObject params, String productSecret) {
        Set<String> keys = getSortedKeys(params);
        keys.remove("sign");
        keys.remove("signMethod");

        StringBuilder content = new StringBuilder();
        for (String key : keys) {
            content.append(key).append(params.getString(key));
        }

        String sign = encrypt(content.toString(), productSecret);
        Log.d(TAG, "Sign content: " + content);
        Log.d(TAG, "Sign result: " + sign);

        return sign;
    }

    private Set<String> getSortedKeys(JSONObject json) {
        SortedMap<String, String> map = new TreeMap<>();
        for (String key : json.keySet()) {
            String value = json.getString(key);
            map.put(key, value);
        }
        return map.keySet();
    }

    private String encrypt(String content, String secret) {
        try {
            byte[] text = content.getBytes(StandardCharsets.UTF_8);
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(key, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(secretKey.getAlgorithm());
            mac.init(secretKey);
            return byte2hex(mac.doFinal(text));
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    private String byte2hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; b != null && n < b.length; n++) {
            String stmp = Integer.toHexString(b[n] & 0XFF);
            if (stmp.length() == 1) {
                sb.append('0');
            }
            sb.append(stmp);
        }
        return sb.toString().toUpperCase();
    }
}