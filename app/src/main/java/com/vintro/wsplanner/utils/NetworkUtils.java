package com.vintro.wsplanner.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

// network connectivity helper and shared http client factory
public class NetworkUtils {
    private static volatile OkHttpClient sharedHttpClient;

    // return a shared base okHttpClient so callers reuse one connection pool and dispatcher
    public static OkHttpClient getSharedHttpClient() {
        OkHttpClient client = sharedHttpClient;
        if (client == null) {
            synchronized (NetworkUtils.class) {
                if (sharedHttpClient == null) {
                    sharedHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .build();
                }
                client = sharedHttpClient;
            }
        }
        return client;
    }

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
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                     capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            );
            if (!available) {
                Logger.d("NetworkUtils.isNetworkAvailable", "Network has no internet transport capability, device is offline");
            }
            return available;
        } catch (Exception e) {
            Logger.e("NetworkUtils.isNetworkAvailable", "Exception checking network availability: " + Logger.formatException(e));
            return false;
        }
    }
}
