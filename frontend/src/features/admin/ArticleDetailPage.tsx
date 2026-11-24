import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ArticleContentView } from "@/features/admin/ArticleContentView";
import { ARTICLE_KEY } from "@/features/admin/articleQueries";
import { eventPath } from "@/features/admin/eventQueries";
import {
  decidedByLabels,
  eventStatusLabels,
  topicStatusLabels,
} from "@/features/admin/labels";
import { formatProbability, formatSeconds } from "@/features/admin/metrics";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Detail = Schemas["ArticleReviewResponse"];
type Assignment = Schemas["ArticleAssignmentResponse"];
type NamedTag = Schemas["NamedTag"];

/**
 * 기사 하나 — 기사 정보(제목 · 원문 링크 · 실린 피드 · 요약문), 평가(버전마다), 사건 소속(배정 · 판정에 쓴 기준 · 저장 후보 · 반영한 Jev
 * 판정), 표현의 핵심 말. 기사가 나오는 운영자 화면에서 기사 제목을 누르면 열린다
 */
export function ArticleDetailPage() {
  const client = useAdminClient();
  const id = Number(useParams().id);
  const detail = useQuery({
    queryKey: [...ARTICLE_KEY, "detail", id],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/articles/{id}", { params: { path: { id } } }),
      ),
  });

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">기사 {id}</h2>
      {detail.error && (
        <p role="alert">
          {failureText(detail.error)}
          <span className="block text-sm text-muted-foreground">
            그새 지운 기사는 열리지 않는다 — 기사를 지우면 평가 · 사건 소속도
            함께 지운다
          </span>
        </p>
      )}
      {detail.data && <DetailView detail={detail.data} />}
    </div>
  );
}

function DetailView({ detail }: { detail: Detail }) {
  return (
    <>
      <ArticleContentView article={detail.article} missingSummary="요약문 없음">
        <p className="text-muted-foreground">
          발행 {formatDateTime(detail.article.publishedAt)}
        </p>
      </ArticleContentView>
      <EvaluationsView evaluations={detail.evaluations} />
      <AssignmentView assignment={detail.assignment} />
      <KeyTermsView keyTerms={detail.keyTerms} />
    </>
  );
}

function EvaluationsView({
  evaluations,
}: {
  evaluations: Schemas["ArticleEvaluationResponse"][];
}) {
  return (
    <section aria-label="평가" className="space-y-2">
      <h3 className="text-lg font-semibold">평가</h3>
      {evaluations.length === 0 ? (
        <p className="text-sm">
          평가가 없다 — 아직 평가 대기에 있거나, 평가하지 못해 대기에서 뺀
          기사다
        </p>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            태그 체계 버전마다 하나다. 판정은 그 버전의 지금 태그 기준 · 저장
            기준으로 한다. 버전 번호가 가장 큰 평가가 다이제스트 · 사건 집계가
            쓰는 평가다
          </p>
          <div className="overflow-x-auto">
            <table
              aria-label="평가"
              className={`w-full text-left text-sm ${REVIEW_TABLE}`}
            >
              <thead>
                <tr>
                  <th className="font-medium">버전</th>
                  <th className="font-medium">평가 시각</th>
                  <th className="font-medium">주제 판정</th>
                  <th className="font-medium">주제</th>
                  <th className="font-medium">지역</th>
                  <th className="font-medium">대표 형식</th>
                  <th className="font-medium">중요도</th>
                  <th className="font-medium">상세</th>
                </tr>
              </thead>
              <tbody>
                {evaluations.map((evaluation) => (
                  <tr key={evaluation.id} className="align-top">
                    <td className="tabular-nums">{evaluation.versionNumber}</td>
                    <td>{formatDateTime(evaluation.evaluatedAt)}</td>
                    <td>{topicStatusLabels[evaluation.topicStatus]}</td>
                    <td>{tags(evaluation.topics)}</td>
                    <td>{tags(evaluation.regions)}</td>
                    <td>
                      {evaluation.format.name}{" "}
                      {formatProbability(evaluation.format.probability)}
                    </td>
                    <td className="tabular-nums">
                      {formatProbability(evaluation.importance)}
                    </td>
                    <td>
                      <Link
                        to={`/admin/evaluations/${evaluation.id}`}
                        className="underline"
                      >
                        평가 {evaluation.id}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function tags(named: NamedTag[]): string {
  return named.length === 0
    ? "없음"
    : named
        .map((tag) => `${tag.name} ${formatProbability(tag.probability)}`)
        .join(", ");
}

function AssignmentView({ assignment }: { assignment: Assignment | null }) {
  if (assignment === null) {
    return (
      <section aria-label="사건 소속" className="space-y-2">
        <h3 className="text-lg font-semibold">사건 소속</h3>
        <p className="text-sm">
          사건에 배정되지 않았다 — 사건 묶기 · 배정 대기에 있거나, 임베더가
          거부해 대기에서 뺐거나, 사건 묶기를 켜기 전에 저장한 기사다(사건 묶기
          채우기로 넣는다)
        </p>
      </section>
    );
  }
  const criteria = assignment.criteria;
  return (
    <section aria-label="사건 소속" className="space-y-3">
      <h3 className="text-lg font-semibold">사건 소속</h3>
      <dl
        aria-label="배정"
        className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm"
      >
        <dt className="text-muted-foreground">사건</dt>
        <dd>
          <Link to={eventPath(assignment.eventId)} className="underline">
            사건 {assignment.eventId}
          </Link>
          {assignment.event === null
            ? " — 지금 값을 읽지 못했다(방금 바뀌었다)"
            : ` · ${eventStatusLabels[assignment.event.status]} · 기사 ${assignment.event.articleCount}`}
        </dd>
        <dt className="text-muted-foreground">점수</dt>
        <dd className="tabular-nums">{formatProbability(assignment.score)}</dd>
        <dt className="text-muted-foreground">판정 방식</dt>
        <dd>
          {decidedByLabels[assignment.decidedBy]}
          {assignment.opened && " · 이 기사로 새 사건을 열었다"}
        </dd>
        <dt className="text-muted-foreground">배정 시각</dt>
        <dd>{formatDateTime(assignment.assignedAt)}</dd>
        <dt className="text-muted-foreground">판정에 쓴 기준</dt>
        <dd>
          시간 창 {formatSeconds(criteria.windowSeconds)} · 위 기준{" "}
          {criteria.upper} · 아래 기준 {criteria.lower} · 저장 기준{" "}
          {criteria.store} · 확률 기준 {criteria.probability}
        </dd>
      </dl>
      <p className="text-sm text-muted-foreground">
        점수는 붙인 후보의 점수(새 사건이면 가장 높은 후보의 점수)다. 합쳐진
        사건에 배정됐던 기사는 합칠 때 남는 사건으로 옮겨져 그 사건에 있다
      </p>
      <CandidatesView candidates={assignment.candidates} />
      <JudgementView assignment={assignment} />
    </section>
  );
}

function CandidatesView({
  candidates,
}: {
  candidates: Schemas["AssignmentCandidateResponse"][];
}) {
  return (
    <div className="space-y-1">
      <h4 className="font-semibold">저장 후보</h4>
      {candidates.length === 0 ? (
        <p className="text-sm">
          저장 기준 이상인 후보가 없었다 — 새 사건을 열었거나 후보 없이 정했다
        </p>
      ) : (
        <table
          aria-label="저장 후보"
          className={`w-full text-left text-sm ${REVIEW_TABLE}`}
        >
          <thead>
            <tr>
              <th className="font-medium">후보 사건</th>
              <th className="font-medium">점수</th>
              <th className="font-medium">판정 확률</th>
            </tr>
          </thead>
          <tbody>
            {candidates.map((candidate) => (
              <tr key={candidate.eventId} className="align-top">
                <td>
                  <Link to={eventPath(candidate.eventId)} className="underline">
                    사건 {candidate.eventId}
                  </Link>
                  {candidate.currentEventId !== candidate.eventId &&
                    ` — 사건 ${candidate.currentEventId}에 합쳐짐`}
                </td>
                <td className="tabular-nums">
                  {formatProbability(candidate.score)}
                </td>
                <td className="tabular-nums">
                  {candidate.probability === null
                    ? "묻지 않음"
                    : formatProbability(candidate.probability)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function JudgementView({ assignment }: { assignment: Assignment }) {
  const judgement = assignment.judgement;
  return (
    <div aria-label="Jev 판정" role="group" className="space-y-1 text-sm">
      <h4 className="font-semibold">Jev 판정</h4>
      {assignment.judgementId === null ? (
        <p>
          이 배정이 반영한 Jev 판정이 없다 — 산식으로 정했거나, 판정을 끝내지
          못했거나, 판정 원본을 남기기 전의 배정이다
        </p>
      ) : judgement === null ? (
        <p>
          판정 원본 {assignment.judgementId}이 없다 — 보존 기간이 지나 지웠다
        </p>
      ) : (
        <>
          <p>
            {formatDateTime(judgement.judgedAt)} · {judgement.evaluator}{" "}
            {judgement.modelVersion} · 문구 {judgement.wordingVersion} · 입력
            토큰 {judgement.inputTokens.toLocaleString("ko-KR")}
          </p>
          <p className="text-muted-foreground">
            후보 사건은 판정 당시의 사건이다 — 그 뒤 합쳐졌으면 링크가 남는
            사건을 연다
          </p>
          <ul aria-label="물은 후보">
            {judgement.candidates.map((candidate) => (
              <li key={candidate.eventId}>
                <Link to={eventPath(candidate.eventId)} className="underline">
                  사건 {candidate.eventId}
                </Link>{" "}
                <span className="tabular-nums">
                  {formatProbability(candidate.probability)}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function KeyTermsView({
  keyTerms,
}: {
  keyTerms: Schemas["ArticleKeyTermsResponse"][];
}) {
  return (
    <section aria-label="핵심 말" className="space-y-2">
      <h3 className="text-lg font-semibold">핵심 말</h3>
      <p className="text-sm text-muted-foreground">
        제목과 요약문 앞부분에서 뽑은 고유명사 · 일반명사 · 외국어다. 후보
        점수의 핵심 말 겹침이 이 말로 센다
      </p>
      {keyTerms.length === 0 ? (
        <p className="text-sm">
          기사 표현이 없다 — 아직 만들지 않았거나, 사건이 닫혀(배정이 없으면
          시간 창이 지나) 정리 작업이 지웠다
        </p>
      ) : (
        <ul className="space-y-1 text-sm">
          {keyTerms.map((representation) => (
            <li key={representation.modelVersion}>
              <span className="text-muted-foreground">
                {representation.modelVersion} ·{" "}
                {formatDateTime(representation.createdAt)}
              </span>
              <span className="block">
                {representation.terms.length === 0
                  ? "뽑은 말 없음"
                  : representation.terms.join(" · ")}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
