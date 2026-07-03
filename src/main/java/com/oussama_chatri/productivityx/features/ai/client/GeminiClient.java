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
public class GeminiClient implements AiModelProvider {

    private static final String BASE_URL  = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    public GeminiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String streamChat(String model, String systemPrompt,
                             List<GeminiMessage> history, String userMessage,
                             SseEmitter emitter) {
        String url  = BASE_URL + model + ":streamGenerateContent?alt=sse&key=" + apiKey;
        String body = buildRequestBody(systemPrompt, history, userMessage);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "text/event-stream")
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
            log.error("Gemini stream IO error: {}", ex.getMessage());
            try { emitter.completeWithError(ex); } catch (Exception ignored) {}
            throw AppException.internal(ErrorCode.EXT_AI_STREAM_ERROR);
        }

        return fullResponse.toString();
    }

    @Override
    public String completeChat(String model, String prompt) {
        String url  = BASE_URL + model + ":generateContent?key=" + apiKey;
        String body = buildRequestBody(null, List.of(), prompt);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
            }
            JsonNode node  = objectMapper.readTree(response.body().string());
            String   token = extractCompletionToken(node);
            if (token == null) {
                log.warn("Gemini completeChat returned null token for model={}", model);
            }
            return token;
        } catch (IOException ex) {
            log.error("Gemini complete error: {}", ex.getMessage());
            throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
        }
    }

    @Override
    public String getName() {
        return "Google Gemini";
    }

    @Override
    public List<String> getSupportedModels() {
        return List.of(
                "gemini-2.0-flash",
                "gemini-2.0-flash-001",
                "gemini-2.0-flash-lite",
                "gemini-2.0-flash-lite-001",
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
                "gemini-2.5-pro",
                "gemini-3-flash-preview",
                "gemini-3-pro-preview",
                "gemini-3.1-flash-lite",
                "gemini-3.1-flash-lite-preview",
                "gemini-3.1-pro-preview",
                "gemini-3.5-flash",
                "gemini-flash-latest",
                "gemini-flash-lite-latest",
                "gemini-pro-latest"
        );
    }

    private String buildRequestBody(String systemPrompt,
                                    List<GeminiMessage> history,
                                    String userMessage) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode systemInstruction = root.putObject("system_instruction");
                ArrayNode  sysParts          = systemInstruction.putArray("parts");
                sysParts.addObject().put("text", systemPrompt);
            }

            ArrayNode contents = root.putArray("contents");

            for (GeminiMessage msg : history) {
                ObjectNode turn  = contents.addObject();
                turn.put("role", msg.role());
                ArrayNode parts  = turn.putArray("parts");
                parts.addObject().put("text", msg.content());
            }

            ObjectNode userTurn  = contents.addObject();
            userTurn.put("role", "user");
            ArrayNode userParts  = userTurn.putArray("parts");
            userParts.addObject().put("text", userMessage);

            ObjectNode generationConfig = root.putObject("generationConfig");
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);

            return root.toString();
        } catch (Exception ex) {
            throw AppException.internal(ErrorCode.EXT_AI_UNAVAILABLE);
        }
    }

    private String extractStreamToken(JsonNode node) {
        try {
            return node.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText(null);
        } catch (Exception ex) {
            log.debug("Stream token extraction failed: {}", node);
            return null;
        }
    }

    private String extractCompletionToken(JsonNode node) {
        try {
            return node.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText(null);
        } catch (Exception ex) {
            log.debug("Completion token extraction failed: {}", node);
            return null;
        }
    }

    public record GeminiMessage(String role, String content) {}
}
