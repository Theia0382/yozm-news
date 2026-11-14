import {
  DIGEST_TRANSFER_ITEMS,
  REPORT_TRANSFER_ITEMS,
} from "@/features/privacy/privacyTexts";

/** 외부 전송 고지 — 다이제스트 · 변화 리포트를 만들 때 고른 LLM 공급자로 가는 항목. 키 화면과 고지 페이지가 함께 쓴다 */
export function ExternalTransferNotice({ headingId }: { headingId: string }) {
  return (
    <section className="space-y-3" aria-labelledby={headingId}>
      <h2 id={headingId} className="text-xl font-bold">
        LLM 공급자에게 보내는 것
      </h2>
      <p className="text-sm">
        등록한 키로 다이제스트와 변화 리포트를 만들 때, 아래 항목이 내가 고른
        LLM 공급자(OpenAI 또는 Anthropic)에게 전송됩니다.
      </p>
      <h3 className="text-sm font-medium">다이제스트를 만들 때</h3>
      <ul className="list-disc space-y-1 pl-5 text-sm">
        {DIGEST_TRANSFER_ITEMS.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <h3 className="text-sm font-medium">변화 리포트를 만들 때</h3>
      <ul className="list-disc space-y-1 pl-5 text-sm">
        {REPORT_TRANSFER_ITEMS.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  );
}
