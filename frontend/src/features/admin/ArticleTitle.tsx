import type { Schemas } from "@/features/admin/adminClient";

/** 기사 제목과 출처 — 그새 지운 기사는 ID 로, 대표가 없으면 비운다 */
export function ArticleTitle({
  id,
  article,
}: {
  id: number | null;
  article: Schemas["ArticleSummaryResponse"] | null;
}) {
  if (id === null) {
    return null;
  }
  if (article === null) {
    return <span className="text-muted-foreground">지운 기사 {id}</span>;
  }
  return (
    <>
      {article.title}
      {article.sources.length > 0 && (
        <span className="block text-muted-foreground">
          {article.sources.join(" · ")}
        </span>
      )}
    </>
  );
}
