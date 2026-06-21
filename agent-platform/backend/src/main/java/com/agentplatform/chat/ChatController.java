package com.agentplatform.chat;

import com.agentplatform.chat.dto.ChatRequest;
import com.agentplatform.chat.dto.ConversationResponse;
import com.agentplatform.chat.dto.MessageResponse;
import com.agentplatform.common.ApiResponse;
import com.agentplatform.common.BusinessException;
import com.agentplatform.orchestrator.ChatChunk;
import com.agentplatform.orchestrator.ChatOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
public class ChatController {

    private final ChatOrchestrator orchestrator;
    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ChatController(ChatOrchestrator orchestrator,
                          ConversationRepository conversations,
                          MessageRepository messages) {
        this.orchestrator = orchestrator;
        this.conversations = conversations;
        this.messages = messages;
    }

    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(@RequestBody ChatRequest req) {
        return orchestrator.chat(req.agentId(), req.conversationId(), req.message(), req.userId())
                .map(chunk -> ServerSentEvent.<ChatChunk>builder()
                        .event(chunk.type())
                        .data(chunk)
                        .build());
    }

    @GetMapping("/api/conversations")
    public ApiResponse<List<ConversationResponse>> myConversations(@RequestParam String userId) {
        List<ConversationResponse> list = conversations.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(ConversationResponse::from).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/api/conversations/{id}/messages")
    public ApiResponse<List<MessageResponse>> history(@PathVariable Long id) {
        List<MessageResponse> list = messages.findByConversationIdOrderByCreatedAtAsc(id)
                .stream().map(MessageResponse::from).toList();
        return ApiResponse.ok(list);
    }

    public record RenameRequest(String title) {}

    @PutMapping("/api/conversations/{id}")
    public ApiResponse<Void> rename(@PathVariable Long id, @RequestBody RenameRequest req) {
        ConversationEntity c = conversations.findById(id)
                .orElseThrow(() -> new BusinessException(40404, "conversation not found"));
        c.setTitle(req.title());
        conversations.save(c);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/conversations/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        messages.deleteByConversationId(id);
        conversations.deleteById(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/conversations")
    public ApiResponse<List<ConversationResponse>> allConversations() {
        return ApiResponse.ok(conversations.findAllByOrderByUpdatedAtDesc()
                .stream().map(ConversationResponse::from).toList());
    }

    @GetMapping("/api/admin/conversations/{id}/messages")
    public ApiResponse<List<MessageResponse>> adminHistory(@PathVariable Long id) {
        return history(id);
    }
}
