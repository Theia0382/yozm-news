import { Link } from "react-router";
import { ExternalTransferNotice } from "@/features/privacy/ExternalTransferNotice";
import { COLLECTED_ITEMS } from "@/features/privacy/privacyTexts";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 고지 페이지 — 이 인스턴스가 저장하는 것과 LLM 공급자에게 보내는 것. 로그인 없이 본다(docs/security.md "개인정보").
 */
export function PrivacyPage() {
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-8`}>
      <h1 className="text-2xl font-bold">개인정보 고지</h1>
      <section className="space-y-3" aria-labelledby="collected-items">
        <h2 id="collected-items" className="text-xl font-bold">
          저장하는 것
        </h2>
        <ul className="list-disc space-y-1 pl-5 text-sm">
          {COLLECTED_ITEMS.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <p className="text-sm text-muted-foreground">
          클릭과 열람은 추적하지 않습니다. 원문 링크는 요즘뉴스를 거치지 않고
          원문 사이트로 바로 갑니다. 이메일은 외부로 전달하지 않습니다.
        </p>
      </section>
      <ExternalTransferNotice headingId="external-transfer" />
      <p className="text-sm">
        <Link to="/" className="underline">
          요즘뉴스로 돌아가기
        </Link>
      </p>
    </section>
  );
}
