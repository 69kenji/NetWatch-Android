buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.4.0") {
            exclude(group = "org.jdom", module = "jdom2")
            exclude(group = "org.apache.httpcomponents", module = "httpclient")
            exclude(group = "org.apache.commons", module = "commons-lang3")
            exclude(group = "org.bitbucket.b_c", module = "jose4j")
            exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
            exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
            exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-gradle-plugin")
        }

        // Replace the excluded AGP build dependencies with patched releases.
        classpath("org.jdom:jdom2:2.0.6.1")
        classpath("org.apache.httpcomponents:httpclient:4.5.14")
        classpath("org.apache.commons:commons-lang3:3.18.0")
        classpath("org.bitbucket.b_c:jose4j:0.9.6")
        classpath("org.bouncycastle:bcpkix-jdk18on:1.84")
        classpath("org.bouncycastle:bcprov-jdk18on:1.84")
        classpath("org.bouncycastle:bcutil-jdk18on:1.84")
    }
}

plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20-RC3" apply false
}
