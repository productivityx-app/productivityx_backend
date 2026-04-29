package com.oussama_chatri.productivityx.features.ai.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.enums.MessageRole;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.util.PageableUtils;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.ai.client.GroqClient;
import com.oussama_chatri.productivityx.features.ai.dto.response.AiContext;
import com.oussama_chatri.productivityx.features.ai.dto.request.ChatRequest;
import com.oussama_chatri.productivityx.features.ai.dto.response.ConversationResponse;
import com.oussama_chatri.productivityx.features.ai.entity.Conversation;
import com.oussama_chatri.productivityx.features.ai.entity.Message;
import com.oussama_chatri.productivityx.features.ai.repository.ConversationRepository;
import com.oussama_chatri.productivityx.features.ai.repository.MessageRepository;
import com.oussama_chatri.productivityx.features.preferences.entity.UserPreferences;
import com.oussama_chatri.productivityx.features.preferences.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceImpl implements AiService {

    private static final int  HISTORY_WINDOW = 20;
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ConversationRepository    conversationRepository;
    private final MessageRepository         messageRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final SecurityUtils             securityUtils;
    private final PageableUtils             pageableUtils;
    private final GroqClient                groqClient;
    private final AiContextBuilder          contextBuilder;

    @Value("${app.groq.model:llama-3.3-70b-versatile}")
    private String defaultModel;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ConversationResponse> listConversations(int page, int size) {
        UUID     userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);
        return pageableUtils.toPagedResponse(
                conversationRepository.findActiveByUserId(userId, pageable)
                        .map(ConversationResponse::summary));
    }

    @Override
    @Transactional
    public ConversationResponse createConversation() {
        var user = securityUtils.currentUser();
        Conversation conversation = conversationRepository.save(
                Conversation.builder().user(user).build());
        return ConversationResponse.from(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId) {
        UUID userId = securityUtils.currentUserId();
        Conversation conversation = findOwnedConversationWithMessages(conversationId, userId);
        return ConversationResponse.from(conversation);
    }

    @Override
    @Transactional
    public void deleteConversation(UUID conversationId) {
        UUID         userId       = securityUtils.currentUserId();
        Conversation conversation = findOwnedConversation(conversationId, userId);
        conversation.setArchived(true);
        conversationRepository.save(conversation);
    }

    @Override
    public SseEmitter sendMessage(UUID conversationId, ChatRequest request) {
        UUID userId = securityUtils.currentUserId();
        findOwnedConversation(conversationId, userId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        streamAsync(emitter, conversationId, userId, request.getContent());
        return emitter;
    }

    @Async("sseTaskExecutor")
    protected void streamAsync(SseEmitter emitter, UUID conversationId,
                               UUID userId, String userMessage) {
        try {
            Conversation conversation = conversationRepository
                    .findByIdAndUserIdWithMessages(conversationId, userId)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.RES_CONVERSATION_NOT_FOUND));

            String model        = resolveModel(userId);
            String systemPrompt = buildSystemPrompt(userId);

            List<GroqClient.GroqMessage> history = buildHistory(conversation);

            Message userMsg = messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role(MessageRole.USER)
                    .content(userMessage)
                    .build());
            log.debug("User message persisted id={}", userMsg.getId());

            String fullResponse = groqClient.streamChat(model, systemPrompt, history,
                    userMessage, emitter);

            String actionBlock = extractActionBlock(fullResponse);
            Message assistantMsg = messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .role(MessageRole.ASSISTANT)
                    .content(fullResponse)
                    .actionBlock(actionBlock)
                    .build());
            log.debug("Assistant message persisted id={}", assistantMsg.getId());

            if (conversation.getTitle() == null && !userMessage.isBlank()) {
                generateAndSaveTitle(conversation, userMessage, model);
            }

            conversationRepository.save(conversation);

        } catch (Exception ex) {
            log.error("SSE stream error conversationId={}: {}", conversationId, ex.getMessage(), ex);
            try { emitter.completeWithError(ex); } catch (Exception ignored) {}
        }
    }

    private String resolveModel(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .map(UserPreferences::getAiModel)
                .orElse(defaultModel);
    }

    private String buildSystemPrompt(UUID userId) {
        boolean contextEnabled = preferencesRepository.findByUserId(userId)
                .map(UserPreferences::isAiContextEnabled)
                .orElse(true);

        if (!contextEnabled) {
            return baseSystemPrompt(null);
        }

        try {
            AiContext context = contextBuilder.build(userId);
            return baseSystemPrompt(context);
        } catch (Exception ex) {
            log.warn("AI context build failed, using prompt without context: {}", ex.getMessage());
            return baseSystemPrompt(null);
        }
    }

    private String baseSystemPrompt(AiContext ctx) {
        if (ctx == null) {
            return """
                    You are the built-in AI assistant for ProductivityX — a productivity app
                    covering notes, tasks, calendar events, and Pomodoro sessions.
                    Be concise and direct. Help the user manage their workspace effectively.
                    When the user asks to create or modify workspace items, include a structured
                    action block in your response using this format:
                    {"action":"CREATE_TASK","title":"...","priority":"HIGH","dueDate":"2026-04-15"}
                    {"action":"CREATE_NOTE","title":"...","content":"..."}
                    {"action":"ADD_EVENT","title":"...","startAt":"2026-04-15T10:00:00","durationMinutes":60}
                    The client will parse these and show a confirmation card before executing.
                    """;
        }

        return """
                You are the built-in AI assistant for ProductivityX.
                
                The user's current workspace state:
                - Active tasks: %d (%d due today, %d overdue)
                - Upcoming events this week: %d
                - Last note edited: "%s"
                - Current Pomodoro task: %s
                - Today's focus time logged: %d min
                
                Be concise and direct. Use this context to give relevant, personalised answers.
                When the user asks to create or modify workspace items, include a structured
                action block in your response using this exact format (one per line, no markdown):
                {"action":"CREATE_TASK","title":"...","priority":"HIGH","dueDate":"2026-04-15"}
                {"action":"CREATE_NOTE","title":"...","content":"..."}
                {"action":"ADD_EVENT","title":"...","startAt":"2026-04-15T10:00:00","durationMinutes":60}
                The client will parse these and show a confirmation card before executing.
                """.formatted(
                ctx.getTotalActiveTasks(),
                ctx.getTasksDueToday(),
                ctx.getTasksOverdue(),
                ctx.getUpcomingEventsThisWeek(),
                ctx.getLastEditedNoteTitle() != null ? ctx.getLastEditedNoteTitle() : "none",
                ctx.getCurrentPomodoroTask() != null ? "\"" + ctx.getCurrentPomodoroTask() + "\"" : "none",
                ctx.getTodayFocusMinutes()
        );
    }

    private List<GroqClient.GroqMessage> buildHistory(Conversation conversation) {
        List<Message> recent = messageRepository.findRecentByConversationId(
                conversation.getId(), HISTORY_WINDOW);
        Collections.reverse(recent);

        List<GroqClient.GroqMessage> history = new ArrayList<>();
        for (Message msg : recent) {
            String role = msg.getRole() == MessageRole.USER ? "user" : "assistant";
            history.add(new GroqClient.GroqMessage(role, msg.getContent()));
        }
        return history;
    }

    private String extractActionBlock(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            int start = response.indexOf("{\"action\":");
            if (start == -1) return null;
            int end = response.indexOf("}", start) + 1;
            if (end <= start) return null;
            return response.substring(start, end);
        } catch (Exception ex) {
            return null;
        }
    }

    private void generateAndSaveTitle(Conversation conversation, String firstMessage, String model) {
        try {
            String prompt = """
                    Generate a short, descriptive title (max 6 words) for an AI conversation
                    that starts with this user message. Return only the title, no quotes,
                    no punctuation at the end.
                    
                    User message: %s
                    """.formatted(firstMessage);

            String title = groqClient.completeChat(model, prompt);
            if (title != null && !title.isBlank()) {
                conversation.setTitle(title.trim());
                conversationRepository.save(conversation);
                log.debug("Conversation title generated: \"{}\"", title.trim());
            }
        } catch (Exception ex) {
            log.warn("Title generation failed: {}", ex.getMessage());
        }
    }

    private Conversation findOwnedConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_CONVERSATION_NOT_FOUND));
    }

    private Conversation findOwnedConversationWithMessages(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserIdWithMessages(conversationId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_CONVERSATION_NOT_FOUND));
    }
}