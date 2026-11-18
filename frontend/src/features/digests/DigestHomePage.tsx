import { type ReactElement, useState } from "react";
import { Link } from "react-router";
import {
  type DigestDay,
  type DigestRequestResult,
  type DigestRequestStatus,
  useDigestHome,
  useRequestDigest,
} from "@/features/digests/digestQueries";
import { DigestDayView } from "@/features/digests/DigestDayView";
import {
  DIGEST_DATES_TITLE,
  DIGEST_HOME_TITLE,
  DIGESTS_PATH,
  IN_PROGRESS_REFUSED,
  LOAD_FAILED,
  NOT_YET_TODAY,
  NO_DIGEST_YET,
  NO_USABLE_MODEL_TEXT,
  REGENERATE_KEEPS_TEXT,
  REGENERATE_TEXT,
  REQUEST_COST_TEXT,
  REQUEST_FAILED,
  REQUEST_NEW,
  REQUEST_REGENERATE,
  dailyLimitText,
  digestDayTitle,
  digestPath,
  remainingText,
  requestTitle,
  scheduledTodayText,
  skipsScheduleText,
} from "@/features/digests/digestTexts";
import {
  beforeGenerationTime,
  zoneLabel,
} from "@/features/digests/todaySchedule";
import {
  type InterestSettings,
  useInterestSettings,
  useInterestTags,
} from "@/features/interests/interestQueries";
import {
  INTERESTS_PATH,
  NO_TOPIC_TEXT,
} from "@/features/interests/interestTexts";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/shared/ui/alert-dialog";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 홈 — 가장 늦은 날짜의 다이제스트와 수동 생성(docs/specs/reader.md reader-R9 · R11). 그 날짜가 오늘이 아니면 오늘 다이제스트를 언제 만드는지
 * 알리고(ADR-0237), 구독한 주제가 없으면 관심 설정으로 안내한다(docs/specs/user.md user-R43).
 */
export function DigestHomePage() {
  const home = useDigestHome();
  const tags = useInterestTags();
  const settings = useInterestSettings();
  const noTopic =
    tags.data !== undefined && tags.data.preferences.topics.length === 0;
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-6`}>
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="text-3xl font-bold tracking-tight">
          {DIGEST_HOME_TITLE}
        </h1>
        <Link to={DIGESTS_PATH} className="text-sm underline">
          {DIGEST_DATES_TITLE}
        </Link>
      </header>
      {noTopic && (
        <p role="note">
          {NO_TOPIC_TEXT}{" "}
          <Link to={INTERESTS_PATH} className="underline">
            관심 설정으로 가기
          </Link>
        </p>
      )}
      {home.isPending && <p className="text-muted-foreground">불러오는 중…</p>}
      {home.isError && <p role="alert">{LOAD_FAILED}</p>}
      {home.data && (
        <>
          {home.data.status && (
            <TodayControls
              status={home.data.status}
              day={home.data.day}
              settings={settings.data}
            />
          )}
          {home.data.day ? (
            <LatestDay day={home.data.day} />
          ) : (
            <p>{NO_DIGEST_YET}</p>
          )}
        </>
      )}
    </section>
  );
}

function LatestDay({ day }: { day: DigestDay }) {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-xl font-semibold">{digestDayTitle(day.date)}</h2>
        {day.previousDate && (
          <Link to={digestPath(day.previousDate)} className="text-sm underline">
            이전 다이제스트
          </Link>
        )}
      </div>
      <DigestDayView day={day} />
    </div>
  );
}

/** 오늘 다이제스트 안내와 수동 생성 버튼 */
function TodayControls({
  status,
  day,
  settings,
}: {
  status: DigestRequestStatus;
  day: DigestDay | null;
  settings: InterestSettings | undefined;
}) {
  const before =
    settings === undefined
      ? null
      : beforeGenerationTime(
          new Date(),
          settings.digestTime,
          settings.timezone,
        );
  const time = settings?.digestTime.slice(0, 5);
  const noToday = day === null || day.date < status.today;
  return (
    <div className="space-y-2">
      {!status.usableModel ? (
        <p role="note">
          {NO_USABLE_MODEL_TEXT}{" "}
          <Link to="/keys" className="underline">
            LLM 키
          </Link>{" "}
          ·{" "}
          <Link to={INTERESTS_PATH} className="underline">
            관심 설정
          </Link>
        </p>
      ) : (
        noToday &&
        !status.inProgress && (
          <p role="note">
            {before === true && settings !== undefined && time !== undefined
              ? scheduledTodayText(time, zoneLabel(settings.timezone))
              : NOT_YET_TODAY}
          </p>
        )
      )}
      <RequestButton
        status={status}
        targetShown={
          day !== null && day.date === status.targetDate && day.shown !== null
        }
        skipsAt={!status.regenerates && before === true ? time : undefined}
      />
    </div>
  );
}

/**
 * 수동 생성 — 확인 창 뒤 요청한다(docs/specs/reader.md reader-R11). 쓸 모델이 없거나 만드는 중이거나 오늘 횟수를 다 썼으면 누를 수 없다.
 * @param targetShown 대상 날짜에 보이는 다이제스트가 있는가 — 다시 만드는 동안 그것이 보인다
 * @param skipsAt 지금 새로 만들면 빠지는 오늘 일정 생성 시각 — 없으면 undefined
 */
function RequestButton({
  status,
  targetShown,
  skipsAt,
}: {
  status: DigestRequestStatus;
  targetShown: boolean;
  skipsAt: string | undefined;
}) {
  const [open, setOpen] = useState(false);
  const request = useRequestDigest();
  const exhausted = status.remainingToday === 0;
  const disabled =
    !status.usableModel || status.inProgress || exhausted || request.isPending;
  return (
    <div className="space-y-1">
      <Button
        variant="outline"
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        {status.regenerates ? REQUEST_REGENERATE : REQUEST_NEW}
      </Button>
      {exhausted && (
        <p className="text-sm text-muted-foreground">
          {dailyLimitText(status.resetsAt)}
        </p>
      )}
      {request.isError && <p role="alert">{REQUEST_FAILED}</p>}
      {request.data && <RequestOutcome result={request.data} />}
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {requestTitle(status.targetDate, status.regenerates)}
            </AlertDialogTitle>
            <AlertDialogDescription render={<div className="space-y-2" />}>
              <p>{REQUEST_COST_TEXT}</p>
              <p>{remainingText(status.remainingToday)}</p>
              {status.regenerates && (
                <p>
                  {REGENERATE_TEXT}
                  {targetShown && ` ${REGENERATE_KEEPS_TEXT}`}
                </p>
              )}
              {skipsAt !== undefined && <p>{skipsScheduleText(skipsAt)}</p>}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <Button
              onClick={() => {
                setOpen(false);
                request.mutate();
              }}
            >
              {status.regenerates ? REQUEST_REGENERATE : REQUEST_NEW}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/** 받지 않은 요청의 까닭 — 받은 요청은 만드는 중 표시가 대신한다 */
function RequestOutcome({
  result,
}: {
  result: DigestRequestResult;
}): ReactElement | null {
  switch (result.result) {
    case "REQUESTED":
      return null;
    case "IN_PROGRESS":
      return <p role="alert">{IN_PROGRESS_REFUSED}</p>;
    case "DAILY_LIMIT_REACHED":
      return (
        <p role="alert">
          {result.resetsAt ? dailyLimitText(result.resetsAt) : REQUEST_FAILED}
        </p>
      );
    case "NO_USABLE_MODEL":
      return <p role="alert">{NO_USABLE_MODEL_TEXT}</p>;
  }
}
