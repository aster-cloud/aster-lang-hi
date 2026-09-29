# aster-lang-hi

Hindi (hi-IN, Devanagari / 天城文) language pack for [Aster Lang](https://github.com/aster-cloud/aster-lang-core).

Aster Lang's CNL compiler is language-agnostic by design. This package ships the
**Hindi lexicon** as an SPI plugin (`aster.core.lexicon.LexiconPlugin`), so it can
be loaded — and **hot-unloaded** — at runtime alongside `aster-lang-en` / `-zh` /
`-de`, without rebuilding the core.

## What's inside

- `src/main/resources/lexicons/hi-IN.json` — Devanagari keyword translations
  (key set identical to the `en-US` backbone, enforced by
  `verifyLexiconKeywordParity`), danda `।` (U+0964) statement-end, `ENGLISH`
  whitespace mode.
- `src/main/java/aster/lang/hi/HiInPlugin.java` — the `LexiconPlugin` SPI
  implementation (registered via `META-INF/services`).
- `src/main/resources/ui-messages/hi-IN.json` — Hindi UI messages (namespace
  set identical to the `en-US` backbone, enforced by `verifyUiMessagesParity`).
  Published as `@aster-cloud/ui-messages-hi` via `exportUiMessages`; **not**
  packaged into the jar (ADR 0018). This repo owns `hi-IN` only — no other
  locale's json may live in that directory.

Hindi equality / comparison use already-implemented keywords (`बराबर` = equals to,
`से अधिक` = greater than, `से कम` = less than), so **no syntax transformers are
needed** — this pack is purely lexicon data.

This pack intentionally ships **only** a `LexiconPlugin` (no `VocabularyPlugin`),
since it contributes lexicon data alone — no domain vocabulary or identifier translations.

Devanagari abugida support (consonant + vowel-sign matras + virama combining marks,
danda statement-end) lives in `aster-lang-core`'s lexer / canonicalizer
(ADR 0017 Phase 1/2). TS↔Java parse-parity is verified there.

## Build

```bash
./gradlew build verifyLexiconKeywordParity
```

`verifyLexiconKeywordParity` asserts the `hi-IN.json` `SemanticTokenKind` key set
matches the `en-US` backbone (translation *values* differ; keys must be identical);
`verifyUiMessagesParity` does the same for the ui-messages namespace set. Both run
under `check`. Each backbone is resolved as `-PenLexiconBackbone` /
`-PenUiMessagesBackbone` override → nested CI checkout (`aster-lang-core/`,
`aster-lang-locales/`) → `../` sibling; when `CI` is set and the backbone is
missing the task fails instead of skipping.

## Publish

Tag `v*` on `main` → CI publishes `cloud.aster-lang:aster-lang-hi` to GitHub
Packages.

## License

Apache-2.0.
