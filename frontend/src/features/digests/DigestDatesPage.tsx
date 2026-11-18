import { Link } from "react-router";
import { useDigestDates } from "@/features/digests/digestQueries";
import {
  DIGEST_DATES_TITLE,
  DIGEST_HOME_PATH,
  DIGEST_HOME_TITLE,
  LOAD_FAILED,
  NO_DIGEST_YET,
  dayStateLabels,
  digestDayTitle,
  digestPath,
} from "@/features/digests/digestTexts";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 지난 다이제스트 — 다이제스트가 있는 날짜를 늦은 순으로, 날짜마다 상태와 함께(docs/specs/reader.md reader-R10 · ADR-0238) */
export function DigestDatesPage() {
  const dates = useDigestDates();
  const entries = dates.data?.pages.flatMap((page) => page.dates) ?? [];
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <p className="text-sm">
        <Link to={DIGEST_HOME_PATH} className="underline">
          {DIGEST_HOME_TITLE}
        </Link>
      </p>
      <h1 className="text-2xl font-bold tracking-tight">
        {DIGEST_DATES_TITLE}
      </h1>
      {dates.isPending && <p className="text-muted-foreground">불러오는 중…</p>}
      {dates.isError && <p role="alert">{LOAD_FAILED}</p>}
      {dates.data && entries.length === 0 && <p>{NO_DIGEST_YET}</p>}
      {entries.length > 0 && (
        <ul className="space-y-1">
          {entries.map((entry) => (
            <li key={entry.date}>
              <Link to={digestPath(entry.date)} className="underline">
                {digestDayTitle(entry.date)}
              </Link>
              <span className="text-sm text-muted-foreground">
                {" · "}
                {dayStateLabels[entry.state]}
              </span>
            </li>
          ))}
        </ul>
      )}
      {dates.hasNextPage && (
        <Button
          variant="outline"
          disabled={dates.isFetchingNextPage}
          onClick={() => dates.fetchNextPage()}
        >
          더 보기
        </Button>
      )}
    </section>
  );
}
