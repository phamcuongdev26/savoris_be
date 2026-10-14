package com.savoris.notification;

import com.savoris.entity.SettingEntity;
import com.savoris.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push (RFC 8030) with VAPID (RFC 8292) and aes128gcm payload encryption (RFC 8291), using only the JDK.
 * The VAPID key pair is created on first use and kept in the settings table, so no configuration is needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSender {

    private static final int CHAIN_SCOPE = 0;
    private static final String PUBLIC_KEY = "VAPID_PUBLIC_KEY";
    private static final String PRIVATE_KEY = "VAPID_PRIVATE_KEY";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SettingRepository settingRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile KeyPair vapid;

    // Raw uncompressed P-256 point, base64url: what the browser's pushManager.subscribe needs
    @Transactional
    public String publicKey() {
        return B64.encodeToString(encodePoint((ECPublicKey) vapid().getPublic()));
    }

    /** Encrypts the payload for one subscription and posts it; returns the push service's HTTP status. */
    public int send(String endpoint, String p256dh, String auth, String payload) throws Exception {
        URI uri = URI.create(endpoint);
        byte[] body = encrypt(payload.getBytes(StandardCharsets.UTF_8), B64D.decode(p256dh), B64D.decode(auth));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("Content-Encoding", "aes128gcm")
            .header("Content-Type", "application/octet-stream")
            .header("TTL", "1800")
            .header("Urgency", "high")
            .header("Authorization", "vapid t=" + vapidJwt(uri.getScheme() + "://" + uri.getHost()) + ", k=" + publicKey())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // ---------------------------------------------------------------- RFC 8291 payload encryption

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair as = gen.generateKeyPair();
        byte[] asPublic = encodePoint((ECPublicKey) as.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(as.getPrivate());
        ka.doPhase(decodePoint(uaPublic, ((ECPublicKey) as.getPublic()).getParams()), true);
        byte[] ecdhSecret = ka.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1});
        byte[] ikm = hmac(prkKey, keyInfo);

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] cipherText = gcm.doFinal(concat(plaintext, new byte[]{2}));   // 0x02 = last (and only) record

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(4096).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), cipherText);
    }

    // ---------------------------------------------------------------- VAPID

    String vapidJwt(String audience) throws Exception {
        String head = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = Instant.now().plusSeconds(12 * 3600).getEpochSecond();
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp
            + ",\"sub\":\"mailto:savoris@example.com\"}").getBytes(StandardCharsets.UTF_8));
        Signature es256 = Signature.getInstance("SHA256withECDSA");
        es256.initSign(vapid().getPrivate());
        es256.update((head + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return head + "." + claims + "." + B64.encodeToString(derToRaw(es256.sign()));
    }

    private KeyPair vapid() {
        KeyPair keys = vapid;
        if (keys != null) return keys;
        synchronized (this) {
            if (vapid != null) return vapid;
            try {
                var pub = settingRepository.findByScopeKeyAndSettingKey(CHAIN_SCOPE, PUBLIC_KEY);
                var priv = settingRepository.findByScopeKeyAndSettingKey(CHAIN_SCOPE, PRIVATE_KEY);
                KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
                gen.initialize(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec params = ((ECPublicKey) gen.generateKeyPair().getPublic()).getParams();
                if (pub.isPresent() && priv.isPresent()) {
                    KeyFactory kf = KeyFactory.getInstance("EC");
                    PublicKey publicKey = decodePoint(B64D.decode(pub.get().getSettingValue()), params);
                    PrivateKey privateKey = kf.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, B64D.decode(priv.get().getSettingValue())), params));
                    vapid = new KeyPair(publicKey, privateKey);
                } else {
                    KeyPair created = gen.generateKeyPair();
                    store(PUBLIC_KEY, B64.encodeToString(encodePoint((ECPublicKey) created.getPublic())));
                    store(PRIVATE_KEY, B64.encodeToString(fixed32(((ECPrivateKey) created.getPrivate()).getS())));
                    vapid = created;
                    log.info("Created the Web Push (VAPID) key pair");
                }
                return vapid;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Web Push keys unavailable", e);
            }
        }
    }

    private void store(String key, String value) {
        SettingEntity setting = new SettingEntity();
        setting.setScopeKey(CHAIN_SCOPE);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setDescription("Web Push (VAPID) key, generated automatically");
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }

    // ---------------------------------------------------------------- encoding helpers

    static byte[] encodePoint(ECPublicKey key) {
        return concat(new byte[]{4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    static PublicKey decodePoint(byte[] point, ECParameterSpec params) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 4) throw new InvalidKeyException("Not an uncompressed P-256 point");
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)), new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, params));
    }

    static byte[] fixed32(BigInteger n) {
        byte[] raw = n.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    // JDK ECDSA signatures are DER; JWS ES256 wants the raw 64-byte R||S
    static byte[] derToRaw(byte[] der) {
        int offset = 2 + (der[1] < 0 ? (der[1] & 0x7f) : 0);
        int rLen = der[offset + 1];
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(der, offset + 2, offset + 2 + rLen));
        int sStart = offset + 2 + rLen;
        int sLen = der[sStart + 1];
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(der, sStart + 2, sStart + 2 + sLen));
        return concat(fixed32(r), fixed32(s));
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }
}
