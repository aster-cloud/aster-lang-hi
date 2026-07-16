rootProject.name = "aster-lang-hi"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    // 共享版本目录（aster-lang-platform，ADR 0012）：aster-lang 生态依赖
    // 版本的单一来源。用 asterLibs.* 别名代替散落的版本字面量，与 aster-lang-locales
    // 同构，杜绝 hi 包脱离 catalog 漂移（曾因硬编码 1.0.x 引发版本失配）。
    versionCatalogs {
        create("asterLibs") {
            from("cloud.aster-lang:aster-lang-platform:1.0.15")
        }
    }
}
