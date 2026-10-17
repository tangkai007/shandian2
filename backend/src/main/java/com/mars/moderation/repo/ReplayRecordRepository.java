package com.mars.moderation.repo;

import com.mars.moderation.ReplayRecord;

import java.util.List;
import java.util.Optional;

/** 回放仓储（表 {@code replay_record}）。 */
public interface ReplayRecordRepository {

    void save(ReplayRecord replay);

    Optional<ReplayRecord> findById(String replayId);

    /** 录制回调幂等：{@code vod_file_id} 唯一。 */
    Optional<ReplayRecord> findByVodFileId(String vodFileId);

    /** 房间回放列表（按 {@code started_at} 倒序）。 */
    List<ReplayRecord> listByRoom(long roomId);
}
