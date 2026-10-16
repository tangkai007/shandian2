package com.mars.spec;

import com.mars.common.ErrorCode;
import com.mars.interaction.DanmakuDecision;
import com.mars.interaction.repo.RoomMuteRepository;
import com.mars.interaction.service.DanmakuGuard;
import com.mars.interaction.service.SensitiveWordCatalog;
import com.mars.interaction.service.SensitiveWordFilter;
import com.mars.interaction.service.SensitiveWordSource;
import com.mars.interaction.service.SensitiveWordTrie;
import com.mars.interaction.service.ResourceSensitiveWordSource;
import com.mars.interaction.web.RoomDanmakuPolicies;
import com.mars.testkit.Registry;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 弹幕合规 spec（<b>AC-4</b>：含敏感词的弹幕不落公屏且有明确反馈；禁言用户被限制发言）。
 *
 * <p>覆盖增量 3 新接入的两处事实来源：敏感词词库（前缀树 + 归一化）与 {@code room_mute} 查询。
 */
public final class SensitiveWordSpecs {

    private SensitiveWordSpecs() {
    }

    /** 内存版禁言仓储（测试替身）。 */
    private static final class FakeRoomMutes implements RoomMuteRepository {
        private final Set<String> muted = new java.util.HashSet<>();

        void mute(long roomId, long userId) {
            muted.add(roomId + ":" + userId);
        }

        @Override
        public boolean isMuted(long roomId, long userId, LocalDateTime nowUtc) {
            return muted.contains(roomId + ":" + userId);
        }
    }

    private static SensitiveWordCatalog catalogOf(Collection<String> words) {
        SensitiveWordSource source = () -> words;
        SensitiveWordCatalog catalog = new SensitiveWordCatalog(source, 3600L);
        catalog.rebuild();
        return catalog;
    }

    public static void register(Registry registry) {

        registry.add("AC-4 · 敏感词前缀树：命中 / 未命中 / 词条计数", a -> {
            SensitiveWordFilter filter = new SensitiveWordTrie(List.of("违禁品", "代充", "外挂"));

            a.isTrue(filter.hit("这里有违禁品出售"), "命中「违禁品」");
            a.isTrue(filter.hit("求代充"), "命中「代充」");
            a.isTrue(filter.hit("开外挂"), "命中「外挂」");
            a.isFalse(filter.hit("今天直播很开心"), "正常内容不命中");
            a.isFalse(filter.hit(""), "空串不命中");
            a.isFalse(filter.hit(null), "null 不命中");
            a.eqLong((long) filter.size(), 3L, "词条计数");

            SensitiveWordTrie empty = new SensitiveWordTrie(List.of());
            a.isFalse(empty.hit("任何内容"), "空词表不命中（不误杀）");
            a.eqLong((long) empty.size(), 0L, "空词表计数 0");
        });

        registry.add("AC-4 · 绕过对抗：大小写 / 空格 / 标点归一化后仍命中", a -> {
            SensitiveWordFilter filter = new SensitiveWordTrie(List.of("代充", "abc"));

            a.isTrue(filter.hit("代 充"), "插空格仍命中");
            a.isTrue(filter.hit("代-充"), "插标点仍命中");
            a.isTrue(filter.hit("代　充"), "全角空格仍命中");
            a.isTrue(filter.hit("A-B-C"), "大小写+标点规避仍命中「abc」");
            a.isTrue(filter.hit("ABC"), "纯大写仍命中");
            a.eq(filter.firstHit("我要代　充").orElse(null), "代充", "返回归一化后的命中词");
        });

        registry.add("AC-4 · 前缀树：重叠词与长尾词不误判", a -> {
            SensitiveWordFilter filter = new SensitiveWordTrie(List.of("加微信", "加微信转账"));

            a.isTrue(filter.hit("请加微信转账"), "命中最长词");
            a.isTrue(filter.hit("加微信"), "命中短词");
            a.isFalse(filter.hit("加好友"), "未登记词不命中");
            a.eq(filter.firstHit("加微信转账").orElse(null), "加微信", "按从左到右最先闭合的词返回");
        });

        registry.add("AC-4 · 敏感词拒绝：明文不落公屏且给出明确反馈码", a -> {
            SensitiveWordCatalog catalog = catalogOf(List.of("违禁品", "代充"));
            RoomDanmakuPolicies policies = new RoomDanmakuPolicies();
            DanmakuGuard guard = new DanmakuGuard(catalog, new FakeRoomMutes(), policies,
                    Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC));

            DanmakuDecision decision = guard.check(9001L, 501L, true, "来买违禁品");
            a.isTrue(decision.outcome() == DanmakuDecision.Outcome.REJECTED, "命中敏感词 → 拒绝");
            a.eq(decision.errorCode(), ErrorCode.PARAM_INVALID, "复用 40001（契约 §2.7 备注）");
            a.isFalse(decision.delivered(), "不投递（不落公屏）");

            DanmakuDecision clean = guard.check(9001L, 502L, true, "主播唱得真好");
            a.isTrue(clean.accepted(), "正常弹幕放行");
        });

        registry.add("AC-4 · 禁言查询接入：被禁言用户 → 40300", a -> {
            FakeRoomMutes mutes = new FakeRoomMutes();
            mutes.mute(9001L, 601L);
            DanmakuGuard guard = new DanmakuGuard(catalogOf(List.of("违禁品")), mutes,
                    new RoomDanmakuPolicies(),
                    Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC));

            DanmakuDecision muted = guard.check(9001L, 601L, true, "你好");
            a.isTrue(muted.outcome() == DanmakuDecision.Outcome.REJECTED, "被禁言 → 拒绝");
            a.eq(muted.errorCode(), ErrorCode.FORBIDDEN, "禁言 → 40300");

            DanmakuDecision otherRoom = guard.check(9002L, 601L, true, "你好");
            a.isTrue(otherRoom.accepted(), "禁言仅作用于本直播间（BR-7）");
        });

        registry.add("AC-4 · 内置词表资源可加载（classpath sensitive-words.txt）", a -> {
            Collection<String> words = new ResourceSensitiveWordSource().load();
            a.isTrue(words.size() >= 5, "内置词表非空（实测 " + words.size() + " 条）");
            a.isTrue(words.contains("违禁品"), "含基线词条「违禁品」");
            a.isFalse(words.stream().anyMatch(word -> word.startsWith("#")), "注释行未被当作词条");

            SensitiveWordFilter filter = new SensitiveWordTrie(words);
            a.isTrue(filter.hit("出售违禁品"), "内置词表可命中");
            a.isFalse(filter.hit("这是一条正常弹幕"), "内置词表不误杀正常内容");
        });

        registry.add("AC-4 · 词库可热重建（运营改词不改代码）", a -> {
            List<String> words = new java.util.ArrayList<>(List.of("违禁品"));
            SensitiveWordCatalog catalog = new SensitiveWordCatalog(() -> words, 3600L);
            catalog.rebuild();
            a.isFalse(catalog.filter().hit("代充"), "重建前不含「代充」");

            words.add("代充");
            catalog.rebuild();
            a.isTrue(catalog.filter().hit("来代充"), "重建后立即生效");
        });
    }
}
