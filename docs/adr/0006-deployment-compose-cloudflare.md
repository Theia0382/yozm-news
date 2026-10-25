# ADR-0006: 배포: Docker Compose + Cloudflare Tunnel

- 상태: 승인
- 날짜: 2026-10-25

## 맥락

- 클라우드 업체는 정하지 않았고, 셀프 호스팅도 선택지다.
- 대규모 인프라는 쓸 수 없다. Cloudflare 등 저비용 서비스는 활용할 수 있다.

## 결정

- 배포 단위는 **Docker Compose** 하나다(`proxy`, `web`, `worker`, `postgres`, `cloudflared`, `backup`).
- 공개는 **Cloudflare Tunnel**로만 한다. 인바운드 포트를 열지 않는다.
- Cloudflare에서 DNS·CDN·WAF·요청 제한, R2(백업)를 쓴다. Jev 호출은 선택적으로 AI Gateway를 경유한다.
- 권장 시작점은 서울 리전 VPS 한 대다. 이미지는 amd64와 arm64로 빌드한다.

## 결과

- 어떤 호스트로 옮겨도 Compose 파일과 환경 변수만 옮기면 된다.
- 단일 호스트라 장애점이 하나다. 백업과 복구 리허설로 보완한다.
- 관리형 DB로 바꾸려면 `postgres` 서비스를 빼고 접속 정보만 바꾸면 된다.

## 검토한 대안

| 대안 | 보류 이유 |
|---|---|
| Kubernetes | 규모에 비해 과하다 |
| 관리형 컨테이너 플랫폼(Fly.io, Railway, Cloud Run 등) | 가능하다. 다만 환경마다 구성이 달라지고 상시 실행 JVM 비용이 든다. 필요하면 이전할 수 있다 |
| Cloudflare Containers | 2026년 4월 정식 출시. Workers 앞단을 전제로 한 실행 모델이라, 상시 실행 worker와 DB 연결 구성에는 아직 맞지 않는다 |
