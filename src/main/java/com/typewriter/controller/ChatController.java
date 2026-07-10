package com.typewriter.controller;

import com.typewriter.model.ChatRequest;
import com.typewriter.service.DeepSeekService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天 SSE 接口控制器
 * 提供流式聊天和健康检查端点
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final DeepSeekService deepSeekService;

    public ChatController(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    /**
     * SSE 流式聊天接口
     * 前端通过 EventSource 或 fetch 流式接收 AI 回复
     *
     * @param request 聊天请求体，包含消息列表和可选 sessionId
     * @return SseEmitter 用于流式推送
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        // 创建 SseEmitter，超时 5 分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        // 注册完成回调和超时回调
        emitter.onCompletion(() -> log.debug("SSE connection completed, sessionId={}", request.getSessionId()));
        emitter.onTimeout(() -> log.warn("SSE connection timed out, sessionId={}", request.getSessionId()));
        emitter.onError(throwable -> log.error("SSE connection error, sessionId={}", request.getSessionId(), throwable));

        log.info("New SSE connection established, sessionId={}", request.getSessionId());

        // 异步调用 DeepSeek 服务
        deepSeekService.streamChat(request, emitter);

        return emitter;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "AI Typewriter Server");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
}