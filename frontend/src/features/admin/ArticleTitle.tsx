import { Link } from "react-router";
import type { Schemas } from "@/features/admin/adminClient";
import { articlePath } from "@/features/admin/articleQueries";

/** 기사 제목과 출처 — 제목은 기사 상세로 잇는다. 그새 지운 기사는 ID 로(상세가 없어 잇지 않는다), 대표가 없으면 비운다 */
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
      <Link to={articlePath(id)} className="underline">
        {article.title}
      </Link>
      {article.sources.length > 0 && (
        <span className="block text-muted-foreground">
          {article.sources.join(" · ")}
        </span>
      )}
    </>
  );
}
