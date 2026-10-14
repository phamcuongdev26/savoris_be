package com.savoris.notification;

import com.savoris.repository.SettingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Plays the browser: decrypts what WebPushSender produced (RFC 8291) and checks the VAPID signature (RFC 8292)
class WebPushSenderTest {

    @Test
    void browserCanDecryptThePayload() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ua = gen.generateKeyPair();
        byte[] uaPublic = WebPushSender.encodePoint((ECPublicKey) ua.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);

        String message = "{\"title\":\"Món đã xong!\"}";
        byte[] body = WebPushSender.encrypt(message.getBytes(StandardCharsets.UTF_8), uaPublic, auth);

        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        assertThat(in.getInt()).isEqualTo(4096);
        byte[] asPublic = new byte[in.get()];
        in.get(asPublic);
        byte[] cipherText = new byte[in.remaining()];
        in.get(cipherText);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(WebPushSender.decodePoint(asPublic, ((ECPublicKey) ua.getPublic()).getParams()), true);
        byte[] prkKey = WebPushSender.hmac(auth, ka.generateSecret());
        byte[] ikm = WebPushSender.hmac(prkKey, WebPushSender.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1}));
        byte[] prk = WebPushSender.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPushSender.hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(WebPushSender.hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = gcm.doFinal(cipherText);

        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        assertThat(new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8)).isEqualTo(message);
    }

    @Test
    void vapidTokenIsSignedWithTheAdvertisedKey() throws Exception {
        SettingRepository settings = Mockito.mock(SettingRepository.class);
        Mockito.when(settings.findByScopeKeyAndSettingKey(Mockito.anyInt(), Mockito.anyString())).thenReturn(Optional.empty());
        WebPushSender sender = new WebPushSender(settings);

        String[] jwt = sender.vapidJwt("https://fcm.googleapis.com").split("\\.");
        byte[] raw = Base64.getUrlDecoder().decode(jwt[2]);
        assertThat(raw).hasSize(64);
        assertThat(new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8)).contains("\"aud\":\"https://fcm.googleapis.com\"");

        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        var params = ((ECPublicKey) gen.generateKeyPair().getPublic()).getParams();
        var publicKey = WebPushSender.decodePoint(Base64.getUrlDecoder().decode(sender.publicKey()), params);
        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(publicKey);
        verify.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(raw)).isTrue();
    }
}
