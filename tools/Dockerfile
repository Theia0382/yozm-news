# 저장소 도구 검사 이미지 — tools/ 아래 도구마다 단위 테스트(compose.check.yaml 의 tools). 검사 전용이라 배포 target 이 없다.
# 설치 단계가 없다 — 도구는 모두 표준 라이브러리 Python 이다. 이미지 내용은 docs/development/infrastructure.md "저장소 도구 검사"
FROM python:3-slim
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONDONTWRITEBYTECODE=1
WORKDIR /workspace
COPY . .
# 도구 디렉터리마다 테스트를 돌리고, 하나라도 실패하면(테스트가 없는 도구 포함) 실패로 끝낸다.
CMD ["sh", "-c", "s=0; for d in tools/*/; do echo \"== $d\"; python -m unittest discover -s \"$d\" || s=1; done; exit $s"]
