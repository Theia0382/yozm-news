import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EvaluationDetail } from "@/features/admin/EvaluationDetail";
import { safeLink } from "@/features/admin/links";
import { detail, renderAlone } from "@/features/admin/reviewTestKit";

describe("EvaluationDetail", () => {
  it("http · https 만 원문 링크로 단다", () => {
    expect(safeLink("https://news.example/a/1")).toBe(
      "https://news.example/a/1",
    );
    expect(safeLink("http://news.example/a/1")).toBe("http://news.example/a/1");
    // 대조: 다른 스킴 · 주소가 아닌 값은 링크가 아니다
    expect(safeLink("javascript:alert(1)")).toBeNull();
    expect(safeLink("not a url")).toBeNull();
  });

  it("원문 링크는 새 탭으로 열고 여는 쪽을 넘기지 않는다", async () => {
    renderAlone(<EvaluationDetail detail={detail(9)} />);

    const link = await screen.findByRole("link", { name: "원문 열기" });
    expect(link).toHaveAttribute("href", "https://news.example/a/1");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("http · https 가 아닌 원문 주소는 링크가 아니라 글자다", async () => {
    renderAlone(<EvaluationDetail detail={detail(9, "javascript:alert(1)")} />);

    expect(
      await screen.findByText("원문 주소: javascript:alert(1)"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "원문 열기" })).toBeNull();
  });

  it("요약문 안의 HTML 은 해석하지 않고 글자로 보인다", async () => {
    renderAlone(<EvaluationDetail detail={detail(9)} />);

    expect(await screen.findByText("<b>요약</b> 원문")).toBeInTheDocument();
    expect(document.body.querySelector("article b")).toBeNull();
  });

  it("저장된 태그 전부를 지금 기준으로 붙었는지와 함께 보인다", async () => {
    renderAlone(<EvaluationDetail detail={detail(9)} />);

    const topics = await screen.findByRole("table", {
      name: "주제(저장된 중분류 전부)",
    });
    const rows = within(topics).getAllByRole("row");
    expect(rows[1]).toHaveTextContent("대통령실·행정부0.8붙음");
    expect(rows[2]).toHaveTextContent("국회·정당·선거0.4붙지 않음");
    expect(
      screen.getByText("한겨레 · 정치 · 카테고리 국회"),
    ).toBeInTheDocument();
  });

  it("그새 지운 기사는 평가 결과만 보인다", async () => {
    renderAlone(<EvaluationDetail detail={{ ...detail(9), article: null }} />);

    expect(
      await screen.findByText(
        "그새 지운 기사다 — 평가 결과만 남아 있다(기사 109)",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "원문 열기" })).toBeNull();
  });
});
