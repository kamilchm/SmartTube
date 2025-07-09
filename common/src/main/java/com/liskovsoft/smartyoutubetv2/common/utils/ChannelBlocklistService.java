package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BlocklistRule;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class ChannelBlocklistService {
    private static final String TAG = ChannelBlocklistService.class.getSimpleName();
    private static ChannelBlocklistService sInstance;
    private final Context mContext;
    private final MainUIData mMainUIData;
    private final OkHttpClient mOkHttpClient;

    private ChannelBlocklistService(Context context) {
        mContext = context.getApplicationContext();
        mMainUIData = MainUIData.instance(mContext);
        mOkHttpClient = new OkHttpClient();
    }

    public static ChannelBlocklistService instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelBlocklistService(context);
        }
        return sInstance;
    }

    public void fetchBlocklist() {
        String url = mMainUIData.getChannelBlocklistUrl();
        if (url == null || url.isEmpty()) {
            Log.d(TAG, "Blocklist URL is empty. Clearing in-memory blocklist.");
            mMainUIData.updateInMemoryChannelBlocklist(new ArrayList<>()); // Store List<BlocklistRule>
            return;
        }

        Observable.fromCallable(() -> {
            Request request = new Request.Builder().url(url).build();
            try (Response response = mOkHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "Failed to fetch blocklist. HTTP code: " + response.code());
                    return null;
                }

                List<BlocklistRule> rules = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty() || line.trim().startsWith("#")) { // Ignore empty lines and comments
                            continue;
                        }
                        BlocklistRule rule = BlocklistRule.fromString(line.trim());
                        if (rule != null) {
                            rules.add(rule);
                        } else {
                            Log.w(TAG, "Malformed rule found and ignored: " + line);
                        }
                    }
                }
                return rules;
            } catch (Exception e) {
                Log.e(TAG, "Error fetching or parsing blocklist: " + e.getMessage());
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.computation())
        .subscribe(rules -> {
            if (rules != null) {
                Log.i(TAG, "Successfully fetched and parsed blocklist. Rule count: " + rules.size());
                mMainUIData.updateInMemoryChannelBlocklist(rules);
            } else {
                Log.w(TAG, "Failed to fetch or parse blocklist.");
                if (!mMainUIData.getInMemoryChannelBlocklist().isEmpty()) {
                    Log.i(TAG, "Using previously cached blocklist. Rule count: " + mMainUIData.getInMemoryChannelBlocklist().size());
                } else {
                    Log.i(TAG, "No cached blocklist available. Blocklist will be empty.");
                    // Ensure it's empty if the first fetch fails and we want to be certain
                    mMainUIData.updateInMemoryChannelBlocklist(new ArrayList<>());
                }
            }
        }, throwable -> {
            Log.e(TAG, "Exception during blocklist fetch observable: " + throwable.getMessage());
            // Similar logic for exceptions if needed, though current setup leads to 'rules' being null
            if (!mMainUIData.getInMemoryChannelBlocklist().isEmpty()) {
                Log.i(TAG, "Exception during fetch. Using previously cached blocklist. Rule count: " + mMainUIData.getInMemoryChannelBlocklist().size());
            } else {
                Log.i(TAG, "Exception during fetch. No cached blocklist available. Blocklist will be empty.");
                 mMainUIData.updateInMemoryChannelBlocklist(new ArrayList<>());
            }
        });
    }
}
