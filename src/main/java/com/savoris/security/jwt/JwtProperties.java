package com.savoris.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * jwt.* in application.yml. Key locations are Spring resource paths:
 * "classpath:certs/private_key.pem" (default, keys under src/main/resources/certs) or "file:/path/to/key.pem".
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String privateKeyPath = "classpath:certs/private_key.pem";
    private String publicKeyPath = "classpath:certs/public_key.pem";
    private Long accessTokenExpiration; // seconds
    private Long refreshTokenExpiration; // seconds
}
