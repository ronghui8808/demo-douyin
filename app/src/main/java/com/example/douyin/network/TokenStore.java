package com.example.douyin.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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

    /**
     * 异步加载 DataStore（及旧 SharedPreferences 迁移）到内存缓存。
     * 有 legacy token 时先 {@code updateDataAsync} 迁移，用其结果填充缓存后再清 legacy；
     * 无 legacy 时读 DataStore，经 {@link #shouldApplyHydratedToken} 策略写入缓存，避免空 prefs 覆盖有效内存 token。
     */
    public void hydrate(@Nullable Runnable onReady) {
        SharedPreferences legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        String legacyToken = legacy.getString("token", null);

        if (!TextUtils.isEmpty(legacyToken)) {
            long legacyUserId = legacy.getLong("user_id", -1L);
            // 迁移完成前拦截器即可读到 token
            cache.set(legacyToken, legacyUserId);
            dataStore.updateDataAsync(prefsIn -> {
                MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
                String existing = prefsIn.get(KEY_TOKEN);
                if (TextUtils.isEmpty(existing)) {
                    mutablePreferences.set(KEY_TOKEN, legacyToken);
                    mutablePreferences.set(KEY_USER_ID, legacyUserId);
                }
                return Single.just(mutablePreferences);
            }).subscribe(prefs -> {
                applyHydratedPreferences(prefs);
                legacy.edit().clear().apply();
                finishHydrate(onReady);
            }, error -> finishHydrate(onReady));
            return;
        }

        dataStore.data().firstOrError()
                .onErrorReturnItem(PreferencesFactory.createEmpty())
                .subscribe(prefs -> {
                    applyHydratedPreferences(prefs);
                    finishHydrate(onReady);
                }, error -> finishHydrate(onReady));
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

    /**
     * Hydrate 写缓存策略：DataStore 有非空 token 时写入；DS 为空时仅当内存也为空才写入（避免擦掉 legacy / 并发 saveToken）。
     */
    @VisibleForTesting
    static boolean shouldApplyHydratedToken(@Nullable String cacheToken, @Nullable String dsToken) {
        boolean cacheHas = cacheToken != null && !cacheToken.isEmpty();
        boolean dsHas = dsToken != null && !dsToken.isEmpty();
        if (dsHas) {
            return true;
        }
        return !cacheHas;
    }

    private void applyHydratedPreferences(Preferences prefs) {
        String dsToken = prefs.get(KEY_TOKEN);
        if (!shouldApplyHydratedToken(cache.getToken(), dsToken)) {
            return;
        }
        Long userId = prefs.get(KEY_USER_ID);
        cache.set(dsToken, userId != null ? userId : -1L);
    }

    private void finishHydrate(@Nullable Runnable onReady) {
        hydrated.set(true);
        if (onReady != null) {
            onReady.run();
        }
    }
}
