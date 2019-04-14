package com.nutomic.syncthingandroid.model;

import com.nutomic.syncthingandroid.R;

import java.util.Collections;
import java.util.List;

public class RunConditionCheckResult {

    public enum BlockerReason {
        ON_BATTERY(R.string.syncthing_disabled_reason_on_battery),
        ON_CHARGER(R.string.syncthing_disabled_reason_on_charger),
        POWERSAVING_ENABLED(R.string.syncthing_disabled_reason_powersaving),
        GLOBAL_SYNC_DISABLED(R.string.syncthing_disabled_reason_android_sync_disabled),
        WIFI_SSID_NOT_WHITELISTED(R.string.syncthing_disabled_reason_wifi_ssid_not_whitelisted),
        WIFI_WIFI_IS_METERED(R.string.syncthing_disabled_reason_wifi_is_metered),
        NO_NETWORK_OR_FLIGHTMODE(R.string.syncthing_disabled_reason_no_network_or_flightmode),
        NO_MOBILE_CONNECTION(R.string.syncthing_disabled_reason_no_mobile_connection),
        NO_WIFI_CONNECTION(R.string.syncthing_disabled_reason_no_wifi_connection),
        NO_ALLOWED_NETWORK(R.string.syncthing_disabled_reason_no_allowed_method),
        NOT_WITHIN_TIMEFRAME(R.string.syncthing_disabled_reason_not_within_timeframe);

        private final int resId;

        BlockerReason(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }

    public static final RunConditionCheckResult SHOULD_RUN = new RunConditionCheckResult();

    private final boolean mShouldRun;
    private final List<BlockerReason> mBlockReasons;

    /**
     * Use SHOULD_RUN instead.
     * Note: of course anybody could still construct it by providing an empty list to the other
     * constructor.
     */
    private RunConditionCheckResult() {
        this(Collections.emptyList());
    }

    public RunConditionCheckResult(List<BlockerReason> blockReasons) {
        mBlockReasons = Collections.unmodifiableList(blockReasons);
        mShouldRun = blockReasons.isEmpty();
    }


    public List<BlockerReason> getBlockReasons() {
        return mBlockReasons;
    }

    public boolean isShouldRun() {
        return mShouldRun;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RunConditionCheckResult that = (RunConditionCheckResult) o;

        if (mShouldRun != that.mShouldRun) return false;
        return mBlockReasons.equals(that.mBlockReasons);
    }

    @Override
    public int hashCode() {
        int result = (mShouldRun ? 1 : 0);
        result = 31 * result + mBlockReasons.hashCode();
        return result;
    }
}
