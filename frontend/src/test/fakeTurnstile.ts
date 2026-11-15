// 화면 시험의 가짜 Turnstile — window.turnstile 자리에 두고, 그린 위젯 · 다시 받기 · 지우기를 남긴다. 토큰은 시험이 준다.

import { act } from "@testing-library/react";
import type {
  TurnstileApi,
  TurnstileOptions,
} from "@/features/humanCheck/turnstile";

export function installFakeTurnstile() {
  const rendered: TurnstileOptions[] = [];
  const resets: string[] = [];
  const api: TurnstileApi = {
    render: (_container, options) => {
      rendered.push(options);
      return `widget-${rendered.length}`;
    },
    reset: (widgetId) => {
      resets.push(widgetId);
    },
    remove: () => {},
  };
  window.turnstile = api;
  return {
    rendered,
    resets,
    /** 마지막으로 그린 위젯이 토큰을 준다 */
    solve(token: string) {
      const widget = rendered.at(-1);
      if (!widget) {
        throw new Error("그린 위젯이 없다");
      }
      act(() => widget.callback(token));
    },
  };
}

export function uninstallFakeTurnstile() {
  delete window.turnstile;
}
