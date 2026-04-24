package com.oussama_chatri.productivityx.features.events.service;

import com.oussama_chatri.productivityx.core.dto.PagedResponse;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.util.PageableUtils;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.features.events.dto.request.EventRequest;
import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;
import com.oussama_chatri.productivityx.features.events.entity.Event;
import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import com.oussama_chatri.productivityx.shared.websocket.WebSocketNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository   eventRepository;
    private final SecurityUtils     securityUtils;
    private final PageableUtils     pageableUtils;
    private final WebSocketNotifier wsNotifier;

    // Create

    @Override
    @Transactional
    public EventResponse create(EventRequest request) {
        User  user  = securityUtils.currentUser();
        Event event = buildEvent(user, request);

        validateTimeRange(event.getStartAt(), event.getEndAt());

        Event saved = eventRepository.save(event);
        wsNotifier.notifyUser(user.getId(), "events.created", EventResponse.from(saved));
        log.debug("Event created id={} user={}", saved.getId(), user.getId());
        return EventResponse.from(saved);
    }

    // Read

    @Override
    @Transactional(readOnly = true)
    public EventResponse getById(UUID eventId) {
        UUID userId = securityUtils.currentUserId();
        return EventResponse.from(findOwnedEvent(eventId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> listByRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw AppException.badRequest(ErrorCode.VAL_REQUEST_BODY_INVALID,
                    "'from' and 'to' are required for range queries.");
        }
        if (!from.isBefore(to)) {
            throw AppException.badRequest(ErrorCode.VAL_REQUEST_BODY_INVALID,
                    "'from' must be before 'to'.");
        }

        UUID userId = securityUtils.currentUserId();
        return eventRepository.findActiveByUserIdAndRange(userId, from, to)
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listPaged(int page, int size) {
        UUID     userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);
        return pageableUtils.toPagedResponse(
                eventRepository.findActiveByUserId(userId, pageable).map(EventResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> listTrash(int page, int size) {
        UUID     userId   = securityUtils.currentUserId();
        Pageable pageable = pageableUtils.build(page, size);
        return pageableUtils.toPagedResponse(
                eventRepository.findDeletedByUserId(userId, pageable).map(EventResponse::from));
    }

    // Update

    @Override
    @Transactional
    public EventResponse update(UUID eventId, EventRequest request) {
        UUID  userId = securityUtils.currentUserId();
        Event event  = findOwnedEvent(eventId, userId);

        if (event.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_EVENT_NOT_FOUND,
                    "Cannot edit a trashed event. Restore it first.");
        }

        applyUpdate(event, request);
        validateTimeRange(event.getStartAt(), event.getEndAt());
        event.setVersion(event.getVersion() + 1);

        Event saved = eventRepository.save(event);
        wsNotifier.notifyUser(userId, "events.updated", EventResponse.from(saved));
        log.debug("Event updated id={} user={}", eventId, userId);
        return EventResponse.from(saved);
    }

    // Soft delete

    @Override
    @Transactional
    public EventResponse softDelete(UUID eventId) {
        UUID  userId = securityUtils.currentUserId();
        Event event  = findOwnedEvent(eventId, userId);

        event.setDeleted(true);
        event.setDeletedAt(Instant.now());

        Event saved = eventRepository.save(event);
        wsNotifier.notifyUser(userId, "events.deleted", EventResponse.from(saved));
        log.debug("Event soft-deleted id={} user={}", eventId, userId);
        return EventResponse.from(saved);
    }

    // Restore

    @Override
    @Transactional
    public EventResponse restore(UUID eventId) {
        UUID  userId = securityUtils.currentUserId();
        Event event  = findOwnedEvent(eventId, userId);

        if (!event.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_EVENT_NOT_FOUND,
                    "Event is not in trash.");
        }

        event.setDeleted(false);
        event.setDeletedAt(null);

        Event saved = eventRepository.save(event);
        wsNotifier.notifyUser(userId, "events.restored", EventResponse.from(saved));
        log.debug("Event restored id={} user={}", eventId, userId);
        return EventResponse.from(saved);
    }

    // Hard delete

    @Override
    @Transactional
    public void hardDelete(UUID eventId) {
        UUID  userId = securityUtils.currentUserId();
        Event event  = findOwnedEvent(eventId, userId);

        if (!event.isDeleted()) {
            throw AppException.badRequest(ErrorCode.RES_EVENT_NOT_FOUND,
                    "Move the event to trash before permanently deleting it.");
        }

        eventRepository.delete(event);
        wsNotifier.notifyUser(userId, "events.deleted", eventId);
        log.debug("Event permanently deleted id={} user={}", eventId, userId);
    }

    // Delete all instances in a recurring series

    @Override
    @Transactional
    public void deleteSeriesFromParent(UUID parentEventId) {
        UUID  userId = securityUtils.currentUserId();
        Event parent = findOwnedEvent(parentEventId, userId);

        // Soft-delete the parent
        parent.setDeleted(true);
        parent.setDeletedAt(Instant.now());
        eventRepository.save(parent);

        // Soft-delete all instances
        List<Event> instances = eventRepository.findActiveInstancesByParentId(parentEventId);
        Instant now = Instant.now();
        for (Event instance : instances) {
            instance.setDeleted(true);
            instance.setDeletedAt(now);
            eventRepository.save(instance);
        }

        wsNotifier.notifyUser(userId, "events.deleted", parentEventId);
        log.debug("Recurring series deleted parentId={} instanceCount={} user={}",
                parentEventId, instances.size(), userId);
    }

    // Private helpers

    private Event buildEvent(User user, EventRequest request) {
        Event.EventBuilder builder = Event.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .location(request.getLocation())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .allDay(Boolean.TRUE.equals(request.getAllDay()))
                .color(request.getColor() != null ? request.getColor() : "#6366F1")
                .recurrenceRule(request.getRecurrenceRule())
                .recurrenceEndAt(request.getRecurrenceEndAt())
                .reminderMinutes(request.getReminderMinutes());

        if (request.getRecurrenceParentId() != null) {
            Event parent = findOwnedEvent(request.getRecurrenceParentId(), user.getId());
            builder.recurrenceParent(parent);
        }

        return builder.build();
    }

    private void applyUpdate(Event event, EventRequest request) {
        if (request.getTitle() != null)
            event.setTitle(request.getTitle().trim());
        if (request.getDescription() != null)
            event.setDescription(request.getDescription());
        if (request.getLocation() != null)
            event.setLocation(request.getLocation());
        if (request.getStartAt() != null)
            event.setStartAt(request.getStartAt());
        if (request.getEndAt() != null)
            event.setEndAt(request.getEndAt());
        if (request.getAllDay() != null)
            event.setAllDay(request.getAllDay());
        if (request.getColor() != null)
            event.setColor(request.getColor());
        if (request.getRecurrenceRule() != null)
            event.setRecurrenceRule(request.getRecurrenceRule());
        if (request.getRecurrenceEndAt() != null)
            event.setRecurrenceEndAt(request.getRecurrenceEndAt());
        if (request.getReminderMinutes() != null)
            event.setReminderMinutes(request.getReminderMinutes());
    }

    private void validateTimeRange(Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw AppException.badRequest(ErrorCode.VAL_REQUEST_BODY_INVALID,
                    "End time must be after start time.");
        }
    }

    private Event findOwnedEvent(UUID eventId, UUID userId) {
        return eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_EVENT_NOT_FOUND));
    }
}
