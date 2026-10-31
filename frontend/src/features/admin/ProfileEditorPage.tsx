import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import { failureText, unwrap } from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import { ProfileProblems, PreviewResultView } from "@/features/admin/results";
import { Button } from "@/shared/ui/button";

/**
 * 운영자 프로필 원문 편집 — /admin/sources/new 는 새로 등록하고, /admin/sources/:id/profile 은 등록된 원문을 고친다. 항목별 폼은
 * 두지 않고 원문을 그대로 편집한다. 등록에 성공하면 그 소스의 원문 편집 화면으로 옮겨 다음 저장이 수정이 되게 한다.
 *
 * 화면의 결과는 그것을 낸 원문에만 붙는다 — 원문 · 미리볼 피드 식별자를 고치면 그전 결과를 지우고, 저장 · 등록 중에는 두 입력을 잠근다.
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

/** 등록 뒤 편집 화면으로 옮길 때 싣는 라우터 상태 */
type RegisteredState = { registered: true };

function isRegisteredState(state: unknown): state is RegisteredState {
  return (
    typeof state === "object" &&
    state !== null &&
    (state as Partial<RegisteredState>).registered === true
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
  const navigate = useNavigate();
  const location = useLocation();
  const [text, setText] = useState(initial);
  const [feedKey, setFeedKey] = useState("");
  // 등록 뒤 옮겨 온 화면이면 안내를 보인다 — 라우터 상태는 읽은 뒤 지워 새로고침 · 뒤로 가기에서 다시 나오지 않게 한다
  const [registeredNotice, setRegisteredNotice] = useState(() =>
    isRegisteredState(location.state),
  );
  useEffect(() => {
    if (isRegisteredState(location.state)) {
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location, navigate]);

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

  /** 원문 · 미리볼 피드 식별자가 바뀌면 그전 결과는 지금 원문의 것이 아니다 */
  function forgetResults() {
    preview.reset();
    register.reset();
    revise.reset();
    setRegisteredNotice(false);
  }

  function save() {
    if (sourceId !== null) {
      revise.mutate(sourceId);
      return;
    }
    register.mutate(undefined, {
      onSuccess: (result) => {
        if (result.result === "REGISTERED" && result.sourceId !== null) {
          navigate(`/admin/sources/${result.sourceId}/profile`, {
            replace: true,
            state: { registered: true } satisfies RegisteredState,
          });
        }
      },
    });
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold">
        {sourceId === null ? "운영자 프로필 작성" : "운영자 프로필 원문 편집"}
      </h2>
      <label className="block space-y-1">
        <span className="text-sm">수집 프로필 원문(YAML)</span>
        <textarea
          value={text}
          readOnly={saving}
          onChange={(event) => {
            setText(event.target.value);
            forgetResults();
          }}
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
            readOnly={saving}
            onChange={(event) => {
              setFeedKey(event.target.value);
              forgetResults();
            }}
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
        <Button disabled={saving} onClick={save}>
          {sourceId === null ? "등록" : "저장"}
        </Button>
        <Link to="/admin" className="underline">
          목록으로
        </Link>
      </div>

      {registeredNotice && (
        <p role="status">
          등록했습니다. 소스와 피드는 꺼진 채이니 목록에서 켜 주세요.
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
