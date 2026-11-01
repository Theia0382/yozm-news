import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { feedRemovalRefusalLabels, sourceName } from "@/features/admin/labels";
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

// 소스 · 피드의 숨기기 · 다시 보이기 · 삭제. 숨기기는 기사를 남기고 되돌릴 수 있고, 삭제는 그 소스(피드)만 가진 기사까지 지우며
// 되돌릴 수 없다 — 두 기능의 차이는 이 대화상자 문구가 정본이다.

type SourceResponse = Schemas["SourceResponse"];
type FeedResponse = Schemas["FeedResponse"];
type FeedRemovalResponse = Schemas["FeedRemovalResponse"];

const HIDE_SOURCE_TEXT =
  "목록에서 숨기고 수집을 멈춥니다. 받은 기사는 그대로 남고, 숨긴 소스에서 다시 보일 수 있습니다.";
const DELETE_SOURCE_TEXT =
  "되돌릴 수 없습니다. 이 소스만 가진 기사가 지워지고 모든 사용자의 기록에서 빠집니다. 다른 소스도 가진 기사는 남습니다.";
const HIDE_FEED_TEXT =
  "목록에서 숨기고 수집을 멈춥니다. 받은 기사는 그대로 남고, 숨긴 피드에서 다시 보일 수 있습니다.";
const DELETE_FEED_TEXT =
  "되돌릴 수 없습니다. 이 피드만 가진 기사가 지워지고 모든 사용자의 기록에서 빠집니다. 다른 피드도 가진 기사는 남습니다.";

/** 목록의 소스 카드에 붙는 숨기기 · 삭제 */
export function SourceRemovalActions({ source }: { source: SourceResponse }) {
  const client = useAdminClient();
  const refresh = useRefreshAdmin();
  const [dialog, setDialog] = useState<"hide" | "delete" | null>(null);
  const params = { params: { path: { id: source.id } } };
  const hide = useMutation({
    mutationFn: () =>
      unwrap(client.POST("/api/admin/sources/{id}/hide", params)),
    onSettled: refresh,
  });
  const remove = useDeleteSource(source.id);
  const busy = hide.isPending || remove.isPending;

  return (
    <>
      <Button
        variant="outline"
        disabled={busy}
        onClick={() => {
          remove.reset();
          setDialog("hide");
        }}
      >
        {hide.isPending ? "숨기는 중…" : "숨기기"}
      </Button>
      <Button
        variant="destructive"
        disabled={busy}
        onClick={() => {
          hide.reset();
          setDialog("delete");
        }}
      >
        {remove.isPending ? "삭제하는 중…" : "삭제"}
      </Button>
      <ConfirmDialog
        open={dialog === "hide"}
        onClose={() => setDialog(null)}
        title={`${sourceName(source)}을(를) 숨길까요?`}
        confirmLabel="숨기기"
        destructive={false}
        onConfirm={() => hide.mutate()}
      >
        <p>{HIDE_SOURCE_TEXT}</p>
      </ConfirmDialog>
      <DeleteSourceDialog
        source={source}
        open={dialog === "delete"}
        onClose={() => setDialog(null)}
        onConfirm={() => remove.mutate()}
      />
      {(hide.error ?? remove.error) && (
        <p role="alert" className="basis-full">
          {failureText(hide.error ?? remove.error)}
        </p>
      )}
    </>
  );
}

/** 숨긴 소스 한 줄 — 다시 보이기 · 삭제 */
export function HiddenSourceItem({ source }: { source: SourceResponse }) {
  const client = useAdminClient();
  const refresh = useRefreshAdmin();
  const [deleting, setDeleting] = useState(false);
  const unhide = useMutation({
    mutationFn: () =>
      unwrap(
        client.POST("/api/admin/sources/{id}/unhide", {
          params: { path: { id: source.id } },
        }),
      ),
    onSettled: refresh,
  });
  const remove = useDeleteSource(source.id);
  const busy = unhide.isPending || remove.isPending;

  return (
    <li className="space-y-2 p-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="font-medium">
          {sourceName(source)}{" "}
          <span className="text-sm font-normal text-muted-foreground">
            {source.presetKey !== null
              ? `프리셋 ${source.presetKey}`
              : "운영자 프로필"}
          </span>
        </p>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            disabled={busy}
            onClick={() => {
              remove.reset();
              unhide.mutate();
            }}
          >
            {unhide.isPending ? "처리하는 중…" : "다시 보이기"}
          </Button>
          <Button
            variant="destructive"
            disabled={busy}
            onClick={() => {
              unhide.reset();
              setDeleting(true);
            }}
          >
            {remove.isPending ? "삭제하는 중…" : "삭제"}
          </Button>
        </div>
      </div>
      <DeleteSourceDialog
        source={source}
        open={deleting}
        onClose={() => setDeleting(false)}
        onConfirm={() => remove.mutate()}
      />
      {(unhide.error ?? remove.error) && (
        <p role="alert">{failureText(unhide.error ?? remove.error)}</p>
      )}
    </li>
  );
}

/** 프로필에 없는 피드의 숨기기 · 삭제 */
export function FeedRemovalActions({
  sourceId,
  feed,
}: {
  sourceId: number;
  feed: FeedResponse;
}) {
  const hide = useFeedRemoval(feed.id, "hide");
  const remove = useFeedRemoval(feed.id, "delete");
  const [dialog, setDialog] = useState<"hide" | "delete" | null>(null);
  const busy = hide.isPending || remove.isPending;

  return (
    <>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          disabled={busy}
          onClick={() => {
            remove.reset();
            setDialog("hide");
          }}
        >
          {hide.isPending ? "숨기는 중…" : "숨기기"}
        </Button>
        <Button
          variant="destructive"
          disabled={busy}
          onClick={() => {
            hide.reset();
            setDialog("delete");
          }}
        >
          {remove.isPending ? "삭제하는 중…" : "삭제"}
        </Button>
      </div>
      <ConfirmDialog
        open={dialog === "hide"}
        onClose={() => setDialog(null)}
        title={`피드 ${feed.feedKey}을(를) 숨길까요?`}
        confirmLabel="숨기기"
        destructive={false}
        onConfirm={() => hide.mutate()}
      >
        <p>{HIDE_FEED_TEXT}</p>
      </ConfirmDialog>
      <DeleteFeedDialog
        sourceId={sourceId}
        feed={feed}
        open={dialog === "delete"}
        onClose={() => setDialog(null)}
        onConfirm={() => remove.mutate()}
      />
      <FeedRemovalOutcome mutation={hide} verb="숨기지" />
      <FeedRemovalOutcome mutation={remove} verb="지우지" />
    </>
  );
}

/** 숨긴 피드 한 줄 — 다시 보이기 · 삭제 */
export function HiddenFeedItem({
  sourceId,
  feed,
}: {
  sourceId: number;
  feed: FeedResponse;
}) {
  const client = useAdminClient();
  const refresh = useRefreshAdmin();
  const [deleting, setDeleting] = useState(false);
  const unhide = useMutation({
    mutationFn: () =>
      unwrap(
        client.POST("/api/admin/feeds/{id}/unhide", {
          params: { path: { id: feed.id } },
        }),
      ),
    onSettled: refresh,
  });
  const remove = useFeedRemoval(feed.id, "delete");
  const busy = unhide.isPending || remove.isPending;

  return (
    <li className="space-y-2 py-2">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p>
          <code>{feed.feedKey}</code>{" "}
          <span className="text-sm text-muted-foreground">숨김</span>
        </p>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            disabled={busy}
            onClick={() => {
              remove.reset();
              unhide.mutate();
            }}
          >
            {unhide.isPending ? "처리하는 중…" : "다시 보이기"}
          </Button>
          <Button
            variant="destructive"
            disabled={busy}
            onClick={() => {
              unhide.reset();
              setDeleting(true);
            }}
          >
            {remove.isPending ? "삭제하는 중…" : "삭제"}
          </Button>
        </div>
      </div>
      <DeleteFeedDialog
        sourceId={sourceId}
        feed={feed}
        open={deleting}
        onClose={() => setDeleting(false)}
        onConfirm={() => remove.mutate()}
      />
      {unhide.error && <p role="alert">{failureText(unhide.error)}</p>}
      <FeedRemovalOutcome mutation={remove} verb="지우지" />
    </li>
  );
}

/** 프리셋 목록 — 숨긴 소스가 있는 프리셋의 다시 보이기 */
export function UnhidePresetSource({ sourceId }: { sourceId: number }) {
  const client = useAdminClient();
  const refresh = useRefreshAdmin();
  const unhide = useMutation({
    mutationFn: () =>
      unwrap(
        client.POST("/api/admin/sources/{id}/unhide", {
          params: { path: { id: sourceId } },
        }),
      ),
    onSettled: refresh,
  });

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center gap-2">
        <span className="text-sm">숨김</span>
        <Button
          variant="outline"
          disabled={unhide.isPending}
          onClick={() => unhide.mutate()}
        >
          {unhide.isPending ? "처리하는 중…" : "다시 보이기"}
        </Button>
      </div>
      {unhide.error && (
        <p role="alert" className="text-sm">
          {failureText(unhide.error)}
        </p>
      )}
    </div>
  );
}

/** 숨김 · 다시 보이기 · 삭제는 목록 · 프리셋 · 수집 현황을 함께 바꾼다 */
function useRefreshAdmin() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEY });
}

function useDeleteSource(id: number) {
  const client = useAdminClient();
  const refresh = useRefreshAdmin();
  return useMutation({
    mutationFn: () =>
      unwrap(
        client.DELETE("/api/admin/sources/{id}", {
          params: { path: { id } },
        }),
      ).then(() => undefined),
    onSettled: refresh,
  });
}

function useFeedRemoval(id: number, action: "hide" | "delete") {
  const client = useAdminClient();
  const refresh = useRefreshAdmin();
  return useMutation({
    mutationFn: () => {
      const params = { params: { path: { id } } };
      return action === "hide"
        ? unwrap(client.POST("/api/admin/feeds/{id}/hide", params))
        : unwrap(client.DELETE("/api/admin/feeds/{id}", params));
    },
    onSettled: refresh,
  });
}

/** 피드 숨기기 · 삭제의 거절 · 오류 — 숨기거나 지웠으면 행이 목록에서 옮겨지므로 따로 알리지 않는다 */
function FeedRemovalOutcome({
  mutation,
  verb,
}: {
  mutation: { data?: FeedRemovalResponse; error: unknown };
  verb: string;
}) {
  if (mutation.error) {
    return <p role="alert">{failureText(mutation.error)}</p>;
  }
  if (mutation.data?.result === "REFUSED" && mutation.data.refusal) {
    return (
      <p role="alert">
        {verb} 않았습니다 — {feedRemovalRefusalLabels[mutation.data.refusal]}
      </p>
    );
  }
  return null;
}

function DeleteSourceDialog({
  source,
  open,
  onClose,
  onConfirm,
}: {
  source: SourceResponse;
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const client = useAdminClient();
  const impact = useQuery({
    queryKey: [...ADMIN_QUERY_KEY, "deletion-impact", "source", source.id],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/sources/{id}/deletion-impact", {
          params: { path: { id: source.id } },
        }),
      ),
    enabled: open,
    staleTime: 0,
  });

  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      title={`${sourceName(source)}을(를) 삭제할까요?`}
      confirmLabel="삭제"
      destructive
      onConfirm={onConfirm}
    >
      <p>{DELETE_SOURCE_TEXT}</p>
      {source.enabled && (
        <p>켜져 있는 소스입니다 — 삭제하면 수집을 멈춥니다.</p>
      )}
      <ImpactLine
        owner="이 소스"
        count={impact.data?.exclusiveArticles}
        error={impact.error}
      />
    </ConfirmDialog>
  );
}

function DeleteFeedDialog({
  sourceId,
  feed,
  open,
  onClose,
  onConfirm,
}: {
  sourceId: number;
  feed: FeedResponse;
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const client = useAdminClient();
  const impact = useQuery({
    queryKey: [
      ...ADMIN_QUERY_KEY,
      "deletion-impact",
      "feed",
      sourceId,
      feed.id,
    ],
    queryFn: () =>
      unwrap(
        client.GET(
          "/api/admin/sources/{sourceId}/feeds/{feedId}/deletion-impact",
          { params: { path: { sourceId, feedId: feed.id } } },
        ),
      ),
    enabled: open,
    staleTime: 0,
  });

  return (
    <ConfirmDialog
      open={open}
      onClose={onClose}
      title={`피드 ${feed.feedKey}을(를) 삭제할까요?`}
      confirmLabel="삭제"
      destructive
      onConfirm={onConfirm}
    >
      <p>{DELETE_FEED_TEXT}</p>
      {feed.enabled && <p>켜져 있는 피드입니다 — 삭제하면 수집을 멈춥니다.</p>}
      <ImpactLine
        owner="이 피드"
        count={impact.data?.exclusiveArticles}
        error={impact.error}
      />
    </ConfirmDialog>
  );
}

/** 지금 기준으로 함께 지워질 기사 수 — 대화상자를 연 뒤에도 수집이 돌면 바뀔 수 있다 */
function ImpactLine({
  owner,
  count,
  error,
}: {
  owner: string;
  count: number | undefined;
  error: unknown;
}) {
  if (error) {
    return <p role="alert">기사 수를 얻지 못했습니다 — {failureText(error)}</p>;
  }
  return (
    <p className="font-medium">
      {count === undefined
        ? "함께 지워질 기사 수를 세는 중…"
        : `지금 기준 ${owner}만 가진 기사 ${count}건이 함께 지워집니다.`}
    </p>
  );
}

function ConfirmDialog({
  open,
  onClose,
  title,
  confirmLabel,
  destructive,
  onConfirm,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  confirmLabel: string;
  destructive: boolean;
  onConfirm: () => void;
  children: ReactNode;
}) {
  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          onClose();
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription render={<div className="space-y-2" />}>
            {children}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>취소</AlertDialogCancel>
          <Button
            variant={destructive ? "destructive" : "default"}
            onClick={() => {
              onClose();
              onConfirm();
            }}
          >
            {confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
