// Cloudflare Turnstile 위젯 스크립트 — 처음 필요할 때 한 번만 넣는다(명시적 렌더링).

/** 위젯 렌더링 옵션 — 쓰는 것만 */
export type TurnstileOptions = {
  sitekey: string;
  action: string;
  callback: (token: string) => void;
  "expired-callback": () => void;
  "error-callback": () => void;
};

/** 스크립트가 window 에 두는 API — 쓰는 것만 */
export type TurnstileApi = {
  render: (container: HTMLElement, options: TurnstileOptions) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
};

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

/** 위젯 스크립트 주소 — 명시적 렌더링 */
export const TURNSTILE_SCRIPT_URL =
  "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

let loading: Promise<TurnstileApi> | null = null;

/** 스크립트를 받아 API 를 준다 — 이미 있으면 바로, 받지 못하면 실패하고 다음 호출이 다시 받는다 */
export function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) {
    return Promise.resolve(window.turnstile);
  }
  if (!loading) {
    loading = new Promise<TurnstileApi>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = TURNSTILE_SCRIPT_URL;
      script.async = true;
      script.onload = () => {
        if (window.turnstile) {
          resolve(window.turnstile);
        } else {
          loading = null;
          reject(new Error("Turnstile 스크립트에 API 가 없다"));
        }
      };
      script.onerror = () => {
        loading = null;
        script.remove();
        reject(new Error("Turnstile 스크립트를 받지 못했다"));
      };
      document.head.appendChild(script);
    });
  }
  return loading;
}
