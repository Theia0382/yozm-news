import { ADMIN_QUERY_KEY } from "@/features/admin/adminSession";

/** 스토리라인 쪽 조회 키의 접두 */
export const STORYLINE_KEY = [...ADMIN_QUERY_KEY, "storylines"];

/** 스토리라인 상세의 주소 */
export function storylinePath(id: number): string {
  return `/admin/storylines/${id}`;
}
