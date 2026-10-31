import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { Link } from "react-router";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { profileStateLabels, refusalLabels } from "@/features/admin/labels";
import { FeedEnableResultView } from "@/features/admin/results";
import { Button } from "@/shared/ui/button";

type PresetResponse = Schemas["PresetResponse"];
type SourceResponse = Schemas["SourceResponse"];
type FeedResponse = Schemas["FeedResponse"];

const PRESETS_KEY = [...ADMIN_QUERY_KEY, "presets"];
const SOURCES_KEY = [...ADMIN_QUERY_KEY, "sources"];

/** 운영자 첫 화면 — 프리셋 목록과 등록된 소스 · 피드의 운영 설정 */
export function SourcesPage() {
  const client = useAdminClient();
  const presets = useQuery({
    queryKey: PRESETS_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/presets")),
  });
  const sources = useQuery({
    queryKey: SOURCES_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/sources")),
  });

  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h2 className="text-xl font-semibold">프리셋</h2>
        {presets.error && <p role="alert">{failureText(presets.error)}</p>}
        {presets.data && <PresetList presets={presets.data} />}
      </section>
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">소스</h2>
          <Link to="/admin/sources/new" className="underline">
            운영자 프로필 작성
          </Link>
        </div>
        {sources.error && <p role="alert">{failureText(sources.error)}</p>}
        {sources.data &&
          (sources.data.sources.length === 0 ? (
            <p className="text-muted-foreground">등록된 소스가 없습니다.</p>
          ) : (
            sources.data.sources.map((source) => (
              <SourceCard
                key={source.id}
                source={source}
                minimum={sources.data.minimumPollIntervalMinutes}
              />
            ))
          ))}
      </section>
    </div>
  );
}

function PresetList({ presets }: { presets: PresetResponse[] }) {
  const client = useAdminClient();
  const queryClient = useQueryClient();
  const register = useMutation({
    mutationFn: (key: string) =>
      unwrap(
        client.POST("/api/admin/presets/{key}/registration", {
          params: { path: { key } },
        }),
      ),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEY }),
  });

  return (
    <div className="space-y-2">
      <ul className="divide-y divide-border rounded-md border border-border">
        {presets.map((preset) => (
          <li
            key={preset.key}
            className="flex items-center justify-between gap-3 p-3"
          >
            <div>
              <p className="font-medium">
                {preset.name} <code className="text-sm">{preset.key}</code>
              </p>
              <p className="text-sm text-muted-foreground">
                피드 {preset.feedCount}개 · 이용조건 검토{" "}
                {preset.termsReviewedAt}
              </p>
            </div>
            {preset.sourceId !== null ? (
              <span className="text-sm">등록됨 (소스 {preset.sourceId})</span>
            ) : (
              <Button
                variant="outline"
                disabled={register.isPending}
                onClick={() => register.mutate(preset.key)}
              >
                등록
              </Button>
            )}
          </li>
        ))}
      </ul>
      {register.data?.result === "ALREADY_REGISTERED" && (
        <p role="alert">이미 등록된 프리셋입니다.</p>
      )}
      {register.error && <p role="alert">{failureText(register.error)}</p>}
    </div>
  );
}

function SourceCard({
  source,
  minimum,
}: {
  source: SourceResponse;
  minimum: number;
}) {
  const client = useAdminClient();
  const queryClient = useQueryClient();
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: SOURCES_KEY });
  const toggle = useMutation({
    mutationFn: async (enable: boolean) => {
      const params = { params: { path: { id: source.id } } };
      return enable
        ? unwrap(client.POST("/api/admin/sources/{id}/enable", params))
        : unwrap(client.POST("/api/admin/sources/{id}/disable", params)).then(
            () => undefined,
          );
    },
    onSettled: refresh,
  });

  return (
    <article className="space-y-3 rounded-md border border-border p-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            {source.name ?? `소스 ${source.id}`}{" "}
            <span className="text-sm font-normal text-muted-foreground">
              {source.presetKey !== null
                ? `프리셋 ${source.presetKey}`
                : "운영자 프로필"}
            </span>
          </h3>
          {source.profileState !== "AVAILABLE" && (
            <p className="text-sm text-destructive">
              {profileStateLabels[source.profileState]}
            </p>
          )}
          {source.termsReviewedAt === null &&
            source.profileState === "AVAILABLE" && (
              <p className="text-sm text-muted-foreground">
                이용조건 검토일이 없습니다
              </p>
            )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm">{source.enabled ? "켜짐" : "꺼짐"}</span>
          <Button
            variant="outline"
            disabled={toggle.isPending}
            onClick={() => toggle.mutate(!source.enabled)}
          >
            {toggle.isPending
              ? "처리하는 중…"
              : source.enabled
                ? "끄기"
                : "켜기"}
          </Button>
          {source.presetKey === null && (
            <Link
              to={`/admin/sources/${source.id}/profile`}
              className="underline"
            >
              원문 편집
            </Link>
          )}
        </div>
      </header>
      {toggle.data?.result === "REFUSED" && toggle.data.refusal && (
        <p role="alert">
          켜지 않았습니다 — {refusalLabels[toggle.data.refusal]}
        </p>
      )}
      {toggle.error && <p role="alert">{failureText(toggle.error)}</p>}
      <PollIntervalEditor
        label="소스 수집 주기"
        current={source.pollIntervalMinutes}
        minimum={minimum}
        save={(minutes) =>
          unwrap(
            client.PUT("/api/admin/sources/{id}/poll-interval", {
              params: { path: { id: source.id } },
              body: { minutes },
            }),
          )
        }
        onSaved={refresh}
      />
      <ul className="divide-y divide-border">
        {source.feeds.map((feed) => (
          <FeedRow key={feed.id} feed={feed} minimum={minimum} />
        ))}
      </ul>
    </article>
  );
}

function FeedRow({ feed, minimum }: { feed: FeedResponse; minimum: number }) {
  const client = useAdminClient();
  const queryClient = useQueryClient();
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: SOURCES_KEY });
  const params = { params: { path: { id: feed.id } } };
  const enable = useMutation({
    mutationFn: () =>
      unwrap(client.POST("/api/admin/feeds/{id}/enable", params)),
    onSettled: refresh,
  });
  const disable = useMutation({
    mutationFn: () =>
      unwrap(client.POST("/api/admin/feeds/{id}/disable", params)),
    onSettled: refresh,
  });
  const busy = enable.isPending || disable.isPending;

  return (
    <li className="space-y-2 py-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p>
            <code>{feed.feedKey}</code> {feed.section}{" "}
            <span className="text-sm">{feed.enabled ? "켜짐" : "꺼짐"}</span>
          </p>
          {feed.inProfile ? (
            <p className="text-sm break-all text-muted-foreground">
              {feed.url} · 실제 주기 {feed.effectivePollIntervalMinutes}분
            </p>
          ) : (
            <p className="text-sm text-destructive">
              프로필에 없는 피드입니다(수집하지 않습니다)
            </p>
          )}
        </div>
        <Button
          variant="outline"
          disabled={busy}
          onClick={() => (feed.enabled ? disable.mutate() : enable.mutate())}
        >
          {enable.isPending
            ? "받아서 확인하는 중…"
            : disable.isPending
              ? "끄는 중…"
              : feed.enabled
                ? "끄기"
                : "켜기"}
        </Button>
      </div>
      {enable.data && <FeedEnableResultView result={enable.data} />}
      {(enable.error ?? disable.error) && (
        <p role="alert">{failureText(enable.error ?? disable.error)}</p>
      )}
      <PollIntervalEditor
        label="피드 수집 주기"
        current={feed.pollIntervalMinutes}
        minimum={minimum}
        save={(minutes) =>
          unwrap(
            client.PUT("/api/admin/feeds/{id}/poll-interval", {
              params: { path: { id: feed.id } },
              body: { minutes },
            }),
          )
        }
        onSaved={refresh}
      />
    </li>
  );
}

/** 운영자 수집 주기 — 비우면 설정을 지운다. 하한은 서버가 알려 준 값을 안내만 한다(판정은 서버) */
function PollIntervalEditor({
  label,
  current,
  minimum,
  save,
  onSaved,
}: {
  label: string;
  current: number | null;
  minimum: number;
  save: (minutes: number | null) => Promise<unknown>;
  onSaved: () => void;
}) {
  const [value, setValue] = useState(current === null ? "" : String(current));
  const mutation = useMutation({
    mutationFn: (minutes: number | null) => save(minutes),
    onSuccess: onSaved,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate(value.trim() === "" ? null : Number(value));
  }

  return (
    <form
      onSubmit={submit}
      className="flex flex-wrap items-center gap-2 text-sm"
    >
      <label className="flex items-center gap-2">
        <span>{label}(분)</span>
        <input
          type="number"
          inputMode="numeric"
          value={value}
          placeholder="설정 없음"
          onChange={(event) => setValue(event.target.value)}
          className="w-24 rounded-md border border-input bg-background px-2 py-1"
        />
      </label>
      <Button
        type="submit"
        size="sm"
        variant="outline"
        disabled={mutation.isPending}
      >
        저장
      </Button>
      <span className="text-muted-foreground">
        {minimum}분 이상, 비우면 설정을 지웁니다
      </span>
      {mutation.error && (
        <span role="alert">{failureText(mutation.error)}</span>
      )}
    </form>
  );
}
