import { providers } from "@/features/keys/keyTexts";
import {
  DIGEST_TRANSFER_ITEMS,
  REPORT_TRANSFER_ITEMS,
  SUMMARY_TRANSFER_ITEMS,
} from "@/features/privacy/privacyTexts";

/** 받는 공급자 나열 — 공급자 이름의 정본은 keyTexts 다 */
const PROVIDER_NAMES = Object.values(providers)
  .map((provider) => provider.name)
  .join(" · ");

/** 외부 전송 고지 — 다이제스트 · 사용자 스토리라인의 정리 · 변화 리포트를 만들 때 쓸 모델의 LLM 공급자로 가는 항목. 키 화면과 고지 페이지가 함께 쓴다 */
export function ExternalTransferNotice({ headingId }: { headingId: string }) {
  return (
    <section className="space-y-3" aria-labelledby={headingId}>
      <h2 id={headingId} className="text-xl font-bold">
        LLM 공급자에게 보내는 것
      </h2>
      <p className="text-sm">
        등록한 키로 다이제스트 · 흐름의 정리 · 변화 리포트를 만들 때, 아래
        항목이 내가 고른 LLM 공급자({PROVIDER_NAMES})에게 전송됩니다. 고른
        모델을 쓸 수 없으면 키를 등록한 다른 공급자에게 전송됩니다.
      </p>
      <p className="text-sm">
        OpenRouter는 중계 서비스입니다. OpenRouter의 모델을 쓰면 아래 항목이
        OpenRouter를 거쳐, 그 모델을 만든 회사나 그 모델을 호스팅하는 공급자에게
        전송됩니다. 받은 데이터를 학습에 쓰는 공급자로는 보내지 않지만, 공급자가
        요청을 보관할 수는 있습니다.
      </p>
      <h3 className="text-sm font-medium">다이제스트를 만들 때</h3>
      <ul className="list-disc space-y-1 pl-5 text-sm">
        {DIGEST_TRANSFER_ITEMS.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <h3 className="text-sm font-medium">관심이 쌓인 흐름을 정리할 때</h3>
      <ul className="list-disc space-y-1 pl-5 text-sm">
        {SUMMARY_TRANSFER_ITEMS.map((item) => (
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
