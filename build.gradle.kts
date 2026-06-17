import java.security.MessageDigest

plugins {
    `java-library`
    `maven-publish`
}

group = "cloud.aster-lang"
version = "1.0.2"

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
    implementation("cloud.aster-lang:aster-lang-core:1.0.2")
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

tasks.named("check") {
    dependsOn("verifyLexiconKeywordParity")
}

/**
 * exportUiMessages（ADR 0018，统一语言包 Phase 1）：把 hi-IN 的界面文案
 * `ui-messages/hi-IN.json` 导出为单一 manifest 制品，与 aster-lang-locales 的
 * 同名任务同构、走独立 npm 通道（不进 JVM jar）。
 *
 * 注意：hi-IN 是**部分翻译**（12/38 namespace），缺失的 namespace 由 aster-cloud
 * 的 deepMergeMessages 在运行时 fallback 到 en——故此处**不强校 namespace parity**，
 * 只导出 hi 自身覆盖的文案 + manifest（带 sha256 给 KV 版本化缓存 key 用）。
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
  "version": "$version",
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
