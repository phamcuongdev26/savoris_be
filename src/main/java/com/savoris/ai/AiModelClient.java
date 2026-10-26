package com.savoris.ai;

import java.util.List;
import java.util.Map;

public interface AiModelClient {
    boolean isConfigured();
    AiCompletion complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools, boolean jsonMode);

    record AiCompletion(String content, List<AiToolCall> toolCalls) {}
    record AiToolCall(String id, String name, String argumentsJson) {}
}
