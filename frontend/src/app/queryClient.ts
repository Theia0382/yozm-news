import { QueryClient } from "@tanstack/react-query";
import { forgetOtherUsersData } from "@/features/session/userChange";

/** 앱의 조회 캐시 — 사용자가 바뀌면 앞 사용자의 캐시를 비우는 감시를 건다 */
export function createQueryClient(): QueryClient {
  const queryClient = new QueryClient();
  forgetOtherUsersData(queryClient);
  return queryClient;
}
