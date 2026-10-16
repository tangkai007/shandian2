package com.mars.asset.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 风控画像<b>占位实现</b>：返回「未实名、非未成年、当日 0 币」。
 *
 * <p><b>fail-safe 取向</b>：在真实画像接入前，OP-21 开关（默认开）会因
 * {@code realnameVerified = false} 而拦截打赏（{@code 40350}），
 * 即「宁可拦住，也不放开限额」。生产 profile 下不装配（必须提供真实实现）。
 */
@Component
@Profile({"local", "dev", "test", "default"})
public class DefaultViewerRiskProfile implements ViewerRiskProfile {

    @Override
    public Profile of(long userId) {
        return new Profile(false, false, 0L);
    }
}
