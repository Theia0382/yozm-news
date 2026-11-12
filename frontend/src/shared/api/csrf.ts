import type { Middleware } from "openapi-fetch";

// 변경 요청의 CSRF 토큰 — 서버가 준 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 돌려보낸다(docs/specs/user.md user-R10).

/** 서버가 CSRF 토큰을 싣는 쿠키 */
export const CSRF_COOKIE = "XSRF-TOKEN";

/** 변경 요청이 CSRF 토큰을 돌려보내는 헤더 */
export const CSRF_HEADER = "X-XSRF-TOKEN";

/** 토큰을 싣지 않는 메서드 — 상태를 바꾸지 않는다 */
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

/** 쿠키 문자열(document.cookie 꼴)에서 이름의 값 — 없으면 null */
export function readCookie(name: string, cookies: string): string | null {
  for (const part of cookies.split(";")) {
    const [key, ...value] = part.trim().split("=");
    if (key === name) {
      return decodeURIComponent(value.join("="));
    }
  }
  return null;
}

/** 변경 메서드 요청에 CSRF 헤더를 붙인다 — 쿠키가 없으면 붙이지 않고 서버가 거부한다 */
export function csrfMiddleware(
  cookies: () => string = () => document.cookie,
): Middleware {
  return {
    onRequest({ request }) {
      if (!SAFE_METHODS.has(request.method)) {
        const token = readCookie(CSRF_COOKIE, cookies());
        if (token) {
          request.headers.set(CSRF_HEADER, token);
        }
      }
      return request;
    },
  };
}
