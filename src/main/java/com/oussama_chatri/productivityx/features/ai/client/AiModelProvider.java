package com.oussama_chatri.productivityx.features.ai.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiModelProvider {

    String streamChat(String model, String systemPrompt,
                      List<GeminiClient.GeminiMessage> history,
                      String userMessage, SseEmitter emitter);

    String completeChat(String model, String prompt);

    String getName();

    List<String> getSupportedModels();
}
