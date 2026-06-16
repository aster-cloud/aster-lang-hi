package aster.lang.hi;

import aster.core.canonicalizer.Canonicalizer;
import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;
import aster.core.lexicon.SemanticTokenKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 印地语语言包插件冒烟测试。
 *
 * <p>验证 SPI 发现、JSON 加载、关键词（天城文）、danda 句末符配置。
 */
@DisplayName("HiInPlugin 冒烟测试")
class HiInPluginTest {

    private static Lexicon lexicon;
    private static HiInPlugin plugin;

    @BeforeAll
    static void loadPlugin() {
        plugin = (HiInPlugin) ServiceLoader.load(LexiconPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p instanceof HiInPlugin)
                .findFirst()
                .orElseThrow(() -> new AssertionError("HiInPlugin 未通过 SPI 发现"));
        lexicon = plugin.createLexicon();
    }

    @Test
    @DisplayName("SPI 能发现 HiInPlugin")
    void spiDiscoversPlugin() {
        assertThat(plugin).isNotNull();
        assertThat(plugin.providedLexiconIds()).containsExactly("hi-IN");
    }

    @Test
    @DisplayName("词法表 id 与显示名为印地语")
    void lexiconMetadata() {
        assertThat(lexicon.getId()).isEqualTo("hi-IN");
        assertThat(lexicon.getName()).isEqualTo("हिन्दी");
    }

    @Test
    @DisplayName("关键词为天城文")
    void keywordsAreDevanagari() {
        assertThat(lexicon.getKeywords().get(SemanticTokenKind.MODULE_DECL)).isEqualTo("मॉड्यूल");
        assertThat(lexicon.getKeywords().get(SemanticTokenKind.FUNC_TO)).isEqualTo("नियम");
        assertThat(lexicon.getKeywords().get(SemanticTokenKind.IF)).isEqualTo("यदि");
        assertThat(lexicon.getKeywords().get(SemanticTokenKind.RETURN)).isEqualTo("लौटाएं");
    }

    @Test
    @DisplayName("句末符为天城文 danda「।」")
    void statementEndIsDanda() {
        assertThat(lexicon.getPunctuation().statementEnd()).isEqualTo("।");
    }

    @Test
    @DisplayName("印地语包无需任何语法变换器（getTransformers 为空）")
    void noTransformers() {
        assertThat(plugin.getTransformers()).isEmpty();
    }

    // ============================================================
    // 最长匹配（longest-match）回归守卫
    //
    // 多个印地语关键词的值互为词前缀（word-prefix），Canonicalizer 必须按关键词
    // 长度降序、最长优先地翻译，否则较短的关键词会先吃掉较长关键词的前缀子串，
    // 留下游离片段或错误的语义记号：
    //   BACKOFF = "प्रतीक्षा"      是 AWAIT = "प्रतीक्षा करें" 的前缀
    //   INT_TYPE = "पूर्णांक"      是 INTEGER_DIVIDED_BY = "पूर्णांक भाग" 的前缀
    // 这里直接用 core 的 Canonicalizer 端到端断言：长形式必须整体翻成对应英语
    // 关键词/符号，而不是 "短关键词 + 残留片段"。core 端若回归（取消最长匹配排序）
    // 本测试会失败。
    // ============================================================

    @Test
    @DisplayName("最长匹配：AWAIT「प्रतीक्षा करें」不被 BACKOFF「प्रतीक्षा」抢先")
    void longestMatchAwaitBeatsBackoff() {
        Canonicalizer canonicalizer = new Canonicalizer(lexicon);

        // AWAIT 的长形式整体翻成 await，且不残留 BACKOFF 的英语形式 backoff。
        String await = canonicalizer.canonicalize("प्रतीक्षा करें");
        assertThat(await).contains("await");
        assertThat(await).doesNotContain("backoff");

        // 短形式仍应翻成 backoff（确认两者确实是不同关键词，而非合并）。
        String backoff = canonicalizer.canonicalize("प्रतीक्षा");
        assertThat(backoff).contains("backoff");
        assertThat(backoff).doesNotContain("await");
    }

    @Test
    @DisplayName("最长匹配：INTEGER_DIVIDED_BY「पूर्णांक भाग」不被 INT_TYPE「पूर्णांक」抢先")
    void longestMatchIntegerDividedByBeatsIntType() {
        Canonicalizer canonicalizer = new Canonicalizer(lexicon);

        // INTEGER_DIVIDED_BY 经运算符符号映射整体翻成 "//"，
        // 不得退化为 "Int भाग"（INT_TYPE 抢先吃掉前缀后残留 "भाग"）。
        String intDiv = canonicalizer.canonicalize("पूर्णांक भाग");
        assertThat(intDiv).contains("//");
        assertThat(intDiv).doesNotContain("भाग");

        // 短形式 INT_TYPE 单独出现时翻成 Int（确认是不同关键词，未被合并）。
        String intType = canonicalizer.canonicalize("पूर्णांक");
        assertThat(intType).contains("Int");
        assertThat(intType).doesNotContain("//");
    }
}
