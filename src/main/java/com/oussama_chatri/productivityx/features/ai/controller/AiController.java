package com.oussama_chatri.productivityx.features.ai.controller;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.ai.dto.request.ChatRequest;
import com.oussama_chatri.productivityx.features.ai.dto.response.ConversationResponse;
import com.oussama_chatri.productivityx.features.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI Assistant — conversations, streaming chat, workspace context")
public class AiController {

    private final AiService aiService;

    @GetMapping("/conversations")
    @Operation(summary = "List all conversations for the current user — newest first, archived excluded")
    public ResponseEntity<ApiResponse<PagedResponse<ConversationResponse>>> listConversations(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(aiService.listConversations(page, size)));
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create a new empty conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation() {
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(aiService.createConversation(), "Conversation created."));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get a conversation with its full message history")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(aiService.getConversation(id)));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Archive a conversation — does not permanently delete it")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable UUID id) {
        aiService.deleteConversation(id);
        return ResponseEntity.ok(ApiResponse.message("Conversation archived."));
    }

    /**
     * SSE streaming endpoint.
     * The client connects with Accept: text/event-stream.
     * Each SSE event carries a token. The stream ends with a "done" event containing "[DONE]".
     * The full response is persisted server-side after streaming completes.
     */
    @PostMapping(
            value = "/conversations/{id}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @Operation(
            summary = "Send a message and receive the AI response as an SSE stream",
            description = "Tokens are emitted one-by-one. The stream ends with event:done data:[DONE]. " +
                          "The user message and full assistant response are persisted after streaming."
    )
    public SseEmitter sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody ChatRequest request) {
        return aiService.sendMessage(id, request);
    }
}
