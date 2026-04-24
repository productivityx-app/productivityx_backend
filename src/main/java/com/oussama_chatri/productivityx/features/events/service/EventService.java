
package com.oussama_chatri.productivityx.features.events.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.features.events.dto.request.EventRequest;
import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventService {

    EventResponse create(EventRequest request);

    EventResponse getById(UUID eventId);

    // Date-range query — used by calendar week/month views
    List<EventResponse> listByRange(Instant from, Instant to);

    // Paged listing when no range is provided (sync, list view)
    PagedResponse<EventResponse> listPaged(int page, int size);

    PagedResponse<EventResponse> listTrash(int page, int size);

    EventResponse update(UUID eventId, EventRequest request);

    // Soft delete — moves to trash
    EventResponse softDelete(UUID eventId);

    // Restore from trash
    EventResponse restore(UUID eventId);

    // Permanent removal — event must be in trash first
    void hardDelete(UUID eventId);

    // Delete all instances of a recurring series
    void deleteSeriesFromParent(UUID parentEventId);
}
