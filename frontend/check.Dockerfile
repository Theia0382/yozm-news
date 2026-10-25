# 프론트엔드 검사 이미지. Node 버전은 이 태그가 정하고, frontend/.nvmrc 와 다르면 이미지 빌드가 실패한다. pnpm 버전은 corepack 이 package.json 의 packageManager 에서 읽는다.
FROM node:24
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8 CI=true COREPACK_ENABLE_DOWNLOAD_PROMPT=0
WORKDIR /workspace
COPY frontend/.nvmrc frontend/package.json frontend/pnpm-lock.yaml frontend/
RUN want="$(cat frontend/.nvmrc)"; have="$(node --version)"; \
    case "${have#v}" in "$want"|"$want".*) ;; *) echo "Node $have does not match frontend/.nvmrc ($want)" >&2; exit 1 ;; esac
RUN corepack enable && pnpm -C frontend install --frozen-lockfile
# Prettier 는 저장소 루트의 .editorconfig 를 설정으로 읽는다.
COPY .editorconfig ./
COPY frontend frontend
# 네 검사를 모두 돌리고, 하나라도 실패하면 실패로 끝낸다.
CMD ["sh", "-c", "s=0; for t in build lint format:check test; do pnpm -C frontend run $t || s=1; done; exit $s"]
