// 로그인 뒤 돌아갈 주소 — 같은 오리진의 경로만 받는다(열린 리다이렉트 방지). 아니면 첫 화면.

const HOME = "/";

const LOGIN = "/login";

export function safeNext(raw: string | null, origin: string): string {
  // 브라우저는 /\host 를 //host 처럼 다른 호스트로 읽는다
  if (
    !raw ||
    !raw.startsWith("/") ||
    raw.startsWith("//") ||
    raw.startsWith("/\\")
  ) {
    return HOME;
  }
  let url: URL;
  try {
    url = new URL(raw, origin);
  } catch {
    return HOME;
  }
  if (url.origin !== origin || url.pathname === LOGIN) {
    return HOME;
  }
  return url.pathname + url.search + url.hash;
}

/** 돌아올 주소(지금 경로 · 질의)를 next 로 단 경로 — 로그인 · 비밀번호 변경 화면으로 보낼 때 쓴다. 받는 쪽은 safeNext 로 읽는다 */
export function withNext(
  path: string,
  here: { pathname: string; search: string },
): string {
  return `${path}?next=${encodeURIComponent(here.pathname + here.search)}`;
}
