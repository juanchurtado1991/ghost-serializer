plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":ghost-serialization"))
    
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)
    
    implementation(libs.okio)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.spring.boot.starter.web)
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
