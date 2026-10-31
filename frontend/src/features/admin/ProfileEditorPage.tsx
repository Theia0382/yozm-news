import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { failureText, unwrap } from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { ProfileProblems, PreviewResultView } from "@/features/admin/results";
import { Button } from "@/shared/ui/button";

/**
 * 운영자 프로필 원문 편집 — /admin/sources/new 는 새로 등록하고, /admin/sources/:id/profile 은 등록된 원문을 고친다. 항목별 폼은
 * 두지 않고 원문을 그대로 편집한다.
 */
export function ProfileEditorPage() {
  const { id } = useParams();
  const client = useAdminClient();
  const sourceId = id === undefined ? null : Number(id);
  const current = useQuery({
    queryKey: [...ADMIN_QUERY_KEY, "profile", sourceId],
    queryFn: () =>
      unwrap(
        client.GET("/api/admin/sources/{id}/profile", {
          params: { path: { id: sourceId as number } },
        }),
      ),
    enabled: sourceId !== null,
  });

  if (sourceId !== null && current.isPending) {
    return <p className="text-muted-foreground">원문을 읽는 중…</p>;
  }
  if (sourceId !== null && current.error) {
    return <p role="alert">{failureText(current.error)}</p>;
  }
  return (
    <ProfileEditor
      key={sourceId ?? "new"}
      sourceId={sourceId}
      initial={current.data?.profile ?? ""}
    />
  );
}

function ProfileEditor({
  sourceId,
  initial,
}: {
  sourceId: number | null;
  initial: string;
}) {
  const client = useAdminClient();
  const queryClient = useQueryClient();
  const [text, setText] = useState(initial);
  const [feedKey, setFeedKey] = useState("");

  const preview = useMutation({
    mutationFn: () =>
      unwrap(
        client.POST("/api/admin/profile-previews", {
          body: { profile: text, feedKey: feedKey.trim() },
        }),
      ),
  });
  const register = useMutation({
    mutationFn: () =>
      unwrap(client.POST("/api/admin/sources", { body: { profile: text } })),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEY }),
  });
  const revise = useMutation({
    mutationFn: (id: number) =>
      unwrap(
        client.PUT("/api/admin/sources/{id}/profile", {
          params: { path: { id } },
          body: { profile: text },
        }),
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ADMIN_QUERY_KEY }),
  });
  const saving = register.isPending || revise.isPending;

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold">
        {sourceId === null
          ? "운영자 프로필 작성"
          : `소스 ${sourceId} 원문 편집`}
      </h2>
      <label className="block space-y-1">
        <span className="text-sm">수집 프로필 원문(YAML)</span>
        <textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          rows={20}
          spellCheck={false}
          className="w-full rounded-md border border-input bg-background p-2 font-mono text-sm"
        />
      </label>

      <div className="flex flex-wrap items-end gap-2">
        <label className="space-y-1">
          <span className="block text-sm">미리볼 피드 식별자</span>
          <input
            value={feedKey}
            onChange={(event) => setFeedKey(event.target.value)}
            className="rounded-md border border-input bg-background px-2 py-1"
          />
        </label>
        <Button
          variant="outline"
          disabled={preview.isPending || feedKey.trim() === ""}
          onClick={() => preview.mutate()}
        >
          {preview.isPending ? "받아서 확인하는 중…" : "미리보기"}
        </Button>
      </div>
      {preview.data && <PreviewResultView result={preview.data} />}
      {preview.error && <p role="alert">{failureText(preview.error)}</p>}

      <div className="flex items-center gap-3">
        <Button
          disabled={saving}
          onClick={() =>
            sourceId === null ? register.mutate() : revise.mutate(sourceId)
          }
        >
          {sourceId === null ? "등록" : "저장"}
        </Button>
        <Link to="/admin" className="underline">
          목록으로
        </Link>
      </div>

      {register.data?.result === "REGISTERED" && (
        <p role="status">
          소스 {register.data.sourceId}(으)로 등록했습니다. 소스와 피드는 꺼진
          채이니 목록에서 켜 주세요.
        </p>
      )}
      {register.data?.result === "INVALID_PROFILE" && (
        <div className="space-y-2">
          <p role="alert">원문이 검증에 걸려 등록하지 않았습니다.</p>
          <ProfileProblems problems={register.data.problems} />
        </div>
      )}
      {revise.data?.result === "REVISED" && (
        <div role="status" className="space-y-1">
          <p>저장했습니다.</p>
          {revise.data.disabledFeedKeys.length > 0 && (
            <p>
              읽기 설정이 바뀌어 끈 피드:{" "}
              {revise.data.disabledFeedKeys.join(", ")} — 다시 켜면 미리보기를
              거칩니다.
            </p>
          )}
          {revise.data.sourceDisabled && (
            <p>이용조건 검토일이 없어 소스를 껐습니다.</p>
          )}
          {revise.data.addedFeedKeys.length > 0 && (
            <p>
              새로 더한 피드(꺼진 채): {revise.data.addedFeedKeys.join(", ")}
            </p>
          )}
        </div>
      )}
      {revise.data?.result === "INVALID_PROFILE" && (
        <div className="space-y-2">
          <p role="alert">원문이 검증에 걸려 저장하지 않았습니다.</p>
          <ProfileProblems problems={revise.data.problems} />
        </div>
      )}
      {(register.error ?? revise.error) && (
        <p role="alert">{failureText(register.error ?? revise.error)}</p>
      )}
    </div>
  );
}
