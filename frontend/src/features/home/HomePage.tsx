import { Link } from "react-router";
import { useInterestTags } from "@/features/interests/interestQueries";
import {
  INTERESTS_PATH,
  NO_TOPIC_TEXT,
} from "@/features/interests/interestTexts";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/** 첫 화면 — 구독한 주제가 없으면 관심 설정으로 안내한다(docs/specs/user.md user-R43) */
export function HomePage() {
  const tags = useInterestTags();
  const noTopic =
    tags.data !== undefined && tags.data.preferences.topics.length === 0;
  // 첫 화면은 모든 방문자가 처음 받는 묶음에 들어가므로 클래스를 합치는 cn 을 들이지 않는다 — cn 은 운영자 화면의 나중에 받는 묶음에만 둔다
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-3`}>
      <h1 className="text-3xl font-bold tracking-tight">요즘뉴스</h1>
      <p className="text-muted-foreground">
        관심 분야의 뉴스를 매일 짧게 정리해 드립니다. 서비스를 준비하고
        있습니다.
      </p>
      {noTopic && (
        <p role="note">
          {NO_TOPIC_TEXT}{" "}
          <Link to={INTERESTS_PATH} className="underline">
            관심 설정으로 가기
          </Link>
        </p>
      )}
    </section>
  );
}
