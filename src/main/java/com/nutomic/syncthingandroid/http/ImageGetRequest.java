package com.nutomic.syncthingandroid.http;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.android.volley.Request;
import com.google.common.base.Optional;

import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * Created by jmintb on 27-06-17.
 */

public class ImageGetRequest extends ApiRequest {

    public static String QR_CODE_GENERATOR = "/qr/";

    public ImageGetRequest(Context context, URL url, String path, String httpsCertPath, String apiKey,
                           @Nullable Map<String, String> params, OnImageSuccessListener onSuccessListener, OnErrorListener onErrorListener) {
        super(context, url, path, httpsCertPath, apiKey);
        Map<String, String> safeParams = Optional.fromNullable(params).or(Collections.emptyMap());
        Uri uri = buildUri(safeParams);
        makeImageRequest(uri, onSuccessListener, onErrorListener);
    }
}
