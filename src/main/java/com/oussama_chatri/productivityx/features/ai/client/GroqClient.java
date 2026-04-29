package com.oussama_chatri.productivityx.features.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class GroqClient {

    private static final String GROQ_BASE    = "https://api.groq.com/openai/v1/chat/completions";
    private static final MediaType JSON_TYPE  = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.groq.api-key}")
    private String apiKey;

    @Value("${app.groq.model:llama-3.3-70b-versatile}")
    private String defaultModel;

    public GroqClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Streams a chat response token-by-token via SSE.
     * Returns the full assembled response string once the stream completes.
     */
    public String streamChat(String model, String systemPrompt,
                             List<GroqMessage> history, String userMessage,
                             SseEmitter emitter) {
        String body = buildRequestBody(model, systemPrompt, history, userMessage, true);

        Request request = new Request.Builder()
                .url(GROQ_BASE)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        StringBuilder fullResponse = new StringBuilder();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() != null ? response.body().string() : "empty";
                log.error("Groq API error status={} body={}", response.code(), errorBody);
                throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.equals("[DONE]")) break;

                        try {
                            JsonNode node  = objectMapper.readTree(data);
                            String   token = extractStreamToken(node);
                            if (token != null && !token.isEmpty()) {
                                fullResponse.append(token);
                                emitter.send(SseEmitter.event().data(token));
                            }
                        } catch (Exception ex) {
                            log.debug("SSE token parse skip: {}", ex.getMessage());
                        }
                    }
                }
            }

            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();

        } catch (IOException ex) {
            log.error("Groq stream IO error: {}", ex.getMessage());
            try { emitter.completeWithError(ex); } catch (Exception ignored) {}
            throw AppException.internal(ErrorCode.EXT_AI_STREAM_ERROR);
        }

        return fullResponse.toString();
    }

    /**
     * Blocking single-turn completion — used for auto-generating conversation titles.
     */
    public String completeChat(String model, String prompt) {
        String body = buildRequestBody(model, null, List.of(), prompt, false);

        Request request = new Request.Builder()
                .url(GROQ_BASE)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
            }
            JsonNode node  = objectMapper.readTree(response.body().string());
            String   token = extractCompletionToken(node);
            if (token == null) {
                log.warn("Groq completeChat returned null token for model={}", model);
            }
            return token;
        } catch (IOException ex) {
            log.error("Groq complete error: {}", ex.getMessage());
            throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
        }
    }

    private String buildRequestBody(String model, String systemPrompt,
                                    List<GroqMessage> history, String userMessage,
                                    boolean stream) {
        try {
            ObjectNode body     = objectMapper.createObjectNode();
            ArrayNode  messages = body.putArray("messages");

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sys = messages.addObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
            }

            for (GroqMessage msg : history) {
                ObjectNode turn = messages.addObject();
                turn.put("role", msg.role());
                turn.put("content", msg.content());
            }

            ObjectNode userTurn = messages.addObject();
            userTurn.put("role", "user");
            userTurn.put("content", userMessage);

            body.put("model", model);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2048);
            body.put("stream", stream);

            return body.toString();
        } catch (Exception ex) {
            throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
        }
    }

    private String extractStreamToken(JsonNode node) {
        try {
            return node.path("choices")
                    .path(0)
                    .path("delta")
                    .path("content")
                    .asText(null);
        } catch (Exception ex) {
            log.debug("Stream token extraction failed: {}", node);
            return null;
        }
    }

    private String extractCompletionToken(JsonNode node) {
        try {
            return node.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText(null);
        } catch (Exception ex) {
            log.debug("Completion token extraction failed: {}", node);
            return null;
        }
    }

    public record GroqMessage(String role, String content) {}
}