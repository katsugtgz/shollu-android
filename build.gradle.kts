import org.gradle.api.artifacts.component.ModuleComponentIdentifier

buildscript {
    // Dependabot alerts 1-57: force patched versions of AGP plugin-classpath transitives.
    // These are build-time-only deps (the app runtime classpath has none of them).
    configurations.getByName("classpath") {
        resolutionStrategy.force(
            "io.netty:netty-buffer:4.1.137.Final",
            "io.netty:netty-codec:4.1.137.Final",
            "io.netty:netty-codec-http:4.1.137.Final",
            "io.netty:netty-codec-http2:4.1.137.Final",
            "io.netty:netty-codec-socks:4.1.137.Final",
            "io.netty:netty-common:4.1.137.Final",
            "io.netty:netty-handler:4.1.137.Final",
            "io.netty:netty-handler-proxy:4.1.137.Final",
            "io.netty:netty-resolver:4.1.137.Final",
            "io.netty:netty-transport:4.1.137.Final",
            "io.netty:netty-transport-native-unix-common:4.1.137.Final",
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcutil-jdk18on:1.84",
            "com.google.protobuf:protobuf-java:3.25.5",
            "com.google.protobuf:protobuf-java-util:3.25.5",
            "com.google.guava:guava:33.4.8-jre",
            "commons-io:commons-io:2.16.1",
            "org.apache.commons:commons-compress:1.27.1",
            // pulled in by commons-compress 1.27.1; alert 58 (< 3.18.0, uncontrolled recursion)
            "org.apache.commons:commons-lang3:3.18.0",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.jdom:jdom2:2.0.6.1",
            // grpc 1.57 (AGP's UTP) is incompatible with netty >= 4.1.101/4.1.111 (grpc-java#10665,
            // #11284). 1.65.1 is the first release containing both fixes and pairs with netty 4.1.13x.
            "io.grpc:grpc-api:1.65.1",
            "io.grpc:grpc-core:1.65.1",
            "io.grpc:grpc-context:1.65.1",
            "io.grpc:grpc-inprocess:1.65.1",
            "io.grpc:grpc-netty:1.65.1",
            "io.grpc:grpc-protobuf:1.65.1",
            "io.grpc:grpc-protobuf-lite:1.65.1",
            "io.grpc:grpc-services:1.65.1",
            "io.grpc:grpc-stub:1.65.1",
            "io.grpc:grpc-util:1.65.1"
        )
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Minimum versions that satisfy every open Dependabot alert (GH alerts 1-57).
// Floors are per-package-family; anything resolving below these fails the gate.
val dependencySecurityFloors = mapOf(
    "io.netty" to "4.1.137.Final",          // alerts 1,2,14,16,18-20,23,25,26,29,31-37,39,40-44,46-57
    "org.bouncycastle" to "1.84",           // alerts 8,9,10,13,17,22,27,28,45
    "com.google.protobuf" to "3.25.5",      // alert 11
    "com.google.guava:guava" to "32.0.0-android", // alerts 3,4 (guava only — listenablefuture/failureaccess are separate versioned artifacts)
    "commons-io" to "2.14.0",               // alert 12
    "org.apache.commons:commons-compress" to "1.26.0", // alerts 5,6
    "org.apache.commons:commons-lang3" to "3.18.0",    // alert 58
    "org.bitbucket.b_c:jose4j" to "0.9.6",  // alert 24
    "org.jdom:jdom2" to "2.0.6.1",          // alert 21
)

fun compareVersions(left: String, right: String): Int {
    fun numericParts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val l = numericParts(left)
    val r = numericParts(right)
    val len = maxOf(l.size, r.size)
    for (i in 0 until len) {
        val a = l.getOrElse(i) { 0 }
        val b = r.getOrElse(i) { 0 }
        if (a != b) return a - b
    }
    return 0
}

tasks.register("verifyDependencySecurity") {
    group = "verification"
    description = "Fails when the build or app classpaths resolve any package below its Dependabot patched-version floor."
    doLast {
        val configs = listOf(
            buildscript.configurations.getByName("classpath") to "build classpath (plugin classpath)",
            project(":app").configurations.getByName("releaseRuntimeClasspath") to ":app releaseRuntimeClasspath",
        )
        val violations = mutableListOf<String>()
        for ((configuration, label) in configs) {
            configuration.incoming.resolutionResult.allComponents.forEach { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@forEach
                for ((key, floor) in dependencySecurityFloors.entries) {
                    val (group, artifact) = key.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    if (id.group == group && (artifact.isEmpty() || id.module == artifact)) {
                        if (compareVersions(id.version, floor) < 0) {
                            violations += "$label: ${id.group}:${id.module}:${id.version} < $floor"
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Dependabot floors not met:\n" + violations.sorted().joinToString("\n")
            )
        }
        println("Dependency security gate passed: all classpaths meet Dependabot floors.")
    }
}
