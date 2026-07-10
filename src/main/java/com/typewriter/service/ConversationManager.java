package com.typewriter.service;

import com.typewriter.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话历史管理器
 * 按 sessionId 维护多轮对话上下文，支持自动轮数裁剪
 */
@Component
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final ConcurrentHashMap<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final int maxRounds;

    public ConversationManager(@Value("${deepseek.max-conversation-rounds:20}") int maxRounds) {
        this.maxRounds = maxRounds;
        log.info("ConversationManager initialized, maxRounds={}", maxRounds);
    }

    /**
     * 获取指定会话的消息历史（返回不可修改的视图）
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> history = conversations.get(sessionId);
        if (history == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(history);
    }

    /**
     * 向指定会话追加一条消息，自动裁剪超出轮数的历史
     */
    public void addMessage(String sessionId, ChatMessage message) {
        conversations.compute(sessionId, (key, history) -> {
            if (history == null) {
                history = Collections.synchronizedList(new ArrayList<>());
            }
            history.add(message);
            return trimHistory(history);
        });
    }

    /**
     * 删除指定会话
     */
    public void removeSession(String sessionId) {
        conversations.remove(sessionId);
        log.debug("Removed session: {}", sessionId);
    }

    /**
     * 清空所有会话
     */
    public void clearAll() {
        conversations.clear();
        log.info("All conversations cleared");
    }

    /**
     * 裁剪历史消息，只保留最近 maxRounds 轮对话
     * 每轮 = 1条 user + 1条 assistant = 2条消息
     */
    private List<ChatMessage> trimHistory(List<ChatMessage> history) {
        int maxMessages = maxRounds * 2;
        if (history.size() > maxMessages) {
            int fromIndex = history.size() - maxMessages;
            List<ChatMessage> trimmed = Collections.synchronizedList(
                    new ArrayList<>(history.subList(fromIndex, history.size())));
            log.debug("Trimmed history: {} -> {} messages", history.size(), trimmed.size());
            return trimmed;
        }
        return history;
    }
}