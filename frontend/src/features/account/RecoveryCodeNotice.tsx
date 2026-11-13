import { useState } from "react";
import { CopyButton } from "@/features/account/CopyButton";
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
  const [leaving, setLeaving] = useState(false);

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
      <p className="text-sm font-medium">
        이 코드를 잃고 비밀번호도 잊으면 계정을 되찾을 수 없습니다 — 메일로
        재설정하지 않습니다.
      </p>
      <p className="rounded-md border bg-muted px-3 py-2 font-mono text-base break-all">
        {/* 문단에는 이름표가 붙지 않아 낭독용 이름을 숨은 글자로 앞에 둔다 — 선택 · 복사는 코드만 */}
        <span className="sr-only">복구 코드 값: </span>
        <span className="select-all">{code}</span>
      </p>
      <CopyButton value={code} />
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
