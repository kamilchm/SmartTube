package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class ChannelBlacklistService {
    private static final String TAG = ChannelBlacklistService.class.getSimpleName();
    private static ChannelBlacklistService sInstance;
    private final Context mContext;
    private final MainUIData mMainUIData;
    private final OkHttpClient mOkHttpClient;

    private ChannelBlacklistService(Context context) {
        mContext = context.getApplicationContext();
        mMainUIData = MainUIData.instance(mContext);
        mOkHttpClient = new OkHttpClient();
    }

    public static ChannelBlacklistService instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelBlacklistService(context);
        }
        return sInstance;
    }

    public void fetchBlacklist() {
        String url = mMainUIData.getChannelBlacklistUrl();
        if (url == null || url.isEmpty()) {
            Log.d(TAG, "Blacklist URL is empty. Clearing in-memory blacklist.");
            mMainUIData.updateInMemoryChannelBlacklist(new HashSet<>());
            return;
        }

        Observable.fromCallable(() -> {
            Request request = new Request.Builder().url(url).build();
            try (Response response = mOkHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "Failed to fetch blacklist. HTTP code: " + response.code());
                    return null;
                }

                Set<String> blacklist = new HashSet<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String channelId = line.trim();
                        if (!channelId.isEmpty() && !channelId.startsWith("#")) { // Ignore empty lines and comments
                            blacklist.add(channelId);
                        }
                    }
                }
                return blacklist;
            } catch (Exception e) {
                Log.e(TAG, "Error fetching or parsing blacklist: " + e.getMessage());
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.computation()) // Computation scheduler for set operations
        .subscribe(blacklist -> {
            if (blacklist != null) {
                Log.d(TAG, "Successfully fetched and parsed blacklist. Size: " + blacklist.size());
                mMainUIData.updateInMemoryChannelBlacklist(blacklist);
            } else {
                // Optionally, handle fetch failure more gracefully, e.g., keep old list or clear it
                Log.d(TAG, "Failed to fetch blacklist. Keeping previous list (if any) or clearing.");
                // Decide if we should clear or keep: mMainUIData.updateInMemoryChannelBlacklist(new HashSet<>());
            }
        }, throwable -> {
            Log.e(TAG, "Exception during blacklist fetch observable: " + throwable.getMessage());
        });
    }
}
