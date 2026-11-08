package com.studymuse.agent;

import com.studymuse.domain.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/** The newest chat turns of one course, capped at maxMessages (short-term memory). */
public class ConversationHistory {

    public static final int MAX_MESSAGES = 20;

    private final List<ChatMessage> messages;
    private final int maxMessages;

    public ConversationHistory(List<ChatMessage> saved, int maxMessages) {
        this.maxMessages = maxMessages;
        this.messages = new ArrayList<>(saved.subList(Math.max(0, saved.size() - maxMessages), saved.size()));
    }

    public void add(ChatMessage m) {
        messages.add(m);
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    /** The last n turns, oldest first. */
    public List<ChatMessage> recent(int n) {
        return List.copyOf(messages.subList(Math.max(0, messages.size() - n), messages.size()));
    }
}
