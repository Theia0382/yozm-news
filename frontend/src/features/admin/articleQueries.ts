import { ADMIN_QUERY_KEY } from "@/features/admin/adminSession";

/** 기사 쪽 조회 키의 접두 */
export const ARTICLE_KEY = [...ADMIN_QUERY_KEY, "articles"];

/** 기사 상세의 주소 */
export function articlePath(id: number): string {
  return `/admin/articles/${id}`;
}
