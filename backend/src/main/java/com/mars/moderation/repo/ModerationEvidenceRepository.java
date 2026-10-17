package com.mars.moderation.repo;

import com.mars.moderation.ModerationEvidence;

import java.util.Optional;

/** 证据快照仓储（表 {@code moderation_evidence}，与处置一一对应，不可删）。 */
public interface ModerationEvidenceRepository {

    void save(ModerationEvidence evidence);

    /** 按处置 ID 取证据（API-ADM-09）。 */
    Optional<ModerationEvidence> findByActionId(String actionId);
}
