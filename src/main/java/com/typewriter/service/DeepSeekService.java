package com.typewriter.service;

import com.typewriter.model.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * DeepSeek 聊天服务接口
 * 提供流式聊天能力
 */
public interface DeepSeekService {

    /**
     * 流式调用 DeepSeek API，结果通过 SseEmitter 推送
     *
     * @param request 聊天请求
     * @param emitter SSE 发射器
     */
    void streamChat(ChatRequest request, SseEmitter emitter);
}