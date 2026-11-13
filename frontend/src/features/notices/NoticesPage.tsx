import { useQueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { useEffect, useRef } from "react";
import { Link } from "react-router";
import {
  type Notice,
  NoticeRequestError,
  useMarkSeen,
  useNoticePages,
} from "@/features/notices/noticeQueries";
import { SESSION_QUERY_KEY } from "@/features/session/sessionQueries";
import type { components } from "@/shared/api/schema";
import { formatDateTime } from "@/shared/lib/dateTime";
import { type FeedStatus, feedStatusLabels } from "@/shared/lib/feedStatus";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

type FeedStatusEntry = components["schemas"]["FeedStatusEntryResponse"];

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
  }
}

function NoticeBody({ notice }: { notice: Notice }): ReactElement {
  switch (notice.kind) {
    case "FEED_STATUS":
      return <FeedStatusBody feeds={notice.feedStatus?.feeds ?? []} />;
  }
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
