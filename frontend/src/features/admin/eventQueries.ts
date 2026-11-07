import { ADMIN_QUERY_KEY } from "@/features/admin/adminSession";

/** 사건 쪽 조회 키의 접두 — 큰 사건 기준을 바꾸면 이 접두로 한꺼번에 버린다(목록의 큰 사건 표시도 바뀐다) */
export const EVENT_KEY = [...ADMIN_QUERY_KEY, "events"];

/** 사건 상세의 주소 */
export function eventPath(id: number): string {
  return `/admin/events/${id}`;
}
