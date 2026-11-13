import { type ReactNode, useState } from "react";
import { RecoveryCodeNotice } from "@/features/account/RecoveryCodeNotice";
import { useEnterSignedIn } from "@/features/session/useEnterSignedIn";

type RecoveryCodeFlowProps = {
  /** 복구 코드를 받기 전의 화면 — 받은 코드로 부른다(코드가 없으면 바로 첫 화면으로) */
  children: (onRecoveryCode: (code: string | null) => void) => ReactNode;
};

/**
 * 복구 코드를 받으며 로그인 상태가 바뀌는 흐름(가입 · 초대 가입 · 복구 재설정 · 첫 로그인의 비밀번호 변경) — 코드를 받으면 코드
 * 안내로 바꾸고(그 뒤로는 children 을 그리지 않는다), 저장을 확인하면 로그인 상태를 다시 읽어 첫 화면으로 간다.
 */
export function RecoveryCodeFlow({ children }: RecoveryCodeFlowProps) {
  const enterSignedIn = useEnterSignedIn();
  const [code, setCode] = useState<string | null>(null);

  if (code !== null) {
    return <RecoveryCodeNotice code={code} onDone={() => enterSignedIn("/")} />;
  }
  return children((received) => {
    if (received === null) {
      void enterSignedIn("/");
      return;
    }
    setCode(received);
  });
}
