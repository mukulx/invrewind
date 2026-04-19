plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("com.github.hierynomus.license") version "0.16.1"
}

group = "dev.mukulx"
version = "1.3.2"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.zaxxer:HikariCP:6.3.3")
    compileOnly("org.xerial:sqlite-jdbc:3.51.2.0")
    implementation("org.bstats:bstats-bukkit:3.2.1")
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
}

val targetJavaVersion = 21

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    dependencies {
        include(dependency("org.bstats:bstats-bukkit:.*"))
        include(dependency("net.kyori:adventure-api:.*"))
        include(dependency("net.kyori:adventure-text-minimessage:.*"))
        include(dependency("net.kyori:adventure-key:.*"))
        include(dependency("net.kyori:examination-api:.*"))
        include(dependency("net.kyori:examination-string:.*"))
        include(dependency("net.kyori:adventure-text-serializer-gson:.*"))
        include(dependency("net.kyori:adventure-text-serializer-json:.*"))
        include(dependency("net.kyori:adventure-text-serializer-legacy:.*"))
    }
    
    relocate("org.bstats", "dev.mukulx.invrewind.bstats")
    relocate("net.kyori", "dev.mukulx.invrewind.libs.kyori")
    
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

license {
    header = file("license.head")
    include("**/*.java")
    strictCheck = true
}
