package com.mars.spec;

import com.mars.testkit.Registry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 装配层配对自查 spec（**B-3 回归守卫**）。
 *
 * <p><b>要拦的缺陷类型</b>：`com.mars.**.repo` 下声明了 `*Repository` 端口，却没有对应的
 * {@code @Mapper} 实现。`@MapperScan("com.mars.**.mapper")` 只注册 mapper 包里的接口，
 * 于是该端口在容器里无 bean —— {@code DomainConfig} 一旦按端口注入，应用<b>启动即失败</b>；
 * 而零依赖 spec 与 `clean check` 绿灯照样全绿（它们不装配 Spring 上下文）。B-3（缺
 * `IapTransactionMapper`）正是这一类。
 *
 * <p><b>为什么是源码扫描而不是反射 / Spring 测试</b>：
 * <ul>
 *   <li>反射扫 classpath 在「零依赖直编直跑」（只编领域核心、无 MyBatis jar）下会看不到 mapper，
 *       只能退化成「跳过」，等于不检查；</li>
 *   <li>Spring context 测试需要 MySQL / Redis，进不了 CI（Leader 已明确排除）。</li>
 * </ul>
 * 源码扫描只依赖 JDK 的 {@code java.nio.file}，在 Gradle `specCheck` 与手工 `javac` 两条路径下
 * 结论一致，且检查的正是「mapper 源文件是否存在且确实 extends 该端口」这一事实。
 *
 * <p>覆盖边界（如实登记）：本 spec 只能保证「端口 → mapper 声明」的<b>结构性</b>配对，
 * <b>不能</b>校验 mapper 的 SQL 列名是否与迁移脚本一致（那需要连库或解析 SQL）。
 * B-3 的「`iap_transaction` 缺 `amount_fen`/`coin_amount`」属后者，由 V5 迁移修复，
 * 本 spec 不覆盖。
 */
public final class MapperPairingSpecs {

    /** 源根候选（Gradle `specCheck` 的 CWD = `backend/`；手工直编时可能停在仓库根）。 */
    private static final Path[] SOURCE_ROOT_CANDIDATES = {
            Paths.get("src", "main", "java"),
            Paths.get("backend", "src", "main", "java"),
    };

    /** B-3 回归守卫：该端口必须始终存在且配对。 */
    private static final String IAP_REPOSITORY = "com.mars.asset.repo.IapTransactionRepository";

    private static final Pattern INTERFACE_DECL =
            Pattern.compile("\\binterface\\s+\\w+([^{]*)\\{");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<String> JAVA_KEYWORDS = Set.of("extends", "implements", "permits");

    private MapperPairingSpecs() {
    }

    public static void register(Registry registry) {
        registry.add("装配自查 · 每个 *Repository 都有配对的 *Mapper（拦 B-3 类缺陷）", a -> {
            Optional<Path> root = resolveSourceRoot();
            a.isTrue(root.isPresent(), "定位 src/main/java 源根（cwd="
                    + Paths.get("").toAbsolutePath() + "）");
            if (root.isEmpty()) {
                a.fail("未找到 src/main/java：请从仓库根或 backend/ 目录运行 specCheck");
                return;
            }
            Path base = root.get();
            List<Path> javaFiles = listJavaFiles(base);
            a.isTrue(!javaFiles.isEmpty(), "扫描到 java 源文件（数量=" + javaFiles.size() + "）");

            // 1) 收集 com.mars.**.repo 下的 *Repository 端口
            Map<String, Path> repositories = new LinkedHashMap<>();
            for (Path file : javaFiles) {
                if (!"repo".equals(parentName(file))) {
                    continue;
                }
                if (!file.getFileName().toString().endsWith("Repository.java")) {
                    continue;
                }
                repositories.put(toFqn(base, file), file);
            }
            a.isTrue(!repositories.isEmpty(),
                    "扫描到 *Repository 端口（数量=" + repositories.size() + "）");

            // 2) 收集 mapper 声明的父类型（只看 interface 声明的 extends 子句）
            Set<String> declaredSupertypes = new LinkedHashSet<>();
            int mapperCount = 0;
            for (Path file : javaFiles) {
                if (!"mapper".equals(parentName(file))) {
                    continue;
                }
                mapperCount++;
                declaredSupertypes.addAll(supertypesOf(read(file)));
            }
            a.isTrue(mapperCount > 0, "扫描到 *Mapper 文件（数量=" + mapperCount + "）");

            // 3) 配对断言：每个端口都必须被某个 mapper 声明为自己的父类型
            List<String> unpaired = new ArrayList<>();
            int paired = 0;
            for (String fqn : repositories.keySet()) {
                if (declaredSupertypes.contains(simpleName(fqn))) {
                    paired++;
                } else {
                    unpaired.add(fqn);
                }
            }
            a.isTrue(unpaired.isEmpty(),
                    "每个仓储端口都有配对 mapper；未配对=" + unpaired);
            a.eqLong(paired, repositories.size(),
                    "配对数量 == 端口数量（" + paired + "/" + repositories.size() + "）");

            // 4) canary：B-3 回归守卫 —— IAP 端口存在且已配对
            a.isTrue(repositories.containsKey(IAP_REPOSITORY),
                    "扫描集合包含 " + IAP_REPOSITORY);
            a.isTrue(declaredSupertypes.contains(simpleName(IAP_REPOSITORY)),
                    "存在 mapper 声明 extends IapTransactionRepository（B-3 回归守卫）");
        });
    }

    // ------------------------------------------------------------------
    // 源码扫描工具
    // ------------------------------------------------------------------

    private static Optional<Path> resolveSourceRoot() {
        for (Path candidate : SOURCE_ROOT_CANDIDATES) {
            if (Files.isDirectory(candidate.resolve("com").resolve("mars"))) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && dir != null; depth++) {
            Optional<Path> found = firstSourceRootUnder(dir);
            if (found.isPresent()) {
                return found;
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    private static Optional<Path> firstSourceRootUnder(Path dir) {
        for (Path candidate : SOURCE_ROOT_CANDIDATES) {
            Path probe = dir.resolve(candidate).resolve("com").resolve("mars");
            if (Files.isDirectory(probe)) {
                return Optional.of(dir.resolve(candidate).toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static List<Path> listJavaFiles(Path base) {
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new UncheckedIOException("扫描源根失败: " + base, ex);
        }
    }

    private static String parentName(Path file) {
        Path parent = file.getParent();
        return parent == null ? "" : parent.getFileName().toString();
    }

    /** {@code <base>/com/mars/asset/repo/X.java} → {@code com.mars.asset.repo.X}。 */
    private static String toFqn(Path base, Path file) {
        String relative = base.relativize(file).toString()
                .replace('\\', '/')
                .replaceAll("\\.java$", "");
        return relative.replace('/', '.');
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    /**
     * 抽出类型声明的 {@code extends} 子句中的标识符。
     *
     * <p>先剥注释，避免把注释里提到的端口名当成实现；再只取「第一个 interface 声明到其 `{`」之间的名字。
     */
    private static Set<String> supertypesOf(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher declaration = INTERFACE_DECL.matcher(stripComments(source));
        if (!declaration.find()) {
            return names;
        }
        Matcher identifier = IDENTIFIER.matcher(declaration.group(1));
        while (identifier.find()) {
            String name = identifier.group();
            if (!JAVA_KEYWORDS.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取失败: " + file, ex);
        }
    }
}
