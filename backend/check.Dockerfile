# 백엔드 검사 이미지. JDK 버전은 이 태그가 정하고, backend/build.gradle.kts 의 toolchain 과 다르면 Gradle 이 맞는 JDK 를 찾지 못해 이미지 빌드가 실패한다.
FROM eclipse-temurin:25-jdk
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
# 테스트가 PostgreSQL 이미지를 읽는 개발용 Compose 파일(backend/build.gradle.kts 의 devComposeFile)
COPY compose.yaml compose.yaml
# 테스트가 현재 코드의 명세와 비교하는 화면용 API 명세(backend/build.gradle.kts 의 openApiSpecFile)
COPY api/openapi.json api/openapi.json
COPY backend/src backend/src
CMD ["backend/gradlew", "-p", "backend", "build", "--no-daemon", "--console=plain"]
