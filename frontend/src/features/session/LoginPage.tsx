import { useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router";
import { safeNext } from "@/features/session/safeNext";
import { SESSION_QUERY_KEY, useMe } from "@/features/session/sessionQueries";
import { waitText } from "@/features/session/waitText";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 로그인 화면 — 이메일 · 비밀번호 · 로그인 유지. 거절은 계정 유무와 무관하게 같은 문구이고, 대기면 남은 시간을 보인다. 로그인하면 next 로 간다.
 */
export function LoginPage() {
  const client = useApiClient();
  const me = useMe();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [keep, setKeep] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const next = safeNext(params.get("next"), window.location.origin);

  if (me.data?.result === "SIGNED_IN") {
    return <Navigate to={next} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setMessage(null);
    try {
      const { data, response } = await client.POST("/api/session", {
        body: { email, password, keep },
      });
      if (!response.ok || !data) {
        setMessage(
          `로그인 요청이 실패했습니다 (HTTP ${response.status}). 다시 시도해 주세요.`,
        );
        // 요청 보호 토큰이 없거나 낡았으면 상태를 다시 읽어 새 토큰을 받는다
        await me.refetch();
        return;
      }
      switch (data.result) {
        case "SIGNED_IN":
          setPassword("");
          await queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
          navigate(next, { replace: true });
          return;
        case "REJECTED":
          setMessage("이메일 또는 비밀번호가 맞지 않습니다.");
          return;
        case "WAIT":
          setMessage(waitText(data.retryAfterSeconds));
          return;
      }
      const unknown: never = data.result;
      setMessage(`알 수 없는 결과입니다: ${String(unknown)}`);
    } catch {
      setMessage("서버에 닿지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH}`}>
      <form onSubmit={submit} className="max-w-sm space-y-4">
        <h1 className="text-2xl font-bold">로그인</h1>
        {message && (
          <p role="alert" className="text-destructive">
            {message}
          </p>
        )}
        <label className="block space-y-1">
          <span className="text-sm">이메일</span>
          <input
            type="email"
            name="email"
            autoComplete="username"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="w-full rounded-md border border-input bg-background px-2 py-1"
          />
        </label>
        <label className="block space-y-1">
          <span className="text-sm">비밀번호</span>
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="w-full rounded-md border border-input bg-background px-2 py-1"
          />
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name="keep"
            checked={keep}
            onChange={(event) => setKeep(event.target.checked)}
          />
          로그인 유지
        </label>
        <Button type="submit" disabled={submitting}>
          로그인
        </Button>
      </form>
    </section>
  );
}
