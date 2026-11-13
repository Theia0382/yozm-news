import { useState } from "react";
import { Button } from "@/shared/ui/button";

type RecoveryCodeNoticeProps = {
  code: string;
  /** "저장했다"를 확인한 뒤 누르는 단추의 다음 행동 */
  onDone: () => void | Promise<void>;
};

/**
 * 복구 코드 안내 — 코드는 이 화면에서 한 번만 보인다. 복사 단추와 안내를 두고, 저장했다고 확인해야 다음으로 간다(docs/specs/user.md §4).
 */
export function RecoveryCodeNotice({ code, onDone }: RecoveryCodeNoticeProps) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState<"done" | "failed" | null>(null);
  const [leaving, setLeaving] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied("done");
    } catch {
      setCopied("failed");
    }
  }

  async function done() {
    setLeaving(true);
    try {
      await onDone();
    } finally {
      setLeaving(false);
    }
  }

  return (
    <section className="max-w-md space-y-4" aria-labelledby="recovery-code">
      <h2 id="recovery-code" className="text-xl font-bold">
        복구 코드
      </h2>
      <p className="text-sm">
        비밀번호를 잊었을 때 이 코드로 비밀번호를 다시 정합니다. 이 화면을
        떠나면 코드를 다시 볼 수 없으니 안전한 곳에 적어 두세요. 코드를 새로
        받거나 이 코드로 재설정하면 이 코드는 더 쓸 수 없습니다.
      </p>
      <p
        aria-label="복구 코드 값"
        className="rounded-md border bg-muted px-3 py-2 font-mono text-base break-all select-all"
      >
        {code}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" variant="outline" onClick={copy}>
          복사
        </Button>
        {copied === "done" && (
          <span role="status" className="text-sm">
            복사했습니다.
          </span>
        )}
        {copied === "failed" && (
          <span role="status" className="text-sm text-destructive">
            복사하지 못했습니다. 직접 적어 두세요.
          </span>
        )}
      </div>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={saved}
          onChange={(event) => setSaved(event.target.checked)}
        />
        복구 코드를 저장했습니다
      </label>
      <Button type="button" disabled={!saved || leaving} onClick={done}>
        계속
      </Button>
    </section>
  );
}
