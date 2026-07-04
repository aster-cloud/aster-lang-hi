package aster.lang.hi;

import aster.core.canonicalizer.Canonicalizer;
import aster.core.lexer.Lexer;
import aster.core.lexer.Token;
import aster.core.lexer.TokenKind;
import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 印地语（天城文 Devanagari）**含元音符号（matra）标识符**的词法/规范化 conformance 测试。
 *
 * <p>背景（issue #24 Medium）：天城文是 abugida 脚本——辅音 + 元音符号（matra，如 ◌ु ◌ी ◌ू）
 * + virama（◌्）组合记号。这些组合记号属 Unicode {@code \p{M}}（NON_SPACING_MARK /
 * COMBINING_SPACING_MARK），{@code Character.isLetter} 对它们返回 {@code false}。生态刚落地的
 * 分词修复（ts {@code identifierTokenRegex} 的 {@code \p{M}}、core 的 {@code WORD_PATTERN}
 * {@code [\p{L}_][\p{L}\p{M}\p{Nd}_]*} 与 {@code isDevanagariMark}）保证含 matra 的词
 * **不在组合记号处碎裂**。此前 hi 测试只覆盖了 Canonicalizer 端的最长匹配前缀冒险
 * （प्रतीक्षा vs प्रतीक्षा करें），**没有任何 matra 标识符分词覆盖**——本文件补齐，
 * 对齐 zh 的 conformance 深度（见 aster-lang-core {@code DevanagariLexerTest}）。
 *
 * <p>下面用到的标识符均**非关键词**且富含组合记号：
 * <ul>
 *   <li>आयु（age）：含 matra ◌ु</li>
 *   <li>मूल्य（value）：含 matra ◌ू + virama ◌्</li>
 *   <li>राशि（amount）：含 matra ◌ा ◌ि</li>
 *   <li>सीमा（limit）：含 matra ◌ी ◌ा</li>
 *   <li>कुल（total）：含 matra ◌ु</li>
 *   <li>आयुपरीक्षण（age-check，复合）、वयस्कहै（is-adult）：多个 matra + virama</li>
 *   <li>प्रतीक्षासूची（waitlist）：以关键词 प्रतीक्षा（BACKOFF）为**前缀**的复合标识符</li>
 * </ul>
 */
@DisplayName("印地语 matra 标识符分词/规范化 conformance")
class HiMatraIdentifierTest {

    private static Lexicon lexicon;

    @BeforeAll
    static void loadLexicon() {
        HiInPlugin plugin = (HiInPlugin) ServiceLoader.load(LexiconPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p instanceof HiInPlugin)
                .findFirst()
                .orElseThrow(() -> new AssertionError("HiInPlugin 未通过 SPI 发现"));
        lexicon = plugin.createLexicon();
    }

    /** 该字符串是否含天城文组合记号（matra/virama，Unicode \p{M}，排除句末 danda）。 */
    private static boolean hasDevanagariMark(String s) {
        return s.codePoints().anyMatch(cp ->
                cp >= 0x0900 && cp <= 0x097F && cp != 0x0964 && cp != 0x0965
                        && (Character.getType(cp) == Character.NON_SPACING_MARK
                        || Character.getType(cp) == Character.COMBINING_SPACING_MARK));
    }

    /** token 文本是否以组合记号开头——若为 true 则说明词在 matra 处被切碎（游离记号成头）。 */
    private static boolean startsWithMark(String v) {
        if (v == null || v.isEmpty()) return false;
        int type = Character.getType(v.charAt(0));
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
    }

    // ============================================================
    // 1. Lexer 分词：含 matra 的标识符必须是**单个** IDENT，不在组合记号处碎裂
    // ============================================================

    @Nested
    @DisplayName("Lexer 分词：matra 标识符不碎裂")
    class LexerSegmentation {

        @Test
        @DisplayName("每个含 matra 的标识符都恰好 lex 成一个完整 IDENT")
        void matraIdentifierIsSingleIntactToken() {
            String[] idents = {"आयु", "मूल्य", "राशि", "सीमा", "कुल", "आयुपरीक्षण", "वयस्कहै"};
            for (String id : idents) {
                assertThat(hasDevanagariMark(id))
                        .as("测试前提：%s 应含天城文组合记号（matra/virama）", id)
                        .isTrue();

                List<Token> tokens = Lexer.lex(id, lexicon);

                long identCount = tokens.stream()
                        .filter(t -> t.kind() == TokenKind.IDENT)
                        .count();
                assertThat(identCount)
                        .as("%s 应恰好 lex 成 1 个 IDENT（非在 matra/virama 处碎成多段）。tokens=%s", id, tokens)
                        .isEqualTo(1);

                boolean intact = tokens.stream()
                        .anyMatch(t -> id.equals(String.valueOf(t.value())));
                assertThat(intact)
                        .as("%s 应作为完整标识符出现（组合记号未被剥离/切分）。tokens=%s", id, tokens)
                        .isTrue();

                boolean anyFragmentHead = tokens.stream()
                        .anyMatch(t -> startsWithMark(String.valueOf(t.value())));
                assertThat(anyFragmentHead)
                        .as("不应有 token 以游离组合记号开头（碎裂特征）。tokens=%s", tokens)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("空格分隔的多个 matra 标识符互不吞并，各自成一个 IDENT")
        void multipleMatraIdentifiersEachStayOwnToken() {
            List<Token> tokens = Lexer.lex("आयु मूल्य राशि सीमा कुल", lexicon);
            long identCount = tokens.stream().filter(t -> t.kind() == TokenKind.IDENT).count();
            assertThat(identCount)
                    .as("5 个 matra 标识符应各自成一个 IDENT。tokens=%s", tokens)
                    .isEqualTo(5);
            assertThat(tokens).noneMatch(t -> startsWithMark(String.valueOf(t.value())));
        }

        @Test
        @DisplayName("danda「।」与含 matra 的词分开成 DOT，不被吞进标识符")
        void dandaStaysSeparateFromMatraIdentifier() {
            // आयुपरीक्षण = आ ◌ु य + प र ◌ी क ◌् ष ण（多个组合记号），后接 danda。
            List<Token> tokens = Lexer.lex("आयुपरीक्षण।", lexicon);
            assertThat(tokens.get(0).kind()).isEqualTo(TokenKind.IDENT);
            assertThat(tokens.get(0).value()).isEqualTo("आयुपरीक्षण");
            assertThat(tokens.get(1).kind())
                    .as("danda「।」应识别为句末 DOT。tokens=%s", tokens)
                    .isEqualTo(TokenKind.DOT);
            assertThat(tokens.get(1).value()).isEqualTo("।");
        }
    }

    // ============================================================
    // 2. Canonicalizer：含 matra 的标识符穿过规范化后原样保留；关键词前缀不吃标识符
    // ============================================================

    @Nested
    @DisplayName("Canonicalizer：matra 标识符原样保留 + 词边界最长匹配")
    class CanonicalizerPreservation {

        @Test
        @DisplayName("含 matra 的标识符在规范化后原样保留，仅关键词被翻译")
        void matraIdentifierPreservedThroughCanonicalize() {
            Canonicalizer canonicalizer = new Canonicalizer(lexicon);
            // मानें आयु हो 30। → Let आयु be 30.（LET/BE/danda 翻译，标识符 आयु 不动）
            String result = canonicalizer.canonicalize("मानें आयु हो 30।");
            assertThat(result)
                    .as("标识符 आयु 应原样保留，实际: %s", result)
                    .contains("आयु");
            assertThat(result).contains("Let");
            assertThat(result).contains("be");
        }

        @Test
        @DisplayName("以关键词为前缀的 matra 标识符不被词内关键词替换破坏（\\p{M} 词边界）")
        void keywordPrefixInsideMatraIdentifierNotTranslated() {
            Canonicalizer canonicalizer = new Canonicalizer(lexicon);
            // प्रतीक्षासूची（waitlist）= 关键词 प्रतीक्षा（BACKOFF）+ सूची。因 WORD_PATTERN 含 \p{M}，
            // 整词是**单个**词元，词内前缀 प्रतीक्षा 不得被替换成 backoff。
            String result = canonicalizer.canonicalize("मानें प्रतीक्षासूची हो 5।");
            assertThat(result)
                    .as("复合标识符 प्रतीक्षासूची 应原样保留，实际: %s", result)
                    .contains("प्रतीक्षासूची");
            assertThat(result)
                    .as("词内前缀 प्रतीक्षा 不得被翻译成 backoff，实际: %s", result)
                    .doesNotContain("backoff");
        }

        @Test
        @DisplayName("最长匹配：AWAIT「प्रतीक्षा करें」不被 BACKOFF「प्रतीक्षा」抢先（含 matra 关键词）")
        void longestMatchHoldsForMatraKeywords() {
            Canonicalizer canonicalizer = new Canonicalizer(lexicon);
            // प्रतीक्षा / प्रतीक्षा करें 均含 matra ◌ी ◌े；核对最长匹配在 matra 词上仍成立。
            String await = canonicalizer.canonicalize("प्रतीक्षा करें");
            assertThat(await).contains("await");
            assertThat(await).doesNotContain("backoff");

            String backoff = canonicalizer.canonicalize("प्रतीक्षा");
            assertThat(backoff).contains("backoff");
            assertThat(backoff).doesNotContain("await");
        }
    }

    // ============================================================
    // 3. 用 matra 标识符写的 rule/module 干净通过词法（无异常、结构完好、标识符不碎）
    // ============================================================

    @Nested
    @DisplayName("module/rule 使用 matra 标识符可干净 lex")
    class ModuleWithMatraIdentifier {

        /**
         * 一个用含 matra 标识符命名的模块 + 规则：
         * <pre>
         * मॉड्यूल आयुपरीक्षण।              // module 名含 matra
         * नियम वयस्कहै दिया गया आयु, उत्पन्न:  // rule/param 名含 matra
         *   यदि आयु से अधिक 18:
         *     लौटाएं सत्य।
         *   लौटाएं असत्य।
         * </pre>
         */
        private static final String MODULE = String.join("\n",
                "मॉड्यूल आयुपरीक्षण।",
                "",
                "नियम वयस्कहै दिया गया आयु, उत्पन्न:",
                "  यदि आयु से अधिक 18:",
                "    लौटाएं सत्य।",
                "  लौटाएं असत्य।",
                "");

        @Test
        @DisplayName("整模块干净 lex：无异常、无 matra 处碎裂、标识符完整")
        void moduleWithMatraIdentifiersLexesCleanly() {
            List<Token> tokens = Lexer.lex(MODULE, lexicon); // 抛 LexerException 即失败

            // 没有任何 token 以游离组合记号开头（碎裂特征）。
            assertThat(tokens)
                    .as("模块内不应有词在 matra/virama 处碎裂。tokens=%s", tokens)
                    .noneMatch(t -> startsWithMark(String.valueOf(t.value())));

            // module 名（含多个 matra）作为完整 IDENT 出现。
            assertThat(tokens)
                    .anyMatch(t -> t.kind() == TokenKind.IDENT && "आयुपरीक्षण".equals(String.valueOf(t.value())));

            // 参数标识符 आयु（含 matra）两处出现，均为完整 IDENT。
            long ayuCount = tokens.stream()
                    .filter(t -> t.kind() == TokenKind.IDENT && "आयु".equals(String.valueOf(t.value())))
                    .count();
            assertThat(ayuCount)
                    .as("参数 आयु 应作为完整 IDENT 出现 2 次。tokens=%s", tokens)
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("模块内结构记号正确：danda→DOT、सत्य/असत्य→BOOL、缩进块成对")
        void moduleStructuralTokensAreWellFormed() {
            List<Token> tokens = Lexer.lex(MODULE, lexicon);

            long dots = tokens.stream().filter(t -> t.kind() == TokenKind.DOT).count();
            assertThat(dots)
                    .as("3 个 danda「।」应各成一个句末 DOT。tokens=%s", tokens)
                    .isEqualTo(3);

            // सत्य=TRUE、असत्य=FALSE 应识别为 BOOL 字面量（证明 lexicon 关键词识别在工作）。
            long bools = tokens.stream().filter(t -> t.kind() == TokenKind.BOOL).count();
            assertThat(bools)
                    .as("सत्य/असत्य 应各识别为一个 BOOL。tokens=%s", tokens)
                    .isEqualTo(2);

            // 缩进块成对（INDENT/DEDENT 数量相等，块结构良构）。
            long indents = tokens.stream().filter(t -> t.kind() == TokenKind.INDENT).count();
            long dedents = tokens.stream().filter(t -> t.kind() == TokenKind.DEDENT).count();
            assertThat(indents)
                    .as("INDENT/DEDENT 应成对。tokens=%s", tokens)
                    .isEqualTo(dedents);
            assertThat(indents).isGreaterThan(0);

            // token 流以 EOF 收尾。
            assertThat(tokens.get(tokens.size() - 1).kind()).isEqualTo(TokenKind.EOF);
        }
    }
}
