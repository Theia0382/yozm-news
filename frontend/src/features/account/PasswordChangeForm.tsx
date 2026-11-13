import { type FormEvent, useState } from "react";
import {
  CURRENT_PASSWORD_ATTEMPT,
  CURRENT_PASSWORD_REJECTED_TEXT,
  PASSWORD_MISMATCH_TEXT,
  passwordProblemText,
  requestFailedText,
  UNREACHABLE_TEXT,
} from "@/features/account/accountTexts";
import { TextField } from "@/features/account/TextField";
import { useMe } from "@/features/session/sessionQueries";
import { waitText } from "@/features/session/waitText";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";

type PasswordChangeFormProps = {
  /** 바꿨다 — 강제 변경이었으면 새 복구 코드, 그 밖 null */
  onChanged: (recoveryCode: string | null) => void;
};

/**
 * 비밀번호 변경 입력 — 지금 비밀번호와 새 비밀번호(확인). 바꾸면 다른 기기의 로그인이 끝난다(docs/specs/user.md §4). 결과 판별자마다
 * 안내 하나(망라 switch).
 */
export function PasswordChangeForm({ onChanged }: PasswordChangeFormProps) {
  const client = useApiClient();
  const me = useMe();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
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
      const { data, response } = await client.PUT("/api/account/password", {
        body: { currentPassword, newPassword },
      });
      if (!response.ok || !data) {
        setMessage(requestFailedText(response.status));
        await me.refetch();
        return;
      }
      switch (data.result) {
        case "CHANGED":
          setCurrentPassword("");
          setNewPassword("");
          setConfirm("");
          onChanged(data.recoveryCode);
          return;
        case "REJECTED":
          setMessage(CURRENT_PASSWORD_REJECTED_TEXT);
          return;
        case "WAIT":
          setMessage(
            waitText(data.retryAfterSeconds, CURRENT_PASSWORD_ATTEMPT),
          );
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
      {message && (
        <p role="alert" className="text-destructive">
          {message}
        </p>
      )}
      <TextField
        label="지금 비밀번호"
        type="password"
        name="currentPassword"
        autoComplete="current-password"
        required
        value={currentPassword}
        onValueChange={setCurrentPassword}
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
      <Button type="submit" disabled={submitting}>
        비밀번호 바꾸기
      </Button>
    </form>
  );
}
