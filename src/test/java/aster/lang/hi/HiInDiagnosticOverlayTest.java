package aster.lang.hi;

import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconExporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * hi-IN 诊断 overlay 契约（与 aster-lang-locales 的 DiagnosticOverlayContract 同构）。
 *
 * <p>英文基准是 aster-lang-ts 的 shared/error_codes.json，本仓测试资源带一份逐字节副本，
 * 由 verifyErrorCodesBaseline 与 core 的同名副本比对。hi-IN 的 message/help 必须覆盖
 * 全部错误码、不含孤儿码，占位符集合与英文一致；core 的 LexiconExporter 必须把两份
 * overlay 原样嵌入导出的 lexicons.json（aster-lang-ts 从中生成诊断译文表）。
 */
@DisplayName("hi-IN 诊断 overlay 契约")
class HiInDiagnosticOverlayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 与 aster-lang-ts DiagnosticBuilder 的 formatMessage 相同的占位符写法。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

    private record English(String message, String help) {
    }

    @Test
    @DisplayName("注册 diagnosticMessages 与 diagnosticHelp 两个 overlay")
    void registersDiagnosticOverlays() {
        assertThat(new HiInPlugin().getOverlayResources())
                .containsEntry("diagnosticMessages", "overlays/hi-IN/diagnostic-messages.json")
                .containsEntry("diagnosticHelp", "overlays/hi-IN/diagnostic-help.json");
    }

    @Test
    @DisplayName("overlay 路径在 classpath 上只有一份（防与其它语言包同名资源互相遮蔽）")
    void overlayPathsAreUniqueOnClasspath() throws Exception {
        HiInPlugin plugin = new HiInPlugin();
        ClassLoader loader = plugin.getClass().getClassLoader();
        for (String path : plugin.getOverlayResources().values()) {
            assertThat(path).startsWith("overlays/hi-IN/");
            assertThat(Collections.list(loader.getResources(path))).as(path).hasSize(1);
        }
    }

    @Test
    @DisplayName("守卫：磁盘上每个 overlay 文件都必须被注册")
    void everyOverlayFileOnDiskIsRegistered() throws Exception {
        Set<String> registered = new HashSet<>(new HiInPlugin().getOverlayResources().values());
        Path resources = Path.of("src/main/resources");
        Path dir = resources.resolve("overlays");
        assertThat(Files.isDirectory(dir)).as("overlays 目录应存在于 %s", dir.toAbsolutePath()).isTrue();
        List<String> unregistered = new ArrayList<>();
        try (var files = Files.walk(dir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                    .map(f -> resources.relativize(f).toString().replace('\\', '/'))
                    .filter(rel -> !registered.contains(rel))
                    .forEach(unregistered::add);
        }
        assertThat(unregistered).as("磁盘上存在未注册的 overlay 文件").isEmpty();
    }

    @Test
    @DisplayName("英文码表副本含 95 个错误码")
    void baselineHasAllCodes() throws Exception {
        assertThat(baseline()).hasSize(95).containsKeys("E800", "E807", "W800", "W801");
    }

    @Test
    @DisplayName("diagnostic-messages 覆盖全部错误码且占位符与英文一致")
    void messagesCoverBaseline() throws Exception {
        JsonNode root = overlay("diagnosticMessages");
        assertThat(root.path("version").asInt()).isEqualTo(1);
        assertThat(problems(baseline(), root.get("messages"), false)).isEmpty();
    }

    @Test
    @DisplayName("diagnostic-help 覆盖全部错误码且占位符与英文一致")
    void helpCoversBaseline() throws Exception {
        JsonNode root = overlay("diagnosticHelp");
        assertThat(root.path("version").asInt()).isEqualTo(1);
        assertThat(problems(baseline(), root.get("help"), true)).isEmpty();
    }

    @Test
    @DisplayName("检查器能发现缺码、空译文、孤儿码与占位符不一致")
    void checkerReportsProblems() throws Exception {
        Map<String, English> english = new LinkedHashMap<>();
        english.put("E001", new English("expected {expected}, got {actual}", "Check."));
        english.put("E002", new English("'{name}'", "Fix {name}."));
        JsonNode texts = MAPPER.readTree("{\"E001\": \"अपेक्षित {expected}\", \"E003\": \"x\"}");
        assertThat(problems(english, texts, false)).containsExactly(
                "E001: 占位符 [expected] 与英文 [actual, expected] 不一致",
                "E002: 缺少译文",
                "E003: 码表中不存在该错误码");
        assertThat(problems(english, MAPPER.readTree("{\"E001\": \" \", \"E002\": \"{name} ठीक करें\"}"), true))
                .containsExactly("E001: 译文为空");
        assertThat(problems(english, null, false)).containsExactly("译文节点缺失或不是对象");
    }

    @Test
    @DisplayName("LexiconExporter 导出时原样嵌入磁盘上的两个诊断 overlay")
    void exporterEmbedsDiagnosticOverlays() throws Exception {
        HiInPlugin plugin = new HiInPlugin();
        Lexicon lexicon = plugin.createLexicon();
        StringWriter out = new StringWriter();
        new LexiconExporter().export("test", List.of(lexicon), Map.of(lexicon.getId(), plugin), out);
        JsonNode overlays = MAPPER.readTree(out.toString()).path("lexicons").path("hi-IN").path("overlays");
        assertThat(overlays.get("diagnosticMessages")).isEqualTo(overlay("diagnosticMessages"));
        assertThat(overlays.get("diagnosticHelp")).isEqualTo(overlay("diagnosticHelp"));
    }

    private static Map<String, English> baseline() throws Exception {
        try (InputStream in = HiInDiagnosticOverlayTest.class.getResourceAsStream("/diagnostics/error_codes.json")) {
            assertThat(in).as("缺少英文码表副本").isNotNull();
            Map<String, English> result = new LinkedHashMap<>();
            for (JsonNode entry : MAPPER.readTree(in)) {
                result.put(entry.get("code").asText(),
                        new English(entry.get("message").asText(), entry.get("help").asText()));
            }
            return result;
        }
    }

    /** 读取磁盘上的 overlay 源文件，而不是可能被同名资源遮蔽的类加载器资源。 */
    private static JsonNode overlay(String key) throws Exception {
        String path = new HiInPlugin().getOverlayResources().get(key);
        assertThat(path).as("未注册 overlay %s", key).isNotNull();
        Path file = Path.of("src/main/resources", path);
        assertThat(file).as("overlay 源文件不存在: %s", file.toAbsolutePath()).isRegularFile();
        return MAPPER.readTree(file.toFile());
    }

    private static List<String> problems(Map<String, English> english, JsonNode texts, boolean useHelp) {
        List<String> problems = new ArrayList<>();
        if (texts == null || !texts.isObject()) {
            problems.add("译文节点缺失或不是对象");
            return problems;
        }
        for (Map.Entry<String, English> e : english.entrySet()) {
            JsonNode node = texts.get(e.getKey());
            if (node == null) {
                problems.add(e.getKey() + ": 缺少译文");
                continue;
            }
            if (!node.isTextual() || node.asText().isBlank()) {
                problems.add(e.getKey() + ": 译文为空");
                continue;
            }
            Set<String> expected = placeholders(useHelp ? e.getValue().help() : e.getValue().message());
            Set<String> actual = placeholders(node.asText());
            if (!expected.equals(actual)) {
                problems.add(e.getKey() + ": 占位符 " + actual + " 与英文 " + expected + " 不一致");
            }
        }
        for (Iterator<String> it = texts.fieldNames(); it.hasNext(); ) {
            String code = it.next();
            if (!english.containsKey(code)) {
                problems.add(code + ": 码表中不存在该错误码");
            }
        }
        return problems;
    }

    private static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
