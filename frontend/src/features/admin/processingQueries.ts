import { useQuery } from "@tanstack/react-query";
import { unwrap } from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";

/** 처리 현황을 다시 읽는 간격 — 화면을 연 동안 */
export const PROCESSING_REFETCH_MS = 30_000;

/** 처리 현황 — 회차마다 남은 수 · 미룬 수 · 최근 처리량 · 마지막 회차. 화면을 연 동안 30초마다 다시 읽는다 */
export function useProcessing() {
  const client = useAdminClient();
  return useQuery({
    queryKey: ["admin", "processing"],
    queryFn: () => unwrap(client.GET("/api/admin/processing")),
    refetchInterval: PROCESSING_REFETCH_MS,
  });
}
