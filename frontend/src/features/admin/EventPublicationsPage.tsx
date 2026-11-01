import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import {
  discardOutcomeLabels,
  resubmissionOutcomeLabels,
  strandedReasonLabels,
} from "@/features/admin/labels";
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
import { Checkbox } from "@/shared/ui/checkbox";

type Listing = Schemas["StrandedPublicationListResponse"];
type Publication = Schemas["StrandedPublicationResponse"];

const PUBLICATIONS_KEY = [...ADMIN_QUERY_KEY, "event-publications"];

/** 남겨진 이벤트 발행 — 재처리가 더 이상 다루지 않는 발행을 보고 다시 처리하거나 폐기한다 */
export function EventPublicationsPage() {
  const client = useAdminClient();
  const listing = useQuery({
    queryKey: PUBLICATIONS_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/event-publications")),
  });

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold">이벤트 발행</h2>
      <Explanation />
      {listing.error && <p role="alert">{failureText(listing.error)}</p>}
      {listing.data && (
        <ListingView
          listing={listing.data}
          refreshing={listing.isFetching}
          onRefresh={() => void listing.refetch()}
        />
      )}
    </div>
  );
}

function Explanation() {
  return (
    <section aria-label="남겨진 발행의 뜻" className="space-y-1 text-sm">
      <p>
        모듈 사이 이벤트 가운데 재처리가 더 이상 다루지 않는 발행입니다 — 처리
        시도가 상한에 이른 실패 발행과, 이벤트 종류를 찾을 수 없어 재처리가 읽지
        못하는 발행.
      </p>
      <p>
        다시 처리는 고른 발행을 지금 한 번 다시 보냅니다(처리 시도가 1 늡니다).
        처리는 뒤에서 이어지므로 결과는 잠시 뒤 새로고침해 확인합니다 — 성공한
        발행은 목록에서 사라지고, 다시 실패한 발행은 남습니다.
      </p>
      <p>
        폐기는 고른 발행을 지웁니다. 되돌릴 수 없고, 그 이벤트는 다시 처리되지
        않습니다.
      </p>
    </section>
  );
}

function ListingView({
  listing,
  refreshing,
  onRefresh,
}: {
  listing: Listing;
  refreshing: boolean;
  onRefresh: () => void;
}) {
  const client = useAdminClient();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [confirming, setConfirming] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: PUBLICATIONS_KEY });
  const resubmit = useMutation({
    mutationFn: (ids: string[]) =>
      unwrap(
        client.POST("/api/admin/event-publications/resubmission", {
          body: { ids },
        }),
      ),
    onSuccess: (response) => {
      setNotice(
        `다시 처리 결과: ${summarize(response.results, resubmissionOutcomeLabels)} — 처리는 뒤에서 이어지므로 잠시 뒤 새로고침해 확인합니다`,
      );
      setSelected(new Set());
    },
    onSettled: refresh,
  });
  const discard = useMutation({
    mutationFn: (ids: string[]) =>
      unwrap(
        client.POST("/api/admin/event-publications/discard", {
          body: { ids },
        }),
      ),
    onSuccess: (response) => {
      setNotice(
        `폐기 결과: ${summarize(response.results, discardOutcomeLabels)}`,
      );
      setSelected(new Set());
    },
    onSettled: refresh,
  });

  const publications = listing.publications;
  const chosen = publications
    .filter((publication) => selected.has(publication.id))
    .map((publication) => publication.id);
  const pending = resubmit.isPending || discard.isPending;
  const allChosen =
    publications.length > 0 && chosen.length === publications.length;

  /** 새 조작 앞에서 지난 조작의 결과 · 거절을 지운다 */
  function clearOutcome() {
    setNotice(null);
    resubmit.reset();
    discard.reset();
  }

  function toggle(id: string, checked: boolean) {
    const next = new Set(selected);
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    setSelected(next);
  }

  function toggleAll(checked: boolean) {
    setSelected(
      checked
        ? new Set(publications.map((publication) => publication.id))
        : new Set(),
    );
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button
          variant="outline"
          disabled={chosen.length === 0 || pending}
          onClick={() => {
            clearOutcome();
            resubmit.mutate(chosen);
          }}
        >
          {resubmit.isPending ? "다시 처리하는 중…" : "다시 처리"}
        </Button>
        <Button
          variant="destructive"
          disabled={chosen.length === 0 || pending}
          onClick={() => setConfirming(true)}
        >
          {discard.isPending ? "폐기하는 중…" : "폐기"}
        </Button>
        <Button variant="ghost" disabled={refreshing} onClick={onRefresh}>
          새로고침
        </Button>
        <span className="text-sm text-muted-foreground">
          {chosen.length}건 고름
        </span>
      </div>
      {notice && <p role="status">{notice}</p>}
      {resubmit.error && <p role="alert">{failureText(resubmit.error)}</p>}
      {discard.error && <p role="alert">{failureText(discard.error)}</p>}
      {listing.total > publications.length && (
        <p className="text-sm">
          남겨진 발행 {listing.total}건 가운데 오래된 {listing.limit}건만
          보입니다 — 처리한 뒤 새로고침하면 다음 발행이 보입니다.
        </p>
      )}
      {publications.length === 0 ? (
        <p className="text-muted-foreground">남겨진 발행이 없습니다.</p>
      ) : (
        <PublicationTable
          publications={publications}
          selected={selected}
          allChosen={allChosen}
          someChosen={chosen.length > 0 && !allChosen}
          onToggle={toggle}
          onToggleAll={toggleAll}
        />
      )}
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              발행 {chosen.length}건을 폐기할까요?
            </AlertDialogTitle>
            <AlertDialogDescription>
              지운 발행은 되돌릴 수 없고, 그 이벤트는 다시 처리되지 않습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <Button
              variant="destructive"
              onClick={() => {
                setConfirming(false);
                clearOutcome();
                discard.mutate(chosen);
              }}
            >
              폐기
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}

function PublicationTable({
  publications,
  selected,
  allChosen,
  someChosen,
  onToggle,
  onToggleAll,
}: {
  publications: Publication[];
  selected: ReadonlySet<string>;
  allChosen: boolean;
  someChosen: boolean;
  onToggle: (id: string, checked: boolean) => void;
  onToggleAll: (checked: boolean) => void;
}) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border text-left">
          <th className="p-2">
            <Checkbox
              aria-label="모두 고르기"
              checked={allChosen}
              indeterminate={someChosen}
              onCheckedChange={(checked) => onToggleAll(checked)}
            />
          </th>
          <th className="p-2">까닭</th>
          <th className="p-2">이벤트 종류</th>
          <th className="p-2">리스너</th>
          <th className="p-2">발행 시각</th>
          <th className="p-2">처리 시도</th>
          <th className="p-2">마지막 재제출</th>
        </tr>
      </thead>
      <tbody>
        {publications.map((publication) => (
          <tr key={publication.id} className="border-b border-border align-top">
            <td className="p-2">
              <Checkbox
                aria-label={`발행 ${publication.id} 고르기`}
                checked={selected.has(publication.id)}
                onCheckedChange={(checked) => onToggle(publication.id, checked)}
              />
            </td>
            <td className="p-2">{strandedReasonLabels[publication.reason]}</td>
            <td className="p-2 break-all">{publication.eventType}</td>
            <td className="p-2 text-xs break-all">{publication.listenerId}</td>
            <td className="p-2 whitespace-nowrap">
              {formatDateTime(publication.publishedAt)}
            </td>
            <td className="p-2 text-right">{publication.completionAttempts}</td>
            <td className="p-2 whitespace-nowrap">
              {publication.lastResubmittedAt === null
                ? "없음"
                : formatDateTime(publication.lastResubmittedAt)}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** 결과를 판별자마다 건수로 — 판별자 순서는 문구 표의 순서 */
function summarize<Outcome extends string>(
  results: { result: Outcome }[],
  labels: Record<Outcome, string>,
): string {
  if (results.length === 0) {
    return "고른 발행이 없습니다";
  }
  return (Object.keys(labels) as Outcome[])
    .map((outcome) => ({
      label: labels[outcome],
      count: results.filter((item) => item.result === outcome).length,
    }))
    .filter(({ count }) => count > 0)
    .map(({ label, count }) => `${label} ${count}건`)
    .join(" · ");
}
