// 평가 검수 · 태그 기준 화면의 표 모양 — 낱말 단위 줄바꿈, 칸 여백과 짝수 행 바탕(--muted). 머리 행은 칠하지 않는다. Tailwind 가 원본 파일의 문자열을 훑으므로 클래스는 문자열 리터럴로 둔다.

/** 표 모양 — 표마다 다른 너비 · 글자 크기 클래스와 함께 쓴다 */
export const REVIEW_TABLE =
  "break-keep [&_td]:px-2 [&_td]:py-1 [&_th]:px-2 [&_th]:py-1 [&_tbody_tr:nth-child(even)]:bg-muted";
