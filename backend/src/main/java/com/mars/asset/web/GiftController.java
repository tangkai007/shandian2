package com.mars.asset.web;

import com.mars.asset.gift.GiftItem;
import com.mars.asset.gift.GiftSendCommand;
import com.mars.asset.gift.GiftSendResult;
import com.mars.asset.gift.GiftSendService;
import com.mars.asset.mapper.GiftMapper;
import com.mars.common.ApiResponse;
import com.mars.common.security.AuthContext;
import com.mars.common.security.RequireRoles;
import com.mars.common.web.RequireIdempotencyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 礼物端点（契约 §4.6 API-ASSET-01/02 · <b>AC-5</b>）。
 *
 * <p>赠送走 {@link GiftSendService}：同一事务内扣币 + 流水 + 收益 + 订单，幂等键必填。
 */
@RestController
@RequestMapping("/api/v1/gifts")
public class GiftController {

    private final GiftMapper giftMapper;
    private final GiftSendService giftSendService;
    private final ViewerRiskProfile riskProfile;

    public GiftController(GiftMapper giftMapper,
                          GiftSendService giftSendService,
                          ViewerRiskProfile riskProfile) {
        this.giftMapper = giftMapper;
        this.giftSendService = giftSendService;
        this.riskProfile = riskProfile;
    }

    /** 契约 §4.6 API-ASSET-01（仅返回在售礼物）。 */
    @GetMapping
    public ApiResponse<List<GiftItem>> catalog() {
        return ApiResponse.ok(giftMapper.findOnSale());
    }

    public record GiftSendRequest(@Positive long sessionId, @Positive long giftId,
                                  @Positive int count, @Positive long toAnchorId) {
    }

    /** 契约 §4.6 API-ASSET-02：赠送（幂等必填；未成年限额与 OP-21 前置在领域层校验）。 */
    @PostMapping("/send")
    @RequireIdempotencyKey
    @RequireRoles({"VIEWER", "ANCHOR"})
    public ApiResponse<GiftSendResult> send(@Valid @RequestBody GiftSendRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        long senderId = AuthContext.requireUserId();
        ViewerRiskProfile.Profile profile = riskProfile.of(senderId);
        GiftSendCommand command = new GiftSendCommand(
                idempotencyKey, request.sessionId(), senderId, request.toAnchorId(),
                request.giftId(), request.count(),
                profile.minor(), profile.realnameVerified(), profile.todayConsumedCoin());
        return ApiResponse.ok(giftSendService.send(command));
    }
}
