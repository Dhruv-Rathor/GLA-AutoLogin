package com.dhruv.glaautologin;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class CredentialManager {
    private static final String PREF_FILE = "gla_secure_creds";
    private static final String KEY_USER = "user_val";
    private static final String KEY_PASS = "pass_val";

    private final SharedPreferences prefs;

    public CredentialManager(Context context) {
        SharedPreferences tempPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            tempPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            tempPrefs = context.getSharedPreferences(PREF_FILE + "_fallback", Context.MODE_PRIVATE);
        }
        this.prefs = tempPrefs;
    }

    public void saveCredentials(String username, String password) {
        prefs.edit()
                .putString(KEY_USER, username.trim())
                .putString(KEY_PASS, password)
                .apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USER, null);
    }

    public String getPassword() {
        return prefs.getString(KEY_PASS, null);
    }

    public boolean hasCredentials() {
        String u = getUsername();
        String p = getPassword();
        return u != null && !u.isEmpty() && p != null && !p.isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
