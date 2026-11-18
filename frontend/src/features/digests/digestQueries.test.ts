import { describe, expect, it } from "vitest";
import {
  DigestRequestError,
  retryUnlessKeyRequired,
} from "@/features/digests/digestQueries";
import { TopicSuggestionRequestError } from "@/features/digests/topicSuggestionQueries";

describe("retryUnlessKeyRequired", () => {
  it("키 관문의 403 은 다시 묻지 않는다", () => {
    expect(retryUnlessKeyRequired(0, new DigestRequestError(403))).toBe(false);
    expect(
      retryUnlessKeyRequired(0, new TopicSuggestionRequestError(403)),
    ).toBe(false);
  });

  it("그 밖의 실패는 세 번까지 다시 묻는다", () => {
    expect(
      retryUnlessKeyRequired(2, new TopicSuggestionRequestError(500)),
    ).toBe(true);
    expect(
      retryUnlessKeyRequired(3, new TopicSuggestionRequestError(500)),
    ).toBe(false);
    expect(retryUnlessKeyRequired(0, new TypeError("Failed to fetch"))).toBe(
      true,
    );
  });
});
