import { useQuery } from "@tanstack/react-query";
import { useApiClient } from "@/shared/api/client";

/** 공개 가입 정책 — 로그인 화면의 가입 링크와 가입 화면이 읽는다(docs/specs/user.md §4) */
export function useSignUpPolicy() {
  const client = useApiClient();
  return useQuery({
    queryKey: ["sign-up-policy"],
    queryFn: async (): Promise<boolean> => {
      const { data, response } = await client.GET("/api/sign-up-policy");
      if (!response.ok || !data) {
        throw new Error(
          `가입 정책을 읽지 못했습니다 (HTTP ${response.status})`,
        );
      }
      return data.open;
    },
    select: (open) => ({ open }),
    retry: false,
  });
}
