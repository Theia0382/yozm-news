import { Link, useParams } from "react-router";
import {
  DigestRequestError,
  useDigestDay,
} from "@/features/digests/digestQueries";
import { DigestDayView } from "@/features/digests/DigestDayView";
import {
  DAY_NOT_FOUND,
  DIGEST_DATES_TITLE,
  DIGEST_HOME_PATH,
  DIGEST_HOME_TITLE,
  DIGESTS_PATH,
  LOAD_FAILED,
  digestDayTitle,
  digestPath,
} from "@/features/digests/digestTexts";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 경로의 날짜 꼴 — YYYY-MM-DD 가 아니면 서버에 묻지 않는다 */
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 날짜 하나의 다이제스트 — 이전 · 다음 날짜로 옮겨 간다(docs/specs/reader.md reader-R9 · ADR-0237). 수동 생성 버튼은 홈에만 있다 */
export function DigestDayPage() {
  const date = useParams().date ?? "";
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <p className="flex flex-wrap gap-x-3 text-sm">
        <Link to={DIGEST_HOME_PATH} className="underline">
          {DIGEST_HOME_TITLE}
        </Link>
        <Link to={DIGESTS_PATH} className="underline">
          {DIGEST_DATES_TITLE}
        </Link>
      </p>
      {DATE.test(date) ? (
        <Day date={date} />
      ) : (
        <p role="alert">{DAY_NOT_FOUND}</p>
      )}
    </section>
  );
}

function Day({ date }: { date: string }) {
  const day = useDigestDay(date);
  if (day.isPending) {
    return <p className="text-muted-foreground">불러오는 중…</p>;
  }
  if (day.isError) {
    const missing =
      day.error instanceof DigestRequestError &&
      (day.error.status === 404 || day.error.status === 400);
    return <p role="alert">{missing ? DAY_NOT_FOUND : LOAD_FAILED}</p>;
  }
  const { previousDate, nextDate } = day.data;
  return (
    <>
      <h1 className="text-2xl font-bold tracking-tight">
        {digestDayTitle(day.data.date)}
      </h1>
      <nav aria-label="다른 날짜" className="flex flex-wrap gap-x-4 text-sm">
        {previousDate && (
          <Link to={digestPath(previousDate)} className="underline">
            이전 다이제스트
          </Link>
        )}
        {nextDate && (
          <Link to={digestPath(nextDate)} className="underline">
            다음 다이제스트
          </Link>
        )}
      </nav>
      <DigestDayView day={day.data} />
    </>
  );
}
