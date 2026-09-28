package com.example.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BiometricSecurityManager {
    private const val KEY_ALIAS = "hexapass_biometric_vault_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "hexapass_biometric_prefs"
    private const val PREF_KEY_ENCRYPTED_KEY = "enc_vault_key"
    private const val PREF_KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    fun canAuthenticate(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(PREF_KEY_BIOMETRIC_ENABLED, false) && prefs.getString(PREF_KEY_ENCRYPTED_KEY, null) != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val keyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (keyEntry != null) {
                return keyEntry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Enables biometric unlock by encrypting the vault's derived SecretKey with the
     * hardware-backed Android Keystore key.
     * The master password itself is NEVER stored anywhere.
     */
    fun enableBiometrics(context: Context, vaultSecretKey: SecretKey): Boolean {
        return try {
            val keystoreKey = getOrCreateKeystoreSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
            val iv = cipher.iv

            val keyBytes = vaultSecretKey.encoded
            val encryptedBytes = cipher.doFinal(keyBytes)
            VaultCrypto.wipeMemory(keyBytes)

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            val payloadBase64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_KEY_ENCRYPTED_KEY, payloadBase64)
                .putBoolean(PREF_KEY_BIOMETRIC_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disableBiometrics(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Retrieves the vault's derived SecretKey by decrypting the Keystore ciphertext.
     * Returns null if biometric auth fails, prefs are missing, or tampering occurred.
     */
    fun retrieveVaultSecretKey(context: Context): SecretKey? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payloadBase64 = prefs.getString(PREF_KEY_ENCRYPTED_KEY, null) ?: return null

        return try {
            val combined = Base64.decode(payloadBase64, Base64.NO_WRAP)
            if (combined.size < 12) return null

            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val cipherTextSize = combined.size - 12
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, 12, cipherText, 0, cipherTextSize)

            val keystoreKey = getOrCreateKeystoreSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey, spec)

            val decryptedKeyBytes = cipher.doFinal(cipherText)
            val vaultKey = SecretKeySpec(decryptedKeyBytes, "AES")
            VaultCrypto.wipeMemory(decryptedKeyBytes)
            vaultKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prompts the user with AndroidX BiometricPrompt.
     * If user authenticates via fingerprint/face, executes onSuccess callback.
     * If authentication fails or is cancelled, executes onError with the reason so
     * the user can fall back to the Master Password.
     */
    fun authenticateWithPrompt(
        activity: FragmentActivity,
        title: String = "Unlock HexaPass Vault",
        subtitle: String = "Authenticate to unlock your credentials",
        negativeButtonText: String = "Use Master Password",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric verification failed. Try again or use master password.")
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}
