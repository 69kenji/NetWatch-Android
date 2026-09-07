buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        components {
            withModule("org.apache.httpcomponents:httpmime") {
                allVariants {
                    withDependencies {
                        removeAll {
                            it.group == "org.apache.httpcomponents" && it.name == "httpclient"
                        }
                        add("org.apache.httpcomponents:httpclient:4.5.14")
                    }
                }
            }
            withModule("org.apache.commons:commons-compress") {
                allVariants {
                    withDependencies {
                        removeAll {
                            it.group == "org.apache.commons" && it.name == "commons-lang3"
                        }
                        add("org.apache.commons:commons-lang3:3.18.0")
                    }
                }
            }
            withModule("com.android.tools:sdk-common") {
                allVariants {
                    withDependencies {
                        removeAll {
                            it.group == "org.bouncycastle" && it.name in setOf(
                                "bcpkix-jdk18on",
                                "bcprov-jdk18on",
                                "bcutil-jdk18on",
                            )
                        }
                        add("org.bouncycastle:bcpkix-jdk18on:1.84")
                        add("org.bouncycastle:bcprov-jdk18on:1.84")
                        add("org.bouncycastle:bcutil-jdk18on:1.84")
                    }
                }
            }
        }

        // Keep AGP's build-only transitive dependencies on patched releases.
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
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
