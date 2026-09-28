package com.example.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

enum class StrengthLevel {
    VERY_WEAK,
    WEAK,
    FAIR,
    STRONG,
    VERY_STRONG
}

data class PasswordStrength(
    val score: Int, // 0 to 100
    val level: StrengthLevel,
    val entropyBits: Int,
    val hasUpper: Boolean,
    val hasLower: Boolean,
    val hasDigit: Boolean,
    val hasSymbol: Boolean,
    val suggestions: List<String>
)

object VaultCrypto {
    private const val ALGORITHM_KEY_DERIVATION = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    const val VERIFICATION_SENTINEL = "HEXAPASS_VAULT_AUTHENTICATED_TOKEN_V1"
    const val BACKUP_CONTAINER_VERSION = 2
    const val BACKUP_APP_ID = "HexaPass"

    private val secureRandom = SecureRandom()

    fun generateRandomSalt(length: Int = 16): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateRandomIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun deriveKey(masterPassword: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val keySpec = PBEKeySpec(masterPassword, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM_KEY_DERIVATION)
        val keyBytes = factory.generateSecret(keySpec).encoded
        keySpec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptContainer(plaintext: String, key: SecretKey, iv: ByteArray, aad: ByteArray): String {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        cipher.updateAAD(aad)
        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decryptContainer(ciphertextBase64: String, key: SecretKey, iv: ByteArray, aad: ByteArray): String {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.updateAAD(aad)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    fun sha256Hex(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encrypt(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)

        val byteBuffer = ByteBuffer.allocate(iv.size + ciphertext.size)
        byteBuffer.put(iv)
        byteBuffer.put(ciphertext)

        return Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String, key: SecretKey): String {
        if (encryptedBase64.isBlank()) return ""
        val decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (decoded.size < GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val ciphertextLength = decoded.size - GCM_IV_LENGTH_BYTES
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(decoded, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    fun verifyMasterPassword(masterPassword: CharArray, saltBase64: String, verifierCipherBase64: String): SecretKey? {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val key = deriveKey(masterPassword, salt)
            val decryptedSentinel = decrypt(verifierCipherBase64, key)
            if (decryptedSentinel == VERIFICATION_SENTINEL) {
                key
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun generatePassword(
        length: Int,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true,
        excludeLookalike: Boolean = false
    ): String {
        var upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var lowerChars = "abcdefghijklmnopqrstuvwxyz"
        var digitChars = "0123456789"
        var symbolChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        if (excludeLookalike) {
            // Exclude O, 0, l, 1, I
            upperChars = upperChars.replace("O", "").replace("I", "")
            lowerChars = lowerChars.replace("l", "")
            digitChars = digitChars.replace("0", "").replace("1", "")
            symbolChars = symbolChars.replace("|", "")
        }

        val pool = StringBuilder()
        val mandatory = mutableListOf<Char>()

        if (includeUpper && upperChars.isNotEmpty()) {
            pool.append(upperChars)
            mandatory.add(upperChars[secureRandom.nextInt(upperChars.length)])
        }
        if (includeLower && lowerChars.isNotEmpty()) {
            pool.append(lowerChars)
            mandatory.add(lowerChars[secureRandom.nextInt(lowerChars.length)])
        }
        if (includeNumbers && digitChars.isNotEmpty()) {
            pool.append(digitChars)
            mandatory.add(digitChars[secureRandom.nextInt(digitChars.length)])
        }
        if (includeSymbols && symbolChars.isNotEmpty()) {
            pool.append(symbolChars)
            mandatory.add(symbolChars[secureRandom.nextInt(symbolChars.length)])
        }

        if (pool.isEmpty()) {
            return "HexaPass#${secureRandom.nextInt(10000)}"
        }

        val targetLength = length.coerceIn(4, 64)
        val passwordChars = ArrayList<Char>(targetLength)
        passwordChars.addAll(mandatory)

        while (passwordChars.size < targetLength) {
            passwordChars.add(pool[secureRandom.nextInt(pool.length)])
        }

        // Shuffle securely
        for (i in passwordChars.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = passwordChars[i]
            passwordChars[i] = passwordChars[j]
            passwordChars[j] = temp
        }

        return String(passwordChars.toCharArray())
    }

    fun evaluateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(
                score = 0,
                level = StrengthLevel.VERY_WEAK,
                entropyBits = 0,
                hasUpper = false,
                hasLower = false,
                hasDigit = false,
                hasSymbol = false,
                suggestions = listOf("Password is empty")
            )
        }

        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 32
        if (poolSize == 0) poolSize = 10

        // Entropy in bits = length * log2(poolSize)
        val entropy = (password.length * (Math.log(poolSize.toDouble()) / Math.log(2.0))).toInt()

        val suggestions = mutableListOf<String>()
        if (password.length < 12) suggestions.add("Use at least 12 characters")
        if (!hasUpper) suggestions.add("Add uppercase letters")
        if (!hasLower) suggestions.add("Add lowercase letters")
        if (!hasDigit) suggestions.add("Include numbers")
        if (!hasSymbol) suggestions.add("Add special symbols")

        var score = 0
        score += (password.length * 4).coerceAtMost(40)
        if (hasUpper) score += 15
        if (hasLower) score += 15
        if (hasDigit) score += 15
        if (hasSymbol) score += 15

        // Penalize repetitive chars
        val distinctRatio = password.toSet().size.toFloat() / password.length
        if (distinctRatio < 0.5f) {
            score -= 20
            suggestions.add("Avoid repeated characters")
        }

        score = score.coerceIn(0, 100)

        val level = when {
            score < 30 || password.length < 8 -> StrengthLevel.VERY_WEAK
            score < 50 || password.length < 10 -> StrengthLevel.WEAK
            score < 70 -> StrengthLevel.FAIR
            score < 88 -> StrengthLevel.STRONG
            else -> StrengthLevel.VERY_STRONG
        }

        return PasswordStrength(
            score = score,
            level = level,
            entropyBits = entropy,
            hasUpper = hasUpper,
            hasLower = hasLower,
            hasDigit = hasDigit,
            hasSymbol = hasSymbol,
            suggestions = suggestions
        )
    }

    fun wipeMemory(charArray: CharArray?) {
        if (charArray != null) {
            Arrays.fill(charArray, '\u0000')
        }
    }

    fun wipeMemory(byteArray: ByteArray?) {
        if (byteArray != null) {
            Arrays.fill(byteArray, 0.toByte())
        }
    }
}

