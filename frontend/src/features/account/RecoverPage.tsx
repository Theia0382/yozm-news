import { type FormEvent, useState } from "react";
import { Link } from "react-router";
import {
  PASSWORD_MISMATCH_TEXT,
  passwordProblemText,
  requestFailedText,
  UNREACHABLE_TEXT,
} from "@/features/account/accountTexts";
import { RecoveryCodeFlow } from "@/features/account/RecoveryCodeFlow";
import { TextField } from "@/features/account/TextField";
import { useMe } from "@/features/session/sessionQueries";
import { waitText } from "@/features/session/waitText";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 복구 코드로 비밀번호 재설정 — 이메일 · 복구 코드 · 새 비밀번호(확인) · 로그인 유지. 거절은 계정 유무와 무관하게 같은 문구다. 재설정하면
 * 다른 기기의 로그인이 끝나고 새 복구 코드를 보인 뒤 첫 화면으로 간다.
 */
export function RecoverPage() {
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-4`}>
      <h1 className="text-2xl font-bold">비밀번호 재설정</h1>
      <RecoveryCodeFlow>
        {(onRecoveryCode) => <RecoverForm onReset={onRecoveryCode} />}
      </RecoveryCodeFlow>
    </section>
  );
}

function RecoverForm({
  onReset,
}: {
  onReset: (recoveryCode: string | null) => void;
}) {
  const client = useApiClient();
  const me = useMe();
  const [email, setEmail] = useState("");
  const [recoveryCode, setRecoveryCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [keep, setKeep] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== confirm) {
      setMessage(PASSWORD_MISMATCH_TEXT);
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      const { data, response } = await client.POST("/api/account-recoveries", {
        body: { email, recoveryCode, newPassword, keep },
      });
      if (!response.ok || !data) {
        setMessage(requestFailedText(response.status));
        await me.refetch();
        return;
      }
      switch (data.result) {
        case "RESET":
          setRecoveryCode("");
          setNewPassword("");
          setConfirm("");
          onReset(data.recoveryCode);
          return;
        case "REJECTED":
          setMessage("이메일 또는 복구 코드가 맞지 않습니다.");
          return;
        case "WAIT":
          setMessage(waitText(data.retryAfterSeconds, "재설정"));
          return;
        case "PASSWORD_REJECTED":
          setMessage(passwordProblemText(data.passwordProblem));
          return;
      }
      const unknown: never = data.result;
      setMessage(`알 수 없는 결과입니다: ${String(unknown)}`);
    } catch {
      setMessage(UNREACHABLE_TEXT);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={send} className="max-w-sm space-y-4">
      <p className="text-sm text-muted-foreground">
        복구 코드가 없으면 운영자에게 문의하세요.
      </p>
      {message && (
        <p role="alert" className="text-destructive">
          {message}
        </p>
      )}
      <TextField
        label="이메일"
        type="email"
        name="email"
        autoComplete="username"
        required
        value={email}
        onValueChange={setEmail}
      />
      <TextField
        label="복구 코드"
        name="recoveryCode"
        autoComplete="off"
        spellCheck={false}
        required
        value={recoveryCode}
        onValueChange={setRecoveryCode}
      />
      <TextField
        label="새 비밀번호"
        type="password"
        name="newPassword"
        autoComplete="new-password"
        required
        value={newPassword}
        onValueChange={setNewPassword}
      />
      <TextField
        label="새 비밀번호 확인"
        type="password"
        name="newPasswordConfirm"
        autoComplete="new-password"
        required
        value={confirm}
        onValueChange={setConfirm}
      />
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          name="keep"
          checked={keep}
          onChange={(event) => setKeep(event.target.checked)}
        />
        로그인 유지
      </label>
      <div className="flex flex-wrap items-center gap-4">
        <Button type="submit" disabled={submitting}>
          재설정
        </Button>
        <Link to="/login" className="text-sm underline">
          로그인으로
        </Link>
      </div>
    </form>
  );
}
