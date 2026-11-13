import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RecoveryCodeNotice } from "@/features/account/RecoveryCodeNotice";

const CODE = "ABCD-EFGH-JKMN-PQRS-TVWX-YZ01-2345-6789";

describe("RecoveryCodeNotice", () => {
  it("코드를 보이고, 저장했다고 확인해야 다음으로 간다", async () => {
    const onDone = vi.fn();
    const user = userEvent.setup();
    render(<RecoveryCodeNotice code={CODE} onDone={onDone} />);

    expect(screen.getByLabelText("복구 코드 값")).toHaveTextContent(CODE);
    expect(screen.getByText(/다시 볼 수 없으니/)).toBeInTheDocument();
    expect(
      screen.getByText(
        /이 코드를 잃고 비밀번호도 잊으면 계정을 되찾을 수 없습니다/,
      ),
    ).toBeInTheDocument();
    const next = screen.getByRole("button", { name: "계속" });
    expect(next).toBeDisabled();

    await user.click(screen.getByLabelText("복구 코드를 저장했습니다"));
    await user.click(next);

    expect(onDone).toHaveBeenCalledOnce();
  });

  it("복사 단추가 코드를 클립보드에 넣는다", async () => {
    const user = userEvent.setup();
    render(<RecoveryCodeNotice code={CODE} onDone={() => {}} />);

    await user.click(screen.getByRole("button", { name: "복사" }));

    expect(await screen.findByText("복사했습니다.")).toBeInTheDocument();
    expect(await navigator.clipboard.readText()).toBe(CODE);
  });
});
