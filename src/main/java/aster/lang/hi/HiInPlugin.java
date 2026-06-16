package aster.lang.hi;

import aster.core.lexicon.DynamicLexicon;
import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 印地语语言包插件 (hi-IN，天城文 Devanagari)。
 *
 * <p>通过 SPI（{@link LexiconPlugin}）从 JSON 配置加载印地语词法表。印地语等值/比较
 * 使用已实现的关键词（如 {@code बराबर}=equals to、{@code से अधिक}=greater than），
 * 因此**无需任何语法变换器**——这是它比中文包简单的地方，{@code getTransformers()}
 * 用接口默认（空）即可。
 *
 * <p>天城文 abugida 脚本支持（辅音 + 元音符号 matra + virama 组合记号、danda「।」
 * 句末符）由 core 的词法器/Canonicalizer 提供（ADR 0017 Phase 1/2），本包只贡献
 * 词法表数据。从 core builtin 抽出为独立 SPI 包后，运维可像 zh/de 一样**热插拔/卸载**
 * 印地语（上传/删除 jar，无需重启）。
 */
public final class HiInPlugin implements LexiconPlugin {

    @Override
    public Set<String> providedLexiconIds() {
        return Set.of("hi-IN");
    }

    @Override
    public Lexicon createLexicon() {
        String json = loadResource("lexicons/hi-IN.json");
        return DynamicLexicon.fromJsonString(json);
    }

    private String loadResource(String path) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource: " + path, e);
        }
    }
}
