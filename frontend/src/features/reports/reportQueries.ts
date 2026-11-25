import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ReportList = components["schemas"]["ReportListResponse"];
export type ReportSummary = components["schemas"]["ReportSummaryResponse"];
export type ReportDetail = components["schemas"]["ReportDetailResponse"];
export type ReportSection = components["schemas"]["ReportSectionResponse"];
export type ReportCitation = components["schemas"]["ReportCitationResponse"];
export type ReportStoryline = components["schemas"]["ReportStorylineResponse"];
export type ReportUserStorylineCount =
  components["schemas"]["ReportUserStorylineCountResponse"];
export type ReportTopicCount =
  components["schemas"]["ReportTopicCountResponse"];
export type ReportInterests = components["schemas"]["ReportInterestsResponse"];
export type ReportUsage = components["schemas"]["ReportUsageResponse"];
export type ReportKind = ReportSummary["kind"];
export type ReportStatus = ReportSummary["status"];
export type ReportFailure = NonNullable<ReportSummary["failure"]>;
export type ReportTrend = ReportTopicCount["trend"];
export type InterestShift = ReportInterests["topics"][number]["shift"];
export type ReportRegeneration =
  components["schemas"]["ReportRegenerationResponse"];
export type ReportRegenerationResult = ReportRegeneration["result"];
export type ReportConditions =
  components["schemas"]["ReportConditionsResponse"];

/** 변화 리포트 조회 키의 접두 */
export const REPORTS_QUERY_KEY = ["reports"] as const;

/** 변화 리포트 요청이 실패했다 — 화면이 상태 코드로 가른다 */
export class ReportRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`변화 리포트를 읽지 못했습니다 (HTTP ${status})`);
    this.status = status;
  }
}

/** 한 종류의 리포트 목록 — 최근 순, 한 쪽씩(docs/specs/reader.md reader-R15) */
export function useReports(kind: ReportKind) {
  const client = useApiClient();
  return useInfiniteQuery({
    queryKey: [...REPORTS_QUERY_KEY, "list", kind],
    initialPageParam: null as number | null,
    queryFn: async ({ pageParam }): Promise<ReportList> => {
      const { data, response } = await client.GET("/api/reports", {
        params: {
          query: pageParam === null ? { kind } : { kind, before: pageParam },
        },
      });
      if (!response.ok || !data) {
        throw new ReportRequestError(response.status);
      }
      return data;
    },
    getNextPageParam: (page): number | undefined => page.next ?? undefined,
  });
}

/** 리포트 한 편(docs/specs/reader.md reader-R16) — 없거나 다른 사용자의 리포트면 404 */
export function useReport(id: number) {
  const client = useApiClient();
  return useQuery({
    queryKey: [...REPORTS_QUERY_KEY, "detail", id],
    queryFn: async (): Promise<ReportDetail> => {
      const { data, response } = await client.GET("/api/reports/{id}", {
        params: { path: { id } },
      });
      if (!response.ok || !data) {
        throw new ReportRequestError(response.status);
      }
      return data;
    },
  });
}

/** 실패한 리포트 다시 만들기(docs/specs/reader.md reader-R16) — 끝나면 리포트 조회를 다시 읽는다 */
export function useRegenerateReport(id: number) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<ReportRegeneration> => {
      const { data, response } = await client.POST(
        "/api/reports/{id}/regeneration",
        { params: { path: { id } } },
      );
      if (!response.ok || !data) {
        throw new ReportRequestError(response.status);
      }
      return data;
    },
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: REPORTS_QUERY_KEY }),
  });
}

/** 리포트의 생성 조건(docs/specs/reader.md reader-R17) — 사용자와 무관한 값이라 한 번 읽어 둔다 */
export function useReportConditions() {
  const client = useApiClient();
  return useQuery({
    queryKey: [...REPORTS_QUERY_KEY, "conditions"],
    queryFn: async (): Promise<ReportConditions> => {
      const { data, response } = await client.GET("/api/reports/conditions");
      if (!response.ok || !data) {
        throw new ReportRequestError(response.status);
      }
      return data;
    },
    staleTime: Number.POSITIVE_INFINITY,
  });
}
