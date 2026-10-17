package com.mars.moderation.repo;

import com.mars.moderation.ModerationAction;

import java.util.List;
import java.util.Optional;

/** 处置记录仓储（表 {@code moderation_action}，仅追加不可删）。 */
public interface ModerationActionRepository {

    void save(ModerationAction action);

    Optional<ModerationAction> findById(String actionId);

    List<ModerationAction> listByTask(String taskId);
}
