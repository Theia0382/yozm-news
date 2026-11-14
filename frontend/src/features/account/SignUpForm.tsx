import { type FormEvent, useState } from "react";
import { Link } from "react-router";
import {
  invitationStateText,
  PASSWORD_MISMATCH_TEXT,
  passwordProblemText,
  requestFailedText,
  UNREACHABLE_TEXT,
} from "@/features/account/accountTexts";
import { TextField } from "@/features/account/TextField";
import { PRIVACY_PATH } from "@/features/privacy/privacyTexts";
import { useMe } from "@/features/session/sessionQueries";
import type { components } from "@/shared/api/schema";
import { Button } from "@/shared/ui/button";

export type SignUpFields = {
  email: string;
  displayName: string;
  password: string;
  keep: boolean;
};

type SignUpResponse = components["schemas"]["SignUpResponse"];

type SignUpFormProps = {
  /** 가입 요청 — 공개 가입과 초대 가입이 각자의 엔드포인트로 보낸다 */
  submit: (
    fields: SignUpFields,
  ) => Promise<{ data?: SignUpResponse; response: Response }>;
  /** 가입해 로그인했다 — 복구 코드를 넘긴다(응답에 없으면 null) */
  onSignedUp: (recoveryCode: string | null) => void;
};

/**
 * 가입 입력 — 이메일 · 표시 이름 · 비밀번호(확인) · 로그인 유지. 결과 판별자마다 안내 하나(망라 switch). 같은 이메일의 계정이 있으면
 * "가입할 수 없는 이메일"이라고만 한다.
 */
export function SignUpForm({ submit, onSignedUp }: SignUpFormProps) {
  const me = useMe();
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [keep, setKeep] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    if (password !== confirm) {
      setMessage(PASSWORD_MISMATCH_TEXT);
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      const { data, response } = await submit({
        email,
        displayName,
        password,
        keep,
      });
      if (!response.ok || !data) {
        setMessage(requestFailedText(response.status));
        // 요청 보호 토큰이 없거나 낡았으면 상태를 다시 읽어 새 토큰을 받는다
        await me.refetch();
        return;
      }
      switch (data.result) {
        case "SIGNED_UP":
          setPassword("");
          setConfirm("");
          onSignedUp(data.recoveryCode);
          return;
        case "CLOSED":
          setMessage("지금은 가입을 받지 않습니다.");
          return;
        case "INVITATION_UNUSABLE":
          setMessage(invitationStateText(data.invitation ?? "UNKNOWN"));
          return;
        case "EMAIL_UNAVAILABLE":
          setMessage("가입할 수 없는 이메일입니다.");
          return;
        case "EMAIL_MALFORMED":
          setMessage("이메일 형식이 아닙니다.");
          return;
        case "DISPLAY_NAME_MALFORMED":
          setMessage("표시 이름을 확인해 주세요. 비어 있거나 너무 깁니다.");
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
        label="이메일"
        type="email"
        name="email"
        autoComplete="username"
        required
        value={email}
        onValueChange={setEmail}
      />
      <TextField
        label="표시 이름"
        name="displayName"
        autoComplete="nickname"
        required
        value={displayName}
        onValueChange={setDisplayName}
      />
      <TextField
        label="비밀번호"
        type="password"
        name="password"
        autoComplete="new-password"
        required
        value={password}
        onValueChange={setPassword}
      />
      <TextField
        label="비밀번호 확인"
        type="password"
        name="passwordConfirm"
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
      <Button type="submit" disabled={submitting}>
        가입
      </Button>
      <p className="text-sm text-muted-foreground">
        <Link to={PRIVACY_PATH} className="underline">
          저장하는 것과 LLM 공급자에게 보내는 것
        </Link>
      </p>
    </form>
  );
}
