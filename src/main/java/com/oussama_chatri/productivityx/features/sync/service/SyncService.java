package com.oussama_chatri.productivityx.features.sync.service;

import com.oussama_chatri.productivityx.features.sync.dto.response.DeltaSyncResponse;

import java.time.Instant;

public interface SyncService {

    DeltaSyncResponse delta(Instant since);
}
