package com.mars.admin.repo;

import com.mars.admin.model.RealnameRecord;

import java.util.Optional;

/** 实名核验仓储（表 {@code user_auth}），契约 §4.1 API-AUTH-10/11。 */
public interface RealnameRepository {

    Optional<RealnameRecord> find(long userId);

    void save(RealnameRecord record);
}
