import { useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useMemo, useState } from "react";
import { Link, Outlet } from "react-router";
import {
  ADMIN_CLOSED,
  AdminApiError,
  createAdminClient,
  failureText,
  unwrap,
} from "@/features/admin/adminClient";
import {
  ADMIN_QUERY_KEY,
  AdminSessionContext,
} from "@/features/admin/adminSession";
import {
  clearAdminToken,
  readAdminToken,
  saveAdminToken,
} from "@/features/admin/adminToken";
import { Button } from "@/shared/ui/button";

type AdminLayoutProps = {
  /** 시험이 가짜 서버 주소 · fetch 를 넣는다 — 화면에서는 같은 오리진의 상대 경로다 */
  baseUrl?: string;
  fetch?: typeof globalThis.fetch;
};

/**
 * 운영자 화면의 틀. 토큰이 없어도 첫 조회를 보내 서버의 답으로 가른다 — 403(닫힘)이면 닫힘 안내, 401 이면 토큰 입력, 성공이면 화면.
 */
export function AdminLayout({ baseUrl, fetch }: AdminLayoutProps) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState(readAdminToken);
  const [notice, setNotice] = useState<string | null>(null);

  // 토큰마다 클라이언트를 새로 만든다 — 미들웨어가 그 토큰을 붙이고, 401 이면 그 토큰을 버린다
  const client = useMemo(
    () =>
      createAdminClient({
        baseUrl,
        fetch,
        token: () => token,
        onUnauthorized: () => {
          if (token) {
            clearAdminToken();
            setToken(null);
            setNotice("토큰이 맞지 않습니다. 다시 입력해 주세요.");
          }
        },
      }),
    [baseUrl, fetch, token],
  );

  const probe = useQuery({
    queryKey: [...ADMIN_QUERY_KEY, "session", token],
    queryFn: () => unwrap(client.GET("/api/admin/presets")),
    retry: false,
  });

  function open(value: string) {
    saveAdminToken(value);
    setNotice(null);
    setToken(value);
    queryClient.removeQueries({ queryKey: ADMIN_QUERY_KEY });
  }

  function lock() {
    clearAdminToken();
    setNotice(null);
    setToken(null);
    queryClient.removeQueries({ queryKey: ADMIN_QUERY_KEY });
  }

  if (probe.isPending) {
    return <p className="text-muted-foreground">운영자 기능을 확인하는 중…</p>;
  }
  if (probe.error) {
    const error = probe.error;
    if (
      error instanceof AdminApiError &&
      error.status === 403 &&
      error.problem?.type === ADMIN_CLOSED
    ) {
      return (
        <section className="space-y-3">
          <h1 className="text-2xl font-bold">운영자 기능이 닫혀 있습니다</h1>
          <p>
            이 인스턴스는 운영자 토큰을 설정하지 않았습니다. 서버에{" "}
            <code>YOZMNEWS_ADMIN_TOKEN</code>을 설정하고 다시 띄우면 열립니다.
          </p>
        </section>
      );
    }
    if (error instanceof AdminApiError && error.status === 401) {
      return <TokenForm notice={notice} onSubmit={open} />;
    }
    return (
      <section className="space-y-3">
        <p role="alert">{failureText(error)}</p>
        <Button variant="outline" onClick={() => probe.refetch()}>
          다시 시도
        </Button>
      </section>
    );
  }

  return (
    <AdminSessionContext.Provider value={client}>
      <div className="space-y-6">
        <header className="flex items-center justify-between gap-3">
          <Link to="/admin" className="text-2xl font-bold">
            운영자
          </Link>
          <Button variant="outline" onClick={lock}>
            잠금
          </Button>
        </header>
        <Outlet />
      </div>
    </AdminSessionContext.Provider>
  );
}

function TokenForm({
  notice,
  onSubmit,
}: {
  notice: string | null;
  onSubmit: (token: string) => void;
}) {
  const [value, setValue] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    if (value.trim()) {
      onSubmit(value.trim());
    }
  }

  return (
    <form onSubmit={submit} className="max-w-md space-y-3">
      <h1 className="text-2xl font-bold">운영자 토큰</h1>
      {notice && (
        <p role="alert" className="text-destructive">
          {notice}
        </p>
      )}
      <label className="block space-y-1">
        <span className="text-sm">서버에 설정한 운영자 토큰</span>
        <input
          type="password"
          autoComplete="off"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          className="w-full rounded-md border border-input bg-background px-2 py-1"
        />
      </label>
      <Button type="submit">열기</Button>
    </form>
  );
}
