plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":ghost-serialization"))
    
    compileOnly("org.springframework.boot:spring-boot-starter-web:3.4.0")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux:3.4.0")
    
    implementation(libs.okio)
    
    testImplementation(libs.kotlin.test)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
