import { useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useEffect, useState } from "react";
import { Link } from "react-router";
import {
  requestFailedText,
  UNREACHABLE_TEXT,
} from "@/features/account/accountTexts";
import { TextField } from "@/features/account/TextField";
import {
  KeyRequestError,
  LLM_KEYS_QUERY_KEY,
  useLlmKeys,
} from "@/features/keys/keyQueries";
import {
  failureTexts,
  type LlmKey,
  type Provider,
  providers,
} from "@/features/keys/keyTexts";
import { ExternalTransferNotice } from "@/features/privacy/ExternalTransferNotice";
import { PRIVACY_PATH } from "@/features/privacy/privacyTexts";
import { SESSION_QUERY_KEY } from "@/features/session/sessionQueries";
import { useApiClient } from "@/shared/api/client";
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
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 한 공급자 칸의 안내 — 성공은 알림(status), 실패는 경고(alert)로 읽힌다 */
type Message = { tone: "status" | "alert"; text: string };

/**
 * LLM 키 화면 — 공급자마다 키를 등록 · 다시 검증 · 삭제한다. 키는 서버가 암호화해 두고 화면에는 끝 4자리만 보인다. 발급 안내와
 * LLM 공급자에게 보내는 항목을 키 유무와 무관하게 늘 보인다(docs/specs/credential.md §3).
 */
export function KeysPage() {
  const keys = useLlmKeys();
  const queryClient = useQueryClient();

  const unauthenticated =
    keys.error instanceof KeyRequestError && keys.error.status === 401;
  useEffect(() => {
    if (unauthenticated) {
      void queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
    }
  }, [unauthenticated, queryClient]);

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-8`}>
      <h1 className="text-2xl font-bold">LLM 키</h1>
      <p className="text-sm text-muted-foreground">
        요즘뉴스는 내가 등록한 LLM API 키로 다이제스트와 변화 리포트를 만듭니다.
        키를 등록해야 서비스를 쓸 수 있습니다.
      </p>
      {keys.isPending && <p className="text-muted-foreground">키를 읽는 중…</p>}
      {keys.isError && !unauthenticated && (
        <div className="space-y-3">
          <p role="alert">LLM 키를 읽지 못했습니다.</p>
          <Button variant="outline" onClick={() => keys.refetch()}>
            다시 시도
          </Button>
        </div>
      )}
      {keys.data?.providers.map((entry) => (
        <ProviderKeySection
          key={entry.provider}
          provider={entry.provider}
          stored={entry.key}
        />
      ))}
      <IssuingGuide />
      <ExternalTransferNotice headingId="key-external-transfer" />
      <p className="text-sm">
        <Link to={PRIVACY_PATH} className="underline">
          저장하는 것과 보내는 것 모두 보기
        </Link>
      </p>
    </section>
  );
}

function ProviderKeySection({
  provider,
  stored,
}: {
  provider: Provider;
  stored: LlmKey | null;
}) {
  const client = useApiClient();
  const queryClient = useQueryClient();
  const [key, setKey] = useState("");
  const [message, setMessage] = useState<Message | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const { name } = providers[provider];
  const headingId = `llm-key-${provider}`;

  /** 키의 상태 · 검증 시각이 바뀌었을 수 있다 — 키 조회를 다시 읽는다 */
  async function reloadKeys() {
    await queryClient.invalidateQueries({ queryKey: LLM_KEYS_QUERY_KEY });
  }

  /** 키가 생기거나 없어졌다 — 키 조회와 로그인 상태(키 유무)를 다시 읽는다 */
  async function reload() {
    await Promise.all([
      reloadKeys(),
      queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY }),
    ]);
  }

  async function run(action: () => Promise<Message>) {
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

  function register(event: FormEvent) {
    event.preventDefault();
    void run(async () => {
      const { data, response } = await client.PUT("/api/llm-keys/{provider}", {
        params: { path: { provider } },
        body: { key },
      });
      if (!response.ok || !data) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      switch (data.result) {
        case "REGISTERED":
          setKey("");
          await reload();
          return { tone: "status", text: "키를 확인하고 저장했습니다." };
        case "REJECTED":
          return { tone: "alert", text: failureText(data.failure) };
      }
      const unknown: never = data.result;
      return {
        tone: "alert",
        text: `알 수 없는 결과입니다: ${String(unknown)}`,
      };
    });
  }

  function check() {
    void run(async () => {
      const { data, response } = await client.POST(
        "/api/llm-keys/{provider}/checks",
        { params: { path: { provider } } },
      );
      if (!response.ok || !data) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      await reloadKeys();
      switch (data.result) {
        case "VALID":
          return { tone: "status", text: "키를 쓸 수 있습니다." };
        case "INVALID":
          return {
            tone: "alert",
            text: `키가 무효가 됐습니다. ${failureText(data.failure)}`,
          };
        case "UNDETERMINED":
          return {
            tone: "alert",
            text: `키를 확인하지 못했습니다. ${failureText(data.failure)}`,
          };
        case "MISSING":
          return { tone: "alert", text: "저장한 키가 없습니다." };
      }
      const unknown: never = data.result;
      return {
        tone: "alert",
        text: `알 수 없는 결과입니다: ${String(unknown)}`,
      };
    });
  }

  function remove() {
    setConfirming(false);
    void run(async () => {
      const { response } = await client.DELETE("/api/llm-keys/{provider}", {
        params: { path: { provider } },
      });
      if (!response.ok) {
        return { tone: "alert", text: requestFailedText(response.status) };
      }
      await reload();
      return { tone: "status", text: "키를 지웠습니다." };
    });
  }

  return (
    <section
      className="space-y-3 rounded-md border p-4"
      aria-labelledby={headingId}
    >
      <h2 id={headingId} className="text-xl font-bold">
        {name}
      </h2>
      {stored === null ? (
        <p className="text-sm text-muted-foreground">저장한 키가 없습니다.</p>
      ) : (
        <StoredKey stored={stored} />
      )}
      {message && (
        <p
          role={message.tone}
          className={message.tone === "alert" ? "text-destructive" : undefined}
        >
          {message.text}
        </p>
      )}
      {stored !== null && (
        <div className="flex gap-2">
          <Button variant="outline" disabled={busy} onClick={check}>
            다시 검증
          </Button>
          <Button
            variant="outline"
            disabled={busy}
            onClick={() => setConfirming(true)}
          >
            삭제
          </Button>
        </div>
      )}
      <form onSubmit={register} className="max-w-sm space-y-3">
        <TextField
          label={stored === null ? `${name} API 키` : `새 ${name} API 키`}
          type="password"
          name={`llm-key-${provider}`}
          autoComplete="off"
          required
          value={key}
          onValueChange={setKey}
        />
        <Button type="submit" disabled={busy}>
          {stored === null ? "등록" : "새 키로 바꾸기"}
        </Button>
      </form>
      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{name} 키를 지울까요?</AlertDialogTitle>
            <AlertDialogDescription>
              지운 키는 되돌릴 수 없습니다. 키가 하나도 없으면 키를 다시 등록할
              때까지 서비스를 쓸 수 없습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <Button variant="destructive" onClick={remove}>
              삭제
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}

function StoredKey({ stored }: { stored: LlmKey }) {
  return (
    <dl className="space-y-1 text-sm">
      <div className="flex gap-2">
        <dt className="shrink-0 text-muted-foreground">저장한 키</dt>
        <dd>{stored.hint === "" ? "저장됨" : `…${stored.hint}`}</dd>
      </div>
      <div className="flex gap-2">
        <dt className="shrink-0 text-muted-foreground">상태</dt>
        <dd>{statusText(stored.status)}</dd>
      </div>
      <div className="flex gap-2">
        <dt className="shrink-0 text-muted-foreground">마지막으로 확인된 때</dt>
        <dd>
          <time dateTime={stored.validatedAt}>
            {formatDateTime(stored.validatedAt)}
          </time>
        </dd>
      </div>
    </dl>
  );
}

function statusText(status: LlmKey["status"]): string {
  switch (status) {
    case "VALID":
      return "쓸 수 있습니다";
    case "INVALID":
      return "무효입니다 — 공급자 콘솔에서 키를 확인하고 새 키를 등록해 주세요";
  }
}

/** 실패 종류의 문구 — 종류가 비어 온 응답은 일반 문구로 받는다 */
function failureText(
  failure: keyof typeof failureTexts | null | undefined,
): string {
  return failure
    ? failureTexts[failure]
    : "공급자가 이 키를 받아 주지 않았습니다.";
}

/** 키 발급 안내 — 구독과 API 키가 다르다는 것, 콘솔 발급, 결제 수단 */
function IssuingGuide() {
  return (
    <section className="space-y-3" aria-labelledby="key-issuing-guide">
      <h2 id="key-issuing-guide" className="text-xl font-bold">
        API 키 받는 법
      </h2>
      <ul className="list-disc space-y-1 pl-5 text-sm">
        <li>
          ChatGPT Plus · Claude Pro 같은 구독과 API 키는 다릅니다. 구독만으로는
          요즘뉴스가 다이제스트를 만들 수 없습니다.
        </li>
        <li>
          API 키는 공급자의 개발자 콘솔에서 발급합니다 —{" "}
          {Object.values(providers).map((provider, index) => (
            <span key={provider.console}>
              {index > 0 && " · "}
              <a
                href={provider.consoleUrl}
                target="_blank"
                rel="noreferrer"
                className="underline"
              >
                {provider.console}
              </a>
            </span>
          ))}
        </li>
        <li>
          콘솔에 결제 수단을 등록해야 키로 요청할 수 있습니다. 다이제스트를
          만드는 비용은 이 키로 공급자에게 직접 냅니다.
        </li>
      </ul>
    </section>
  );
}
