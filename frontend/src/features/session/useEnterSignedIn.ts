import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { NOTICES_QUERY_KEY } from "@/features/notices/noticeQueries";
import { SESSION_QUERY_KEY } from "@/features/session/sessionQueries";

/**
 * 로그인 상태가 바뀐 뒤(로그인 · 가입 · 복구 재설정 · 강제 변경) 화면을 옮긴다 — 같은 창에서 앞서 로그인했던 사용자의 알림을 버리고,
 * 로그인 상태를 다시 읽은 뒤 간다.
 */
export function useEnterSignedIn() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return async (to: string) => {
    queryClient.removeQueries({ queryKey: NOTICES_QUERY_KEY });
    await queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
    navigate(to, { replace: true });
  };
}
