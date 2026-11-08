# 사건 묶기 평가 도구의 검사 이미지 — 도구의 단위 시험 · 운영자 API 명세 대조 · 라벨 기준 판 대조(compose.check.yaml 의 event-eval).
# 검사 전용이라 배포 target 이 없다. 설치 단계가 없다 — 이미지 내용은 docs/development/infrastructure.md "사건 평가 도구 검사"
FROM python:3-slim
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONDONTWRITEBYTECODE=1
WORKDIR /workspace
COPY . .
CMD ["python", "-m", "unittest", "discover", "-s", "tools/event-eval"]
