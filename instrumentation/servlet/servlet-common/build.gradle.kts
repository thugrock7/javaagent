plugins {
    `java-library`
}

dependencies {
    compileOnly("javax.servlet:servlet-api:2.3")
    api("io.opentelemetry:opentelemetry-api:0.10.0")
}
