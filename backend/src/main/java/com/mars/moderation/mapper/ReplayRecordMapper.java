package com.mars.moderation.mapper;

import com.mars.moderation.ReplayRecord;
import com.mars.moderation.repo.ReplayRecordRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 回放 Mapper（表 {@code replay_record}，{@code vod_file_id} 唯一 —— 录制回调幂等）。
 */
@Mapper
public interface ReplayRecordMapper extends ReplayRecordRepository {

    String COLUMNS = "replay_id AS replayId, session_id AS sessionId, room_id AS roomId, "
            + "anchor_id AS anchorId, vod_file_id AS vodFileId, play_url AS playUrl, "
            + "duration_sec AS durationSec, started_at AS startedAt, ended_at AS endedAt, "
            + "expire_at AS expireAt, status AS status";

    @Insert("INSERT INTO replay_record (replay_id, session_id, room_id, anchor_id, vod_file_id, "
            + "play_url, duration_sec, started_at, ended_at, expire_at, status) "
            + "VALUES (#{replayId}, #{sessionId}, #{roomId}, #{anchorId}, #{vodFileId}, "
            + "#{playUrl}, #{durationSec}, #{startedAt}, #{endedAt}, #{expireAt}, #{status}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), play_url = VALUES(play_url), "
            + "expire_at = VALUES(expire_at), ended_at = VALUES(ended_at), "
            + "duration_sec = VALUES(duration_sec)")
    int upsert(ReplayRecord replay);

    @Override
    default void save(ReplayRecord replay) {
        upsert(replay);
    }

    @Override
    @Select("SELECT " + COLUMNS + " FROM replay_record WHERE replay_id = #{replayId}")
    Optional<ReplayRecord> findById(String replayId);

    @Override
    @Select("SELECT " + COLUMNS + " FROM replay_record WHERE vod_file_id = #{vodFileId}")
    Optional<ReplayRecord> findByVodFileId(String vodFileId);

    @Override
    @Select("SELECT " + COLUMNS + " FROM replay_record WHERE room_id = #{roomId} "
            + "ORDER BY started_at DESC")
    List<ReplayRecord> listByRoom(long roomId);
}
