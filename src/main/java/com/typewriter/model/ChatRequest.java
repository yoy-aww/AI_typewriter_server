package com.typewriter.model;

import java.util.List;

/**
 * 聊天请求 DTO
 */
public class ChatRequest {

    private List<ChatMessage> messages;
    private String sessionId;
    private Double temperature;

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}