package com.mars.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 闪电AI2.0 · 瞬间直播 后端入口（模块化单体，S1a v0.4 §5.1）。
 *
 * <p>分层：{@code app}（入口/配置）→ 各业务模块的 {@code web}（适配）→ {@code service}（领域）
 * → {@code mapper}（持久化）。领域判定不依赖 Spring，便于直编直跑（见 {@code specCheck}）。
 */
@SpringBootApplication(scanBasePackages = "com.mars")
@MapperScan("com.mars.**.mapper")
@ConfigurationPropertiesScan("com.mars")
@EnableTransactionManagement
public class MarsLiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarsLiveApplication.class, args);
    }
}
