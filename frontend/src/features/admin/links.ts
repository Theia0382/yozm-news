// 운영자 화면이 그리는 바깥 링크.

/** 원문 링크로 달 수 있는 주소 — http · https 만(피드에서 온 값이라 다른 스킴은 글자로만 보인다) */
export function safeLink(url: string): string | null {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:"
      ? parsed.href
      : null;
  } catch {
    return null;
  }
}
