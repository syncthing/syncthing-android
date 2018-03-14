package com.nutomic.syncthingandroid.http;

import android.content.Context;
import android.net.Uri;

import com.android.volley.Request;
import com.google.common.collect.ImmutableMap;

import java.net.URL;

public class PostScanRequest extends ApiRequest {
    private static final String URI_SCAN = "/rest/db/scan";

    public PostScanRequest(Context context, URL url, String apiKey,
                           String folder, String sub) {
        super(context, url, URI_SCAN, apiKey);
        
        /* Prevent to post rooted sub to scanner rest api */
        /* Remove the leading slash for syncthing versions <= 0.14.45 */
        /* fixes #1032 */
        sub = sub.replaceAll("^/+", "");
        Uri uri = buildUri(ImmutableMap.of("folder", folder, "sub", sub));
        connect(Request.Method.POST, uri, null, null, null);
    }

}
