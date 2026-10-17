package com.mars.admin.service;

import com.mars.admin.model.GiftAdminItem;
import com.mars.admin.repo.GiftAdminRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AdminOperator;
import com.mars.moderation.AuditRecorder;

import java.util.List;

/**
 * 后台礼物目录管理（契约 §4.9 API-ADM-14/15/16 · FR-5.2）。
 *
 * <p>新建 / 更新落审计（{@code GIFT_CREATE} / {@code GIFT_UPDATE}）。校验：名称非空、价格 ≥ 0、
 * 状态 ∈ {@code ON/OFF}。
 */
public final class GiftAdminService {

    private static final String TARGET_TYPE = "GIFT";

    private final GiftAdminRepository gifts;
    private final AuditRecorder audit;

    public GiftAdminService(GiftAdminRepository gifts, AuditRecorder audit) {
        this.gifts = gifts;
        this.audit = audit;
    }

    /** API-ADM-14。 */
    public List<GiftAdminItem> list() {
        return gifts.listAll();
    }

    /** API-ADM-15：新建（默认在售）。 */
    public GiftAdminItem create(String name, long priceCoin, String effectType, AdminOperator operator) {
        GiftAdminItem draft = new GiftAdminItem(0L, requireName(name), requirePrice(priceCoin),
                effectType == null ? "" : effectType, GiftAdminItem.STATUS_ON);
        long id = gifts.insert(draft);
        GiftAdminItem created = new GiftAdminItem(id, draft.name(), draft.priceCoin(),
                draft.effectType(), draft.status());
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_GIFT_CREATE,
                TARGET_TYPE, String.valueOf(id), created.name(), null, null, null, operator.ip());
        return created;
    }

    /** API-ADM-16：局部更新（{@code null} 字段保持不变）。 */
    public GiftAdminItem update(long giftId, String name, Long priceCoin, String status, AdminOperator operator) {
        GiftAdminItem current = gifts.findById(giftId).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "礼物不存在", "gift_id", "not_found"));
        String nextName = name == null ? current.name() : requireName(name);
        long nextPrice = priceCoin == null ? current.priceCoin() : requirePrice(priceCoin);
        String nextStatus = status == null ? current.status() : requireStatus(status);
        GiftAdminItem updated = new GiftAdminItem(giftId, nextName, nextPrice,
                current.effectType(), nextStatus);
        gifts.update(updated);
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_GIFT_UPDATE,
                TARGET_TYPE, String.valueOf(giftId), updated.name(), null, null, null, operator.ip());
        return updated;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "礼物名不能为空", "name", "blank");
        }
        return name.trim();
    }

    private static long requirePrice(long priceCoin) {
        if (priceCoin < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "价格不能为负", "price_coin", "negative");
        }
        return priceCoin;
    }

    private static String requireStatus(String status) {
        if (!GiftAdminItem.STATUS_ON.equals(status) && !GiftAdminItem.STATUS_OFF.equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态非法", "status", "invalid");
        }
        return status;
    }
}
