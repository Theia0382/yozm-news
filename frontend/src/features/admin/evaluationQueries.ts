import { useQuery } from "@tanstack/react-query";
import { unwrap } from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import type { Period } from "@/features/admin/periods";

/** 평가 쪽 조회 키의 접두 — 기준을 바꾸면 이 접두로 한꺼번에 버린다 */
export const EVALUATION_KEY = [...ADMIN_QUERY_KEY, "evaluation"];

/** 버전 하나 · 기간의 평가 현황 — 평가 현황 화면과 평가 결과 화면(거르기 선택지 · 수)이 같은 캐시를 쓴다 */
export function useEvaluationOverview(
  version: number | null,
  period: Period,
  enabled: boolean,
) {
  const client = useAdminClient();
  return useQuery({
    queryKey: [...EVALUATION_KEY, "overview", version, period],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/evaluation-overview", {
          params: { query: { version: version ?? 0, ...period } },
        }),
      ),
    enabled: enabled && version !== null,
  });
}
