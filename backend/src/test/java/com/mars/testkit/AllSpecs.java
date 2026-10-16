package com.mars.testkit;

import com.mars.spec.AccountSpecs;
import com.mars.spec.CommonSpecs;
import com.mars.spec.EarningSpecs;
import com.mars.spec.FundSpecs;
import com.mars.spec.InteractionSpecs;
import com.mars.spec.LiveSpecs;
import com.mars.spec.SettlementSpecs;

import java.util.List;

/**
 * 全部 spec 的入口。CI 侧由 Gradle 任务 {@code specCheck} 调用；
 * 无 Gradle 的环境直接 {@code java -cp <classes> com.mars.testkit.AllSpecs}。
 *
 * <p>退出码：全部通过 0；有失败 1（便于流水线判定）。
 */
public final class AllSpecs {

    public static void main(String[] args) {
        Registry registry = new Registry();
        CommonSpecs.register(registry);
        SettlementSpecs.register(registry);
        FundSpecs.register(registry);
        InteractionSpecs.register(registry);
        AccountSpecs.register(registry);
        EarningSpecs.register(registry);
        LiveSpecs.register(registry);

        List<Registry.Named> specs = registry.named();
        int totalChecks = 0;
        int failed = 0;
        System.out.println("== MARS-1 backend spec suite ==");
        for (Registry.Named named : specs) {
            Assert a = new Assert();
            try {
                named.spec().run(a);
            } catch (Throwable t) {
                a.fail("spec threw " + t.getClass().getName() + ": " + t.getMessage());
            }
            totalChecks += a.checks();
            if (a.ok()) {
                System.out.printf("[PASS] %-58s (%d checks)%n", named.name(), a.checks());
            } else {
                failed++;
                System.out.printf("[FAIL] %-58s (%d checks)%n", named.name(), a.checks());
                for (String failure : a.failures()) {
                    System.out.println("        - " + failure);
                }
            }
        }
        System.out.printf("-- specs: %d, checks: %d, failed specs: %d%n", specs.size(), totalChecks, failed);
        if (failed > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS (all specs green)");
    }

    private AllSpecs() {
    }
}
