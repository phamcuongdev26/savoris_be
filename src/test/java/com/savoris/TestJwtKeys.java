package com.savoris;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Throwaway RSA key pair for tests, so no real key ever needs to live in the repository.
 */
final class TestJwtKeys {

    private static Path privateKey;
    private static Path publicKey;

    private TestJwtKeys() {
    }

    static synchronized void register(DynamicPropertyRegistry registry) throws Exception {
        if (privateKey == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Path dir = Files.createTempDirectory("savoris-keys");
            privateKey = Files.writeString(dir.resolve("private_key.pem"), "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
            publicKey = Files.writeString(dir.resolve("public_key.pem"), "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
        }
        registry.add("jwt.private-key-path", () -> privateKey.toUri().toString());
        registry.add("jwt.public-key-path", () -> publicKey.toUri().toString());
    }
}
