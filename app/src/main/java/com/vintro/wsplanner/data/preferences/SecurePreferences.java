package com.vintro.wsplanner.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.vintro.wsplanner.utils.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

// lightweight hardware-backed encrypted shared preferences using android keystore (aes-256-gcm)
public class SecurePreferences implements SharedPreferences {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "wsplanner_master_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96-bit IV standard for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag

    private static volatile SecretKey cachedSecretKey;

    private final SharedPreferences delegate;
    private final Map<String, CachedEntry> decryptedCache = new ConcurrentHashMap<>();

    private static final class CachedEntry {
        final String encrypted;
        final String decrypted;

        CachedEntry(String encrypted, String decrypted) {
            this.encrypted = encrypted;
            this.decrypted = decrypted;
        }
    }

    private SecurePreferences(SharedPreferences delegate) {
        this.delegate = delegate;
    }

    // create or return secure preferences instance
    public static SecurePreferences create(Context context, String name) throws GeneralSecurityException, IOException {
        ensureKeyExists();
        SharedPreferences delegate = context.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
        return new SecurePreferences(delegate);
    }

    // ensure aes master key exists in android keystore
    private static synchronized void ensureKeyExists() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build();
            keyGenerator.init(spec);
            keyGenerator.generateKey();
            Logger.i("SecurePreferences.generateKey", "Generated new hardware-backed AES-256 master key in AndroidKeyStore");
        }
    }

    public static void clearKeyCache() {
        cachedSecretKey = null;
    }

    private static SecretKey getKey() throws GeneralSecurityException, IOException {
        SecretKey key = cachedSecretKey;
        if (key != null) {
            return key;
        }
        synchronized (SecurePreferences.class) {
            if (cachedSecretKey == null) {
                KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
                keyStore.load(null);
                cachedSecretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            return cachedSecretKey;
        }
    }

    // encrypt plaintext using aes-gcm with random iv
    private String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            SecretKey key = getKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            cachedSecretKey = null;
            Logger.e("SecurePreferences.encrypt", "Encryption failed: " + Logger.formatException(e));
            return null;
        } catch (Exception e) {
            Logger.e("SecurePreferences.encrypt", "Encryption failed: " + Logger.formatException(e));
            return null;
        }
    }

    // decrypt base64 payload extracting iv and ciphertext
    private String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null) return null;
        try {
            byte[] combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP);
            if (combined == null || combined.length < GCM_IV_LENGTH) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            SecretKey key = getKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            cachedSecretKey = null;
            Logger.e("SecurePreferences.decrypt", "Decryption failed: " + Logger.formatException(e));
            return null;
        } catch (Exception e) {
            Logger.e("SecurePreferences.decrypt", "Decryption failed: " + Logger.formatException(e));
            return null;
        }
    }

    @Override
    public Map<String, ?> getAll() {
        Map<String, ?> all = delegate.getAll();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof String) {
                String decrypted = decrypt((String) entry.getValue());
                result.put(entry.getKey(), decrypted != null ? decrypted : entry.getValue());
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        String encrypted = delegate.getString(key, null);
        if (encrypted == null) {
            decryptedCache.remove(key);
            return defValue;
        }
        CachedEntry cached = decryptedCache.get(key);
        if (cached != null && encrypted.equals(cached.encrypted)) {
            return cached.decrypted;
        }
        String decrypted = decrypt(encrypted);
        if (decrypted != null) {
            decryptedCache.put(key, new CachedEntry(encrypted, decrypted));
            return decrypted;
        }
        return defValue;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return delegate.getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        String val = getString(key, null);
        if (val == null) return defValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        String val = getString(key, null);
        if (val == null) return defValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        String val = getString(key, null);
        if (val == null) return defValue;
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        String val = getString(key, null);
        if (val == null) return defValue;
        return Boolean.parseBoolean(val);
    }

    @Override
    public boolean contains(String key) {
        return delegate.contains(key);
    }

    @Override
    public Editor edit() {
        return new SecureEditor(delegate.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        delegate.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener);
    }

    // editor that encrypts string values before committing to delegate preferences
    private class SecureEditor implements Editor {
        private final Editor delegateEditor;

        SecureEditor(Editor delegateEditor) {
            this.delegateEditor = delegateEditor;
        }

        @Override
        public Editor putString(String key, @Nullable String value) {
            if (value == null) {
                delegateEditor.remove(key);
                decryptedCache.remove(key);
            } else {
                String encrypted = encrypt(value);
                if (encrypted != null) {
                    delegateEditor.putString(key, encrypted);
                    decryptedCache.put(key, new CachedEntry(encrypted, value));
                }
            }
            return this;
        }

        @Override
        public Editor putStringSet(String key, @Nullable Set<String> values) {
            delegateEditor.putStringSet(key, values);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            return putString(key, String.valueOf(value));
        }

        @Override
        public Editor putLong(String key, long value) {
            return putString(key, String.valueOf(value));
        }

        @Override
        public Editor putFloat(String key, float value) {
            return putString(key, String.valueOf(value));
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            return putString(key, String.valueOf(value));
        }

        @Override
        public Editor remove(String key) {
            delegateEditor.remove(key);
            decryptedCache.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            delegateEditor.clear();
            decryptedCache.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return delegateEditor.commit();
        }

        @Override
        public void apply() {
            delegateEditor.apply();
        }
    }
}
