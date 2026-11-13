import { useState } from "react";
import { Button } from "@/shared/ui/button";

/**
 * 값을 클립보드에 넣는 단추 — 결과를 옆에 알리고, 넣지 못하면 직접 적으라고 한다. 복구 코드 · 초대 링크가 쓴다.
 */
export function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState<"done" | "failed" | null>(null);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied("done");
    } catch {
      setCopied("failed");
    }
  }

  return (
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
  );
}
