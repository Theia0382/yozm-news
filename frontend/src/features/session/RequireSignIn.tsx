import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router";
import { SESSION_QUERY_KEY, useMe } from "@/features/session/sessionQueries";
import { useApiClient } from "@/shared/api/client";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 로그인한 사용자만 보는 화면의 틀 — 로그인하지 않았으면 로그인 화면으로 보내고(돌아올 주소를 next 로), 로그인했으면 머리줄에 이름과 로그아웃을
 * 둔다.
 */
export function RequireSignIn() {
  const me = useMe();
  const location = useLocation();
  const navigate = useNavigate();
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [signOutFailed, setSignOutFailed] = useState(false);

  // 첫 화면 묶음에 들어가므로 cn · 공용 버튼을 들이지 않는다 — HomePage 와 같은 이유
  if (me.isPending) {
    return (
      <p className={`${PAGE_FRAME} ${NARROW_WIDTH} text-muted-foreground`}>
        로그인 상태를 확인하는 중…
      </p>
    );
  }
  if (me.isError) {
    return (
      <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-3`}>
        <p role="alert">로그인 상태를 확인하지 못했습니다.</p>
        <button
          type="button"
          className="rounded-md border px-3 py-1"
          onClick={() => me.refetch()}
        >
          다시 시도
        </button>
      </section>
    );
  }
  if (me.data.result === "ANONYMOUS") {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  async function signOut() {
    setSignOutFailed(false);
    try {
      const { response } = await client.DELETE("/api/session");
      if (!response.ok) {
        // 로그아웃하지 못했으면 로그인한 화면에 남아 알린다 — 상태를 다시 읽어 새 요청 보호 토큰을 받는다
        setSignOutFailed(true);
        await me.refetch();
        return;
      }
    } catch {
      setSignOutFailed(true);
      return;
    }
    queryClient.removeQueries({ queryKey: SESSION_QUERY_KEY });
    navigate("/login", { replace: true });
  }

  return (
    <>
      <header
        className={`${PAGE_FRAME} ${NARROW_WIDTH} flex items-center justify-end gap-3 pb-6 text-sm`}
      >
        <span className="text-muted-foreground">
          {me.data.displayName ?? me.data.email}
        </span>
        <button type="button" className="underline" onClick={signOut}>
          로그아웃
        </button>
      </header>
      {signOutFailed && (
        <p role="alert" className={`${PAGE_FRAME} ${NARROW_WIDTH} pb-6`}>
          로그아웃하지 못했습니다. 다시 시도해 주세요.
        </p>
      )}
      <Outlet />
    </>
  );
}
