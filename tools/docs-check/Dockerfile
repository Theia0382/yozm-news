# 문서 검사 이미지 — 공개 마크다운의 상대 링크 · 앵커(compose.check.yaml 의 docs). 검사 전용이라 배포 target 이 없다.
# 설치 단계가 없다 — 검사기와 이미지 내용은 docs/development/infrastructure.md "문서 링크 검사"
FROM python:3-slim
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONDONTWRITEBYTECODE=1
WORKDIR /workspace
COPY . .
# 검사기의 단위 테스트는 저장소 도구 검사(tools/Dockerfile)가 돌린다.
CMD ["python", "tools/docs-check/check_links.py"]
