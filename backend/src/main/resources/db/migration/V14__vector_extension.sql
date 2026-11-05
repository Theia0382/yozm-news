-- platform 모듈 — pgvector 확장 설치(docs/specs/platform.md §6 · platform-R19).
--
-- superuser 권한이 필요하다. 앱 계정이 superuser 가 아니면 DBA 가 먼저 설치해 두고, 그때 이 문장은 건너뛴다.
-- 버전 · 스키마를 정하지 않는다 — DB 서버의 기본 버전을 현재 스키마에 설치한다.
CREATE EXTENSION IF NOT EXISTS vector;
