// 원문 문제의 줄:열을 편집기 안의 자리로 옮긴다.

/**
 * 1 부터 센 줄 · 열이 가리키는 원문의 자리(UTF-16 색인). 열은 코드 포인트로 센다 — 서버의 YAML 읽기가 그렇게 센다. 줄이 원문보다 뒤면
 * 원문 끝, 열이 그 줄보다 뒤면 그 줄 끝이다. 줄바꿈은 편집기 값의 줄바꿈(`\n`)이다.
 */
export function textOffset(text: string, line: number, column: number): number {
  let start = 0;
  for (let current = 1; current < line; current++) {
    const next = text.indexOf("\n", start);
    if (next === -1) {
      return text.length;
    }
    start = next + 1;
  }
  const lineEnd = text.indexOf("\n", start);
  const end = lineEnd === -1 ? text.length : lineEnd;
  let offset = start;
  for (let current = 1; current < column && offset < end; current++) {
    offset += (text.codePointAt(offset) ?? 0) > 0xffff ? 2 : 1;
  }
  return offset;
}
