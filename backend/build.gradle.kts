plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId = providers.gradleProperty("quarkusPlatformGroupId").get()
val quarkusPlatformArtifactId = providers.gradleProperty("quarkusPlatformArtifactId").get()
val quarkusPlatformVersion = providers.gradleProperty("quarkusPlatformVersion").get()

val esperVersion = "9.0.0"
val lombokVersion = "1.18.46"

dependencies {
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-container-image-docker")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-arc")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")


    implementation("com.espertech:esper-common:$esperVersion")
    implementation("com.espertech:esper-runtime:$esperVersion")
    implementation("com.espertech:esper-compiler:$esperVersion")


    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    implementation("org.jspecify:jspecify:1.0.0")
}

group = "de.ur"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
tasks.withType<JavaExec> {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
tasks.withType<io.quarkus.gradle.tasks.QuarkusDev> {
    jvmArguments.add("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}