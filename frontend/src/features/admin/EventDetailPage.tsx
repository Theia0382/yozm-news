import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ArticleTitle } from "@/features/admin/ArticleTitle";
import { EVENT_KEY } from "@/features/admin/eventQueries";
import { decidedByLabels, eventStatusLabels } from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Detail = Schemas["EventDetailResponse"];

/**
 * 사건 하나 — 사건 값 · 대표 기사 · 사건의 기사(출처 · 점수 · 판정 방식) · 이 사건으로 합쳐진 사건. 합쳐진 사건의 ID 로 열면 남는 사건을 보이고
 * 그 사실을 알린다.
 */
export function EventDetailPage() {
  const client = useAdminClient();
  const id = Number(useParams().id);
  const detail = useQuery({
    queryKey: [...EVENT_KEY, "detail", id],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/events/{id}", { params: { path: { id } } }),
      ),
  });

  return (
    <div className="space-y-6">
      <p className="text-sm">
        <Link to="/admin/events" className="underline">
          사건 목록
        </Link>
      </p>
      {detail.error && <p role="alert">{failureText(detail.error)}</p>}
      {detail.data && <DetailView detail={detail.data} />}
    </div>
  );
}

function DetailView({ detail }: { detail: Detail }) {
  return (
    <>
      <h2 className="text-xl font-semibold">사건 {detail.id}</h2>
      {detail.requestedId !== detail.id && (
        <p role="status" className="text-sm">
          사건 {detail.requestedId}은 사건 {detail.id}에 합쳐졌다 — 남는 사건을
          보인다
        </p>
      )}
      <dl
        aria-label="사건 값"
        className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm"
      >
        <dt className="text-muted-foreground">상태</dt>
        <dd>{eventStatusLabels[detail.status]}</dd>
        <dt className="text-muted-foreground">기사 수</dt>
        <dd className="tabular-nums">{detail.articleCount}</dd>
        <dt className="text-muted-foreground">첫 기사</dt>
        <dd>{formatDateTime(detail.firstArticleAt)}</dd>
        <dt className="text-muted-foreground">마지막 기사</dt>
        <dd>{formatDateTime(detail.lastArticleAt)}</dd>
        <dt className="text-muted-foreground">연 시각</dt>
        <dd>{formatDateTime(detail.createdAt)}</dd>
        <dt className="text-muted-foreground">대표 기사</dt>
        <dd>
          <ArticleTitle
            id={detail.representativeId}
            article={detail.representative}
          />
        </dd>
      </dl>

      <section aria-label="사건의 기사" className="space-y-2">
        <h3 className="text-lg font-semibold">기사</h3>
        <p className="text-sm text-muted-foreground">
          점수는 붙인 후보의 점수(새 사건이면 가장 높은 후보의 점수)다. 판정
          방식은 산식(Jev 없이), Jev(띠에서 판정), 판정 실패(판정을 끝내지 못해
          새 사건) 가운데 하나다
        </p>
        <table
          aria-label="사건의 기사"
          className={`w-full text-left text-sm ${REVIEW_TABLE}`}
        >
          <thead>
            <tr>
              <th className="font-medium">기사</th>
              <th className="font-medium">발행 시각</th>
              <th className="font-medium">점수</th>
              <th className="font-medium">판정 방식</th>
              <th className="font-medium">배정 시각</th>
            </tr>
          </thead>
          <tbody>
            {detail.articles.map((article) => (
              <tr key={article.articleId} className="align-top">
                <td>
                  <ArticleTitle
                    id={article.articleId}
                    article={article.article}
                  />
                </td>
                <td>{formatDateTime(article.publishedAt)}</td>
                <td className="tabular-nums">
                  {formatProbability(article.score)}
                </td>
                <td>{decidedByLabels[article.decidedBy]}</td>
                <td>{formatDateTime(article.assignedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section aria-label="합쳐진 사건" className="space-y-2">
        <h3 className="text-lg font-semibold">합쳐진 사건</h3>
        {detail.mergedEvents.length === 0 ? (
          <p className="text-sm">이 사건으로 합쳐진 사건이 없다</p>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">
              기사 수 · 시각은 합칠 때의 값이다. 병합 판정 확률은 Jev 에 물어
              합친 쌍만 있다
            </p>
            <table
              aria-label="합쳐진 사건"
              className={`w-full text-left text-sm ${REVIEW_TABLE}`}
            >
              <thead>
                <tr>
                  <th className="font-medium">사건</th>
                  <th className="font-medium">합친 시각</th>
                  <th className="font-medium">기사 수</th>
                  <th className="font-medium">첫 기사</th>
                  <th className="font-medium">마지막 기사</th>
                  <th className="font-medium">병합 판정 확률</th>
                </tr>
              </thead>
              <tbody>
                {detail.mergedEvents.map((merged) => (
                  <tr key={merged.id} className="align-top">
                    <td>사건 {merged.id}</td>
                    <td>{formatDateTime(merged.mergedAt)}</td>
                    <td className="tabular-nums">{merged.articleCount}</td>
                    <td>{formatDateTime(merged.firstArticleAt)}</td>
                    <td>{formatDateTime(merged.lastArticleAt)}</td>
                    <td className="tabular-nums">
                      {merged.probability === null
                        ? "기록 없음"
                        : formatProbability(merged.probability)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>
    </>
  );
}
