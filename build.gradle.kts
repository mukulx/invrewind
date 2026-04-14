plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta4"
    id("com.github.hierynomus.license") version "0.16.1"
}

group = "dev.mukulx"
version = "1.1.0"

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
        exclude {
            it.moduleGroup != "org.bstats"
        }
    }
    
    relocate("org.bstats", "dev.mukulx.invrewind.bstats")
    
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
