package com.typewriter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE 事件数据模型，通过 SSE 流推送给前端
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SSEEvent {

    private String content;
    private String sessionId;
    private boolean isEnd;
    private String error;

    public SSEEvent() {
    }

    public SSEEvent(String content, String sessionId, boolean isEnd) {
        this.content = content;
        this.sessionId = sessionId;
        this.isEnd = isEnd;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean getIsEnd() {
        return isEnd;
    }

    public void setIsEnd(boolean isEnd) {
        this.isEnd = isEnd;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}