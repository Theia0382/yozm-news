import { Link, useSearchParams } from "react-router";
import { type ReportKind, useReports } from "@/features/reports/reportQueries";
import {
  LIST_LOAD_FAILED,
  NO_REPORTS,
  REPORTS_INTRO,
  REPORTS_TITLE,
  kindLabels,
  periodText,
  reportPath,
  statusLabels,
} from "@/features/reports/reportTexts";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

const KINDS = Object.keys(kindLabels) as ReportKind[];

/** 주소의 종류 — 없거나 모르는 값이면 주간 */
function readKind(value: string | null): ReportKind {
  return KINDS.find((kind) => kind === value) ?? "WEEK";
}

/**
 * 변화 리포트 목록 — 종류 탭(주간 · 월간)마다 최근 순으로, 줄마다 기간과 상태(docs/specs/reader.md reader-R15). 고른 종류는 주소의
 * kind 에 둔다
 */
export function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const kind = readKind(params.get("kind"));
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <header className="space-y-2">
        <h1 className="text-2xl font-bold tracking-tight">{REPORTS_TITLE}</h1>
        <p className="text-sm text-muted-foreground">{REPORTS_INTRO}</p>
      </header>
      <div role="group" aria-label="리포트 종류" className="flex gap-2">
        {KINDS.map((choice) => (
          <Button
            key={choice}
            variant={choice === kind ? "default" : "outline"}
            aria-pressed={choice === kind}
            onClick={() => setParams({ kind: choice })}
          >
            {kindLabels[choice]}
          </Button>
        ))}
      </div>
      <KindList key={kind} kind={kind} />
    </section>
  );
}

function KindList({ kind }: { kind: ReportKind }) {
  const reports = useReports(kind);
  const entries = reports.data?.pages.flatMap((page) => page.reports) ?? [];
  return (
    <>
      {reports.isPending && (
        <p className="text-muted-foreground">불러오는 중…</p>
      )}
      {reports.isError && <p role="alert">{LIST_LOAD_FAILED}</p>}
      {reports.data && entries.length === 0 && <p>{NO_REPORTS}</p>}
      {entries.length > 0 && (
        <ul aria-label={`${kindLabels[kind]} 리포트`} className="space-y-1">
          {entries.map((report) => (
            <li key={report.id} className="tabular-nums">
              <Link to={reportPath(report.id)} className="underline">
                {periodText(report.firstDay, report.lastDay)}
              </Link>
              {statusLabels[report.status] !== null && (
                <span className="text-sm text-muted-foreground">
                  {" · "}
                  {statusLabels[report.status]}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      {reports.hasNextPage && (
        <Button
          variant="outline"
          disabled={reports.isFetchingNextPage}
          onClick={() => reports.fetchNextPage()}
        >
          더 보기
        </Button>
      )}
    </>
  );
}
