import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

export function HomePage() {
  // 첫 화면은 모든 방문자가 처음 받는 묶음에 들어가므로 클래스를 합치는 cn 을 들이지 않는다 — cn 은 운영자 화면의 나중에 받는 묶음에만 둔다
  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-3`}>
      <h1 className="text-3xl font-bold tracking-tight">요즘뉴스</h1>
      <p className="text-muted-foreground">
        관심 분야의 뉴스를 매일 짧게 정리해 드립니다. 서비스를 준비하고
        있습니다.
      </p>
    </section>
  );
}
