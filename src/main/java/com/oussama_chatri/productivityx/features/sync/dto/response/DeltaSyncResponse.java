package com.oussama_chatri.productivityx.features.sync.dto.response;

import com.oussama_chatri.productivityx.features.events.dto.response.EventResponse;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.pomodoro.dto.response.PomodoroSessionResponse;
import com.oussama_chatri.productivityx.features.tasks.dto.response.TaskResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class DeltaSyncResponse {

    private final List<NoteResponse>            notes;
    private final List<TaskResponse>            tasks;
    private final List<EventResponse>           events;
    private final List<PomodoroSessionResponse> pomodoroSessions;
    private final Instant                       syncedAt;
    private final int                           totalChanges;
}
