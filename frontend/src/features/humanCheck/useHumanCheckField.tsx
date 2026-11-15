import { type ReactNode, useState } from "react";
import { useHumanCheck } from "@/features/humanCheck/humanCheckQueries";
import {
  HUMAN_CHECK_HEADER,
  HUMAN_CHECK_PENDING_TEXT,
  HUMAN_CHECK_UNAVAILABLE_TEXT,
  type HumanCheckAction,
} from "@/features/humanCheck/humanCheckTexts";
import { TurnstileWidget } from "@/features/humanCheck/TurnstileWidget";

export type HumanCheckField = {
  /** 입력 칸 사이에 둘 것 — 봇 차단을 끈 인스턴스 · 대상이 아닌 화면이면 null */
  field: ReactNode;
  /** 제출해도 되는가 — 켰으면 토큰을 받은 뒤 */
  ready: boolean;
  /** 요청에 실을 헤더 — 토큰이 없으면 빈 객체 */
  headers: () => Record<string, string>;
  /** 제출 뒤 위젯을 다시 받는다 — 토큰은 한 번만 쓰인다 */
  reset: () => void;
};

/**
 * 로그인 · 복구 · 공개 가입의 사람 확인 칸 — 봇 차단 설정이 Turnstile 이면 위젯을 띄우고 토큰을 헤더로 싣는다. 설정이 끔이거나 아직 / 끝내
 * 읽지 못했으면 칸 없이 보낸다(봇 차단을 켠 인스턴스면 서버가 403 으로 알린다). action 이 null 이면 대상이 아닌 화면이다.
 */
export function useHumanCheckField(
  action: HumanCheckAction | null,
): HumanCheckField {
  const setting = useHumanCheck();
  const [token, setToken] = useState<string | null>(null);
  const [generation, setGeneration] = useState(0);
  const [unavailable, setUnavailable] = useState(false);
  const siteKey =
    action !== null && setting.data?.provider === "TURNSTILE"
      ? setting.data.siteKey
      : null;

  if (action === null || siteKey === null) {
    return { field: null, ready: true, headers: () => ({}), reset: () => {} };
  }
  return {
    field: unavailable ? (
      <p role="alert" className="text-destructive">
        {HUMAN_CHECK_UNAVAILABLE_TEXT}
      </p>
    ) : (
      <div className="space-y-1">
        <TurnstileWidget
          siteKey={siteKey}
          action={action}
          generation={generation}
          onToken={setToken}
          onUnavailable={() => setUnavailable(true)}
        />
        {token === null && (
          <p className="text-sm text-muted-foreground">
            {HUMAN_CHECK_PENDING_TEXT}
          </p>
        )}
      </div>
    ),
    ready: token !== null,
    headers: (): Record<string, string> =>
      token === null ? {} : { [HUMAN_CHECK_HEADER]: token },
    reset: () => {
      setToken(null);
      setGeneration((current) => current + 1);
    },
  };
}
