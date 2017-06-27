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

    public static final String URI_CONFIG      = "/rest/system/config";
    public static final String URI_VERSION     = "/rest/system/version";
    public static final String URI_SYSTEM      = "/rest/system/status";
    public static final String URI_CONNECTIONS = "/rest/system/connections";
    public static final String URI_MODEL       = "/rest/db/status";
    public static final String URI_DEVICEID    = "/rest/svc/deviceid";
    public static final String URI_REPORT      = "/rest/svc/report";
    public static final String URI_EVENTS      = "/rest/events";

    public ImageGetRequest(Context context, URL url, String path, String httpsCertPath, String apiKey,
                           @Nullable Map<String, String> params, OnImageSuccessListener listener) {
        super(context, url, path, httpsCertPath, apiKey);
        Map<String, String> safeParams = Optional.fromNullable(params).or(Collections.emptyMap());
        Uri uri = buildUri(safeParams);
        connect(Request.Method.GET, uri, null, null, listener, null);
    }
}
