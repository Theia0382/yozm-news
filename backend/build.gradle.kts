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
	implementation("org.jobrunr:jobrunr-spring-boot-4-starter:9.0.2")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0")
	// 수집 프로필 · 프리셋 파일(YAML 1.2) 읽기 — 이벤트 API 로 키 · 값의 태그 · 앵커 · 별칭을 그대로 본다.
	// Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("org.snakeyaml:snakeyaml-engine:3.0.1")
	// 피드 XML 문서 — 파서는 정규화가 직접 구성한다. 버전은 Spring Boot 의존성 관리
	implementation("org.jdom:jdom2")
	// 피드 텍스트의 HTML 제거 · HTML 엔티티 표. Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("org.jsoup:jsoup:1.23.2")
	// RSS · Atom 형식 판별과 제목 · 링크. Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("com.rometools:rome:2.1.0")
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
	// 이벤트 발행 레지스트리의 직렬화기(EventSerializer)로 이벤트 왕복을 확인한다 — 런타임에는 starter-jdbc 가 들인다
	testImplementation("org.springframework.modulith:spring-modulith-events-core")
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

// 화면용 API 명세의 정본(저장소 루트 api/openapi.json) — OpenApiSpecTests 가 현재 코드의 명세와 비교한다.
// 파일이 바뀌면 테스트가 다시 돌도록 입력으로 등록하고(파일이 없어도 구성은 되고 테스트가 안내와 함께 실패한다),
// updateOpenApiSpec 은 같은 테스트로 파일을 다시 쓴다.
val openApiSpecFile = layout.projectDirectory.file("../api/openapi.json")
val openApiSpecFileProperty = "yozmnews.openapi-spec-file"

tasks.withType<Test> {
	systemProperty(openApiSpecFileProperty, openApiSpecFile.asFile.path)
}

tasks.named<Test>("test") {
	inputs.files(openApiSpecFile).withPropertyName("openApiSpecFile").withPathSensitivity(PathSensitivity.NONE)
}

tasks.register<Test>("updateOpenApiSpec") {
	description = "현재 코드의 화면용 API 명세로 api/openapi.json 을 다시 쓴다."
	group = "documentation"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	filter {
		includeTestsMatching("net.yozmnews.OpenApiSpecTests")
	}
	systemProperty("yozmnews.openapi-spec-update", "true")
	outputs.upToDateWhen { false }
}
