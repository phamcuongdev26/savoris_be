package com.dinechain.image;

import com.dinechain.exception.AppException;
import com.dinechain.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores pictures (dish photos, the bank QR) on Cloudinary with a signed upload.
 * Configured by CLOUDINARY_URL = cloudinary://API_KEY:API_SECRET@CLOUD_NAME, as shown on the Cloudinary dashboard.
 */
@Slf4j
@Component
public class Cloudinary {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String apiBase;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Cloudinary(@Value("${dinechain.cloudinary.url:}") String url,
                      @Value("${dinechain.cloudinary.api-base:https://api.cloudinary.com}") String apiBase) {
        this.apiBase = apiBase;
        String[] parts = parse(url);
        this.apiKey = parts[0];
        this.apiSecret = parts[1];
        this.cloudName = parts[2];
        if (url != null && !url.isBlank() && !enabled()) {
            log.warn("CLOUDINARY_URL is set but not in the form cloudinary://API_KEY:API_SECRET@CLOUD_NAME: uploads are off");
        }
    }

    public boolean enabled() {
        return cloudName != null;
    }

    // Uploads the image into the folder and returns its https address
    public String upload(byte[] data, String contentType, String folder) {
        if (!enabled()) {
            throw new AppException(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        // Cloudinary signs the parameters sorted by name, then appends the secret
        String signature = sha1("folder=" + folder + "&timestamp=" + timestamp + apiSecret);
        String boundary = "dinechain-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "api_key", apiKey);
        field(body, boundary, "timestamp", timestamp);
        field(body, boundary, "folder", folder);
        field(body, boundary, "signature", signature);
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n");
        body.writeBytes(data);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/v1_1/" + cloudName + "/image/upload"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = JSON.readTree(response.body());
            if (response.statusCode() / 100 != 2 || !json.hasNonNull("secure_url")) {
                String reason = json.path("error").path("message").asString("HTTP " + response.statusCode());
                log.warn("Cloudinary refused the upload: {}", reason);
                throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED, "Cloudinary: " + reason);
            }
            return json.get("secure_url").asString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Upload to Cloudinary failed: {}", e.getMessage());
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    // [apiKey, apiSecret, cloudName], all null when the URL is missing or malformed
    static String[] parse(String url) {
        if (url == null || !url.trim().startsWith("cloudinary://")) {
            return new String[3];
        }
        String rest = url.trim().substring("cloudinary://".length());
        int at = rest.lastIndexOf('@');
        int colon = rest.indexOf(':');
        if (at < 0 || colon < 0 || colon > at) {
            return new String[3];
        }
        String cloud = rest.substring(at + 1);
        int query = cloud.indexOf('?');
        if (query >= 0) {
            cloud = cloud.substring(0, query);
        }
        String key = rest.substring(0, colon);
        String secret = URLDecoder.decode(rest.substring(colon + 1, at), StandardCharsets.UTF_8);
        if (key.isEmpty() || secret.isEmpty() || cloud.isEmpty()) {
            return new String[3];
        }
        return new String[]{key, secret, cloud};
    }

    static String sha1(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void field(ByteArrayOutputStream body, String boundary, String name, String value) {
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
