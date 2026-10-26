package com.savoris.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.configuration.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleClientTest {

    @Test
    void sendsToolSchemasAndParsesAProviderToolCall() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (StubProvider provider = new StubProvider(200, """
            {"choices":[{"message":{"content":null,"tool_calls":[{"id":"call-1","type":"function","function":{"name":"get_branches","arguments":"{}"}}]}}]}
            """, requestBody)) {
            OpenAiCompatibleClient client = client(provider, "secret-test-key");
            var completion = client.complete(
                List.of(Map.of("role", "user", "content", "Có chi nhánh nào?")),
                List.of(Map.of("type", "function", "function", Map.of("name", "get_branches"))), false);

            assertThat(completion.content()).isNull();
            assertThat(completion.toolCalls()).containsExactly(
                new AiModelClient.AiToolCall("call-1", "get_branches", "{}"));
            assertThat(requestBody.get()).contains("\"model\":\"test-model\"")
                .contains("\"tools\"").contains("\"tool_choice\":\"auto\"");
        }
    }

    @Test
    void providerFailureIsReturnedAsAServiceErrorWithoutLeakingProviderBody() throws Exception {
        try (StubProvider provider = new StubProvider(401, "private provider details", new AtomicReference<>())) {
            assertThatThrownBy(() -> client(provider, "bad-key").complete(List.of(), List.of(), false))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_PROVIDER_FAILED);
        }
    }

    @Test
    void blankCredentialsDisableTheProviderClient() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://localhost:1/v1");
        properties.setModel("test-model");

        assertThat(new OpenAiCompatibleClient(properties, new ObjectMapper()).isConfigured()).isFalse();
    }

    private OpenAiCompatibleClient client(StubProvider provider, String apiKey) {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://localhost:" + provider.port() + "/v1");
        properties.setApiKey(apiKey);
        properties.setModel("test-model");
        properties.setTimeout(Duration.ofSeconds(2));
        return new OpenAiCompatibleClient(properties, new ObjectMapper());
    }

    private static final class StubProvider implements AutoCloseable {
        private final HttpServer server;

        StubProvider(int status, String body, AtomicReference<String> requestBody) throws Exception {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
