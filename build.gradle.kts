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
