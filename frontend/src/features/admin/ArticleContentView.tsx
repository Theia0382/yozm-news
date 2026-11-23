import type { ReactNode } from "react";
import type { Schemas } from "@/features/admin/adminClient";
import { safeLink } from "@/features/admin/links";

/**
 * 기사 정보 — 제목 · 원문 링크 · 실린 피드(출처 · 섹션 · 카테고리) · 요약문. 평가 상세와 기사 상세가 함께 쓴다. 요약문 원문은 운영자 화면에만
 * 보인다(ADR-0068)
 */
export function ArticleContentView({
  article,
  missingSummary,
  children,
}: {
  article: Schemas["ArticleDetailResponse"];
  /** 요약문이 없을 때의 문구 */
  missingSummary: string;
  /** 원문 링크 아래에 덧붙일 것 */
  children?: ReactNode;
}) {
  const link = safeLink(article.canonicalUrl);
  return (
    <section aria-label="기사" className="space-y-2 text-sm">
      <h3 className="text-base font-semibold">{article.title}</h3>
      {link ? (
        <a
          href={link}
          target="_blank"
          rel="noopener noreferrer"
          className="underline"
        >
          원문 열기
        </a>
      ) : (
        <p className="break-all text-muted-foreground">
          원문 주소: {article.canonicalUrl}
        </p>
      )}
      {children}
      <ul aria-label="실린 피드" className="space-y-0.5">
        {article.entries.map((entry, index) => (
          <li key={index}>
            {entry.sourceName ?? "이름 없는 출처"}
            {entry.section && ` · ${entry.section}`}
            {entry.categories.length > 0 &&
              ` · 카테고리 ${entry.categories.join(", ")}`}
          </li>
        ))}
      </ul>
      <p className="rounded-md bg-muted p-2 whitespace-pre-wrap">
        {article.summary ?? missingSummary}
      </p>
    </section>
  );
}
