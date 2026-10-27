# ADR-0027: 벡터 저장은 PostgreSQL pgvector 확장으로 한다

- 상태: 승인
- 날짜: 2026-10-27
- 근거: [이슈 #36](https://github.com/Theia0382/yozm-news/issues/36) — 로컬 `postgres:18` 이미지의 Debian 버전(13), Docker Hub 의 `pgvector/pgvector:pg18-trixie` 게시 플랫폼(`linux/amd64` · `linux/arm64`), pgvector 문서와 `vector.control` 을 확인
- 관련: [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(사건 묶기), [ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md)(PostgreSQL 이미지의 정본은 `compose.yaml` — 그대로 성립), [ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)(실행 단계에 명령 없음), [ADR-0029](0029-indefinite-summary-retention-with-optional-deletion.md)(요약문 보존)

## 맥락

- 사건 묶기는 기사 임베딩을 저장하고 가까운 사건을 찾는다([ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)). 장기 기능은 오래된 사건 사이의 유사도 검색도 쓴다.
- 상태 저장소는 PostgreSQL 하나다([overview.md](../overview.md) §3).
- 지금 PostgreSQL 이미지 `postgres:18` 은 Debian 13(trixie) 기반이다. pgvector 이미지의 짧은 태그 `pg18` 은 Debian 12(bookworm) 기반이라, 그대로 바꾸면 glibc 버전이 내려가 문자열 정렬 규칙(collation)이 달라질 수 있다. OS 계열을 밝힌 태그(`pg18-trixie`)도 게시되어 있다.
- pgvector 확장은 trusted 로 표시되어 있지 않아 `CREATE EXTENSION` 에 superuser 권한이 필요하다.
- HNSW 인덱스는 근사 검색이다. 같은 입력에서도 결과가 달라질 수 있고, 조건을 걸면 인덱스를 훑은 뒤 걸러서 결과가 적게 나올 수 있다(0.8 부터 반복 검색으로 보완). 인덱스를 걸 수 있는 차원은 `vector` 2,000, `halfvec` 4,000 까지다.
- 기사 벡터를 무기한 쌓으면 하루 4,000건 · 768차원 `float4` 기준 1년에 약 4.5GB 에 인덱스가 더해진다(추정).

## 결정

- **pgvector 확장을 쓴다.** 이미지는 지금과 OS 계열을 맞춘 태그(`pgvector/pgvector:pg18-trixie` 형식)를 쓴다. 이미지의 정본은 계속 `compose.yaml` 의 한 줄이고([ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md)), 테스트는 그 이미지를 PostgreSQL 호환 이미지로 선언해 쓴다. 이미지 교체는 사건 묶기를 구현할 때 한다.
- **최소 버전은 0.8 이다**(`halfvec` 0.7, 반복 인덱스 검색 0.8). 앱은 기동할 때 확장 버전을 확인한다.
- **설치**: 마이그레이션은 `CREATE EXTENSION IF NOT EXISTS` 로 쓴다. 앱 계정에 superuser 권한이 없는 외부 DB 에서는 DBA 가 확장을 먼저 설치한다.
- **사건 배정은 인덱스 없이 정확 검색으로 한다.** 비교 대상이 시간 창 안의 활성 사건뿐이라 수가 작다(추정). HNSW 인덱스는 오래된 사건을 찾는 장기 검색에만 건다.
- **오래 남기는 벡터는 사건 중심 벡터(`halfvec`)뿐이다.** 기사 벡터는 사건 배정에 쓰는 기간에 여유를 더한 만큼만 보관하고, 필요하면 요약문으로 다시 계산한다([ADR-0029](0029-indefinite-summary-retention-with-optional-deletion.md)).
- **벡터마다 임베딩 모델 버전을 기록한다.** 모델을 바꾸면 새 컬럼 · 재계산 · 전환 · 이전 컬럼 삭제를 백그라운드 작업으로 한다.

## 결과

- 셀프 호스팅 요건에 pgvector 확장이 더해진다. PostgreSQL 하나라는 원칙은 그대로다.
- 공식 이미지 대신 pgvector 프로젝트의 이미지에 의존한다. PostgreSQL 보안 패치가 반영되는 주기를 확인해야 한다.
- 확장 버전을 올릴 때 `ALTER EXTENSION vector UPDATE` 가 필요하다. PostgreSQL 메이저 업그레이드는 지금처럼 덤프 · 복원으로 하고, 새 버전에도 확장이 있어야 한다.
- Testcontainers 는 `postgres` 가 아닌 이미지 이름을 거부하므로 호환 선언이 필요하다.
- 사건 배정이 정확 검색이라 같은 입력에서 같은 후보가 나온다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 별도 벡터 DB(Qdrant 등)를 둔다 | 상태 저장소가 둘이 되고 셀프 호스팅 부담이 늘어난다 |
| 벡터를 일반 배열 컬럼에 두고 애플리케이션 메모리에서 비교한다 | 장기 검색이 어렵고, worker 마다 벡터를 읽어 오는 코드와 메모리가 필요하며, 나중에 벡터 타입으로 옮기는 변환이 생긴다 |
| 짧은 태그 `pg18` 을 쓴다 | Debian 계열이 바뀌어 정렬 규칙이 달라질 수 있다 |
| `postgres:18` 위에 패키지를 설치한 이미지를 직접 만든다 | 실행 단계에서 명령을 돌려야 해 에뮬레이션 없는 arm64 빌드가 실패한다([ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)) |
| 기사 벡터를 무기한 보관한다 | 저장 공간과 백업이 크게 늘어난다. 장기 검색은 사건 단위로 충분하다 |
| 사건 배정에 HNSW 인덱스를 쓴다 | 근사 검색이라 같은 입력에서 배정이 달라질 수 있어 판단 근거를 재현하기 어렵다 |
