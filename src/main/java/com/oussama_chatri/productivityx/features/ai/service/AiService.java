package com.oussama_chatri.productivityx.features.ai.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.ai.dto.response.ConversationResponse;
import com.oussama_chatri.productivityx.features.ai.dto.request.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface AiService {

    PagedResponse<ConversationResponse> listConversations(int page, int size);

    ConversationResponse createConversation();

    ConversationResponse getConversation(UUID conversationId);

    void deleteConversation(UUID conversationId);

    SseEmitter sendMessage(UUID conversationId, ChatRequest request);
}
