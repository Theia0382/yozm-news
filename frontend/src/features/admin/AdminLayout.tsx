import { useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useMemo } from "react";
import { cn } from "cn";
import { Link, Navigate, NavLink, Outlet, useLocation } from "react-router";
import { createAdminClient } from "@/features/admin/adminClient";
import { AdminSessionContext } from "@/features/admin/adminSession";
import { NoticesLink } from "@/features/notices/NoticesLink";
import { PASSWORD_PATH } from "@/features/session/RequireSignIn";
import { withNext } from "@/features/session/safeNext";
import { SESSION_QUERY_KEY, useMe } from "@/features/session/sessionQueries";
import { useSignOut } from "@/features/session/useSignOut";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME, WIDE_WIDTH } from "@/shared/ui/widths";

type AdminLayoutProps = {
  /** 시험이 가짜 서버 주소 · fetch 를 넣는다 — 화면에서는 같은 오리진의 상대 경로다 */
  baseUrl?: string;
  fetch?: typeof globalThis.fetch;
};

/**
 * 운영자 화면의 틀 — 운영자 계정 세션으로 연다. 로그인 상태를 읽어, 로그인하지 않았으면 로그인 화면으로(돌아올 주소를 next 로),
 * 첫 로그인의 비밀번호 변경이 남았으면 비밀번호 변경 화면으로(바꾼 뒤 돌아올 주소를 next 로) 보내고, 운영자가 아니면 권한 없음을
 * 알린다. 운영자면 머리줄에 운영자 메뉴 · 알림 링크 · 사용자 화면 링크 · 이름 · 로그아웃을 둔다. 운영자 API 가 401(세션이 끝남)로
 * 답하면 로그인 상태를 다시 읽어 로그인 화면으로 보낸다. LLM 키는 보지 않는다 — 운영자 경로는 키 관문 밖이다(docs/specs/user.md
 * user-R1).
 */
export function AdminLayout({ baseUrl, fetch }: AdminLayoutProps) {
  const queryClient = useQueryClient();
  const me = useMe();
  const location = useLocation();
  const { signOut, failed: signOutFailed } = useSignOut();

  const client = useMemo(
    () =>
      createAdminClient({
        baseUrl,
        fetch,
        onUnauthorized: () => {
          void queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
        },
      }),
    [baseUrl, fetch, queryClient],
  );

  if (me.isPending) {
    return (
      <NarrowFrame>
        <p className="text-muted-foreground">로그인 상태를 확인하는 중…</p>
      </NarrowFrame>
    );
  }
  if (me.isError) {
    return (
      <NarrowFrame>
        <section className="space-y-3">
          <p role="alert">로그인 상태를 확인하지 못했습니다.</p>
          <Button variant="outline" onClick={() => me.refetch()}>
            다시 시도
          </Button>
        </section>
      </NarrowFrame>
    );
  }
  if (me.data.result === "ANONYMOUS") {
    return <Navigate to={withNext("/login", location)} replace />;
  }
  if (me.data.passwordChangeRequired) {
    return <Navigate to={withNext(PASSWORD_PATH, location)} replace />;
  }
  if (me.data.role !== "ADMIN") {
    return (
      <NarrowFrame>
        <section className="space-y-3">
          <h1 className="text-2xl font-bold">운영자 권한이 없습니다</h1>
          <p>운영자 화면은 운영자 계정만 씁니다.</p>
          <Link to="/" className="underline">
            첫 화면으로
          </Link>
        </section>
      </NarrowFrame>
    );
  }

  return (
    <AdminSessionContext.Provider value={client}>
      <div className={cn(PAGE_FRAME, WIDE_WIDTH, "space-y-6")}>
        {/* 두 줄 — 첫 줄은 제목과 계정 링크, 둘째 줄은 메뉴(메뉴가 길어 한 줄에 두면 계정 링크가 줄바꿈된다) */}
        <header className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Link to="/admin" className="text-2xl font-bold">
              운영자
            </Link>
            <div className="flex items-center gap-3 text-sm">
              <NoticesLink />
              <Link to="/" className="underline">
                사용자 화면
              </Link>
              <span className="text-muted-foreground">
                {me.data.displayName ?? me.data.email}
              </span>
              <button type="button" className="underline" onClick={signOut}>
                로그아웃
              </button>
            </div>
          </div>
          <nav
            aria-label="운영자 메뉴"
            className="flex flex-wrap gap-x-4 gap-y-1"
          >
            <NavLink to="/admin" end className={navLinkClass}>
              소스 · 피드
            </NavLink>
            <NavLink to="/admin/collection" className={navLinkClass}>
              수집 현황
            </NavLink>
            <NavLink to="/admin/evaluation" className={navLinkClass}>
              평가 현황
            </NavLink>
            <NavLink to="/admin/evaluations" className={navLinkClass}>
              평가 결과
            </NavLink>
            <NavLink to="/admin/samples" className={navLinkClass}>
              표본 검수
            </NavLink>
            <NavLink to="/admin/thresholds" className={navLinkClass}>
              태그 기준
            </NavLink>
            <NavLink to="/admin/events" className={navLinkClass}>
              사건
            </NavLink>
            <NavLink to="/admin/storylines" className={navLinkClass}>
              스토리라인
            </NavLink>
            <NavLink to="/admin/big-storylines" className={navLinkClass}>
              큰 스토리라인
            </NavLink>
            <NavLink to="/admin/event-judgements" className={navLinkClass}>
              판정 기록
            </NavLink>
            <NavLink to="/admin/event-publications" className={navLinkClass}>
              이벤트 발행
            </NavLink>
            <NavLink to="/admin/accounts" className={navLinkClass}>
              계정
            </NavLink>
          </nav>
        </header>
        {signOutFailed && (
          <p role="alert">로그아웃하지 못했습니다. 다시 시도해 주세요.</p>
        )}
        <Outlet />
      </div>
    </AdminSessionContext.Provider>
  );
}

/** 운영자 틀의 안내 화면(확인 중 · 권한 없음 · 오류)의 그릇 — 첫 화면과 같은 가운데 · 읽기 폭 */
function NarrowFrame({ children }: { children: ReactNode }) {
  return <div className={cn(PAGE_FRAME, NARROW_WIDTH)}>{children}</div>;
}

/** 지금 화면의 메뉴는 굵게 밑줄 — NavLink 가 aria-current 도 단다 */
function navLinkClass({ isActive }: { isActive: boolean }): string {
  return isActive ? "font-semibold underline" : "text-muted-foreground";
}
