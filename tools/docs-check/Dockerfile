# 문서 검사 이미지 — 공개 마크다운의 상대 링크 · 앵커(compose.check.yaml 의 docs). 검사 전용이라 배포 target 이 없다.
# 검사기는 표준 라이브러리만 쓴다 — 설치 단계가 없다. 이미지에 들어가는 파일은 Dockerfile.dockerignore 의 허용 목록이 정한다.
FROM python:3-slim
ENV TZ=UTC LANG=C.UTF-8 LC_ALL=C.UTF-8 PYTHONDONTWRITEBYTECODE=1
WORKDIR /workspace
COPY . .
# 단위 테스트와 저장소 검사를 모두 돌리고, 하나라도 실패하면 실패로 끝낸다.
CMD ["sh", "-c", "s=0; python -m unittest discover -s tools/docs-check || s=1; python tools/docs-check/check_links.py || s=1; exit $s"]
