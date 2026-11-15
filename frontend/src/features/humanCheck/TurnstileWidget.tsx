import { useEffect, useRef } from "react";
import { loadTurnstile } from "@/features/humanCheck/turnstile";

type TurnstileWidgetProps = {
  siteKey: string;
  action: string;
  /** 바뀌면 위젯을 다시 받는다 — 토큰은 한 번만 쓰인다 */
  generation: number;
  /** 토큰을 받았다(null 이면 만료 · 오류로 토큰이 없어졌다) */
  onToken: (token: string | null) => void;
  /** 스크립트를 받지 못했다 */
  onUnavailable: () => void;
};

/** Turnstile 위젯 — 스크립트를 받아 자리에 그리고, generation 이 바뀌면 다시 받는다 */
export function TurnstileWidget({
  siteKey,
  action,
  generation,
  onToken,
  onUnavailable,
}: TurnstileWidgetProps) {
  const holder = useRef<HTMLDivElement>(null);
  const widget = useRef<string | null>(null);
  // 위젯에 넘긴 콜백이 늘 지금 렌더의 함수를 부르게 한다 — 위젯을 다시 그리지 않는다
  const tokenCallback = useRef(onToken);
  const unavailableCallback = useRef(onUnavailable);
  useEffect(() => {
    tokenCallback.current = onToken;
    unavailableCallback.current = onUnavailable;
  });

  useEffect(() => {
    let cancelled = false;
    loadTurnstile()
      .then((turnstile) => {
        if (cancelled || !holder.current) {
          return;
        }
        widget.current = turnstile.render(holder.current, {
          sitekey: siteKey,
          action,
          callback: (token) => tokenCallback.current(token),
          "expired-callback": () => tokenCallback.current(null),
          "error-callback": () => tokenCallback.current(null),
        });
      })
      .catch(() => {
        if (!cancelled) {
          unavailableCallback.current();
        }
      });
    return () => {
      cancelled = true;
      if (widget.current !== null) {
        window.turnstile?.remove(widget.current);
        widget.current = null;
      }
    };
  }, [siteKey, action]);

  useEffect(() => {
    if (generation > 0 && widget.current !== null) {
      window.turnstile?.reset(widget.current);
    }
  }, [generation]);

  return <div ref={holder} data-testid="turnstile-widget" />;
}
