# 프론트엔드 이미지 — target check 는 검사(compose.check.yaml), proxy 는 배포(deploy/compose.yaml). 구성은 docs/development/infrastructure.md "이미지".
# Node 태그는 frontend/.nvmrc 와 같아야 한다 — 다르면 이미지 빌드가 실패한다.

# 빌드는 빌더 플랫폼에서 돈다 — 정적 파일은 아키텍처와 무관하다.
FROM --platform=$BUILDPLATFORM node:24 AS deps
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8 CI=true COREPACK_ENABLE_DOWNLOAD_PROMPT=0
WORKDIR /workspace
COPY frontend/.nvmrc frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/
RUN want="$(cat frontend/.nvmrc)"; have="$(node --version)"; \
    case "${have#v}" in "$want"|"$want".*) ;; *) echo "Node $have does not match frontend/.nvmrc ($want)" >&2; exit 1 ;; esac
# corepack 은 실행 위치의 package.json 에서 packageManager 를 찾는다 — 여기부터 pnpm 은 frontend 안에서 돈다.
WORKDIR /workspace/frontend
RUN corepack enable && pnpm install --frozen-lockfile
# Prettier 는 저장소 루트의 .editorconfig 를 설정으로 읽는다.
COPY .editorconfig /workspace/
# build 가 API 타입을 생성하는 화면용 API 명세(package.json 의 generate:api)
COPY api/openapi.json /workspace/api/openapi.json
COPY frontend /workspace/frontend

FROM deps AS check
# 네 검사를 모두 돌리고, 하나라도 실패하면 실패로 끝낸다.
CMD ["sh", "-c", "s=0; for t in build lint format:check test; do pnpm run $t || s=1; done; exit $s"]

FROM deps AS build
RUN pnpm run build

# 실행 단계에는 복사와 메타데이터만 둔다(RUN 없음) — docs/development/infrastructure.md "이미지"
FROM caddy:2 AS proxy
COPY frontend/proxy/Caddyfile frontend/proxy/backend-paths.caddy /etc/caddy/
COPY --from=build /workspace/frontend/dist /srv
# 비root(nobody) — 1024 이상 포트로 받고, 설정을 디스크에 저장하지 않는다(Caddyfile)
USER 65534:65534
EXPOSE 8080
