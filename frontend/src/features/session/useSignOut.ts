import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router";
import { useMe } from "@/features/session/sessionQueries";
import { useApiClient } from "@/shared/api/client";

/**
 * 로그아웃 — 서버 세션을 끝내고 캐시를 모두 버린 뒤(같은 창에 앞 사용자의 데이터 · 운영자 데이터가 남지 않게) 로그인 화면으로 간다.
 * 실패하면 로그인한 화면에 남아 failed 를 켜고, 로그인 상태를 다시 읽어 새 요청 보호 토큰을 받는다. 로그인 틀과 운영자 틀이 함께 쓴다.
 */
export function useSignOut(): {
  signOut: () => Promise<void>;
  failed: boolean;
} {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const me = useMe();
  const [failed, setFailed] = useState(false);

  async function signOut() {
    setFailed(false);
    try {
      const { response } = await client.DELETE("/api/session");
      if (!response.ok) {
        setFailed(true);
        await me.refetch();
        return;
      }
    } catch {
      setFailed(true);
      return;
    }
    queryClient.removeQueries();
    navigate("/login", { replace: true });
  }

  return { signOut, failed };
}
