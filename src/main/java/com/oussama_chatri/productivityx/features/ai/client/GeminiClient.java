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
public class GeminiClient {

    private static final String GEMINI_BASE  = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String STREAM_PATH  = ":streamGenerateContent?alt=sse";
    private static final String CONTENT_PATH = ":generateContent";
    private static final MediaType JSON_TYPE  = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.model:gemini-2.0-flash}")
    private String defaultModel;

    public GeminiClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String streamChat(String model, String systemPrompt,
                             List<GeminiMessage> history, String userMessage,
                             SseEmitter emitter) {
        // API key goes in the header — never in the URL (URLs appear in server logs)
        String url  = GEMINI_BASE + model + STREAM_PATH;
        String body = buildRequestBody(systemPrompt, history, userMessage);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        StringBuilder fullResponse = new StringBuilder();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() != null ? response.body().string() : "empty";
                log.error("Gemini API error status={} body={}", response.code(), errorBody);
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
                            String   token = extractToken(node);
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
            log.error("Gemini stream IO error: {}", ex.getMessage());
            try { emitter.completeWithError(ex); } catch (Exception ignored) {}
            throw AppException.internal(ErrorCode.EXT_AI_STREAM_ERROR);
        }

        return fullResponse.toString();
    }

    public String completeChat(String model, String prompt) {
        String url = GEMINI_BASE + model + CONTENT_PATH;

        ObjectNode body     = objectMapper.createObjectNode();
        ArrayNode  contents = body.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", prompt);

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(RequestBody.create(body.toString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
                }
                JsonNode node  = objectMapper.readTree(response.body().string());
                String   token = extractToken(node);
                if (token == null) {
                    log.warn("Gemini completeChat returned null token for model={}", model);
                }
                return token;
            }
        } catch (IOException ex) {
            log.error("Gemini complete error: {}", ex.getMessage());
            throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
        }
    }

    private String buildRequestBody(String systemPrompt, List<GeminiMessage> history,
                                    String userMessage) {
        try {
            ObjectNode body = objectMapper.createObjectNode();

            ObjectNode systemInstruction = body.putObject("system_instruction");
            systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

            ArrayNode contents = body.putArray("contents");
            for (GeminiMessage msg : history) {
                ObjectNode turn = contents.addObject();
                turn.put("role", msg.role());
                turn.putArray("parts").addObject().put("text", msg.content());
            }

            ObjectNode userTurn = contents.addObject();
            userTurn.put("role", "user");
            userTurn.putArray("parts").addObject().put("text", userMessage);

            ObjectNode config = body.putObject("generationConfig");
            config.put("temperature", 0.7);
            config.put("maxOutputTokens", 2048);

            return body.toString();
        } catch (Exception ex) {
            throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
        }
    }

    private String extractToken(JsonNode node) {
        try {
            return node.path("candidates")
                       .path(0)
                       .path("content")
                       .path("parts")
                       .path(0)
                       .path("text")
                       .asText(null);
        } catch (Exception ex) {
            log.debug("Token extraction failed from node: {}", node);
            return null;
        }
    }

    public record GeminiMessage(String role, String content) {}
}
