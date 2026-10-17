package com.mars.admin.repo;

import com.mars.admin.model.GiftAdminItem;

import java.util.List;
import java.util.Optional;

/** 后台礼物目录仓储（表 {@code gift}），契约 §4.9 API-ADM-14/15/16。 */
public interface GiftAdminRepository {

    List<GiftAdminItem> listAll();

    Optional<GiftAdminItem> findById(long giftId);

    /** 新建，返回自增主键。 */
    long insert(GiftAdminItem item);

    /** 全字段更新（按主键）。 */
    void update(GiftAdminItem item);
}
