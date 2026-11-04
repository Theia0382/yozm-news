// 표본 검수의 씨앗.

/** 새 씨앗 — 0 ~ 2^53 − 1(JavaScript 수가 정확히 나타내는 범위, 서버 상한과 같다) */
export function newSeed(): number {
  const words = new Uint32Array(2);
  crypto.getRandomValues(words);
  const [high = 0, low = 0] = words;
  return (high & 0x1fffff) * 2 ** 32 + low;
}
