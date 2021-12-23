package com.nutomic.syncthingandroid.views;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.PermissionUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * MultiSelectListPreference which allows the user to select on which WiFi networks (based on SSID)
 * syncing should be allowed.
 *
 * Setting can be "All networks" (none selected), or selecting individual networks.
 *
 * Due to restrictions in Android, it is possible/likely, that the list of saved WiFi networks
 * cannot be retrieved if the WiFi is turned off. In this case, an explanation is shown.
 *
 * The preference is stored as Set&lt;String&gt; where an empty set represents
 * "all networks allowed".
 *
 * SSIDs are formatted according to the naming convention of WifiManager, i.e. they have the
 * surrounding double-quotes (") for UTF-8 names, or they are hex strings (if not quoted).
 */
public class WifiSsidPreference extends MultiSelectListPreference {

    public WifiSsidPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDefaultValue(new TreeSet<String>());
    }

    public WifiSsidPreference(Context context) {
        this(context, null);
    }

    /**
     * Show the dialog if WiFi is available and configured networks can be loaded.
     * Otherwise will display a Toast requesting to turn on WiFi.
     *
     * On opening of the dialog, will also remove any SSIDs from the set that have been removed
     * by the user in the WiFi manager. This change will be persisted only if the user changes
     * any other setting
     */
    @Override
    protected void showDialog(Bundle state) {
        Context context = getContext();

        Set<String> selected = getSharedPreferences().getStringSet(getKey(), new HashSet<>());
        // from JavaDoc: Note that you must not modify the set instance returned by this call.
        // therefore required to make a defensive copy of the elements
        selected = new HashSet<>(selected);
        List<String> all = new ArrayList<>(selected);

        boolean connected = false;
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null) {
                String ssid = info.getSSID();
                // api lvl 30 will have WifiManager.UNKNOWN_SSID
                if (ssid != null && ssid != "" && !ssid.contains("unknown ssid")) {
                    if (!selected.contains(ssid)) {
                        all.add(ssid);
                    }
                    connected = true;
                }
            }
        }

        boolean hasPerms = hasLocationPermissions();
        if (!connected) {
            if (!hasPerms) {
                Toast.makeText(context, R.string.sync_only_wifi_ssids_need_to_grant_location_permission, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, R.string.sync_only_wifi_ssids_connect_to_wifi, Toast.LENGTH_LONG).show();
            }
        }

        if (all.size() > 0 ) {
            setEntries(stripQuotes(all)); // display without surrounding quotes
            setEntryValues(all.toArray(new CharSequence[all.size()])); // the value of the entry is the SSID "as is"
            setValues(selected); // the currently selected values (without meanwhile deleted networks)
            super.showDialog(state);
        }

        if (!hasPerms && context instanceof Activity) {
            Activity activity = (Activity) context;
            ActivityCompat.requestPermissions(activity, PermissionUtil.getLocationPermissions(), Constants.PermissionRequestType.LOCATION.ordinal());
        }
    }

    /**
     * Checks if the required location permissions to obtain WiFi SSID are granted.
     */
    private boolean hasLocationPermissions() {
        String[] perms = PermissionUtil.getLocationPermissions();
        for (int i = 0; i < perms.length; i++) {
            if (ContextCompat.checkSelfPermission(getContext(), perms[i]) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a copy of the given WiFi SSIDs with quotes stripped.
     *
     * @param ssids the list of ssids to strip quotes from
     */
    private CharSequence[] stripQuotes(List<String> ssids) {
        CharSequence[] result = new CharSequence[ssids.size()];
        for (int i = 0; i < ssids.size(); i++) {
            result[i] = ssids.get(i).replaceFirst("^\"", "").replaceFirst("\"$", "");
        }
        return result;
    }

}
