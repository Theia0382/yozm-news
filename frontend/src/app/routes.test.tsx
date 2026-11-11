import { render, screen } from "@testing-library/react";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { routes } from "@/app/routes";
import { BigStorylinesPage } from "@/features/admin/BigStorylinesPage";
import { CollectionPage } from "@/features/admin/CollectionPage";
import { EvaluationDetailPage } from "@/features/admin/EvaluationDetailPage";
import { EvaluationListPage } from "@/features/admin/EvaluationListPage";
import { EvaluationPage } from "@/features/admin/EvaluationPage";
import { EventDetailPage } from "@/features/admin/EventDetailPage";
import { EventJudgementsPage } from "@/features/admin/EventJudgementsPage";
import { EventPublicationsPage } from "@/features/admin/EventPublicationsPage";
import { EventsPage } from "@/features/admin/EventsPage";
import { SamplePage } from "@/features/admin/SamplePage";
import { ThresholdsPage } from "@/features/admin/ThresholdsPage";

describe("routes", () => {
  it("루트 경로에서 첫 화면을 보여준다", async () => {
    const router = createMemoryRouter(routes, { initialEntries: ["/"] });
    render(<RouterProvider router={router} />);

    expect(
      await screen.findByRole("heading", { level: 1, name: "요즘뉴스" }),
    ).toBeInTheDocument();
  });

  it("운영자 화면 아래 수집 현황 경로가 수집 현황 화면을 내려받는다", async () => {
    const admin = routes[0]?.children?.find((route) => route.path === "admin");
    const collection = admin?.children?.find(
      (route) => route.path === "collection",
    );
    const lazy = collection?.lazy;

    expect(typeof lazy).toBe("function");
    const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
    expect(loaded.Component).toBe(CollectionPage);
  });

  it("운영자 화면 아래 평가 현황 경로가 평가 현황 화면을 내려받는다", async () => {
    const admin = routes[0]?.children?.find((route) => route.path === "admin");
    const evaluation = admin?.children?.find(
      (route) => route.path === "evaluation",
    );
    const lazy = evaluation?.lazy;

    expect(typeof lazy).toBe("function");
    const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
    expect(loaded.Component).toBe(EvaluationPage);
  });

  it.each([
    ["evaluations", "EvaluationListPage", () => EvaluationListPage],
    ["evaluations/:id", "EvaluationDetailPage", () => EvaluationDetailPage],
    ["samples", "SamplePage", () => SamplePage],
    ["thresholds", "ThresholdsPage", () => ThresholdsPage],
    ["events", "EventsPage", () => EventsPage],
    ["events/:id", "EventDetailPage", () => EventDetailPage],
    ["big-storylines", "BigStorylinesPage", () => BigStorylinesPage],
    ["event-judgements", "EventJudgementsPage", () => EventJudgementsPage],
  ])(
    "운영자 화면 아래 %s 경로가 %s 를 내려받는다",
    async (path, _name, component) => {
      const admin = routes[0]?.children?.find(
        (route) => route.path === "admin",
      );
      const lazy = admin?.children?.find((route) => route.path === path)?.lazy;

      expect(typeof lazy).toBe("function");
      const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
      expect(loaded.Component).toBe(component());
    },
  );

  it("운영자 화면 아래 이벤트 발행 경로가 이벤트 발행 화면을 내려받는다", async () => {
    const admin = routes[0]?.children?.find((route) => route.path === "admin");
    const publications = admin?.children?.find(
      (route) => route.path === "event-publications",
    );
    const lazy = publications?.lazy;

    expect(typeof lazy).toBe("function");
    const loaded = await (lazy as () => Promise<{ Component?: unknown }>)();
    expect(loaded.Component).toBe(EventPublicationsPage);
  });
});
