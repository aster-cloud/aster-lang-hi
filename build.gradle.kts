import java.security.MessageDigest

plugins {
    `java-library`
    `maven-publish`
}

group = "cloud.aster-lang"

// 共享版本目录句柄（aster-lang-platform，ADR 0012），与 aster-lang-locales 同构。
val asterLibs: VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("asterLibs")

// Maven lexicon jar 版本 = 版本目录的 asterLang（JVM 生态单一版本源，ADR 0012）。
// **不**硬编码字面量、**不**随 ui-messages npm 包的独立 cadence 漂移——曾误 bump 到
// 1.0.6 脱离 catalog asterLang，导致消费方按 catalog 解析 aster-lang-hi:1.0.3 但本仓
// 发的是孤儿 1.0.6→core CI parity 解析失败。从 catalog 取让 Maven jar 永远跟随生态版本。
version = asterLibs.findVersion("asterLang").get().requiredVersion

// ui-messages manifest / npm 包的独立版本（与 Maven jar 解耦，走 npm 发布 cadence）。
// 与 ui-messages/package.json 的 version 对齐。
val uiMessagesVersion = "1.0.6"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-hi"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aster-cloud/${rootProject.name}")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

dependencies {
    implementation(asterLibs.findLibrary("core").get())
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

/**
 * verifyLexiconKeywordParity：hi-IN.json 的 SemanticTokenKind 键集必须与 en-US
 * backbone 一致（翻译值不同是正常的，键必须相同）。与 aster-lang-zh/de 的同名任务
 * 同构，防止印地语包漏键或多键导致跨引擎分歧。
 */
tasks.register("verifyLexiconKeywordParity") {
    group = "verification"
    description = "Ensure hi-IN.json keyword set matches en-US backbone"
    val ours = file("src/main/resources/lexicons/hi-IN.json")
    val coreBuiltin = file("../aster-lang-core/src/main/resources/builtin/en-US.json")
    doLast {
        if (!coreBuiltin.exists()) {
            logger.lifecycle("verifyLexiconKeywordParity: en-US backbone not found at ${coreBuiltin.absolutePath}; skipping (CI checks out core as a sibling).")
            return@doLast
        }
        val mapper = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val ourKeys = ((mapper.parse(ours) as Map<String, Any>)["keywords"] as Map<String, Any>).keys
        @Suppress("UNCHECKED_CAST")
        val baseKeys = ((mapper.parse(coreBuiltin) as Map<String, Any>)["keywords"] as Map<String, Any>).keys
        val missing = baseKeys - ourKeys
        val extra = ourKeys - baseKeys
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            throw GradleException(
                "verifyLexiconKeywordParity FAILED:\n" +
                    "  missing in hi-IN.json: $missing\n" +
                    "  extra in hi-IN.json:   $extra"
            )
        }
        logger.lifecycle("verifyLexiconKeywordParity: hi-IN.json keyword set matches en-US backbone ✓")
    }
}

/**
 * verifyUiMessagesParity（审计 #24 / locales#25）：hi-IN 的 ui-messages namespace 键集
 * 必须与 en-US backbone 一致——闭合此前的跨仓缺口（locales 的同名门禁只校验 zh/de，
 * hi 在独立仓故落在门外，en 新增 namespace 会静默把 hi 落下）。翻译值不同正常、键必须相同。
 * 门禁放在 hi 仓（hi-IN 的属主），对齐 verifyLexiconKeywordParity 的 sibling-checkout 模式：
 * CI 把 aster-lang-locales 作为 sibling 检出；本地缺 sibling 则跳过（非阻断）。
 */
tasks.register("verifyUiMessagesParity") {
    group = "verification"
    description = "Ensure hi-IN ui-messages namespace set matches en-US backbone"
    val ours = file("src/main/resources/ui-messages/hi-IN.json")
    val enBackbone = file("../aster-lang-locales/locales/en/src/main/resources/ui-messages/en-US.json")
    doLast {
        if (!enBackbone.exists()) {
            logger.lifecycle("verifyUiMessagesParity: en-US ui-messages backbone not found at ${enBackbone.absolutePath}; skipping (CI checks out aster-lang-locales as a sibling).")
            return@doLast
        }
        val parser = groovy.json.JsonSlurper()
        @Suppress("UNCHECKED_CAST")
        val ourNs = (parser.parse(ours) as Map<String, Any>).keys
        @Suppress("UNCHECKED_CAST")
        val baseNs = (parser.parse(enBackbone) as Map<String, Any>).keys
        val missing = baseNs - ourNs
        val extra = ourNs - baseNs
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            throw GradleException(
                "verifyUiMessagesParity FAILED (hi-IN vs en-US backbone):\n" +
                    "  missing in hi-IN: $missing\n" +
                    "  extra in hi-IN:   $extra\n" +
                    "Sync the ui-messages namespace set with en-US."
            )
        }
        logger.lifecycle("verifyUiMessagesParity: hi-IN ui-messages (${ourNs.size} namespaces) matches en-US backbone ✓")
    }
}

tasks.named("check") {
    dependsOn("verifyLexiconKeywordParity", "verifyUiMessagesParity")
}

/**
 * exportUiMessages（ADR 0018，统一语言包 Phase 1）：把 hi-IN 的界面文案
 * `ui-messages/hi-IN.json` 导出为单一 manifest 制品，与 aster-lang-locales 的
 * 同名任务同构、走独立 npm 通道（不进 JVM jar）。
 *
 * 注意：hi-IN 现已**全量翻译**（2153 key，与 en backbone 对齐）。此处仍不强校
 * namespace parity（历史上 hi 部分翻译时缺失的由 aster-cloud deepMergeMessages
 * fallback 到 en；全量后无缺口），只导出 hi 文案 + manifest（带 sha256 给 KV 版本化
 * 缓存 key 用）。
 *
 * 跨仓 parity 缺口（audit #24 Low）：hi 目前**不在** aster-lang-locales 的
 * `verifyUiMessagesParity` 门禁内——该门禁只校验 zh/de vs en backbone。hi-IN 的
 * 38 个 ui-messages namespace 今天与 backbone 一致，但**无任何 CI 强制**，故 en
 * 新增 namespace 会静默把 hi 落下。修复属跨仓、在 aster-lang-locales#25 统筹（给
 * locales 门禁加一条 hi 分支）；本仓无需改代码，仅此处留痕。
 */
val exportUiMessages by tasks.registering {
    group = "aster"
    description = "导出 hi-IN ui-messages 为 manifest 制品（ADR 0018 Phase 1）"

    val msgDir = file("src/main/resources/ui-messages")
    val outDir = layout.buildDirectory.dir("ui-messages")
    inputs.dir(msgDir).optional()
    outputs.dir(outDir)

    doLast {
        val md = MessageDigest.getInstance("SHA-256")
        val out = outDir.get().asFile
        out.mkdirs()
        val files = (msgDir.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList())
            .sortedBy { it.nameWithoutExtension }
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val entries = files.joinToString(",\n") { f ->
            val bytes = f.readBytes()
            md.reset()
            val sha = md.digest(bytes).joinToString("") { "%02x".format(it) }
            f.copyTo(out.resolve(f.name), overwrite = true)
            """    { "id": "${esc(f.nameWithoutExtension)}", "file": "${esc(f.name)}", """ +
                """"sha256": "$sha", "bytes": ${bytes.size} }"""
        }
        out.resolve("ui-messages-manifest.json").writeText(
            """{
  "schema": "aster-ui-messages-manifest/v1",
  "version": "$uiMessagesVersion",
  "locales": [
$entries
  ]
}
"""
        )
        logger.lifecycle("exportUiMessages → ${out.absolutePath} (${files.size} locale)")
    }
}

tasks.named("build").configure { dependsOn(exportUiMessages) }
