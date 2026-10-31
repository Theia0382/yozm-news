// 운영자 토큰 보관 — 탭의 sessionStorage 에만 둔다(새로고침에는 남고 탭을 닫으면 사라진다).
const KEY = "yozmnews.admin.token";

export function readAdminToken(): string | null {
  return sessionStorage.getItem(KEY);
}

export function saveAdminToken(token: string): void {
  sessionStorage.setItem(KEY, token);
}

export function clearAdminToken(): void {
  sessionStorage.removeItem(KEY);
}
