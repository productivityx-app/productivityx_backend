package com.oussama_chatri.productivityx.features.ai.client;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModelRegistry {

    private final List<AiModelProvider> providers;
    private final Map<String, AiModelProvider> modelToProvider = new LinkedHashMap<>();
    private List<ModelInfo> availableModels;

    @PostConstruct
    public void init() {
        for (AiModelProvider provider : providers) {
            for (String model : provider.getSupportedModels()) {
                modelToProvider.put(model, provider);
            }
        }
        log.info("ModelRegistry initialized with {} models across {} providers",
                modelToProvider.size(), providers.size());
    }

    public AiModelProvider resolveProvider(String modelName) {
        AiModelProvider provider = modelToProvider.get(modelName);
        if (provider == null) {
            throw AppException.badRequest(ErrorCode.VAL_REQUEST_BODY_INVALID,
                    "Unsupported model: " + modelName);
        }
        return provider;
    }

    public List<ModelInfo> getAvailableModels() {
        if (availableModels == null) {
            availableModels = modelToProvider.keySet().stream()
                    .map(model -> {
                        AiModelProvider p = modelToProvider.get(model);
                        return new ModelInfo(model, p.getName());
                    })
                    .toList();
        }
        return availableModels;
    }

    public record ModelInfo(String id, String provider) {}
}
