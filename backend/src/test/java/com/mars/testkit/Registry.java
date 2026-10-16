package com.mars.testkit;

import java.util.ArrayList;
import java.util.List;

/** 规格登记表：把各模块的 spec 集合起来一次性运行。 */
public final class Registry {

    public record Named(String name, Spec spec) {
    }

    private final List<Named> named = new ArrayList<>();

    public Registry add(String name, Spec spec) {
        named.add(new Named(name, spec));
        return this;
    }

    public List<Named> named() {
        return named;
    }
}
