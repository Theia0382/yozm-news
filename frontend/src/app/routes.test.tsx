import { render, screen } from "@testing-library/react";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { routes } from "@/app/routes";
import { CollectionPage } from "@/features/admin/CollectionPage";

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
});
