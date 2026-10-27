import { render, screen } from "@testing-library/react";
import { createMemoryRouter } from "react-router";
import { RouterProvider } from "react-router/dom";
import { describe, expect, it } from "vitest";
import { routes } from "@/app/routes";

describe("routes", () => {
  it("루트 경로에서 첫 화면을 보여준다", async () => {
    const router = createMemoryRouter(routes, { initialEntries: ["/"] });
    render(<RouterProvider router={router} />);

    expect(
      await screen.findByRole("heading", { level: 1, name: "요즘뉴스" }),
    ).toBeInTheDocument();
  });
});
