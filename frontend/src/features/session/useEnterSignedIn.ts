import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { SESSION_QUERY_KEY } from "@/features/session/sessionQueries";

/**
 * 로그인 상태가 바뀐 뒤(로그인 · 가입 · 복구 재설정 · 강제 변경) 화면을 옮긴다 — 로그인 상태를 (지금 관찰하지 않는 조회까지) 다시 읽은
 * 뒤 간다. 다시 읽은 사용자가 앞 사용자와 다르면 앱의 감시(userChange.ts)가 앞 사용자의 캐시를 비운다 — 화면을 옮기기 전에 끝난다.
 */
export function useEnterSignedIn() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return async (to: string) => {
    await queryClient.invalidateQueries({
      queryKey: SESSION_QUERY_KEY,
      refetchType: "all",
    });
    navigate(to, { replace: true });
  };
}
