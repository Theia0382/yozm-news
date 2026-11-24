import { useIsFetching, useQueryClient } from "@tanstack/react-query";
import {
  type FormEvent,
  type ReactNode,
  useEffect,
  useRef,
  useState,
} from "react";
import { Link, useLocation } from "react-router";
import {
  requestFailedText,
  UNREACHABLE_TEXT,
} from "@/features/account/accountTexts";
import {
  INTERESTS_QUERY_KEY,
  INTEREST_MODEL_QUERY_KEY,
  INTEREST_SETTINGS_QUERY_KEY,
  INTEREST_SOURCES_QUERY_KEY,
  INTEREST_TAGS_QUERY_KEY,
  INTEREST_VOLUME_QUERY_KEY,
  type InterestModel,
  type InterestSettings,
  type InterestSources,
  type InterestTags,
  type InterestVolume,
  InterestRequestError,
  useInterestModel,
  useInterestSettings,
  useInterestSources,
  useInterestTags,
  useInterestVolume,
} from "@/features/interests/interestQueries";
import {
  BIG_SLOTS_HELP_TEXT,
  BIG_SLOTS_OFF_TEXT,
  CANDIDATE_VOLUME_HELP_TEXT,
  type CandidateVolume,
  candidateVolumeNames,
  modelReasonTexts,
  MONTHLY_DAY_HELP_TEXT,
  MONTHLY_NEEDS_WEEKLY_TEXT,
  monthlyDayName,
  NO_TOPIC_TEXT,
  REASONING_EFFORT_HELP_TEXT,
  type ReasoningEffort,
  REPORT_SCHEDULE_HELP_TEXT,
  reasoningEffortNames,
  SUMMARY_LENGTH_HELP_TEXT,
  type SummaryLength,
  summaryLengthNames,
  type Weekday,
  weekdayNames,
} from "@/features/interests/interestTexts";
import { providers } from "@/features/keys/keyTexts";
import { PRIVACY_PATH } from "@/features/privacy/privacyTexts";
import {
  INTERESTS_LINK as REPORTS_INTERESTS_LINK,
  REPORTS_PATH,
} from "@/features/reports/reportTexts";
import { SESSION_QUERY_KEY } from "@/features/session/sessionQueries";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 한 절의 안내 — 성공은 알림(status), 실패는 경고(alert)로 읽힌다 */
type Message = { tone: "status" | "alert"; text: string };

/**
 * 관심 설정 — 주제 구독 · 지역 · 형식 제외, 제외 소스, 일정 · 변화 리포트, 다이제스트 분량, 모델. 절마다 저장한다. 키가 없어도 쓰고, 모델 절만
 * 키 등록을 안내한다(docs/specs/account.md §3 · account-R5). 주소에 절의 id(`#interest-model` 등)가 있으면 조회가 모두 끝난 뒤 그 절로 내려간다.
 */
export function InterestsPage() {
  const tags = useInterestTags();
  const queryClient = useQueryClient();
  useScrollToHashWhenLoaded(!tags.isPending);
  const unauthenticated =
    tags.error instanceof InterestRequestError && tags.error.status === 401;
  useEffect(() => {
    if (unauthenticated) {
      void queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
    }
  }, [unauthenticated, queryClient]);

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-8`}>
      <h1 className="text-2xl font-bold">관심 설정</h1>
      <p className="text-sm text-muted-foreground">
        다이제스트에 넣을 기사와 만드는 때를 정합니다. 절마다 저장합니다.
      </p>
      {tags.isPending && <p className="text-muted-foreground">읽는 중…</p>}
      {tags.isError && !unauthenticated && (
        <div className="space-y-3">
          <p role="alert">관심 설정을 읽지 못했습니다.</p>
          <Button variant="outline" onClick={() => tags.refetch()}>
            다시 시도
          </Button>
        </div>
      )}
      {tags.data && <TagSection data={tags.data} />}
      <SourcesSection />
      <ScheduleSection />
      <VolumeSection />
      <ModelSection />
      <p className="text-sm">
        <Link to={PRIVACY_PATH} className="underline">
          저장하는 것과 LLM 공급자에게 보내는 것
        </Link>
      </p>
    </section>
  );
}

/**
 * 관심 설정의 조회가 모두 끝난 뒤 한 번, 라우터 위치의 해시가 가리키는 절로 내려간다 — 절 위의 내용이 비동기로 채워져 자리가 바뀌므로 다 읽은 뒤에
 * 내려간다. 브라우저 주소가 아니라 라우터 위치를 읽어 화면 안 이동과 같은 값을 쓴다.
 * @param started 첫 조회가 시작됐다 — 조회가 걸리기 전의 0 을 끝으로 읽지 않게 한다
 */
function useScrollToHashWhenLoaded(started: boolean) {
  const { hash } = useLocation();
  const fetching = useIsFetching({ queryKey: INTERESTS_QUERY_KEY });
  const scrolled = useRef(false);
  useEffect(() => {
    if (scrolled.current || hash === "" || !started || fetching > 0) {
      return;
    }
    scrolled.current = true;
    document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash, started, fetching]);
}

/** 서버 값이 바뀌면 절의 입력을 서버 값으로 다시 채운다 */
function tagsKey(data: InterestTags): string {
  return JSON.stringify([data.tagSet.id, data.preferences]);
}

function Section({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="space-y-3" aria-labelledby={id}>
      <h2 id={id} className="text-xl font-bold">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Notice({ message }: { message: Message | null }) {
  if (!message) {
    return null;
  }
  return (
    <p
      role={message.tone}
      className={message.tone === "alert" ? "text-destructive" : undefined}
    >
      {message.text}
    </p>
  );
}

function CheckField({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <Checkbox
        aria-label={label}
        checked={checked}
        disabled={disabled}
        onCheckedChange={(next) => onChange(next === true)}
      />
      {label}
    </label>
  );
}

/** 저장 요청을 보내고 안내를 고른다 — 닿지 못하면 연결 안내 */
async function attempt(
  setBusy: (busy: boolean) => void,
  setMessage: (message: Message | null) => void,
  action: () => Promise<Message>,
) {
  setBusy(true);
  setMessage(null);
  try {
    setMessage(await action());
  } catch {
    setMessage({ tone: "alert", text: UNREACHABLE_TEXT });
  } finally {
    setBusy(false);
  }
}

/** 저장 안내는 바깥에 둔다 — 저장 뒤 다시 읽어 폼을 새로 채워도 안내가 남는다 */
type FormProps<T> = {
  data: T;
  setMessage: (message: Message | null) => void;
};

function TagSection({ data }: { data: InterestTags }) {
  const [message, setMessage] = useState<Message | null>(null);
  return (
    <div className="space-y-3">
      <TagForm key={tagsKey(data)} data={data} setMessage={setMessage} />
      <Notice message={message} />
    </div>
  );
}

function TagForm({ data, setMessage }: FormProps<InterestTags>) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [topics, setTopics] = useState(() => new Set(data.preferences.topics));
  const [regions, setRegions] = useState(
    () => new Set(data.preferences.excludedRegions),
  );
  const [formats, setFormats] = useState(
    () => new Set(data.preferences.excludedFormats),
  );
  const [busy, setBusy] = useState(false);

  function toggle(set: Set<string>, key: string, on: boolean): Set<string> {
    const next = new Set(set);
    if (on) {
      next.add(key);
    } else {
      next.delete(key);
    }
    return next;
  }

  function save(event: FormEvent) {
    event.preventDefault();
    void attempt(setBusy, setMessage, async () => {
      const { data: saved, response } = await client.PUT(
        "/api/interests/tags",
        {
          body: {
            tagSetId: data.tagSet.id,
            topics: [...topics],
            excludedRegions: [...regions],
            excludedFormats: [...formats],
          },
        },
      );
      if (!response.ok || !saved) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      switch (saved.result) {
        case "SAVED":
          await queryClient.invalidateQueries({
            queryKey: INTEREST_TAGS_QUERY_KEY,
          });
          return { tone: "status", text: "주제 · 지역 · 형식을 저장했습니다." };
        case "TAG_SET_CHANGED":
          await queryClient.invalidateQueries({
            queryKey: INTEREST_TAGS_QUERY_KEY,
          });
          return {
            tone: "alert",
            text: "그사이 태그 체계가 바뀌었습니다. 새 체계로 다시 읽었으니 확인하고 다시 저장해 주세요.",
          };
      }
      const unknown: never = saved.result;
      return {
        tone: "alert",
        text: `알 수 없는 결과입니다: ${String(unknown)}`,
      };
    });
  }

  return (
    <form onSubmit={save} className="space-y-8">
      <Section id="interest-topics" title="구독할 주제">
        <p className="text-sm text-muted-foreground">
          분야를 고르면 그 아래 주제 전부를 구독합니다.
        </p>
        {data.preferences.topics.length === 0 && (
          <p role="note">{NO_TOPIC_TEXT}</p>
        )}
        {data.tagSet.topics.map((category) => {
          const whole = topics.has(category.key);
          return (
            <fieldset key={category.key} className="space-y-1">
              <legend className="sr-only">{category.name}</legend>
              <CheckField
                label={category.name}
                checked={whole}
                onChange={(on) => setTopics(toggle(topics, category.key, on))}
              />
              <div className="grid grid-cols-2 gap-1 pl-6">
                {category.subtopics.map((subtopic) => (
                  <CheckField
                    key={subtopic.key}
                    label={subtopic.name}
                    checked={whole || topics.has(subtopic.key)}
                    disabled={whole}
                    onChange={(on) =>
                      setTopics(toggle(topics, subtopic.key, on))
                    }
                  />
                ))}
              </div>
            </fieldset>
          );
        })}
      </Section>
      <Section id="interest-regions" title="뺄 지역">
        <div className="grid grid-cols-2 gap-1">
          {data.tagSet.regions.map((region) => (
            <CheckField
              key={region.key}
              label={region.name}
              checked={regions.has(region.key)}
              onChange={(on) => setRegions(toggle(regions, region.key, on))}
            />
          ))}
        </div>
      </Section>
      <Section id="interest-formats" title="뺄 형식">
        <div className="grid grid-cols-2 gap-1">
          {data.tagSet.formats.map((format) => (
            <CheckField
              key={format.key}
              label={format.name}
              checked={formats.has(format.key)}
              onChange={(on) => setFormats(toggle(formats, format.key, on))}
            />
          ))}
        </div>
      </Section>
      <Button type="submit" disabled={busy}>
        주제 · 지역 · 형식 저장
      </Button>
    </form>
  );
}

function SourcesSection() {
  const sources = useInterestSources();
  const [message, setMessage] = useState<Message | null>(null);
  return (
    <Section id="interest-sources" title="뺄 소스">
      {sources.isPending && <p className="text-muted-foreground">읽는 중…</p>}
      {sources.isError && <p role="alert">소스를 읽지 못했습니다.</p>}
      {sources.data && (
        <SourcesForm
          key={JSON.stringify(sources.data.excluded)}
          data={sources.data}
          setMessage={setMessage}
        />
      )}
      <Notice message={message} />
    </Section>
  );
}

function SourcesForm({ data, setMessage }: FormProps<InterestSources>) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [excluded, setExcluded] = useState(() => new Set(data.excluded));
  const [busy, setBusy] = useState(false);

  function save(event: FormEvent) {
    event.preventDefault();
    void attempt(setBusy, setMessage, async () => {
      const { data: saved, response } = await client.PUT(
        "/api/interests/sources",
        { body: { excluded: [...excluded] } },
      );
      if (!response.ok || !saved) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      await queryClient.invalidateQueries({
        queryKey: INTEREST_SOURCES_QUERY_KEY,
      });
      return { tone: "status", text: "뺄 소스를 저장했습니다." };
    });
  }

  if (data.sources.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">고를 소스가 없습니다.</p>
    );
  }
  return (
    <form onSubmit={save} className="space-y-3">
      <div className="grid grid-cols-2 gap-1">
        {data.sources.map((source) => (
          <CheckField
            key={source.id}
            label={source.name ?? `이름 없는 소스 #${source.id}`}
            checked={excluded.has(source.id)}
            onChange={(on) => {
              const next = new Set(excluded);
              if (on) {
                next.add(source.id);
              } else {
                next.delete(source.id);
              }
              setExcluded(next);
            }}
          />
        ))}
      </div>
      <Button type="submit" disabled={busy}>
        뺄 소스 저장
      </Button>
    </form>
  );
}

function ScheduleSection() {
  const settings = useInterestSettings();
  const [message, setMessage] = useState<Message | null>(null);
  return (
    <Section id="interest-schedule" title="일정 · 변화 리포트">
      <p className="text-sm">
        <Link to={REPORTS_PATH} className="underline">
          {REPORTS_INTERESTS_LINK}
        </Link>
      </p>
      {settings.isPending && <p className="text-muted-foreground">읽는 중…</p>}
      {settings.isError && <p role="alert">설정을 읽지 못했습니다.</p>}
      {settings.data && (
        <ScheduleForm
          key={JSON.stringify(settings.data)}
          data={settings.data}
          setMessage={setMessage}
        />
      )}
      <Notice message={message} />
    </Section>
  );
}

const WEEKDAYS = Object.keys(weekdayNames) as Weekday[];

function ScheduleForm({ data, setMessage }: FormProps<InterestSettings>) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [timezone, setTimezone] = useState(data.timezone);
  const [digestTime, setDigestTime] = useState(data.digestTime);
  const [weeklyReport, setWeeklyReport] = useState(data.weeklyReport);
  const [weeklyReportDay, setWeeklyReportDay] = useState(data.weeklyReportDay);
  const [monthlyReport, setMonthlyReport] = useState(data.monthlyReport);
  const [monthlyReportDay, setMonthlyReportDay] = useState(
    data.monthlyReportDay,
  );
  const [busy, setBusy] = useState(false);
  const monthlyDays = Array.from(
    {
      length:
        data.monthlyReportDayRange.max - data.monthlyReportDayRange.min + 1,
    },
    (_, index) => data.monthlyReportDayRange.min + index,
  );

  function save(event: FormEvent) {
    event.preventDefault();
    void attempt(setBusy, setMessage, async () => {
      const { data: saved, response } = await client.PUT(
        "/api/interests/settings",
        {
          body: {
            timezone,
            digestTime,
            weeklyReport,
            weeklyReportDay,
            monthlyReport,
            monthlyReportDay,
          },
        },
      );
      if (!response.ok || !saved) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      switch (saved.result) {
        case "SAVED":
          await queryClient.invalidateQueries({
            queryKey: INTEREST_SETTINGS_QUERY_KEY,
          });
          return {
            tone: "status",
            text: "일정 · 리포트를 저장했습니다.",
          };
        case "UNKNOWN_TIMEZONE":
          return {
            tone: "alert",
            text: "모르는 시간대입니다. Asia/Seoul 같은 시간대 이름을 적어 주세요.",
          };
      }
      const unknown: never = saved.result;
      return {
        tone: "alert",
        text: `알 수 없는 결과입니다: ${String(unknown)}`,
      };
    });
  }

  return (
    <form onSubmit={save} className="max-w-sm space-y-3">
      <label className="block space-y-1">
        <span className="text-sm">시간대</span>
        <input
          name="timezone"
          required
          value={timezone}
          onChange={(event) => setTimezone(event.target.value)}
          className="w-full rounded-md border border-input bg-background px-2 py-1"
        />
      </label>
      <label className="block space-y-1">
        <span className="text-sm">다이제스트를 만드는 시각</span>
        <input
          type="time"
          name="digestTime"
          required
          step={60}
          value={digestTime}
          onChange={(event) => setDigestTime(event.target.value)}
          className="w-full rounded-md border border-input bg-background px-2 py-1"
        />
      </label>
      <CheckField
        label="주간 변화 리포트 받기"
        checked={weeklyReport}
        onChange={setWeeklyReport}
      />
      <label className="block space-y-1">
        <span className="text-sm">주간 리포트 요일</span>
        <select
          name="weeklyReportDay"
          value={weeklyReportDay}
          disabled={!weeklyReport}
          onChange={(event) =>
            setWeeklyReportDay(event.target.value as Weekday)
          }
          className="w-full rounded-md border border-input bg-background px-2 py-1"
        >
          {WEEKDAYS.map((day) => (
            <option key={day} value={day}>
              {weekdayNames[day]}
            </option>
          ))}
        </select>
      </label>
      <CheckField
        label="월간 변화 리포트 받기"
        checked={monthlyReport}
        disabled={!weeklyReport}
        onChange={setMonthlyReport}
      />
      {!weeklyReport && (
        <p className="text-sm text-muted-foreground">
          {MONTHLY_NEEDS_WEEKLY_TEXT}
        </p>
      )}
      <label className="block space-y-1">
        <span className="text-sm">월간 리포트 날짜</span>
        <select
          name="monthlyReportDay"
          value={monthlyReportDay}
          disabled={!weeklyReport || !monthlyReport}
          onChange={(event) => setMonthlyReportDay(Number(event.target.value))}
          className="w-full rounded-md border border-input bg-background px-2 py-1"
        >
          {monthlyDays.map((day) => (
            <option key={day} value={day}>
              {monthlyDayName(day)}
            </option>
          ))}
        </select>
      </label>
      <p className="text-sm text-muted-foreground">{MONTHLY_DAY_HELP_TEXT}</p>
      <p className="text-sm text-muted-foreground">
        {REPORT_SCHEDULE_HELP_TEXT}
      </p>
      <Button type="submit" disabled={busy}>
        일정 · 리포트 저장
      </Button>
    </form>
  );
}

function VolumeSection() {
  const volume = useInterestVolume();
  const [message, setMessage] = useState<Message | null>(null);
  return (
    <Section id="interest-volume" title="다이제스트 분량">
      {volume.isPending && <p className="text-muted-foreground">읽는 중…</p>}
      {volume.isError && <p role="alert">분량을 읽지 못했습니다.</p>}
      {volume.data && (
        <VolumeForm
          key={JSON.stringify(volume.data)}
          data={volume.data}
          setMessage={setMessage}
        />
      )}
      <Notice message={message} />
    </Section>
  );
}

function VolumeForm({ data, setMessage }: FormProps<InterestVolume>) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [volume, setVolume] = useState<CandidateVolume>(data.volume);
  const [customCount, setCustomCount] = useState(
    String(data.customCount ?? data.countRange.min),
  );
  const [bigSlots, setBigSlots] = useState(String(data.bigSlots));
  const [summaryLength, setSummaryLength] = useState<SummaryLength>(
    data.summaryLength,
  );
  const [busy, setBusy] = useState(false);
  const custom = volume === "CUSTOM";

  function save(event: FormEvent) {
    event.preventDefault();
    void attempt(setBusy, setMessage, async () => {
      const { data: saved, response } = await client.PUT(
        "/api/interests/volume",
        {
          body: {
            volume,
            customCount: custom ? Number(customCount) : null,
            bigSlots: Number(bigSlots),
            summaryLength,
          },
        },
      );
      if (!response.ok || !saved) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      switch (saved.result) {
        case "SAVED":
          await queryClient.invalidateQueries({
            queryKey: INTEREST_VOLUME_QUERY_KEY,
          });
          return { tone: "status", text: "다이제스트 분량을 저장했습니다." };
        case "COUNT_OUT_OF_RANGE": {
          const range = saved.range ?? data.countRange;
          return {
            tone: "alert",
            text: `직접 입력한 후보 수는 ${range.min} ~ ${range.max}건이어야 합니다.`,
          };
        }
        case "BIG_SLOTS_OUT_OF_RANGE": {
          const range = saved.range ?? data.bigSlotRange;
          return {
            tone: "alert",
            text: `큰 화제 수는 ${range.min} ~ ${range.max}개여야 합니다.`,
          };
        }
      }
      const unknown: never = saved.result;
      return {
        tone: "alert",
        text: `알 수 없는 결과입니다: ${String(unknown)}`,
      };
    });
  }

  return (
    <form onSubmit={save} className="max-w-sm space-y-4">
      <fieldset className="space-y-2" aria-describedby="candidate-volume-help">
        <legend className="text-sm">최대 후보 수</legend>
        {data.presets.map((preset) => (
          <label
            key={preset.volume}
            className="flex items-center gap-2 text-sm"
          >
            <input
              type="radio"
              name="candidateVolume"
              value={preset.volume}
              checked={volume === preset.volume}
              onChange={() => setVolume(preset.volume)}
            />
            {candidateVolumeNames[preset.volume]} ({preset.count}건)
          </label>
        ))}
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="candidateVolume"
            value="CUSTOM"
            checked={custom}
            onChange={() => setVolume("CUSTOM")}
          />
          {candidateVolumeNames.CUSTOM}
        </label>
        <label className="block space-y-1 pl-6">
          <span className="text-sm">
            {`직접 입력한 후보 수 (${data.countRange.min} ~ ${data.countRange.max}건)`}
          </span>
          <input
            type="number"
            name="customCount"
            required={custom}
            disabled={!custom}
            min={data.countRange.min}
            max={data.countRange.max}
            step={1}
            value={customCount}
            onChange={(event) => setCustomCount(event.target.value)}
            className="w-full rounded-md border border-input bg-background px-2 py-1"
          />
        </label>
        <p id="candidate-volume-help" className="text-sm text-muted-foreground">
          {CANDIDATE_VOLUME_HELP_TEXT}
        </p>
      </fieldset>
      <div className="space-y-1">
        <label className="block space-y-1">
          <span className="text-sm">
            {`큰 화제 수 (${data.bigSlotRange.min} ~ ${data.bigSlotRange.max}개)`}
          </span>
          <input
            type="number"
            name="bigSlots"
            required
            min={data.bigSlotRange.min}
            max={data.bigSlotRange.max}
            step={1}
            value={bigSlots}
            aria-describedby="big-slots-help"
            onChange={(event) => setBigSlots(event.target.value)}
            className="w-full rounded-md border border-input bg-background px-2 py-1"
          />
        </label>
        <p id="big-slots-help" className="text-sm text-muted-foreground">
          {BIG_SLOTS_HELP_TEXT} {BIG_SLOTS_OFF_TEXT}
        </p>
      </div>
      <fieldset className="space-y-2" aria-describedby="summary-length-help">
        <legend className="text-sm">요약 길이</legend>
        {data.summaryLengths.map((length) => (
          <label key={length} className="flex items-center gap-2 text-sm">
            <input
              type="radio"
              name="summaryLength"
              value={length}
              checked={summaryLength === length}
              onChange={() => setSummaryLength(length)}
            />
            {summaryLengthNames[length]}
          </label>
        ))}
        <p id="summary-length-help" className="text-sm text-muted-foreground">
          {SUMMARY_LENGTH_HELP_TEXT}
        </p>
      </fieldset>
      <Button type="submit" disabled={busy}>
        분량 저장
      </Button>
    </form>
  );
}

function ModelSection() {
  const model = useInterestModel();
  const [message, setMessage] = useState<Message | null>(null);
  return (
    <Section id="interest-model" title="모델">
      {model.isPending && <p className="text-muted-foreground">읽는 중…</p>}
      {model.isError && <p role="alert">모델을 읽지 못했습니다.</p>}
      {model.data && (
        <ModelForm
          key={JSON.stringify(model.data)}
          data={model.data}
          setMessage={setMessage}
        />
      )}
      {model.data && (
        <EffortForm
          key={`effort-${model.data.effort}`}
          data={model.data}
          setMessage={setMessage}
        />
      )}
      <Notice message={message} />
    </Section>
  );
}

/** 추론 강도 — 고른 모델 · 키와 무관하게 저장한다 */
function EffortForm({ data, setMessage }: FormProps<InterestModel>) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [effort, setEffort] = useState<ReasoningEffort>(data.effort);
  const [busy, setBusy] = useState(false);

  function save(event: FormEvent) {
    event.preventDefault();
    void attempt(setBusy, setMessage, async () => {
      const { data: saved, response } = await client.PUT(
        "/api/interests/effort",
        { body: { effort } },
      );
      if (!response.ok || !saved) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      await queryClient.invalidateQueries({
        queryKey: INTEREST_MODEL_QUERY_KEY,
      });
      return { tone: "status", text: "추론 강도를 저장했습니다." };
    });
  }

  return (
    <form onSubmit={save} className="max-w-sm space-y-3">
      <fieldset className="space-y-2" aria-describedby="reasoning-effort-help">
        <legend className="text-sm">추론 강도</legend>
        {data.efforts.map((value) => (
          <label key={value} className="flex items-center gap-2 text-sm">
            <input
              type="radio"
              name="reasoningEffort"
              value={value}
              checked={effort === value}
              onChange={() => setEffort(value)}
            />
            {reasoningEffortNames[value]}
          </label>
        ))}
        <p id="reasoning-effort-help" className="text-sm text-muted-foreground">
          {REASONING_EFFORT_HELP_TEXT}
        </p>
      </fieldset>
      <Button type="submit" disabled={busy}>
        추론 강도 저장
      </Button>
    </form>
  );
}

function modelValue(model: { provider: string; id: string }): string {
  return `${model.provider}:${model.id}`;
}

function ModelForm({ data, setMessage }: FormProps<InterestModel>) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [choice, setChoice] = useState(() =>
    data.chosen ? modelValue(data.chosen) : "",
  );
  const [busy, setBusy] = useState(false);

  if (data.choosable.length === 0) {
    return (
      <p className="text-sm">
        LLM 키를 등록하면 그 공급자의 모델을 고를 수 있습니다.{" "}
        <Link to="/keys" className="underline">
          LLM 키 등록하기
        </Link>
      </p>
    );
  }

  function save(event: FormEvent) {
    event.preventDefault();
    const picked = data.choosable.find((model) => modelValue(model) === choice);
    if (!picked) {
      setMessage({ tone: "alert", text: "모델을 골라 주세요." });
      return;
    }
    void attempt(setBusy, setMessage, async () => {
      const { data: saved, response } = await client.PUT(
        "/api/interests/model",
        { body: { provider: picked.provider, modelId: picked.id } },
      );
      if (!response.ok || !saved) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      switch (saved.result) {
        case "SAVED":
          await queryClient.invalidateQueries({
            queryKey: INTEREST_MODEL_QUERY_KEY,
          });
          return { tone: "status", text: "모델을 저장했습니다." };
        case "PROVIDER_WITHOUT_KEY":
          return {
            tone: "alert",
            text: "그 공급자에 등록한 키가 없습니다. 키를 먼저 등록해 주세요.",
          };
        case "MODEL_NOT_ALLOWED":
          await queryClient.invalidateQueries({
            queryKey: INTEREST_MODEL_QUERY_KEY,
          });
          return {
            tone: "alert",
            text: "이 인스턴스에서 고를 수 없는 모델입니다. 목록을 다시 읽었습니다.",
          };
      }
      const unknown: never = saved.result;
      return {
        tone: "alert",
        text: `알 수 없는 결과입니다: ${String(unknown)}`,
      };
    });
  }

  return (
    <form onSubmit={save} className="max-w-sm space-y-3">
      {data.reason !== "CHOSEN" && (
        <p role="note" className="text-sm">
          {modelReasonTexts[data.reason]}{" "}
          {data.effective
            ? `지금은 ${providers[data.effective.provider].name}의 ${data.effective.id} 모델을 씁니다.`
            : "지금은 쓸 모델이 없어 다이제스트를 만들지 못합니다."}
        </p>
      )}
      <label className="block space-y-1">
        <span className="text-sm">쓸 모델</span>
        <select
          name="model"
          value={choice}
          onChange={(event) => setChoice(event.target.value)}
          className="w-full rounded-md border border-input bg-background px-2 py-1"
        >
          <option value="">고르세요</option>
          {data.choosable.map((model) => (
            <option key={modelValue(model)} value={modelValue(model)}>
              {providers[model.provider].name} · {model.id}
            </option>
          ))}
        </select>
      </label>
      <Button type="submit" disabled={busy}>
        모델 저장
      </Button>
    </form>
  );
}
