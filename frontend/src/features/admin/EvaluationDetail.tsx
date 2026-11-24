import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ArticleContentView } from "@/features/admin/ArticleContentView";
import { articlePath } from "@/features/admin/articleQueries";
import { EVALUATION_KEY } from "@/features/admin/evaluationQueries";
import { topicStatusLabels } from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Detail = Schemas["EvaluationDetailResponse"];
type StoredTag = Schemas["StoredTagView"];

/** 평가 하나의 상세를 읽어 그린다 — 상세 화면과 표본 검수가 함께 쓴다 */
export function EvaluationDetailLoader({ id }: { id: number }) {
  const client = useAdminClient();
  const detail = useQuery({
    queryKey: [...EVALUATION_KEY, "detail", id],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/evaluations/{id}", {
          params: { path: { id } },
        }),
      ),
  });
  if (detail.error) {
    return <p role="alert">{failureText(detail.error)}</p>;
  }
  if (!detail.data) {
    return <p className="text-sm text-muted-foreground">불러오는 중</p>;
  }
  return <EvaluationDetail detail={detail.data} />;
}

/** 평가 하나 — 기사(평가기가 본 입력과 원문 링크), 저장된 태그 전부와 지금 기준으로 붙었는지, 분포, 모델 · 토큰 */
export function EvaluationDetail({ detail }: { detail: Detail }) {
  return (
    <article aria-label="평가 상세" className="space-y-5 text-sm">
      <ArticleView detail={detail} />
      <section aria-label="판정" className="space-y-3">
        <h4 className="font-semibold">
          판정 — {topicStatusLabels[detail.topicStatus]}
        </h4>
        <p className="text-muted-foreground">
          평가 버전 {detail.versionNumber}의 지금 기준으로 판정했다 — 주제 태그
          기준 {detail.topicThresholds.tagThreshold} · 저장 기준{" "}
          {detail.topicThresholds.storeThreshold}, 지역 태그 기준{" "}
          {detail.regionThresholds.tagThreshold} · 저장 기준{" "}
          {detail.regionThresholds.storeThreshold}
        </p>
        <StoredTable label="주제(저장된 중분류 전부)" tags={detail.topics} />
        <p>
          붙은 대분류:{" "}
          {detail.topicCategories.length === 0
            ? "없음"
            : detail.topicCategories.map((tag) => tag.name).join(", ")}
        </p>
        <StoredTable label="지역(저장된 지역 전부)" tags={detail.regions} />
      </section>
      <section aria-label="분포" className="space-y-2">
        <h4 className="font-semibold">형식 분포</h4>
        <ul className="space-y-0.5">
          {detail.formats.map((share) => (
            <li key={share.key}>
              {share.name} {formatProbability(share.probability)}
              {share.key === detail.format && " — 대표 형식"}
            </li>
          ))}
        </ul>
        <h4 className="font-semibold">
          중요도 분포 — 기대값 {formatProbability(detail.importance)}
        </h4>
        <ul className="space-y-0.5">
          {detail.importanceLevels.map((share) => (
            <li key={share.key}>
              {share.key} ({share.name}) {formatProbability(share.probability)}
            </li>
          ))}
        </ul>
      </section>
      <dl
        aria-label="평가 정보"
        className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1"
      >
        <dt>평가 시각</dt>
        <dd>{formatDateTime(detail.evaluatedAt)}</dd>
        <dt>평가기 · 모델</dt>
        <dd>
          {detail.evaluator} · {detail.modelVersion}
        </dd>
        <dt>태그 체계 버전</dt>
        <dd>{detail.versionNumber}</dd>
        <dt>입력 토큰</dt>
        <dd className="tabular-nums">
          {detail.inputTokens.toLocaleString("ko-KR")}
        </dd>
      </dl>
    </article>
  );
}

function ArticleView({ detail }: { detail: Detail }) {
  if (!detail.article) {
    return (
      <section aria-label="기사">
        <p>그새 지운 기사다 — 평가 결과만 남아 있다(기사 {detail.articleId})</p>
      </section>
    );
  }
  return (
    <ArticleContentView
      article={detail.article}
      missingSummary="요약문 없음 — 제목만으로 평가했다"
    >
      <p>
        <Link to={articlePath(detail.articleId)} className="underline">
          기사 상세
        </Link>{" "}
        <span className="text-muted-foreground">
          — 이 기사의 다른 버전 평가와 사건 소속
        </span>
      </p>
    </ArticleContentView>
  );
}

function StoredTable({ label, tags }: { label: string; tags: StoredTag[] }) {
  return (
    <table aria-label={label} className={`w-full text-left ${REVIEW_TABLE}`}>
      <caption className="text-left text-muted-foreground">{label}</caption>
      <thead>
        <tr>
          <th className="font-medium">태그</th>
          <th className="font-medium">확률</th>
          <th className="font-medium">지금 기준으로</th>
        </tr>
      </thead>
      <tbody>
        {tags.length === 0 ? (
          <tr>
            <td colSpan={3}>저장된 태그 없음</td>
          </tr>
        ) : (
          tags.map((tag) => (
            <tr key={tag.key}>
              <td>{tag.name}</td>
              <td className="tabular-nums">
                {formatProbability(tag.probability)}
              </td>
              <td>{tag.attached ? "붙음" : "붙지 않음"}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
