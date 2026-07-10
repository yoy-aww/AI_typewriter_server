package com.typewriter.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typewriter.config.DeepSeekConfig;
import com.typewriter.model.ChatMessage;
import com.typewriter.model.ChatRequest;
import com.typewriter.model.SSEEvent;
import com.typewriter.service.ConversationManager;
import com.typewriter.service.DeepSeekService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek 聊天服务实现
 * 使用 OkHttp 进行流式 API 调用，逐 token 解析并通过 SseEmitter 推送
 */
@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekServiceImpl.class);

    private final DeepSeekConfig deepSeekConfig;
    private final ConversationManager conversationManager;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekServiceImpl(DeepSeekConfig deepSeekConfig, ConversationManager conversationManager) {
        this.deepSeekConfig = deepSeekConfig;
        this.conversationManager = conversationManager;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(deepSeekConfig.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(deepSeekConfig.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    @Async("sseTaskExecutor")
    public void streamChat(ChatRequest request, final SseEmitter emitter) {
        final String sessionId;
        String rawSessionId = request.getSessionId();
        if (rawSessionId == null || rawSessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        } else {
            sessionId = rawSessionId;
        }

        try {
            // 将用户消息保存到会话历史
            List<ChatMessage> userMessages = request.getMessages();
            if (userMessages != null) {
                for (ChatMessage msg : userMessages) {
                    conversationManager.addMessage(sessionId, msg);
                }
            }

            // 获取完整会话历史构建请求体
            List<ChatMessage> fullHistory = conversationManager.getHistory(sessionId);
            String requestBody = buildRequestBody(fullHistory, request.getTemperature());

            // 构建 HTTP 请求
            Request httpRequest = new Request.Builder()
                    .url(deepSeekConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            log.debug("Sending request to DeepSeek API, sessionId={}, historySize={}",
                    sessionId, fullHistory.size());

            // 执行异步请求，流式读取响应
            httpClient.newCall(httpRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("DeepSeek API call failed, sessionId={}", sessionId, e);
                    sendError(emitter, "API 调用失败: " + e.getMessage(), sessionId);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            String errorMsg = "API 返回错误状态码: " + response.code();
                            log.error("{}, sessionId={}", errorMsg, sessionId);
                            sendError(emitter, errorMsg, sessionId);
                            return;
                        }

                        // 逐行读取 SSE 流
                        BufferedReader reader = new BufferedReader(
                                responseBody.charStream());

                        String line;
                        StringBuilder contentBuilder = new StringBuilder();

                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();

                                // 检查是否是结束标记
                                if ("[DONE]".equals(data)) {
                                    // 将 assistant 回复保存到会话历史
                                    String fullContent = contentBuilder.toString();
                                    if (!fullContent.isEmpty()) {
                                        conversationManager.addMessage(sessionId,
                                                new ChatMessage("assistant", fullContent));
                                    }

                                    // 发送结束事件
                                    SSEEvent endEvent = new SSEEvent("", sessionId, true);
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(Objects.requireNonNull(objectMapper.writeValueAsString(endEvent))));
                                    log.debug("Stream completed, sessionId={}", sessionId);
                                    break;
                                }

                                // 解析 JSON 数据
                                try {
                                    JsonNode jsonNode = objectMapper.readTree(data);
                                    JsonNode choices = jsonNode.get("choices");

                                    if (choices != null && choices.isArray() && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.get("content").asText();
                                            contentBuilder.append(content);

                                            // 推送文本片段
                                            SSEEvent event = new SSEEvent(content, sessionId, false);
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(Objects.requireNonNull(objectMapper.writeValueAsString(event))));
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to parse SSE data line, sessionId={}, line={}",
                                            sessionId, line, e);
                                }
                            }
                        }

                        log.info("Stream finished, sessionId={}, totalChars={}",
                                sessionId, contentBuilder.length());
                        emitter.complete();

                    } catch (Exception e) {
                        log.error("Error reading stream response, sessionId={}", sessionId, e);
                        sendError(emitter, "读取流式响应失败", sessionId);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to start stream chat, sessionId={}", sessionId, e);
            sendError(emitter, "启动流式对话失败: " + e.getMessage(), sessionId);
        }
    }

    /**
     * 构建 DeepSeek API 请求体 JSON 字符串
     *
     * @param messages    消息列表
     * @param temperature 生成温度（可选）
     */
    private String buildRequestBody(List<ChatMessage> messages, Double temperature) {
        try {
            // 构建 messages 数组
            StringBuilder messagesJson = new StringBuilder("[");
            if (messages != null) {
                for (int i = 0; i < messages.size(); i++) {
                    ChatMessage msg = messages.get(i);
                    if (i > 0) {
                        messagesJson.append(",");
                    }
                    messagesJson.append("{")
                            .append("\"role\":\"").append(escapeJson(msg.getRole())).append("\",")
                            .append("\"content\":\"").append(escapeJson(msg.getContent())).append("\"")
                            .append("}");
                }
            }
            messagesJson.append("]");

            // 构建完整的请求 JSON
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"model\":\"").append(escapeJson(deepSeekConfig.getModel())).append("\",");
            json.append("\"messages\":").append(messagesJson).append(",");
            json.append("\"stream\":true,");
            json.append("\"max_tokens\":").append(deepSeekConfig.getMaxTokens());

            if (temperature != null) {
                json.append(",\"temperature\":").append(temperature);
            }

            json.append("}");

            return json.toString();

        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * 简单 JSON 字符串转义
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 发送错误事件并完成 emitter
     */
    private void sendError(SseEmitter emitter, String errorMsg, String sessionId) {
        try {
            SSEEvent errorEvent = new SSEEvent("", sessionId, true);
            errorEvent.setError(errorMsg);
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Objects.requireNonNull(objectMapper.writeValueAsString(errorEvent))));
            emitter.complete();
        } catch (IOException e) {
            log.warn("Failed to send error event, sessionId={}", sessionId, e);
            emitter.completeWithError(new RuntimeException(errorMsg));
        }
    }
}