plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":ghost-serialization"))
    
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)
    
    implementation(libs.okio)
    
    testImplementation(libs.kotlin.test)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
