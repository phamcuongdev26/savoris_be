package com.savoris.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA key pair used to sign (private) and verify (public) JWTs, read once at startup from the locations in
 * {@link JwtProperties}. A missing or broken key stops the application instead of failing on the first login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RsaKeyProvider {

    private final JwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    // Load once at startup: lazy loading from many request threads at once raced and caused random 401s under load
    @PostConstruct
    void loadKeys() {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(readPem(jwtProperties.getPrivateKeyPath(), "PRIVATE KEY")));
            publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(readPem(jwtProperties.getPublicKeyPath(), "PUBLIC KEY")));
            log.info("RSA keys loaded from {} and {}", jwtProperties.getPrivateKeyPath(), jwtProperties.getPublicKeyPath());
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load JWT RSA keys: " + ex.getMessage(), ex);
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    private byte[] readPem(String location, String type) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException(location + " not found");
        }
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
            return Base64.getDecoder().decode(pem);
        }
    }
}
