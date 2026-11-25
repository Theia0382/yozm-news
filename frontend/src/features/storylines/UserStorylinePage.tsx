import { Link, useParams } from "react-router";
import { digestPath } from "@/features/digests/digestTexts";
import { SubscribeButton } from "@/features/storylines/SubscribeButton";
import { StorylineRequestError } from "@/features/storylines/storylineQueries";
import { useTrackingView } from "@/features/storylines/subscriptionQueries";
import {
  ITEMS_LOAD_FAILED,
  ITEMS_TITLE,
  ITEM_UNTITLED,
  LOAD_FAILED,
  NOT_FOUND,
  NO_ITEMS,
  PARTS_TITLE,
  PART_UNWRITTEN,
  STORYLINES_PATH,
  STORYLINES_TITLE,
  TIMELINE_LINK,
  TRACKING_STATES_FAILED,
  itemKindLabels,
  storylinePath,
  summarizedAt,
  summaryHeadings,
  summaryTotalUsageText,
  summaryUsageText,
} from "@/features/storylines/storylineTexts";
import {
  type UserStorylineDetail,
  type UserStorylineTop,
  useUserStoryline,
  useUserStorylineItems,
} from "@/features/storylines/userStorylineQueries";
import { formatDate } from "@/shared/lib/dateTime";
import { storylineStateLabels } from "@/shared/lib/storylineState";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 경로의 사용자 스토리라인 ID — 1 이상의 정수가 아니면 null */
function readId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id >= 1 ? id : null;
}

/**
 * 개인 흐름 화면 — 정리된 사용자 스토리라인 하나의 정리(제목 · 흐름 요약 · 최근 전개), 갈래마다의 정리와 공용 타임라인 링크, 그 사용자 스토리라인에
 * 묶인 지난 다이제스트 항목, 정리에 쓴 토큰(docs/specs/reader.md). 정리는 그 사용자의 LLM 키로 만든 글이다
 */
export function UserStorylinePage() {
  const id = readId(useParams().id);
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <p className="text-sm">
        <Link to={STORYLINES_PATH} className="underline">
          {STORYLINES_TITLE} 목록
        </Link>
      </p>
      {id === null ? <p role="alert">{NOT_FOUND}</p> : <Detail id={id} />}
    </section>
  );
}

function Detail({ id }: { id: number }) {
  const detail = useUserStoryline(id);
  const tracking = useTrackingView(
    detail.data === undefined ? [] : [detail.data.storylineId],
  );

  if (detail.isPending) {
    return <p className="text-muted-foreground">불러오는 중…</p>;
  }
  if (detail.isError) {
    const notFound =
      detail.error instanceof StorylineRequestError &&
      detail.error.status === 404;
    return <p role="alert">{notFound ? NOT_FOUND : LOAD_FAILED}</p>;
  }
  const userStoryline = detail.data;
  return (
    <>
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h1 className="min-w-0 text-2xl font-bold tracking-tight break-words">
            {userStoryline.title}
          </h1>
          <SubscribeButton
            storylineId={userStoryline.storylineId}
            tracking={tracking}
          />
        </div>
        <p className="text-sm text-muted-foreground tabular-nums">
          {summarizedAt(userStoryline.updatedAt)}
        </p>
        {tracking.failed && (
          <p role="alert" className="text-sm">
            {TRACKING_STATES_FAILED}
          </p>
        )}
      </header>
      <SummaryText
        summary={userStoryline.summary}
        recent={userStoryline.recent}
      />
      <Parts tops={userStoryline.tops} />
      <Items id={userStoryline.id} />
      <UsageLines userStoryline={userStoryline} />
    </>
  );
}

function SummaryText({ summary, recent }: { summary: string; recent: string }) {
  return (
    <div className="space-y-4">
      <section className="space-y-1">
        <h2 className="text-sm font-medium text-muted-foreground">
          {summaryHeadings.summary}
        </h2>
        <p className="break-words whitespace-pre-line">{summary}</p>
      </section>
      <section className="space-y-1">
        <h2 className="text-sm font-medium text-muted-foreground">
          {summaryHeadings.recent}
        </h2>
        <p className="break-words whitespace-pre-line">{recent}</p>
      </section>
    </div>
  );
}

/** 갈래 — 하나면 타임라인 링크만, 둘 이상이면 갈래마다의 정리와 타임라인 링크 */
function Parts({ tops }: { tops: UserStorylineTop[] }) {
  if (tops.length === 0) {
    return null;
  }
  if (tops.length === 1) {
    return (
      <p className="text-sm">
        <Link to={storylinePath(tops[0]!.storylineId)} className="underline">
          {TIMELINE_LINK}
        </Link>
      </p>
    );
  }
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">{PARTS_TITLE}</h2>
      <ul aria-label={PARTS_TITLE} className="space-y-3">
        {tops.map((top) => (
          <li
            key={top.storylineId}
            className="space-y-1 rounded-md border border-input p-4"
          >
            <h3 className="font-medium break-words">
              {top.title ?? PART_UNWRITTEN}
            </h3>
            {top.summary !== null && (
              <p className="text-sm break-words">{top.summary}</p>
            )}
            {top.recent !== null && (
              <p className="text-sm break-words">
                <span className="font-medium">{summaryHeadings.recent}</span>{" "}
                {top.recent}
              </p>
            )}
            <p className="text-sm text-muted-foreground">
              {storylineStateLabels[top.state]} ·{" "}
              <Link to={storylinePath(top.storylineId)} className="underline">
                {TIMELINE_LINK}
              </Link>
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** 그 사용자 스토리라인에 묶인 지난 다이제스트 항목 — 날짜 화면으로 간다 */
function Items({ id }: { id: number }) {
  const items = useUserStorylineItems(id);
  const rows = items.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">{ITEMS_TITLE}</h2>
      {items.isPending && <p className="text-muted-foreground">불러오는 중…</p>}
      {items.isError && <p role="alert">{ITEMS_LOAD_FAILED}</p>}
      {items.data && rows.length === 0 && (
        <p className="text-sm text-muted-foreground">{NO_ITEMS}</p>
      )}
      {rows.length > 0 && (
        <ul aria-label={ITEMS_TITLE} className="space-y-2">
          {rows.map((item, index) => (
            <li key={`${item.date}-${index}`} className="text-sm">
              <Link to={digestPath(item.date)} className="underline">
                {formatDate(item.date)}
              </Link>{" "}
              <span className="text-muted-foreground">
                {itemKindLabels[item.kind]}
              </span>{" "}
              <span className="break-words">{item.title ?? ITEM_UNTITLED}</span>
            </li>
          ))}
        </ul>
      )}
      {items.hasNextPage && (
        <button
          type="button"
          onClick={() => items.fetchNextPage()}
          disabled={items.isFetchingNextPage}
          className="rounded-md border border-input px-3 py-1 text-sm"
        >
          더 보기
        </button>
      )}
    </section>
  );
}

/** 정리에 쓴 모델 · 토큰 — 다이제스트의 사용량 줄과 같은 자리(맨 아래) · 같은 꼴이다 */
function UsageLines({ userStoryline }: { userStoryline: UserStorylineDetail }) {
  const usage = userStoryline.usage;
  return (
    <div className="space-y-1 text-xs text-muted-foreground tabular-nums">
      <p>
        {summaryUsageText(usage.model, usage.inputTokens, usage.outputTokens)}
        {" · "}
        {summarizedAt(userStoryline.updatedAt)}
      </p>
      {usage.calls > 1 && (
        <p>
          {summaryTotalUsageText(
            usage.calls,
            usage.totalInputTokens,
            usage.totalOutputTokens,
          )}
        </p>
      )}
    </div>
  );
}
