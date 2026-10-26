plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "net.yozmnews"
version = "0.0.1-SNAPSHOT"
description = "요즘뉴스 - AI 뉴스 다이제스트"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	// DevComposeFile 이 직접 쓴다 — 버전은 Spring Boot 의존성 관리
	testImplementation("org.yaml:snakeyaml")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

// 개발용 Compose 파일 — 테스트의 PostgreSQL 이미지 정본(DevComposeFile).
// 경로를 시스템 속성으로 넘기고, 파일이 바뀌면 테스트가 다시 돌도록 입력으로 등록한다.
val devComposeFile = layout.projectDirectory.file("../compose.yaml")
val devComposeFileProperty = "yozmnews.dev-compose-file"

tasks.withType<Test> {
	useJUnitPlatform()
	inputs.file(devComposeFile).withPropertyName("devComposeFile").withPathSensitivity(PathSensitivity.NONE)
	systemProperty(devComposeFileProperty, devComposeFile.asFile.path)
}

tasks.named<JavaExec>("bootTestRun") {
	systemProperty(devComposeFileProperty, devComposeFile.asFile.path)
}
