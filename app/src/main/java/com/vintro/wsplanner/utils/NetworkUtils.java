package com.vintro.wsplanner.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

// network connectivity helper utilities
public class NetworkUtils {
    // check if device has active internet-capable network connection
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                Logger.w("NetworkUtils.isNetworkAvailable", "ConnectivityManager is null, assuming offline");
                return false;
            }
            Network network = cm.getActiveNetwork();
            if (network == null) {
                Logger.d("NetworkUtils.isNetworkAvailable", "No active network, device is offline");
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            boolean available = capabilities != null && (
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            );
            if (!available) {
                Logger.d("NetworkUtils.isNetworkAvailable", "Network has no internet transport capability, device is offline");
            }
            return available;
        } catch (Exception e) {
            Logger.e("NetworkUtils.isNetworkAvailable", "Exception checking network availability: " + e.getMessage());
            return false;
        }
    }
}
