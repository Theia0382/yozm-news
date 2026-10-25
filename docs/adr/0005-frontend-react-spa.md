# ADR-0005: 프론트엔드: React SPA, 단일 오리진 제공

- 상태: 승인
- 날짜: 2026-10-25

## 맥락

- 메인 뷰는 웹이다. 화면은 다이제스트 읽기, 지난 다이제스트, 피드백, 설정 폼, 키 등록, 운영자 화면 정도다.
- 다이제스트는 로그인 뒤에 있으므로 SEO나 서버 렌더링(SSR)의 이점이 거의 없다.
- 비교한 선택지: React, Vue, SvelteKit, Thymeleaf/JTE + htmx, Next.js/Nuxt, Angular.

## 결정

- **React + TypeScript + Vite** 기반 SPA로 만든다.
- 빌드 결과는 정적 파일이다. `proxy` 컨테이너(Caddy)가 내려주고, 같은 오리진에서 `/api/**`를 백엔드로 프록시한다.
- 백엔드 OpenAPI 명세로 TS 타입을 생성해 API 계약을 맞춘다.
- 세부 스택: React Router(라우팅), TanStack Query(서버 상태), Tailwind CSS + shadcn/ui(스타일·컴포넌트).

## 결과

- 단일 오리진이라 CORS와 교차 사이트 쿠키를 신경 쓰지 않아도 된다.
- 셀프 호스팅과 클라우드 배포의 구성이 같다. Cloudflare CDN이 정적 파일을 캐싱한다.
- 프론트엔드와 백엔드 빌드가 두 벌이 된다.
- 나중에 모바일 앱을 만들면 화면용 API를 재사용할 수 있다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| Thymeleaf/JTE + htmx | 배포물이 하나라는 장점이 있지만, 사용자가 React를 선택했다 |
| Next.js / Nuxt | Node 런타임을 추가로 운영해야 하는데, 로그인 뒤 앱이라 얻는 게 적다 |
| Cloudflare Pages로 프론트엔드를 따로 호스팅 | 가능하지만, API와 오리진이 달라지고 셀프 호스팅 구성과 차이가 생긴다. 필요하면 나중에 바꿀 수 있다 |
