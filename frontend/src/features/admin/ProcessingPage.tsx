import { failureText, type Schemas } from "@/features/admin/adminClient";
import { roundEndLabels, roundLabels } from "@/features/admin/labels";
import { useProcessing } from "@/features/admin/processingQueries";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";
import { Button } from "@/shared/ui/button";

type Round = Schemas["ProcessingRoundResponse"];

/**
 * 처리 현황 — 반복 작업의 회차마다 남은 대기 · 미룬 대기와 최근 1시간의 처리 수 · 회차 수, 마지막 회차를 한 표로 보인다. 다시 묶기 · 다시
 * 잇기 · 태그 재평가 뒤 진행을 따라가는 화면이다. 화면을 연 동안 30초마다 다시 읽는다.
 */
export function ProcessingPage() {
  const processing = useProcessing();

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-bold">처리 현황</h1>
      {processing.isPending && (
        <p className="text-muted-foreground">처리 현황을 읽는 중…</p>
      )}
      {processing.error && (
        <div className="space-y-2">
          <p role="alert">{failureText(processing.error)}</p>
          <Button variant="outline" onClick={() => processing.refetch()}>
            다시 시도
          </Button>
        </div>
      )}
      {processing.data && <RoundsTable rounds={processing.data.rounds} />}
      <p className="text-sm text-muted-foreground">
        대기가 밀리면 회차가 쉬지 않고 처리합니다. 같은 일의 Jev 비용은 같고
        시간당 지출이 커집니다.
      </p>
    </section>
  );
}

function RoundsTable({ rounds }: { rounds: Round[] }) {
  return (
    <div className="overflow-x-auto">
      <table
        aria-label="회차"
        className={`w-full text-left text-sm ${REVIEW_TABLE}`}
      >
        <thead>
          <tr>
            <th className="font-medium">회차</th>
            <th className="font-medium">대기</th>
            <th
              className="font-medium"
              title="가져올 시각이 아직 오지 않은 대기(일시 실패 · 정착 시간)"
            >
              미룸
            </th>
            <th className="font-medium">최근 1시간 처리</th>
            <th className="font-medium">최근 1시간 회차</th>
            <th className="font-medium">마지막 회차</th>
          </tr>
        </thead>
        <tbody>
          {rounds.map((round) => (
            <tr key={round.round} className="align-top">
              <td>{roundLabels[round.round] ?? round.round}</td>
              <td className="tabular-nums">{round.waiting}</td>
              <td className="tabular-nums">{round.deferred}</td>
              <td className="tabular-nums">{round.recentHandled}</td>
              <td className="tabular-nums">{round.recentRuns}</td>
              <td>
                {round.lastRun
                  ? `${formatDateTime(round.lastRun.endedAt)} · ${roundEndLabels[round.lastRun.endedBy]}`
                  : "기록 없음"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
