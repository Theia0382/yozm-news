// 관심 값의 색 칸 — 관심사 히스토리와 변화 리포트의 관심 표가 함께 쓴다(docs/specs/reader.md reader-R16).

/** 칸의 색 — 관심 쪽은 주 색, 싫어요 쪽은 경고 색, 신호 없음 · 0 은 바탕 */
export function cellColor(level: number | null): string {
  if (level === null || level === 0) {
    return "bg-muted";
  }
  return level > 0 ? "bg-primary" : "bg-destructive";
}

/** 칸의 진하기 — 관심의 크기. 작은 값도 보이게 바닥을 둔다 */
export function cellOpacity(level: number | null): number {
  if (level === null || level === 0) {
    return 1;
  }
  return Math.max(0.2, Math.abs(level));
}
