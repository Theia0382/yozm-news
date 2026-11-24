import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useId, useRef, useState } from "react";
import { Link } from "react-router";
import {
  type AdminClient,
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import {
  lineageChangeLabels,
  tagKindLabels,
  tagSetKindLabels,
  versionCreatorLabels,
} from "@/features/admin/labels";
import { TextProblems } from "@/features/admin/results";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { useTagSetVersions } from "@/features/admin/tagSetVersions";
import { textOffset } from "@/features/admin/textPosition";
import { formatDateTime } from "@/shared/lib/dateTime";
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

type Version = Schemas["TagSetVersionItem"];
type Review = Schemas["TagSetReviewResponse"];
type Activation = Schemas["TagSetActivationResponse"];
type Changes = Schemas["TagSetChanges"];
type TagRef = Schemas["TagRef"];

/** 편집기에 넣을 원문과 그 출처 버전 */
type Source = { number: number; text: string };

/** 편집 중인 원문 — 불러온 원문(loaded)과 다르면 고친 것이다 */
type Draft = { from: number; text: string; loaded: string };

/** 확인을 낸 입력 — 활성화는 이 입력 그대로 한다 */
type Asked = { text: string; days: number };

/** 버전 원문의 조회 키 — 버전 목록 키 아래에 두어 목록과 함께 다시 읽는다 */
function textKey(id: number) {
  return [...ADMIN_QUERY_KEY, "tag-sets", id, "text"];
}

function fetchText(client: AdminClient, id: number) {
  return unwrap(
    client.GET("/api/admin/tag-sets/{id}/text", {
      params: { path: { id } },
    }),
  );
}

function useVersionText(id: number | null) {
  const client = useAdminClient();
  return useQuery({
    queryKey: textKey(id ?? 0),
    queryFn: () => fetchText(client, id as number),
    enabled: id !== null,
  });
}

/**
 * 태그 체계 — 버전 목록과 버전 원문을 보고, 원문을 고쳐 확인한 뒤 새 버전으로 활성화한다. 편집기는 활성 버전의 원문에서 시작하고, 예전 버전의
 * 원문을 불러와 되돌리는 새 버전을 낼 수 있다. 고친 원문을 다른 버전의 원문으로 바꿀 때는 묻는다. 화면의 짜임과 끼어든 활성화를 알아차리는 방식은
 * ADR-0307 이 정한다.
 */
export function TagSetsPage() {
  const client = useAdminClient();
  const queries = useQueryClient();
  const versions = useTagSetVersions();
  const active = versions.data?.versions.find((item) => item.active) ?? null;
  const [viewing, setViewing] = useState<number | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  // 편집기를 새 원문으로 다시 그릴 때마다 는다 — 앞 원문의 확인 · 활성화 결과를 버린다
  const [generation, setGeneration] = useState(0);
  // 바꿀지 묻는 원문 — 대화상자가 닫히는 동안에도 문구가 그대로이게 열림과 따로 둔다
  const [replacing, setReplacing] = useState<Source | null>(null);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const activeText = useVersionText(
    draft === null && active ? active.id : null,
  );

  // 편집기의 처음 원문은 활성 버전의 원문이다 — 받은 렌더에서 한 번 맞춘다(효과를 거치지 않는다)
  if (draft === null && active && activeText.data) {
    setDraft({
      from: active.number,
      text: activeText.data.text,
      loaded: activeText.data.text,
    });
  }

  function start(source: Source) {
    setDraft({ from: source.number, text: source.text, loaded: source.text });
    setGeneration((value) => value + 1);
  }

  /** 고친 원문이 있으면 버릴지 묻는다 */
  function requestStart(source: Source) {
    if (draft !== null && draft.text !== draft.loaded) {
      setReplacing(source);
      setReplaceOpen(true);
      return;
    }
    start(source);
  }

  const restart = useMutation({
    mutationFn: (version: { id: number; number: number }) =>
      queries
        .fetchQuery({
          queryKey: textKey(version.id),
          queryFn: () => fetchText(client, version.id),
        })
        .then((text) => ({ number: version.number, text: text.text })),
    onSuccess: requestStart,
  });

  const viewed =
    versions.data?.versions.find((item) => item.id === viewing) ?? null;

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">태그 체계</h2>
      <p className="text-sm text-muted-foreground">
        평가가 기사에 붙이는 태그 체계의 버전과 원문이다. 원문을 고쳐 확인한 뒤
        새 버전으로 활성화한다 — 버전은 앞으로만 켜고, 예전 버전으로 돌아가려면
        그 버전의 원문을 불러와 새 버전으로 낸다.
      </p>
      {versions.isPending && (
        <p className="text-muted-foreground">버전 목록을 읽는 중…</p>
      )}
      {versions.error && <p role="alert">{failureText(versions.error)}</p>}
      {versions.data && (
        <VersionsView
          versions={versions.data.versions}
          viewing={viewing}
          onView={(id) => setViewing(id === viewing ? null : id)}
        />
      )}
      {viewed && (
        <VersionTextView
          key={viewed.id}
          version={viewed}
          onStart={requestStart}
          onClose={() => setViewing(null)}
        />
      )}

      {activeText.isPending && active && draft === null && (
        <p className="text-muted-foreground">활성 버전의 원문을 읽는 중…</p>
      )}
      {activeText.error && <p role="alert">{failureText(activeText.error)}</p>}
      {versions.data && draft && (
        <TagSetEditor
          key={generation}
          draft={draft}
          maxDays={versions.data.maxReevaluationDays}
          onText={(text) => setDraft((now) => now && { ...now, text })}
          onActivated={(text) =>
            setDraft((now) => now && { ...now, loaded: text })
          }
          onRestart={(version) => restart.mutate(version)}
          restarting={restart.isPending}
        />
      )}
      {restart.error && <p role="alert">{failureText(restart.error)}</p>}

      <AlertDialog open={replaceOpen} onOpenChange={setReplaceOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>고친 원문을 버릴까요?</AlertDialogTitle>
            <AlertDialogDescription>
              편집기의 원문을 버전 {replacing?.number} 원문으로 바꾸고, 확인 ·
              활성화 결과도 지운다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <Button
              variant="destructive"
              onClick={() => {
                setReplaceOpen(false);
                if (replacing) {
                  start(replacing);
                }
              }}
            >
              바꾸기
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/** 버전 목록 — 번호가 큰 것부터(서버 순서). 원문 보기는 다시 누르면 닫는다 */
function VersionsView({
  versions,
  viewing,
  onView,
}: {
  versions: Version[];
  viewing: number | null;
  onView: (id: number) => void;
}) {
  return (
    <section aria-label="버전" className="space-y-2">
      <h3 className="text-lg font-semibold">버전</h3>
      <table
        aria-label="버전 목록"
        className={`w-full text-left text-sm ${REVIEW_TABLE}`}
      >
        <thead>
          <tr>
            <th className="font-medium">번호</th>
            <th className="font-medium">활성</th>
            <th className="font-medium">만든 이</th>
            <th className="font-medium">만든 시각</th>
            <th className="font-medium">원문</th>
          </tr>
        </thead>
        <tbody>
          {versions.map((version) => (
            <tr key={version.id}>
              <td className="tabular-nums">{version.number}</td>
              <td>{version.active ? "활성" : ""}</td>
              <td>{versionCreatorLabels[version.createdBy]}</td>
              <td>{formatDateTime(version.createdAt)}</td>
              <td>
                <button
                  type="button"
                  aria-label={`버전 ${version.number} 원문 보기`}
                  aria-pressed={viewing === version.id}
                  className="underline aria-pressed:font-semibold"
                  onClick={() => onView(version.id)}
                >
                  원문 보기
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

/** 버전 하나의 원문 — 읽기 전용. 편집기로 불러와 새 버전의 출발점으로 쓴다 */
function VersionTextView({
  version,
  onStart,
  onClose,
}: {
  version: Version;
  onStart: (source: Source) => void;
  onClose: () => void;
}) {
  const text = useVersionText(version.id);
  const name = `버전 ${version.number} 원문`;
  return (
    <section aria-label={name} className="space-y-2">
      <h3 className="text-lg font-semibold">{name}</h3>
      <p className="text-sm text-muted-foreground">
        그 버전의 내용과 지금 태그 기준 · 저장 기준으로 만든 원문이다 — 계보
        절과 주석은 없다.
      </p>
      {text.isPending && (
        <p className="text-muted-foreground">원문을 읽는 중…</p>
      )}
      {text.error && <p role="alert">{failureText(text.error)}</p>}
      {text.data && (
        <textarea
          aria-label={name}
          value={text.data.text}
          readOnly
          rows={16}
          wrap="off"
          spellCheck={false}
          className="w-full rounded-md border border-input bg-muted p-2 font-mono text-sm"
        />
      )}
      <div className="flex gap-2">
        <Button
          variant="outline"
          disabled={!text.data}
          onClick={() =>
            text.data &&
            onStart({ number: version.number, text: text.data.text })
          }
        >
          편집기로 불러오기
        </Button>
        <Button variant="ghost" onClick={onClose}>
          닫기
        </Button>
      </div>
    </section>
  );
}

/** 재평가 기간 입력 — 0 이상 상한 이하의 정수만, 아니면 null */
function parseDays(value: string, max: number): number | null {
  if (!/^\d+$/.test(value.trim())) {
    return null;
  }
  const days = Number(value.trim());
  return days <= max ? days : null;
}

/**
 * 새 원문 편집 · 확인 · 활성화. 활성화는 마지막 확인이 활성화할 수 있다고 답한 원문 · 재평가 기간 그대로일 때만 할 수 있다. 확인 뒤 다른 활성화가
 * 끼어 활성화 결과가 확인과 다르면 알린다 — 활성화 결과가 정본이다.
 */
function TagSetEditor({
  draft,
  maxDays,
  onText,
  onActivated,
  onRestart,
  restarting,
}: {
  draft: Draft;
  maxDays: number;
  onText: (text: string) => void;
  onActivated: (text: string) => void;
  onRestart: (version: { id: number; number: number }) => void;
  restarting: boolean;
}) {
  const client = useAdminClient();
  const queries = useQueryClient();
  const daysId = useId();
  const editor = useRef<HTMLTextAreaElement>(null);
  const [daysInput, setDaysInput] = useState("0");
  const [asked, setAsked] = useState<Asked | null>(null);
  const [confirming, setConfirming] = useState(false);

  const review = useMutation({
    mutationFn: (request: Asked) =>
      unwrap(
        client.POST("/api/admin/tag-set-reviews", {
          body: { text: request.text, recentDays: request.days },
        }),
      ),
  });
  const activate = useMutation({
    mutationFn: (request: Asked) =>
      unwrap(
        client.POST("/api/admin/tag-sets", {
          body: { text: request.text, recentDays: request.days },
        }),
      ),
    onSuccess: (result, request) => {
      if (result.result === "ACTIVATED") {
        onActivated(request.text);
      }
      // 활성화가 아니어도 그사이 다른 활성화가 있었을 수 있다 — 목록 · 기준 · 평가 화면을 다시 읽는다
      return queries.invalidateQueries({ queryKey: ADMIN_QUERY_KEY });
    },
  });

  const days = parseDays(daysInput, maxDays);
  const current: Asked | null =
    days === null ? null : { text: draft.text, days };
  const fresh =
    asked !== null &&
    current !== null &&
    asked.text === current.text &&
    asked.days === current.days;
  const ready =
    fresh &&
    !review.isPending &&
    review.data?.result === "READY" &&
    !activate.isPending &&
    !activate.isSuccess;
  const locked = activate.isPending;

  /** 원문의 줄:열로 간다 — 줄이 화면 가운데쯤 오게 굴린다 */
  function locate(line: number, column: number) {
    const element = editor.current;
    if (!element) {
      return;
    }
    const offset = textOffset(element.value, line, column);
    element.focus();
    element.setSelectionRange(offset, offset);
    const lineHeight = Number.parseFloat(getComputedStyle(element).lineHeight);
    if (Number.isFinite(lineHeight)) {
      element.scrollTop = Math.max(
        0,
        (line - 1) * lineHeight - element.clientHeight / 2,
      );
    }
  }

  return (
    <section aria-label="새 버전 만들기" className="space-y-3">
      <h3 className="text-lg font-semibold">새 버전 만들기</h3>
      <p className="text-sm text-muted-foreground">
        버전 {draft.from} 원문에서 시작했다. 키를 바꾸거나 나누거나 합쳤으면
        원문 끝에 계보 절(lineage)을 적는다 — 계보 없이 지운 키를 선호하던
        사용자는 선호가 해제된다. 확인은 지금 활성 버전과 대조할 뿐 아무것도
        바꾸지 않는다.
      </p>
      <label className="block space-y-1">
        <span className="text-sm">새 원문(YAML)</span>
        <textarea
          ref={editor}
          value={draft.text}
          readOnly={locked}
          onChange={(event) => onText(event.target.value)}
          rows={24}
          wrap="off"
          spellCheck={false}
          className="w-full rounded-md border border-input bg-background p-2 font-mono text-sm"
        />
      </label>
      <div className="space-y-1 text-sm">
        <label htmlFor={daysId} className="block">
          재평가 기간(일)
        </label>
        <input
          id={daysId}
          type="number"
          inputMode="numeric"
          min={0}
          max={maxDays}
          step={1}
          value={daysInput}
          readOnly={locked}
          onChange={(event) => setDaysInput(event.target.value)}
          className="w-24 rounded-md border border-input px-2 py-1"
        />
        <p className="text-muted-foreground">
          활성화 시각에서 이 일수 안에 처음 본 기사를 새 버전으로 다시 평가한다
          — 0 이면 재평가하지 않고, 이전 기사는 이전 버전의 결과를 유지한다.
        </p>
        {days === null && (
          <p className="text-destructive">
            재평가 기간은 0일에서 {maxDays}일까지의 정수다
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          disabled={current === null || review.isPending || locked}
          onClick={() => {
            if (current === null) {
              return;
            }
            setAsked(current);
            activate.reset();
            review.mutate(current);
          }}
        >
          {review.isPending ? "확인하는 중…" : "확인"}
        </Button>
        <Button disabled={!ready} onClick={() => setConfirming(true)}>
          {activate.isPending ? "활성화하는 중…" : "활성화"}
        </Button>
      </div>
      {review.error && <p role="alert">{failureText(review.error)}</p>}
      {review.data && asked && (
        <ReviewView
          review={review.data}
          asked={asked}
          stale={!fresh}
          onLocate={locate}
        />
      )}
      {activate.error && <p role="alert">{failureText(activate.error)}</p>}
      {activate.data && (
        <ActivationView
          activation={activate.data}
          review={review.data ?? null}
          onLocate={locate}
          onRestart={onRestart}
          restarting={restarting}
        />
      )}

      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>새 버전으로 활성화할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              확인한 원문으로 다음 번호의 버전을 만들어 곧바로 활성으로 바꾼다.
              버전은 끌 수 없다 — 돌아가려면 예전 버전의 원문으로 새 버전을 다시
              만든다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {review.data?.result === "READY" && asked && (
            <ActivationCosts review={review.data} days={asked.days} />
          )}
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <Button
              onClick={() => {
                setConfirming(false);
                if (asked) {
                  activate.mutate(asked);
                }
              }}
            >
              활성화
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}

/** 활성화가 사용자 · 평가에 미치는 것 — 확인 대화상자에 둔다 */
function ActivationCosts({ review, days }: { review: Review; days: number }) {
  const removed = review.changes?.removed.length ?? 0;
  if (removed === 0 && days === 0) {
    return null;
  }
  return (
    <ul className="list-disc space-y-1 pl-5 text-sm">
      {removed > 0 && (
        <li>
          계보 없이 지운 키 {removed}개 — 그 키를 선호하던 사용자의 선호가
          해제된다
        </li>
      )}
      {days > 0 && (
        <li>
          재평가 기간 {days}일 — 기사{" "}
          {(review.recentArticles ?? 0).toLocaleString("ko-KR")}건을 다시
          평가한다
        </li>
      )}
    </ul>
  );
}

function ReviewView({
  review,
  asked,
  stale,
  onLocate,
}: {
  review: Review;
  asked: Asked;
  stale: boolean;
  onLocate: (line: number, column: number) => void;
}) {
  return (
    <section aria-label="확인 결과" className="space-y-2 text-sm">
      <h4 className="font-semibold">확인 결과</h4>
      {stale && (
        <p className="text-muted-foreground">
          확인한 뒤 원문 · 재평가 기간이 바뀌었다 — 아래는 앞의 입력의 결과다.
          다시 확인해야 활성화할 수 있다
        </p>
      )}
      {review.result === "READY" && review.changes && (
        <>
          <p>활성화할 수 있다 — 지금 활성 버전과의 차이:</p>
          <ChangesView changes={review.changes} />
          <p>
            {asked.days === 0
              ? "재평가 기간 0일 — 재평가하지 않는다"
              : `재평가 기간 ${asked.days}일에 처음 본 기사 ${(review.recentArticles ?? 0).toLocaleString("ko-KR")}건 — 활성화하면 새 버전으로 다시 평가한다. 그동안 새 기사의 평가가 늦어지고 기사마다 평가 비용이 든다`}
          </p>
        </>
      )}
      {review.result === "UNCHANGED" && (
        <p>
          지금 활성 버전과 내용이 같다 — 활성화할 것이 없다. 원문의 태그 기준 ·
          저장 기준만 다른 원문도 같은 내용이다 — 그 값은{" "}
          <Link to="/admin/thresholds" className="underline">
            태그 기준
          </Link>{" "}
          화면에서 바꾼다
        </p>
      )}
      {review.result === "INVALID_TAG_SET" && (
        <>
          <p role="alert">
            원문에 문제가 {review.problems.length}개 있다 — 줄:열을 누르면
            원문의 그 자리로 간다
          </p>
          <TextProblems problems={review.problems} onLocate={onLocate} />
        </>
      )}
    </section>
  );
}

/**
 * 활성화 결과가 확인 결과와 같은가 — 판별자와 변경 목록 · 문제를 견준다. 다른 활성화가 끼어 기준 버전이 바뀌면 다르게 나온다. 두 응답은 같은
 * 서버가 같은 모양으로 쓰므로 JSON 으로 견준다.
 */
function matchesReview(review: Review, activation: Activation): boolean {
  switch (activation.result) {
    case "ACTIVATED":
      return (
        review.result === "READY" &&
        JSON.stringify(review.changes) === JSON.stringify(activation.changes)
      );
    case "UNCHANGED":
      return review.result === "UNCHANGED";
    case "INVALID_TAG_SET":
      return (
        review.result === "INVALID_TAG_SET" &&
        JSON.stringify(review.problems) === JSON.stringify(activation.problems)
      );
  }
}

function ActivationView({
  activation,
  review,
  onLocate,
  onRestart,
  restarting,
}: {
  activation: Activation;
  review: Review | null;
  onLocate: (line: number, column: number) => void;
  onRestart: (version: { id: number; number: number }) => void;
  restarting: boolean;
}) {
  const interfered = review !== null && !matchesReview(review, activation);
  return (
    <section aria-label="활성화 결과" className="space-y-2 text-sm">
      <h4 className="font-semibold">활성화 결과</h4>
      {interfered && (
        <p role="alert" className="text-destructive">
          확인 뒤 다른 활성화가 끼어 결과가 확인과 다르다 — 아래 활성화 결과가
          정본이다
        </p>
      )}
      {activation.result === "ACTIVATED" && (
        <>
          <p role="status">
            활성화했다 — 새 버전 {activation.number}(ID {activation.id}). 재평가
            대기에 넣거나 실패에서 되돌린 기사{" "}
            {(activation.requeued ?? 0).toLocaleString("ko-KR")}건(이미 대기
            중인 기사는 세지 않는다)
          </p>
          {interfered && activation.changes ? (
            <>
              <p>지금 활성이던 버전과의 차이:</p>
              <ChangesView changes={activation.changes} />
            </>
          ) : (
            <p>변경 목록은 확인 결과와 같다.</p>
          )}
          <p>
            계보 절은 이 버전에만 속한다 — 다음 원문은 새 버전의 원문(계보 절
            없음)에서 시작한다.
          </p>
          {activation.id !== null && activation.number !== null && (
            <Button
              variant="outline"
              disabled={restarting}
              onClick={() =>
                onRestart({
                  id: activation.id as number,
                  number: activation.number as number,
                })
              }
            >
              버전 {activation.number} 원문으로 다시 시작
            </Button>
          )}
        </>
      )}
      {activation.result === "UNCHANGED" && (
        <p>활성 버전과 내용이 같아 아무것도 하지 않았다.</p>
      )}
      {activation.result === "INVALID_TAG_SET" && (
        <>
          <p>원문에 문제가 있어 아무것도 하지 않았다.</p>
          <TextProblems problems={activation.problems} onLocate={onLocate} />
        </>
      )}
    </section>
  );
}

/** 기준 버전과의 변경 목록 — 빈 갈래도 "없음"으로 보인다 */
function ChangesView({ changes }: { changes: Changes }) {
  const empty =
    changes.added.length === 0 &&
    changes.removed.length === 0 &&
    changes.revised.length === 0 &&
    changes.lineage.length === 0 &&
    !changes.questionsRevised &&
    !changes.importanceRevised &&
    changes.thresholds.length === 0;
  return (
    <div className="space-y-2">
      {empty && <p>순서만 바뀌었다 — 바뀐 키 · 문구가 없다</p>}
      <dl
        aria-label="변경 목록"
        className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1"
      >
        <dt>더한 키</dt>
        <dd>
          <TagRefs refs={changes.added} />
        </dd>
        <dt>계보 없이 지운 키</dt>
        <dd>
          <TagRefs refs={changes.removed} />
          {changes.removed.length > 0 && (
            <p className="text-destructive">
              이 키를 선호하던 사용자는 선호가 해제되고 알림을 받는다 — 이름을
              바꾸거나 나누거나 합친 키면 계보 절에 적는다
            </p>
          )}
        </dd>
        <dt>고친 키(이름 · 설명 · 기본 제외)</dt>
        <dd>
          <TagRefs refs={changes.revised} />
        </dd>
        <dt>계보</dt>
        <dd>
          {changes.lineage.length === 0 ? (
            "없음"
          ) : (
            <ul>
              {changes.lineage.map((item, index) => (
                <li key={index}>
                  {tagSetKindLabels[item.kind]}{" "}
                  {lineageChangeLabels[item.change]}:{" "}
                  <code>{item.from.join(", ")}</code> →{" "}
                  <code>{item.to.join(", ")}</code>
                </li>
              ))}
            </ul>
          )}
        </dd>
        <dt>문구</dt>
        <dd>
          질문 {changes.questionsRevised ? "바뀜" : "그대로"} · 중요도 기준{" "}
          {changes.importanceRevised ? "바뀜" : "그대로"}
        </dd>
        <dt>태그 기준 · 저장 기준</dt>
        <dd>
          {changes.thresholds.length === 0 ? (
            "지금 값과 같다"
          ) : (
            <ul>
              {changes.thresholds.map((item) => (
                <li key={item.proposed.kind}>
                  {tagKindLabels[item.proposed.kind]} — 태그 기준{" "}
                  {item.current.tagThreshold} → {item.proposed.tagThreshold} ·
                  저장 기준 {item.current.storeThreshold} →{" "}
                  {item.proposed.storeThreshold}
                </li>
              ))}
              <li className="text-muted-foreground">
                원문의 값이 새 버전의 처음 값이 된다
              </li>
            </ul>
          )}
        </dd>
      </dl>
    </div>
  );
}

/** 태그 키들 — 종류 이름과 키, 없으면 "없음" */
function TagRefs({ refs }: { refs: TagRef[] }) {
  if (refs.length === 0) {
    return <>없음</>;
  }
  return (
    <ul className="flex flex-wrap gap-x-3">
      {refs.map((ref) => (
        <li key={`${ref.kind}:${ref.key}`}>
          {tagSetKindLabels[ref.kind]} <code>{ref.key}</code>
        </li>
      ))}
    </ul>
  );
}
