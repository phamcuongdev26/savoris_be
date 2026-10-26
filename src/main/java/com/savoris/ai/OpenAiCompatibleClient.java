package com.savoris.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.configuration.AiProperties;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleClient implements AiModelClient {
    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleClient(AiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    @Override
    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank()
            && properties.getModel() != null && !properties.getModel().isBlank();
    }

    @Override
    public AiCompletion complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, boolean jsonMode) {
        if (!isConfigured()) throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("messages", messages);
            if (tools != null && !tools.isEmpty()) {
                payload.put("tools", tools);
                payload.put("tool_choice", "auto");
            }
            if (jsonMode) payload.put("response_format", Map.of("type", "json_object"));
            String base = properties.getBaseUrl().replaceAll("/+$", "");
            String endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(properties.getTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AppException(ErrorCode.AI_PROVIDER_FAILED);
            }
            JsonNode message = mapper.readTree(response.body()).path("choices").path(0).path("message");
            if (message.isMissingNode()) throw new AppException(ErrorCode.AI_PROVIDER_FAILED);
            List<AiToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                Map<String, Object> extraContent = call.path("extra_content").isObject()
                    ? mapper.convertValue(call.path("extra_content"), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
                calls.add(new AiToolCall(call.path("id").asText(), call.path("function").path("name").asText(),
                    call.path("function").path("arguments").asText("{}"), extraContent));
            }
            JsonNode content = message.path("content");
            return new AiCompletion(content.isNull() || content.isMissingNode() ? null : content.asText(), List.copyOf(calls));
        } catch (AppException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.AI_PROVIDER_FAILED, ex);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.AI_PROVIDER_FAILED, ex);
        }
    }
}
