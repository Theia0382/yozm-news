import { ADMIN_QUERY_KEY } from "@/features/admin/adminSession";

/** 사건 쪽 조회 키의 접두 */
export const EVENT_KEY = [...ADMIN_QUERY_KEY, "events"];

/** 사건 상세의 주소 */
export function eventPath(id: number): string {
  return `/admin/events/${id}`;
}
