package com.agentplatform.chat;

/** Roles stored on message.role and mapped to Spring AI message types. */
public final class MessageRole {
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    public static final String SYSTEM = "system";

    private MessageRole() {}
}
