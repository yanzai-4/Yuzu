package ai.yuzu.settings;

import ai.yuzu.config.YuzuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * v0.0.7 🍊 AES-256-GCM encryption of secrets (API keys) at rest.
 *
 * <p>The 32-byte master key comes from the {@code YUZU_MASTER_KEY} environment variable (base64) or from
 * {@code <secretDir>/master.key}, which is generated on first use with owner-only permissions. Ciphertexts
 * are bound to a purpose string (AAD) so a key encrypted for one provider cannot be swapped into another.
 * Plaintext secrets are never logged.</p>
 */
@Component
public class SecretVault {

    private static final Logger log = LoggerFactory.getLogger(SecretVault.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    /** v0.0.7 🍊 Loads or creates the master key. */
    public SecretVault(YuzuProperties properties) {
        this.key = new SecretKeySpec(loadMasterKey(properties.secretDirPath()), "AES");
    }

    /** v0.0.7 🍊 Encrypts a secret for a purpose; returns base64(iv || ciphertext || tag). */
    public String encrypt(String plaintext, String purpose) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            byte[] body = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** v0.0.7 🍊 Decrypts a value produced by {@link #encrypt(String, String)} for the same purpose. */
    public String decrypt(String encoded, String purpose) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored secret cannot be decrypted (was the master key changed?)", e);
        }
    }

    /** v0.0.7 🍊 Masks a secret for display: "sk-…abcd". */
    public static String mask(String secret) {
        if (secret == null || secret.length() < 8) {
            return "••••";
        }
        return secret.substring(0, 3) + "…" + secret.substring(secret.length() - 4);
    }

    /** v0.0.7 🍊 Reads the master key from the environment or the key file (creating it if needed). */
    private static byte[] loadMasterKey(Path dir) {
        String env = System.getenv("YUZU_MASTER_KEY");
        if (env != null && !env.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(env.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("YUZU_MASTER_KEY must be 32 bytes (base64).");
            }
            return decoded;
        }
        Path file = dir.resolve("master.key");
        try {
            if (Files.exists(file)) {
                return Base64.getDecoder().decode(Files.readString(file).trim());
            }
            Files.createDirectories(dir);
            byte[] fresh = new byte[32];
            new SecureRandom().nextBytes(fresh);
            Files.writeString(file, Base64.getEncoder().encodeToString(fresh));
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX file system.
            }
            log.info("🍊 Created a new master key at {}", file);
            return fresh;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read or create the master key at " + file, e);
        }
    }
}
