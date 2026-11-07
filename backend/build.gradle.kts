import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.Properties

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
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jobrunr:jobrunr-spring-boot-4-starter:9.0.2")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
	// 이벤트 발행 레지스트리의 직렬화기(EventSerializer) — 남겨진 발행 판정이 본문을 읽는다(docs/specs/platform.md platform-R16)
	implementation("org.springframework.modulith:spring-modulith-events-core")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0")
	// 수집 프로필 · 프리셋 파일(YAML 1.2) 읽기 — Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("org.snakeyaml:snakeyaml-engine:3.0.1")
	// 피드 XML 문서 — 파서는 정규화가 직접 구성한다. 버전은 Spring Boot 의존성 관리
	implementation("org.jdom:jdom2")
	// 피드 텍스트의 HTML 제거 · HTML 엔티티 표. Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("org.jsoup:jsoup:1.23.2")
	// RSS · Atom 형식 판별과 제목 · 링크. Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("com.rometools:rome:2.1.0")
	// 기사 표현의 핵심 말 — 한국어 형태소 분석(Lucene Nori). Spring Boot 가 버전을 관리하지 않아 직접 고정한다
	implementation("org.apache.lucene:lucene-analysis-nori:10.6.0")
	// 기본 로컬 임베딩 — ONNX Runtime(linux-x64 · linux-aarch64 네이티브 포함)과 HF 토크나이저(DJL, 네이티브 포함). Spring Boot 가 버전을 관리하지 않아 직접 고정한다 — ADR-0091
	implementation("com.microsoft.onnxruntime:onnxruntime:1.31.1")
	implementation("ai.djl.huggingface:tokenizers:0.40.0")
	// 피드 수집 HTTP 클라이언트 — 버전은 Spring Boot 의존성 관리
	implementation("org.apache.httpcomponents.client5:httpclient5")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	// 외부 HTTP 대체 — Jetty · Jackson 을 안에 숨긴 판이라 Spring Boot 가 관리하는 판과 부딪히지 않는다. Spring Boot 가
	// 버전을 관리하지 않아 직접 고정한다
	testImplementation("org.wiremock:wiremock-standalone:3.13.2")
	// DevComposeFile 이 직접 쓴다 — 버전은 Spring Boot 의존성 관리
	testImplementation("org.yaml:snakeyaml")
	// RoleMembershipTests 가 직접 쓴다. Spring Boot · Modulith 가 버전을 관리하지 않아 Modulith 가 들이는 판으로 직접 고정한다
	testImplementation("com.tngtech.archunit:archunit:1.4.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

// 개발용 Compose 파일 — 테스트가 PostgreSQL 이미지를 읽는다. 결선은 docs/development/infrastructure.md "테스트가 저장소 파일을 읽는 결선"
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

// 화면용 API 명세(저장소 루트 api/openapi.json) — 결선은 docs/development/infrastructure.md "테스트가 저장소 파일을 읽는 결선".
// 파일이 없어도 구성은 되고 테스트가 안내와 함께 실패하도록 inputs.files 로 등록한다. updateOpenApiSpec 은 같은 테스트로 파일을 다시 쓴다.
val openApiSpecFile = layout.projectDirectory.file("../api/openapi.json")
val openApiSpecFileProperty = "yozmnews.openapi-spec-file"

tasks.withType<Test> {
	systemProperty(openApiSpecFileProperty, openApiSpecFile.asFile.path)
}

tasks.named<Test>("test") {
	inputs.files(openApiSpecFile).withPropertyName("openApiSpecFile").withPathSensitivity(PathSensitivity.NONE)
}

// 운영 안내서(저장소 루트 docs/operations/sources.md · tag-sets.md) — 테스트가 예시 프로필 · 예시 원문을 검증한다. 결선은 docs/development/infrastructure.md "테스트가 저장소 파일을 읽는 결선"
val sourcesGuideFile = layout.projectDirectory.file("../docs/operations/sources.md")
val sourcesGuideFileProperty = "yozmnews.sources-guide-file"
val tagSetsGuideFile = layout.projectDirectory.file("../docs/operations/tag-sets.md")
val tagSetsGuideFileProperty = "yozmnews.tag-sets-guide-file"

tasks.withType<Test> {
	inputs.file(sourcesGuideFile).withPropertyName("sourcesGuideFile").withPathSensitivity(PathSensitivity.NONE)
	systemProperty(sourcesGuideFileProperty, sourcesGuideFile.asFile.path)
	inputs.file(tagSetsGuideFile).withPropertyName("tagSetsGuideFile").withPathSensitivity(PathSensitivity.NONE)
	systemProperty(tagSetsGuideFileProperty, tagSetsGuideFile.asFile.path)
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

// Javadoc 형식 검사 — 누락은 보지 않는다. 단계마다 잡는 것은 docs/development/infrastructure.md "불변식과 지키는 장치"
val doclint = "Xdoclint:all,-missing"

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.add("-$doclint")
}

val javadocTest = tasks.register<Javadoc>("javadocTest") {
	description = "테스트 소스의 Javadoc 형식을 검사한다."
	group = "verification"
	source = sourceSets["test"].allJava
	classpath = sourceSets["test"].compileClasspath + sourceSets["test"].output
	setDestinationDir(layout.buildDirectory.dir("docs/javadoc-test").get().asFile)
}

tasks.withType<Javadoc>().configureEach {
	(options as StandardJavadocDocletOptions).apply {
		encoding = "UTF-8"
		addBooleanOption(doclint, true)
		addBooleanOption("Werror", true)
		addBooleanOption("private", true)
	}
}

tasks.named("check") {
	dependsOn(tasks.named("javadoc"), javadocTest)
}

// 기본 로컬 임베딩 모델 — 정본은 backend/embedding-model.properties(앱의 EmbeddingModel 도 같은 파일을 읽는다) · ADR-0091.
// 고정 리비전의 파일을 받아 sha256 을 확인하고 Gradle 사용자 홈에 해시로 캐시한 뒤, 생성 리소스 embedding-model/ 로 놓는다.
// 결선은 docs/development/infrastructure.md "테스트가 저장소 파일을 읽는 결선"
val embeddingModelFile = layout.projectDirectory.file("embedding-model.properties")
val embeddingModelDir = layout.buildDirectory.dir("generated/embedding-model")

val fetchEmbeddingModel = tasks.register("fetchEmbeddingModel") {
	description = "기본 로컬 임베딩 모델 · 토크나이저를 고정 리비전 · 해시로 받아 생성 리소스로 놓는다."
	group = "build"
	inputs.file(embeddingModelFile).withPropertyName("embeddingModelFile").withPathSensitivity(PathSensitivity.NONE)
	outputs.dir(embeddingModelDir)
	val cacheRoot = gradle.gradleUserHomeDir.resolve("caches/yozmnews-embedding-model")
	doLast {
		val properties = Properties()
		embeddingModelFile.asFile.inputStream().use { properties.load(it) }
		fun required(key: String): String =
			properties.getProperty(key) ?: throw GradleException("embedding-model.properties 에 $key 가 없다")
		val repository = required("repository")
		val revision = required("revision")
		val target = embeddingModelDir.get().asFile.resolve("embedding-model")
		target.deleteRecursively()
		target.mkdirs()
		val client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30))
			.build()
		fun sha256(file: File): String {
			val digest = MessageDigest.getInstance("SHA-256")
			file.inputStream().use { input ->
				val buffer = ByteArray(1 shl 16)
				while (true) {
					val read = input.read(buffer)
					if (read < 0) break
					digest.update(buffer, 0, read)
				}
			}
			return HexFormat.of().formatHex(digest.digest())
		}
		for ((name, key) in listOf("model.onnx" to "model", "tokenizer.json" to "tokenizer")) {
			val path = required("$key.path")
			val expected = required("$key.sha256")
			val cached = cacheRoot.resolve(expected).resolve(name)
			if (!cached.isFile || sha256(cached) != expected) {
				cached.parentFile.mkdirs()
				val partial = File(cached.parentFile, "$name.part")
				val uri = URI.create("https://huggingface.co/$repository/resolve/$revision/$path")
				val request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).GET().build()
				val response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial.toPath()))
				if (response.statusCode() != 200) {
					throw GradleException("임베딩 모델 파일을 받지 못했다 — $uri: 상태 ${response.statusCode()}")
				}
				val actual = sha256(partial)
				if (actual != expected) {
					partial.delete()
					throw GradleException("임베딩 모델 파일의 sha256 이 다르다 — $uri: $actual (기대 $expected)")
				}
				Files.move(partial.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
			}
			cached.copyTo(target.resolve(name), overwrite = true)
		}
		embeddingModelFile.asFile.copyTo(target.resolve("model.properties"), overwrite = true)
	}
}

sourceSets.main {
	resources.srcDir(fetchEmbeddingModel.map { embeddingModelDir })
}

// 로컬 임베딩은 ONNX Runtime 텔레메트리를 끄는 이 환경 변수가 없으면 모델을 열지 않는다(정본은 OnnxArticleEmbedder). 네이티브를 올리는 JNA · ONNX Runtime 의 제한 메서드 경고를 끈다 — ADR-0091
tasks.withType<Test> {
	environment("ORT_DISABLE_TELEMETRY", "1")
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// 모델 파일은 따로 계층을 둔다 — 코드만 바뀌면 이 계층(약 135 MB)을 다시 받지 않는다. 계층 이름은 backend/Dockerfile 이 쓴다
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	layered {
		application {
			intoLayer("spring-boot-loader") {
				include("org/springframework/boot/loader/**")
			}
			// 패턴은 jar 안 경로(BOOT-INF/classes/…)가 아니라 classpath 기준 경로에 맞는다 — jar 의 BOOT-INF/layers.idx 로 확인한다
			intoLayer("embedding-model") {
				include("embedding-model/**")
			}
			intoLayer("application")
		}
		dependencies {
			intoLayer("snapshot-dependencies") {
				include("*:*:*SNAPSHOT")
			}
			intoLayer("dependencies")
		}
		layerOrder.set(listOf("dependencies", "spring-boot-loader", "snapshot-dependencies", "embedding-model", "application"))
	}
}
