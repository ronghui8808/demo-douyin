package com.example.douyin.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesFactory;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Single;

public final class TokenStore {

    private static final String STORE_NAME = "douyin_auth";
    private static final String LEGACY_PREFS = "douyin_auth";
    private static final Preferences.Key<String> KEY_TOKEN = PreferencesKeys.stringKey("token");
    private static final Preferences.Key<Long> KEY_USER_ID = PreferencesKeys.longKey("user_id");

    private static volatile TokenStore instance;

    private final Context appContext;
    private final RxDataStore<Preferences> dataStore;
    private final TokenMemoryCache cache = new TokenMemoryCache();
    private final AtomicBoolean hydrated = new AtomicBoolean(false);

    private TokenStore(Context context) {
        appContext = context.getApplicationContext();
        dataStore = new RxPreferenceDataStoreBuilder(appContext, STORE_NAME).build();
    }

    public static TokenStore get(Context context) {
        if (instance == null) {
            synchronized (TokenStore.class) {
                if (instance == null) {
                    instance = new TokenStore(context);
                }
            }
        }
        return instance;
    }

    /** 异步加载 DataStore（及旧 SharedPreferences 迁移）到内存缓存。 */
    public void hydrate(@Nullable Runnable onReady) {
        migrateLegacyIfNeeded();
        dataStore.data().firstOrError()
                .onErrorReturnItem(PreferencesFactory.createEmpty())
                .subscribe(prefs -> {
                    String token = prefs.get(KEY_TOKEN);
                    Long userId = prefs.get(KEY_USER_ID);
                    cache.set(token, userId != null ? userId : -1L);
                    hydrated.set(true);
                    if (onReady != null) {
                        onReady.run();
                    }
                }, error -> {
                    hydrated.set(true);
                    if (onReady != null) {
                        onReady.run();
                    }
                });
    }

    public boolean isHydrated() {
        return hydrated.get();
    }

    public void saveToken(String token, long userId) {
        cache.set(token, userId);
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(KEY_TOKEN, token);
            mutablePreferences.set(KEY_USER_ID, userId);
            return Single.just(mutablePreferences);
        }).subscribe(p -> {}, e -> {});
    }

    @Nullable
    public String getToken() {
        return cache.getToken();
    }

    public long getUserId() {
        return cache.getUserId();
    }

    public boolean isLoggedIn() {
        return cache.isLoggedIn();
    }

    public void clear() {
        cache.clear();
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.remove(KEY_TOKEN);
            mutablePreferences.remove(KEY_USER_ID);
            return Single.just(mutablePreferences);
        }).subscribe(p -> {}, e -> {});
    }

    private void migrateLegacyIfNeeded() {
        SharedPreferences legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        String legacyToken = legacy.getString("token", null);
        if (TextUtils.isEmpty(legacyToken)) {
            return;
        }
        long legacyUserId = legacy.getLong("user_id", -1L);
        cache.set(legacyToken, legacyUserId);
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(KEY_TOKEN, legacyToken);
            mutablePreferences.set(KEY_USER_ID, legacyUserId);
            return Single.just(mutablePreferences);
        }).subscribe(p -> legacy.edit().clear().apply(), e -> {});
    }
}
