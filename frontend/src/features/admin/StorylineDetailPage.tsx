import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { useAdminClient } from "@/features/admin/adminSession";
import { ArticleTitle } from "@/features/admin/ArticleTitle";
import { eventPath } from "@/features/admin/eventQueries";
import {
  eventStatusLabels,
  linkedByLabels,
  relationOutcomeLabels,
  relationReadingLabels,
  storylineStateLabels,
} from "@/features/admin/labels";
import { formatProbability } from "@/features/admin/metrics";
import {
  STORYLINE_KEY,
  storylinePath,
} from "@/features/admin/storylineQueries";
import {
  linkingCriteriaText,
  relationCriteriaText,
  relationDirection,
} from "@/features/admin/storylineText";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";

type Detail = Schemas["StorylineDetailResponse"];
type Event = Schemas["StorylineEventResponse"];
type Membership = Schemas["StorylineMembershipResponse"];
type Judgement = Schemas["RelationJudgementResponse"];

/**
 * 스토리라인 하나 — 값 · 대표 기사, 사건(직접 · 하위에서, 그 사건을 직접 가진 스토리라인마다 소속 근거), 관계(이 스토리라인 기준 방향 ·
 * 확률 · 판정 원본), 물은 쌍(마지막으로 물은 결말 · 판정 원본). 합쳐진 스토리라인의 ID 로 열면 남는 스토리라인을 보이고 그 사실을 알린다.
 */
export function StorylineDetailPage() {
  const client = useAdminClient();
  const id = Number(useParams().id);
  const detail = useQuery({
    queryKey: [...STORYLINE_KEY, "detail", id],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/storylines/{id}", {
          params: { path: { id } },
        }),
      ),
  });

  return (
    <div className="space-y-6">
      <p className="text-sm">
        <Link to="/admin/storylines" className="underline">
          스토리라인 목록
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
      <h2 className="text-xl font-semibold">스토리라인 {detail.id}</h2>
      {detail.requestedId !== detail.id && (
        <p role="status" className="text-sm">
          합쳐진 스토리라인 {detail.requestedId} 대신 남는 스토리라인을 보인다
        </p>
      )}
      <dl
        aria-label="스토리라인 값"
        className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm"
      >
        <dt className="text-muted-foreground">상태</dt>
        <dd>{storylineStateLabels[detail.state]}</dd>
        <dt className="text-muted-foreground">상위</dt>
        <dd>
          {detail.parentId === null ? (
            "없음"
          ) : (
            <StorylineLink id={detail.parentId} />
          )}
        </dd>
        <dt className="text-muted-foreground">사건 수</dt>
        <dd className="tabular-nums">{detail.eventCount}</dd>
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
            id={detail.representativeArticleId}
            article={detail.representative}
          />
        </dd>
      </dl>
      <EventsSection detail={detail} />
      <RelationsSection detail={detail} />
      <AskedSection detail={detail} />
    </>
  );
}

function EventsSection({ detail }: { detail: Detail }) {
  return (
    <section aria-label="스토리라인의 사건" className="space-y-2">
      <h3 className="text-lg font-semibold">사건</h3>
      <p className="text-sm text-muted-foreground">
        첫 기사 시각 순이다. 하위 스토리라인의 사건도 들고
        &ldquo;하위에서&rdquo;로 표시한다. 소속 근거는 그 사건을 직접 가진
        스토리라인마다의 것이다 — 기준은 그 판정에 쓴 값이라 지금 기준과 다를 수
        있다
      </p>
      <div className="overflow-x-auto">
        <table
          aria-label="스토리라인의 사건"
          className={`w-full text-left text-sm ${REVIEW_TABLE}`}
        >
          <thead>
            <tr>
              <th className="font-medium">사건</th>
              <th className="font-medium">소속</th>
              <th className="font-medium">상태</th>
              <th className="font-medium">기사 수</th>
              <th className="font-medium">첫 기사</th>
              <th className="font-medium">대표 기사</th>
              <th className="font-medium">소속 근거</th>
            </tr>
          </thead>
          <tbody>
            {detail.events.map((event) => (
              <EventRow key={event.id} event={event} self={detail.id} />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function EventRow({ event, self }: { event: Event; self: number }) {
  return (
    <tr className="align-top">
      <td>
        <Link to={eventPath(event.id)} className="underline">
          사건 {event.id}
        </Link>
      </td>
      <td>{event.direct ? "직접" : "하위에서"}</td>
      <td>{eventStatusLabels[event.status]}</td>
      <td className="tabular-nums">{event.articleCount}</td>
      <td>{formatDateTime(event.firstArticleAt)}</td>
      <td>
        <ArticleTitle
          id={event.representativeArticleId}
          article={event.representative}
        />
      </td>
      <td>
        <ul className="space-y-1">
          {event.memberships.map((membership) => (
            <li key={membership.storylineId}>
              <MembershipText membership={membership} self={self} />
            </li>
          ))}
        </ul>
      </td>
    </tr>
  );
}

function MembershipText({
  membership,
  self,
}: {
  membership: Membership;
  self: number;
}) {
  return (
    <>
      {membership.storylineId === self ? (
        "이 스토리라인"
      ) : (
        <StorylineLink id={membership.storylineId} />
      )}{" "}
      · {linkedByLabels[membership.linkedBy]}
      {membership.score !== null && (
        <>
          {" "}
          · 점수 {formatProbability(membership.score)} (코사인{" "}
          {formatProbability(membership.cosine)} · 겹침{" "}
          {formatProbability(membership.keyTermOverlap)})
        </>
      )}
      {membership.criteria !== null && (
        <span className="block text-muted-foreground">
          {linkingCriteriaText(membership.criteria)} ·{" "}
          {formatDateTime(membership.linkedAt)}
        </span>
      )}
      {membership.criteria === null && (
        <span className="block text-muted-foreground">
          {formatDateTime(membership.linkedAt)}
        </span>
      )}
    </>
  );
}

function RelationsSection({ detail }: { detail: Detail }) {
  return (
    <section aria-label="관계" className="space-y-2">
      <h3 className="text-lg font-semibold">관계</h3>
      {detail.relations.length === 0 ? (
        <p className="text-sm">맺은 관계가 없다</p>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            방향은 이 스토리라인 기준이다. 확률은 그 관계를 맺은 판정에서 가장
            높았던 답의 확률이다
          </p>
          <JudgementLegend />
          <div className="overflow-x-auto">
            <table
              aria-label="관계"
              className={`w-full text-left text-sm ${REVIEW_TABLE}`}
            >
              <thead>
                <tr>
                  <th className="font-medium">방향</th>
                  <th className="font-medium">상대</th>
                  <th className="font-medium">확률</th>
                  <th className="font-medium">맺은 시각</th>
                  <th className="font-medium">기준</th>
                  <th className="font-medium">판정 원본</th>
                </tr>
              </thead>
              <tbody>
                {detail.relations.map((relation) => {
                  const other =
                    relation.sourceId === detail.id
                      ? relation.targetId
                      : relation.sourceId;
                  return (
                    <tr
                      key={`${relation.kind}-${relation.sourceId}-${relation.targetId}`}
                      className="align-top"
                    >
                      <td>
                        {relationDirection(
                          relation.kind,
                          relation.sourceId,
                          detail.id,
                        )}
                      </td>
                      <td>
                        <StorylineLink id={other} />
                      </td>
                      <td className="tabular-nums">
                        {formatProbability(relation.probability)}
                      </td>
                      <td>{formatDateTime(relation.linkedAt)}</td>
                      <td>{relationCriteriaText(relation.criteria)}</td>
                      <td>
                        <JudgementText
                          judgementId={relation.judgementId}
                          judgement={relation.judgement}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function AskedSection({ detail }: { detail: Detail }) {
  return (
    <section aria-label="물은 쌍" className="space-y-2">
      <h3 className="text-lg font-semibold">물은 쌍</h3>
      {detail.asked.length === 0 ? (
        <p className="text-sm">관계를 물은 상대가 없다</p>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            상대마다 마지막으로 물은 결말이다 — 같은 쌍을 다시 물으면 덮어쓴다.
            작은 쪽 사건 수는 물었을 때의 값이다
          </p>
          <JudgementLegend />
          <div className="overflow-x-auto">
            <table
              aria-label="물은 쌍"
              className={`w-full text-left text-sm ${REVIEW_TABLE}`}
            >
              <thead>
                <tr>
                  <th className="font-medium">상대</th>
                  <th className="font-medium">마지막으로 물은 결말</th>
                  <th className="font-medium">작은 쪽 사건 수</th>
                  <th className="font-medium">물은 시각</th>
                  <th className="font-medium">기준</th>
                  <th className="font-medium">판정 원본</th>
                </tr>
              </thead>
              <tbody>
                {detail.asked.map((asked) => (
                  <tr key={asked.otherId} className="align-top">
                    <td>
                      <StorylineLink id={asked.otherId} />
                    </td>
                    <td>{relationOutcomeLabels[asked.outcome]}</td>
                    <td className="tabular-nums">{asked.smallerEvents}</td>
                    <td>{formatDateTime(asked.askedAt)}</td>
                    <td>{relationCriteriaText(asked.criteria)}</td>
                    <td>
                      <JudgementText
                        judgementId={asked.judgementId}
                        judgement={asked.judgement}
                      />
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

/** 판정 원본의 답을 읽는 법 */
function JudgementLegend() {
  return (
    <p className="text-sm text-muted-foreground">
      판정 원본의 번호는 판정을 물을 때의 앞 · 뒤 스토리라인이다 — A ⊂ B 는 A 가
      B 의 부분, A → B 는 A 가 B 를 낳음이다
    </p>
  );
}

/** 판정 원본 — 원본을 남기지 않은 결말, 지워진 원본, 읽지 못한 원본, 읽은 확률 */
function JudgementText({
  judgementId,
  judgement,
}: {
  judgementId: number | null;
  judgement: Judgement | null;
}) {
  if (judgementId === null) {
    return (
      <span className="text-muted-foreground">원본을 남기지 않는 결말</span>
    );
  }
  if (judgement === null) {
    return <span className="text-muted-foreground">원본 없음</span>;
  }
  const first = judgement.firstId;
  const second = judgement.secondId;
  return (
    <>
      <span className="block text-muted-foreground">
        {judgement.modelVersion} · 입력 토큰 {judgement.inputTokens} ·{" "}
        {formatDateTime(judgement.judgedAt)}
      </span>
      {judgement.reading === "READ" ? (
        <span className="block tabular-nums">
          해당 없음 {formatProbability(judgement.none)} · {second} ⊂ {first}{" "}
          {formatProbability(judgement.secondPartOfFirst)} · {first} ⊂ {second}{" "}
          {formatProbability(judgement.firstPartOfSecond)} · {first} → {second}{" "}
          {formatProbability(judgement.firstLedToSecond)} · {second} → {first}{" "}
          {formatProbability(judgement.secondLedToFirst)} · 한 사안 {first}{" "}
          {formatProbability(judgement.firstOneMatter)} · {second}{" "}
          {formatProbability(judgement.secondOneMatter)}
        </span>
      ) : (
        <span className="block">
          {relationReadingLabels[judgement.reading]} — {judgement.message}
        </span>
      )}
    </>
  );
}

function StorylineLink({ id }: { id: number }) {
  return (
    <Link to={storylinePath(id)} className="underline">
      스토리라인 {id}
    </Link>
  );
}
