package com.mars.testkit;

/** 一个可命名的测试规格。 */
@FunctionalInterface
public interface Spec {
    void run(Assert a);
}
