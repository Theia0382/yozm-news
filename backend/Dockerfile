# 백엔드 이미지 — target check 는 검사(compose.check.yaml), app 은 배포(deploy/compose.yaml). 구성은 docs/development/infrastructure.md "이미지".
# JAVA_VERSION 은 backend/build.gradle.kts 의 toolchain 과 같아야 한다 — 다르면 deps 단계가 실패한다.
ARG JAVA_VERSION=25

# 빌드는 빌더 플랫폼에서 돈다 — jar 는 아키텍처와 무관하다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:${JAVA_VERSION}-jdk AS deps
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8
WORKDIR /workspace
# toolchain 에 맞는 JDK 를 내려받지 않는다 — 내려받기 설정이 생겨도 JDK 는 이 이미지의 것 하나뿐이라 태그와 toolchain 이 다르면 실패한다.
RUN mkdir -p /root/.gradle && echo "org.gradle.java.installations.auto-download=false" > /root/.gradle/gradle.properties
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/
COPY backend/gradle backend/gradle
# 의존성 계층 — 빌드 파일이 그대로면 캐시를 재사용한다. dependencies 가 toolchain 을 확인하고,
# 검증 메타데이터 dry-run 이 모든 구성의 jar 를 내려받는다(dry-run 이 만든 파일은 지운다).
RUN chmod +x backend/gradlew \
 && backend/gradlew -p backend dependencies --no-daemon --console=plain > /dev/null \
 && backend/gradlew -p backend help --write-verification-metadata sha256 --dry-run --no-daemon --console=plain > /dev/null \
 && rm backend/gradle/verification-metadata.dryrun.xml
# 기본 로컬 임베딩 모델 — 고정 리비전 · sha256 으로 받는다(정본 backend/embedding-model.properties · ADR-0091). 모델 정보가 그대로면 캐시를 재사용한다.
COPY backend/embedding-model.properties backend/
RUN backend/gradlew -p backend fetchEmbeddingModel --no-daemon --console=plain

FROM deps AS check
# 테스트가 PostgreSQL 이미지를 읽는 개발용 Compose 파일(backend/build.gradle.kts 의 devComposeFile)
COPY compose.yaml compose.yaml
# 테스트가 현재 코드의 명세와 비교하는 화면용 API 명세(backend/build.gradle.kts 의 openApiSpecFile)
COPY api/openapi.json api/openapi.json
# 테스트가 예시 프로필을 검증하는 운영 안내서(backend/build.gradle.kts 의 sourcesGuideFile)
COPY docs/operations/sources.md docs/operations/sources.md
# 테스트가 예시 원문을 검증하는 운영 안내서(backend/build.gradle.kts 의 tagSetsGuideFile)
COPY docs/operations/tag-sets.md docs/operations/tag-sets.md
COPY backend/src backend/src
CMD ["backend/gradlew", "-p", "backend", "build", "--no-daemon", "--console=plain"]

FROM deps AS build
COPY backend/src backend/src
# 실행 jar 를 계층(의존성 · 로더 · 스냅샷 의존성 · 임베딩 모델 · 애플리케이션 — backend/build.gradle.kts 의 bootJar)으로 푼다 — jar 이름(버전)은 적지 않는다.
# 모델 파일은 풀린 파일 그대로 읽힌다(jar 안에서는 열지 않는다). 모델이 자기 계층에 들지 않으면(계층 패턴이 빗나가면) 여기서 멈춘다.
RUN backend/gradlew -p backend bootJar --no-daemon --console=plain \
 && java -Djarmode=tools -jar backend/build/libs/*.jar extract --layers --launcher --destination /extracted \
 && test -s /extracted/embedding-model/BOOT-INF/classes/embedding-model/model.onnx

# 실행 단계에는 복사와 메타데이터만 둔다(RUN 없음) — docs/development/infrastructure.md "이미지"
FROM eclipse-temurin:${JAVA_VERSION}-jre AS app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/embedding-model/ ./
COPY --from=build /extracted/application/ ./
# ONNX Runtime 텔레메트리를 끈다 — 없으면 로컬 임베딩 모델을 열지 않는다(정본 OnnxArticleEmbedder · ADR-0091)
ENV ORT_DISABLE_TELEMETRY=1
# 비root(nobody) — 계정을 만드는 명령 없이 숫자 uid 로 실행한다
USER 65534:65534
EXPOSE 8080
# ONNX Runtime · DJL 토크나이저가 네이티브를 올린다 — 제한 메서드 경고를 끈다
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "org.springframework.boot.loader.launch.JarLauncher"]
