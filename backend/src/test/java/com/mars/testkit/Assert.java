package com.mars.testkit;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量断言器：收集失败而不中断，便于一次跑出全部问题。
 *
 * <p>刻意<b>零依赖</b>——本仓库当前环境无 Gradle / JUnit 依赖缓存，spec 需能被
 * {@code javac + java} 直接编译运行；断言语义与 JUnit 5 的 {@code assertEquals} 一致，
 * 后续迁移到 JUnit 5 时逐个映射即可。
 */
public final class Assert {

    private int checks;
    private final List<String> failures = new ArrayList<>();

    public void eq(Object actual, Object expected, String what) {
        checks++;
        boolean same = (actual == null) ? expected == null : actual.equals(expected);
        if (!same) {
            failures.add(what + " — expected [" + expected + "] but was [" + actual + "]");
        }
    }

    public void eqLong(long actual, long expected, String what) {
        checks++;
        if (actual != expected) {
            failures.add(what + " — expected [" + expected + "] but was [" + actual + "]");
        }
    }

    public void isTrue(boolean condition, String what) {
        checks++;
        if (!condition) {
            failures.add(what + " — expected true but was false");
        }
    }

    public void isFalse(boolean condition, String what) {
        checks++;
        if (condition) {
            failures.add(what + " — expected false but was true");
        }
    }

    public void notNull(Object value, String what) {
        checks++;
        if (value == null) {
            failures.add(what + " — expected non-null");
        }
    }

    public void isNull(Object value, String what) {
        checks++;
        if (value != null) {
            failures.add(what + " — expected null but was [" + value + "]");
        }
    }

    public void fail(String what) {
        checks++;
        failures.add(what);
    }

    /** 断言闭包：{@code body} 抛出 {@code expected} 类型异常则通过。 */
    public <T extends Throwable> void throwsType(Class<T> expected, Runnable body, String what) {
        checks++;
        try {
            body.run();
            failures.add(what + " — expected " + expected.getSimpleName() + " but nothing was thrown");
        } catch (Throwable thrown) {
            if (!expected.isInstance(thrown)) {
                failures.add(what + " — expected " + expected.getSimpleName()
                        + " but was " + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
            }
        }
    }

    public int checks() {
        return checks;
    }

    public List<String> failures() {
        return failures;
    }

    public boolean ok() {
        return failures.isEmpty();
    }
}
