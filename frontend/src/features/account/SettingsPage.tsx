import { type FormEvent, useState } from "react";
import {
  CURRENT_PASSWORD_ATTEMPT,
  CURRENT_PASSWORD_REJECTED_TEXT,
  requestFailedText,
  UNREACHABLE_TEXT,
} from "@/features/account/accountTexts";
import { PasswordChangeForm } from "@/features/account/PasswordChangeForm";
import { RecoveryCodeNotice } from "@/features/account/RecoveryCodeNotice";
import { TextField } from "@/features/account/TextField";
import { useMe } from "@/features/session/sessionQueries";
import { waitText } from "@/features/session/waitText";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 설정 — 비밀번호 변경과 복구 코드 새로 받기. 둘 다 지금 비밀번호를 확인한다(docs/specs/user.md §4).
 */
export function SettingsPage() {
  const [changed, setChanged] = useState(false);
  const [code, setCode] = useState<string | null>(null);
  const [reissued, setReissued] = useState(false);

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-8`}>
      <h1 className="text-2xl font-bold">설정</h1>
      {code !== null ? (
        <RecoveryCodeNotice
          code={code}
          onDone={() => {
            setCode(null);
            setReissued(true);
          }}
        />
      ) : (
        <>
          <section className="space-y-3" aria-labelledby="password-change">
            <h2 id="password-change" className="text-xl font-bold">
              비밀번호 변경
            </h2>
            <p className="text-sm text-muted-foreground">
              바꾸면 이 기기를 뺀 다른 기기의 로그인이 모두 끝납니다.
            </p>
            {changed && (
              <p role="status">
                비밀번호를 바꿨습니다. 다른 기기의 로그인은 모두 끝났습니다.
              </p>
            )}
            <PasswordChangeForm
              onChanged={(received) => {
                setReissued(false);
                if (received === null) {
                  setChanged(true);
                  return;
                }
                setCode(received);
              }}
            />
          </section>
          <section
            className="space-y-3"
            aria-labelledby="recovery-code-reissue"
          >
            <h2 id="recovery-code-reissue" className="text-xl font-bold">
              복구 코드 새로 받기
            </h2>
            <p className="text-sm text-muted-foreground">
              새 코드를 받으면 지금 코드는 더 쓸 수 없습니다.
            </p>
            {reissued && (
              <p role="status">
                새 복구 코드를 받았습니다. 이전 코드는 더 쓸 수 없습니다.
              </p>
            )}
            <RecoveryCodeReissueForm
              onIssued={(received) => {
                setChanged(false);
                setCode(received);
              }}
            />
          </section>
        </>
      )}
    </section>
  );
}

function RecoveryCodeReissueForm({
  onIssued,
}: {
  onIssued: (recoveryCode: string) => void;
}) {
  const client = useApiClient();
  const me = useMe();
  const [currentPassword, setCurrentPassword] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setMessage(null);
    try {
      const { data, response } = await client.POST(
        "/api/account/recovery-code",
        { body: { currentPassword } },
      );
      if (!response.ok || !data) {
        setMessage(requestFailedText(response.status));
        await me.refetch();
        return;
      }
      switch (data.result) {
        case "ISSUED":
          setCurrentPassword("");
          if (data.recoveryCode === null) {
            setMessage("복구 코드를 받지 못했습니다. 다시 시도해 주세요.");
            return;
          }
          onIssued(data.recoveryCode);
          return;
        case "REJECTED":
          setMessage(CURRENT_PASSWORD_REJECTED_TEXT);
          return;
        case "WAIT":
          setMessage(
            waitText(data.retryAfterSeconds, CURRENT_PASSWORD_ATTEMPT),
          );
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
      <Button type="submit" disabled={submitting}>
        복구 코드 새로 받기
      </Button>
    </form>
  );
}
