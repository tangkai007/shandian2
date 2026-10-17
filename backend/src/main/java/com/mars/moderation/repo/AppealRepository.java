package com.mars.moderation.repo;

import com.mars.moderation.Appeal;

import java.util.List;
import java.util.Optional;

/** 申诉仓储（表 {@code appeal}，不可删）。 */
public interface AppealRepository {

    void save(Appeal appeal);

    Optional<Appeal> findById(String appealId);

    /** 同一处置仅可申诉一次（{@code action_id} 唯一）。 */
    Optional<Appeal> findByActionId(String actionId);

    /** 队列：按状态（{@code null} 取全部）与创建时间升序。 */
    List<Appeal> listByStatus(Appeal.AppealStatus status);
}
