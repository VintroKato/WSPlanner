package com.vintro.wsplanner.enums;

import androidx.annotation.StringRes;
import com.vintro.wsplanner.R;

// classification of lesson location: campus, online, or off-site
public enum LocationType {
    UCZELNIA(R.string.location_uczelnia),
    ONLINE(R.string.location_online),
    W_TERENIE(R.string.location_w_terenie);

    @StringRes
    private final int stringResId;

    LocationType(@StringRes int stringResId) {
        this.stringResId = stringResId;
    }

    @StringRes
    public int getStringResId() {
        return stringResId;
    }
}
