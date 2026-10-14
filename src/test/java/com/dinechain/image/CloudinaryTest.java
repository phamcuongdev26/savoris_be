package com.dinechain.image;

import com.dinechain.exception.AppException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String answer = "{\"secure_url\":\"https:\\/\\/res.cloudinary.com\\/demo\\/image\\/upload\\/v1\\/dinechain\\/menu\\/abc.png\"}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] out = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Cloudinary client(String url) {
        return new Cloudinary(url, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void signedUploadReturnsTheHttpsAddress() {
        String url = client("cloudinary://123456:s3cr%40t@demo").upload(PNG, "image/png", "dinechain/menu");

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/dinechain/menu/abc.png");
        assertThat(path.get()).isEqualTo("/v1_1/demo/image/upload");
        String sent = body.get();
        assertThat(field(sent, "api_key")).isEqualTo("123456");
        assertThat(field(sent, "folder")).isEqualTo("dinechain/menu");
        String timestamp = field(sent, "timestamp");
        // The secret is decoded from the URL and only ever travels inside the signature
        assertThat(field(sent, "signature")).isEqualTo(Cloudinary.sha1("folder=dinechain/menu&timestamp=" + timestamp + "s3cr@t"));
        assertThat(sent).doesNotContain("s3cr");
        assertThat(sent).contains("Content-Type: image/png");
    }

    @Test
    void cloudinaryErrorsReachTheOwner() {
        status = 401;
        answer = "{\"error\":{\"message\":\"Invalid Signature\"}}";
        assertThatThrownBy(() -> client("cloudinary://1:2@demo").upload(PNG, "image/png", "x"))
            .isInstanceOf(AppException.class)
            .hasMessageContaining("Invalid Signature");
    }

    @Test
    void missingOrMalformedUrlTurnsUploadsOff() {
        assertThat(client("").enabled()).isFalse();
        assertThat(client("https://example.com").enabled()).isFalse();
        assertThat(client("cloudinary://keyonly@demo").enabled()).isFalse();
        assertThat(client("cloudinary://k:s@demo").enabled()).isTrue();
        assertThatThrownBy(() -> client(null).upload(PNG, "image/png", "x")).isInstanceOf(AppException.class);
    }

    @Test
    void onlyRealImagesPass() {
        assertThat(ImageFiles.check(PNG, 1024)).isEqualTo("image/png");
        assertThatThrownBy(() -> ImageFiles.check("not an image".getBytes(), 1024)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> ImageFiles.check(PNG, 4)).isInstanceOf(AppException.class);
    }

    private static String field(String multipart, String name) {
        Matcher m = Pattern.compile("name=\"" + name + "\"\r\n\r\n([^\r]*)\r\n").matcher(multipart);
        assertThat(m.find()).as("field " + name).isTrue();
        return m.group(1);
    }
}
