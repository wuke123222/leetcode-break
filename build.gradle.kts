plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.leetcodebreak"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde)
        else intellijIdea(providers.gradleProperty("platformVersion").get())
        bundledPlugin("com.intellij.modules.jcef")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
            .orElse(providers.gradleProperty("intellijPlatformPublishingToken"))
    }
    signing {
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

tasks.processResources { from("LICENSE") { into("META-INF") } }

tasks.wrapper {
    gradleVersion = "9.5.0"
    distributionType = Wrapper.DistributionType.BIN
}
