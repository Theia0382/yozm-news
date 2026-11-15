import type { components } from "@/shared/api/schema";

// 키 화면의 문구 — 공급자 · 실패 종류마다 하나. 값이 늘면 타입 오류로 드러난다.

export type Provider = components["schemas"]["ProviderKeyResponse"]["provider"];

export type LlmKey = NonNullable<
  components["schemas"]["ProviderKeyResponse"]["key"]
>;

export type LlmFailureKind = NonNullable<
  components["schemas"]["KeyRegistrationResponse"]["failure"]
>;

/** 공급자의 이름과 API 키를 발급하는 개발자 콘솔 */
export const providers: Record<
  Provider,
  { name: string; console: string; consoleUrl: string }
> = {
  OPENAI: {
    name: "OpenAI",
    console: "OpenAI Platform",
    consoleUrl: "https://platform.openai.com/api-keys",
  },
  ANTHROPIC: {
    name: "Anthropic",
    console: "Claude Console",
    consoleUrl: "https://platform.claude.com/settings/keys",
  },
};

/** 공급자가 키를 받아 주지 않은 까닭 */
export const failureTexts: Record<LlmFailureKind, string> = {
  INVALID_KEY:
    "공급자가 이 키를 받아 주지 않았습니다. 키를 빠짐없이 붙여 넣었는지, 폐기된 키가 아닌지 확인해 주세요.",
  PERMISSION_DENIED:
    "이 키에는 필요한 권한이 없습니다. 공급자 콘솔에서 키의 권한을 확인해 주세요.",
  QUOTA_EXHAUSTED:
    "공급자 계정의 잔액이나 지출 한도가 소진됐습니다. 공급자 콘솔에서 결제와 한도를 확인해 주세요.",
  TRANSIENT: "공급자가 지금 답하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.",
  UNEXPECTED:
    "공급자에게서 예상하지 못한 답을 받았습니다. 잠시 뒤에 다시 시도해 주세요.",
};
