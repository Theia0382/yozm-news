# 백엔드 이미지 — target check 는 검사(compose.check.yaml), app 은 배포(deploy/compose.yaml).
# JDK 버전은 JAVA_VERSION 이 정하고, backend/build.gradle.kts 의 toolchain 과 다르면 Gradle 이 맞는 JDK 를 찾지 못해 deps 단계가 실패한다.
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

FROM deps AS check
# 테스트가 PostgreSQL 이미지를 읽는 개발용 Compose 파일(backend/build.gradle.kts 의 devComposeFile)
COPY compose.yaml compose.yaml
# 테스트가 현재 코드의 명세와 비교하는 화면용 API 명세(backend/build.gradle.kts 의 openApiSpecFile)
COPY api/openapi.json api/openapi.json
COPY backend/src backend/src
CMD ["backend/gradlew", "-p", "backend", "build", "--no-daemon", "--console=plain"]

FROM deps AS build
COPY backend/src backend/src
# 실행 jar 를 Spring Boot 계층(의존성 · 로더 · 스냅샷 의존성 · 애플리케이션)으로 푼다 — jar 이름(버전)은 적지 않는다.
RUN backend/gradlew -p backend bootJar --no-daemon --console=plain \
 && java -Djarmode=tools -jar backend/build/libs/*.jar extract --layers --launcher --destination /extracted

# 실행 단계는 대상 플랫폼 이미지다 — 명령(RUN)을 두면 에뮬레이션 없는 빌더에서 빌더와 다른 아키텍처(amd64 빌더면 arm64)의 빌드가 깨진다. 복사와 메타데이터만 둔다.
FROM eclipse-temurin:${JAVA_VERSION}-jre AS app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
# 비root(nobody) — 계정을 만드는 명령 없이 숫자 uid 로 실행한다
USER 65534:65534
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
