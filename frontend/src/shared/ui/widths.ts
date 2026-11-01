// 화면 그릇 폭의 정본 — 화면과 운영자 틀의 그릇은 모두 이 상수로 폭을 정한다(그릇 안 요소의 폭은 각 화면이 정한다). Tailwind 가 원본 파일의 문자열을 훑으므로 클래스는 문자열 리터럴로 둔다.

/** 바깥 그릇 — 가운데 정렬과 창 가장자리 여백. 폭 상수와 함께 쓴다 */
export const PAGE_FRAME = "mx-auto px-4";

/** 읽기 폭 — 첫 화면 · 소스 · 피드 · 원문 편집 · 운영자 틀의 안내 화면 */
export const NARROW_WIDTH = "max-w-3xl";

/** 넓은 폭 — 운영자 영역(머리줄 · 수집 현황) */
export const WIDE_WIDTH = "max-w-screen-2xl";
