package com.oussama_chatri.productivityx.features.tasks.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReorderRequest {

    @NotNull(message = "Items list is required.")
    @Valid
    private List<ReorderItem> items;

    @Data
    public static class ReorderItem {

        @NotNull(message = "Task ID is required.")
        private UUID id;

        // Zero-based position within the target column / list
        @NotNull(message = "Position is required.")
        private Integer position;
    }
}
