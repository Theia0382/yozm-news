// 태그 체계 버전 목록 — 운영자 화면이 함께 쓰는 조회와 고르기.
import { useQuery } from "@tanstack/react-query";
import { type Schemas, unwrap } from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";

type TagSetVersion = Schemas["TagSetVersionItem"];

/** 태그 체계 버전 목록 — 화면이 함께 쓰는 조회 키 */
export function useTagSetVersions() {
  const client = useAdminClient();
  return useQuery({
    queryKey: [...ADMIN_QUERY_KEY, "tag-sets"],
    queryFn: () => unwrap(client.GET("/api/admin/tag-sets")),
  });
}

/** 고른 버전 — 고르지 않았으면 활성 버전, 목록이 아직 없으면 null */
export function chosenOrActive(
  chosen: number | null,
  versions: TagSetVersion[] | undefined,
): number | null {
  return chosen ?? versions?.find((item) => item.active)?.id ?? null;
}

export function versionLabel(version: TagSetVersion): string {
  return `버전 ${version.number}${version.active ? " (활성)" : ""}`;
}
