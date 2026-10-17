package com.mars.moderation;

import java.util.List;

/**
 * 处置提交入参（契约 §4.9 API-ADM-08 请求体 + S1b D-ADM-002）。
 *
 * <p>操作者身份由服务端从 {@code AuthContext} 注入（<b>不信任前端传参</b>）。
 */
public record DispositionCommand(
        String taskId,
        Long roomId,
        Long sessionId,
        Long targetUserId,
        ModerationActionType actionType,
        String reasonCode,
        String remark,
        List<String> evidenceUrls,
        List<String> notifyChannels,
        long operatorId,
        String operatorName) {

    public DispositionCommand {
        evidenceUrls = evidenceUrls == null ? List.of() : List.copyOf(evidenceUrls);
        notifyChannels = notifyChannels == null ? List.of() : List.copyOf(notifyChannels);
    }
}
