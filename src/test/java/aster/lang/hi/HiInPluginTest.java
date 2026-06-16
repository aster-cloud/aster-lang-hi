package aster.lang.hi;

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
}
