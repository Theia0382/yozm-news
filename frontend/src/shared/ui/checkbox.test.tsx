import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Checkbox } from "@/shared/ui/checkbox";

describe("Checkbox", () => {
  it("일부 고름이면 대시를 그리고 체크는 그리지 않는다", () => {
    const { container } = render(<Checkbox aria-label="일부" indeterminate />);

    expect(container.querySelector("svg.lucide-minus")).not.toBeNull();
    expect(container.querySelector("svg.lucide-check")).toBeNull();
  });

  it("고름이면 체크를 그리고 대시는 그리지 않는다", () => {
    const { container } = render(<Checkbox aria-label="고름" checked />);

    expect(container.querySelector("svg.lucide-check")).not.toBeNull();
    expect(container.querySelector("svg.lucide-minus")).toBeNull();
  });
});
