import { describe, expect, it } from "vitest";
import { createQueryClient } from "@/app/queryClient";
import { ME_QUERY_KEY } from "@/features/session/sessionQueries";

describe("createQueryClient", () => {
  it("사용자가 바뀌면 앞 사용자의 조회를 비우는 감시가 걸려 있다", () => {
    const queryClient = createQueryClient();
    queryClient.setQueryData(ME_QUERY_KEY, {
      result: "SIGNED_IN",
      userId: 1,
      email: "user1@example.com",
      displayName: "사용자 1",
      role: "USER",
      passwordChangeRequired: false,
      llmKeyRequired: false,
    });
    queryClient.setQueryData(["llm-keys"], 1);

    queryClient.setQueryData(ME_QUERY_KEY, {
      result: "SIGNED_IN",
      userId: 2,
      email: "user2@example.com",
      displayName: "사용자 2",
      role: "USER",
      passwordChangeRequired: false,
      llmKeyRequired: false,
    });

    expect(queryClient.getQueryData(["llm-keys"])).toBeUndefined();
  });
});
