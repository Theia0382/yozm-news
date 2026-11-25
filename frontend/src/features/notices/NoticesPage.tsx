import { useQueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { useEffect, useRef } from "react";
import { Link } from "react-router";
import { useDigestHome } from "@/features/digests/digestQueries";
import {
  DIGEST_HOME_PATH,
  FAILED_DAY_LINK,
  RETRY_AT_HOME_LINK,
  failureCauseLinks,
  digestFailureCauseTexts,
  digestPath,
  retryAtHome,
} from "@/features/digests/digestTexts";
import { INTERESTS_PATH } from "@/features/interests/interestTexts";
import { providers } from "@/features/keys/keyTexts";
import {
  type Notice,
  NoticeRequestError,
  useMarkSeen,
  useNoticePages,
} from "@/features/notices/noticeQueries";
import {
  FAILED_REPORT_LINK,
  REPORT_FAILED_TITLE,
  reportFailureCauseTexts,
  reportPath,
  reportTitle,
} from "@/features/reports/reportTexts";
import { SESSION_QUERY_KEY } from "@/features/session/sessionQueries";
import { SUBSCRIPTIONS_PATH } from "@/features/storylines/storylineTexts";
import type { components } from "@/shared/api/schema";
import { formatDate, formatDateTime } from "@/shared/lib/dateTime";
import { type FeedStatus, feedStatusLabels } from "@/shared/lib/feedStatus";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

type FeedStatusEntry = components["schemas"]["FeedStatusEntryResponse"];

type PasswordChangedNotice =
  components["schemas"]["PasswordChangedNoticeResponse"];

type PasswordChangeMethod = PasswordChangedNotice["method"];

type KeyInvalidatedNotice =
  components["schemas"]["KeyInvalidatedNoticeResponse"];

type PreferencesAdjustedNotice =
  components["schemas"]["PreferencesAdjustedNoticeResponse"];

type Adjustment = PreferencesAdjustedNotice["adjustments"][number];

type StorylineSubscriptionsRemovedNotice =
  components["schemas"]["StorylineSubscriptionsRemovedNoticeResponse"];

type DigestFailedNotice = components["schemas"]["DigestFailedNoticeResponse"];

type ReportFailedNotice = components["schemas"]["ReportFailedNoticeResponse"];

type DigestUnresolvedFailure =
  components["schemas"]["DigestUnresolvedFailureResponse"];

/** 피드를 묶어 보이는 지금 상태의 순서 — 손볼 것부터 */
const STATUS_ORDER: readonly FeedStatus[] = ["DEGRADED", "STALE", "ACTIVE"];

/**
 * 알림 페이지 — 최근 알림부터 보이고, 받은 쪽의 안 읽은 알림을 받은 판까지 읽음으로 보낸다. 방금까지 안 읽었던 알림은 페이지에 머무는
 * 동안 "새 알림"으로 강조한다. 목록이 401 이면 로그인 상태를 다시 읽어 로그인 틀이 로그인 화면으로 보내게 한다.
 */
export function NoticesPage() {
  const pages = useNoticePages();
  const markSeen = useMarkSeen();
  const queryClient = useQueryClient();
  const sentPages = useRef(0);
  const { mutate } = markSeen;

  const loaded = pages.data?.pages;
  useEffect(() => {
    if (!loaded) {
      return;
    }
    for (const page of loaded.slice(sentPages.current)) {
      const unread = page.notices
        .filter((notice) => !notice.read)
        .map((notice) => ({ id: notice.id, revision: notice.revision }));
      if (unread.length > 0) {
        mutate(unread);
      }
    }
    sentPages.current = loaded.length;
  }, [loaded, mutate]);

  const unauthenticated =
    pages.error instanceof NoticeRequestError && pages.error.status === 401;
  useEffect(() => {
    if (unauthenticated) {
      void queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
    }
  }, [unauthenticated, queryClient]);

  const notices = loaded?.flatMap((page) => page.notices) ?? [];
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <h1 className="text-3xl font-bold tracking-tight">알림</h1>
      {pages.isPending && (
        <p className="text-muted-foreground">알림을 읽는 중…</p>
      )}
      {pages.isError && !unauthenticated && (
        <div className="space-y-3">
          <p role="alert">알림을 읽지 못했습니다.</p>
          <Button variant="outline" onClick={() => pages.refetch()}>
            다시 시도
          </Button>
        </div>
      )}
      {pages.isSuccess && notices.length === 0 && (
        <p className="text-muted-foreground">알림이 없습니다.</p>
      )}
      {notices.length > 0 && (
        <ol className="space-y-4">
          {notices.map((notice) => (
            <li key={notice.id}>
              <NoticeItem notice={notice} />
            </li>
          ))}
        </ol>
      )}
      {pages.hasNextPage && (
        <Button
          variant="outline"
          disabled={pages.isFetchingNextPage}
          onClick={() => pages.fetchNextPage()}
        >
          더 보기
        </Button>
      )}
    </section>
  );
}

function NoticeItem({ notice }: { notice: Notice }): ReactElement {
  return (
    <article
      className={`space-y-3 rounded-md border p-4 ${notice.read ? "" : "border-primary"}`}
    >
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-semibold">
          {!notice.read && (
            <span className="mr-2 rounded bg-primary px-1.5 py-0.5 text-xs text-primary-foreground">
              새 알림
            </span>
          )}
          {noticeTitle(notice)}
        </h2>
        <time
          className="text-sm text-muted-foreground"
          dateTime={notice.updatedAt}
        >
          {formatDateTime(notice.updatedAt)}
        </time>
      </header>
      <NoticeBody notice={notice} />
    </article>
  );
}

function noticeTitle(notice: Notice): string {
  switch (notice.kind) {
    case "FEED_STATUS":
      return "피드 상태가 바뀌었습니다";
    case "PASSWORD_CHANGED":
      return "비밀번호가 바뀌었습니다";
    case "KEY_INVALIDATED":
      return "LLM 키가 무효가 됐습니다";
    case "PREFERENCES_ADJUSTED":
      return "태그 체계가 바뀌어 관심 설정을 맞췄습니다";
    case "STORYLINE_SUBSCRIPTIONS_REMOVED":
      return "추적하던 흐름이 사라졌습니다";
    case "DIGEST_FAILED":
      return "다이제스트를 만들지 못했습니다";
    case "REPORT_FAILED":
      return REPORT_FAILED_TITLE;
  }
}

function NoticeBody({ notice }: { notice: Notice }): ReactElement {
  switch (notice.kind) {
    case "FEED_STATUS":
      return <FeedStatusBody feeds={notice.feedStatus?.feeds ?? []} />;
    case "PASSWORD_CHANGED":
      return <PasswordChangedBody body={notice.passwordChanged} />;
    case "KEY_INVALIDATED":
      return <KeyInvalidatedBody body={notice.keyInvalidated} />;
    case "PREFERENCES_ADJUSTED":
      return <PreferencesAdjustedBody body={notice.preferencesAdjusted} />;
    case "STORYLINE_SUBSCRIPTIONS_REMOVED":
      return (
        <StorylineSubscriptionsRemovedBody
          body={notice.storylineSubscriptionsRemoved}
        />
      );
    case "DIGEST_FAILED":
      return <DigestFailedBody body={notice.digestFailed} />;
    case "REPORT_FAILED":
      return <ReportFailedBody body={notice.reportFailed} />;
  }
}

/** 비밀번호를 바꾼 방법의 문구 */
const passwordChangeMethodText: Record<PasswordChangeMethod, string> = {
  CHANGE: "비밀번호 변경으로 바꿨습니다.",
  RECOVERY: "복구 코드로 재설정했습니다.",
};

function PasswordChangedBody({
  body,
}: {
  body: PasswordChangedNotice | null | undefined;
}) {
  if (!body) {
    return null;
  }
  return (
    <div className="space-y-1 text-sm">
      <p>
        {passwordChangeMethodText[body.method]}{" "}
        <time dateTime={body.changedAt}>{formatDateTime(body.changedAt)}</time>
      </p>
      <p className="text-muted-foreground">
        직접 바꾸지 않았다면 바로 비밀번호를 바꾸세요.
      </p>
    </div>
  );
}

/**
 * 다이제스트 실패 알림 — 날짜 화면과 갈래의 화면으로 잇는다. 다시 만들기 링크는 해결되지 않은 마지막 실패(홈 응답의 수동 생성 상태 —
 * digest-R40)의 알림에만 단다. 홈 응답은 홈 화면과 같은 조회(캐시 공유)이고, 읽지 못하면 다시 만들기 링크만 달지 않는다.
 */
function DigestFailedBody({
  body,
}: {
  body: DigestFailedNotice | null | undefined;
}) {
  const home = useDigestHome();
  if (!body) {
    return null;
  }
  const causeLink = failureCauseLinks[body.cause];
  return (
    <div className="space-y-1 text-sm">
      <p>
        {formatDate(body.digestDate)} 다이제스트 ·{" "}
        <time dateTime={body.failedAt}>{formatDateTime(body.failedAt)}</time>
      </p>
      <p className="text-muted-foreground">
        {digestFailureCauseTexts[body.cause]}
      </p>
      <p className="flex flex-wrap gap-x-3">
        <Link to={digestPath(body.digestDate)} className="underline">
          {FAILED_DAY_LINK}
        </Link>
        {causeLink !== null && (
          <Link to={causeLink.to} className="underline">
            {causeLink.label}
          </Link>
        )}
        {retryAtHome[body.cause] &&
          isUnresolved(body, home.data?.status?.unresolvedFailure) && (
            <Link to={DIGEST_HOME_PATH} className="underline">
              {RETRY_AT_HOME_LINK}
            </Link>
          )}
      </p>
    </div>
  );
}

/** 리포트 실패 — 종류 · 기간 · 실패 시각, 갈래의 할 일, 그 리포트와 갈래마다 가 볼 화면 */
function ReportFailedBody({
  body,
}: {
  body: ReportFailedNotice | null | undefined;
}) {
  if (!body) {
    return null;
  }
  const causeLink = failureCauseLinks[body.cause];
  return (
    <div className="space-y-1 text-sm">
      <p>
        {reportTitle(body.kind, body.firstDay, body.lastDay)} ·{" "}
        <time dateTime={body.failedAt}>{formatDateTime(body.failedAt)}</time>
      </p>
      <p className="text-muted-foreground">
        {reportFailureCauseTexts[body.cause]}
      </p>
      <p className="flex flex-wrap gap-x-3">
        <Link to={reportPath(body.reportId)} className="underline">
          {FAILED_REPORT_LINK}
        </Link>
        {causeLink !== null && (
          <Link to={causeLink.to} className="underline">
            {causeLink.label}
          </Link>
        )}
      </p>
    </div>
  );
}

/** 그 실패 알림이 해결되지 않은 마지막 실패인가 — 날짜와 끝낸 시각이 같다(서버가 같은 값으로 낸다) */
function isUnresolved(
  body: DigestFailedNotice,
  unresolved: DigestUnresolvedFailure | null | undefined,
): boolean {
  return (
    unresolved !== null &&
    unresolved !== undefined &&
    unresolved.digestDate === body.digestDate &&
    unresolved.failedAt === body.failedAt
  );
}

function KeyInvalidatedBody({
  body,
}: {
  body: KeyInvalidatedNotice | null | undefined;
}) {
  if (!body) {
    return null;
  }
  return (
    <div className="space-y-1 text-sm">
      <p>
        {providers[body.provider].name} 키를 공급자가 받아 주지 않았습니다.{" "}
        <time dateTime={body.invalidatedAt}>
          {formatDateTime(body.invalidatedAt)}
        </time>
      </p>
      <p className="text-muted-foreground">
        새 키를 등록할 때까지 이 키로는 다이제스트를 만들지 못합니다.
      </p>
      <Link to="/keys" className="underline">
        LLM 키 화면에서 확인하기
      </Link>
    </div>
  );
}

/** 조정 종류의 문구 — 해제는 옛 버전의 이름, 더함은 새 버전의 이름이다 */
const adjustmentText: Record<Adjustment["change"], string> = {
  RELEASED_REMOVED: "태그가 없어져 풀었습니다",
  RELEASED_PARTIAL_MERGE: "다른 태그와 합쳐져 제외를 풀었습니다",
  ADDED_MOVED_SUBTOPIC: "구독하던 분야에서 옮겨 가 구독에 더했습니다",
  ADDED_DEFAULT_EXCLUDED: "새로 생긴 기본 제외 형식이라 제외에 더했습니다",
};

/** 선호 종류의 이름 */
const adjustmentKindName: Record<Adjustment["kind"], string> = {
  TOPIC: "주제",
  REGION: "지역",
  FORMAT: "형식",
};

function PreferencesAdjustedBody({
  body,
}: {
  body: PreferencesAdjustedNotice | null | undefined;
}) {
  if (!body) {
    return null;
  }
  return (
    <div className="space-y-2 text-sm">
      <p>태그 체계 버전 {body.tagSetNumber}에 맞춰 관심 설정이 바뀌었습니다.</p>
      <ul className="space-y-1">
        {body.adjustments.map((adjustment) => (
          <li key={`${adjustment.kind}:${adjustment.key}:${adjustment.change}`}>
            {adjustmentKindName[adjustment.kind]}{" "}
            <span className="font-medium">{adjustment.name}</span> —{" "}
            {adjustmentText[adjustment.change]}
          </li>
        ))}
      </ul>
      <Link to={INTERESTS_PATH} className="underline">
        관심 설정에서 확인하기
      </Link>
    </div>
  );
}

/**
 * 사라진 구독 — 스토리라인마다 그 스토리라인에 남았던 기사의 지금 제목. 제목은 알림함을 열 때 읽은 값이라, 기사가 지워졌으면 비어 있다.
 */
function StorylineSubscriptionsRemovedBody({
  body,
}: {
  body: StorylineSubscriptionsRemovedNotice | null | undefined;
}) {
  if (!body) {
    return null;
  }
  return (
    <div className="space-y-2 text-sm">
      <p>
        추적하던 흐름이 더 이어지지 않아 추적에서 뺐습니다. 관련 소식은 다른
        흐름으로 이어질 수 있습니다.
      </p>
      <ul className="space-y-1">
        {body.removed.map((item) => (
          <li key={item.storylineId}>
            {item.title ?? (
              <span className="text-muted-foreground">
                제목을 알 수 없는 흐름
              </span>
            )}
          </li>
        ))}
      </ul>
      <Link to={SUBSCRIPTIONS_PATH} className="underline">
        추적 중인 흐름 보기
      </Link>
    </div>
  );
}

function FeedStatusBody({ feeds }: { feeds: FeedStatusEntry[] }) {
  return (
    <div className="space-y-3">
      {STATUS_ORDER.map((status) => {
        const group = feeds.filter((feed) => feed.lastTo === status);
        if (group.length === 0) {
          return null;
        }
        return (
          <section key={status} className="space-y-1">
            <h3 className="text-sm font-medium">
              지금 {feedStatusLabels[status]} — {group.length}개
            </h3>
            <ul className="space-y-1 text-sm">
              {group.map((feed) => (
                <li key={feed.feedId}>
                  <span className="font-medium">{feedName(feed)}</span>{" "}
                  {feedStatusLabels[feed.firstFrom]} →{" "}
                  {feedStatusLabels[feed.lastTo]}
                  {feed.changes > 1 && ` · ${feed.changes}번 바뀜`}
                  <span className="text-muted-foreground">
                    {" "}
                    · {formatDateTime(feed.lastAt)}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
      <Link to="/admin" className="text-sm underline">
        운영자 화면에서 자세히 보기
      </Link>
    </div>
  );
}

/** 피드 이름 — 출처 이름 · 섹션, 둘 다 없으면 피드 ID */
function feedName(feed: FeedStatusEntry): string {
  const parts = [feed.sourceName, feed.section].filter(
    (part): part is string => part !== null && part !== "",
  );
  return parts.length > 0
    ? parts.join(" · ")
    : `이름 없는 피드 (ID ${feed.feedId})`;
}
