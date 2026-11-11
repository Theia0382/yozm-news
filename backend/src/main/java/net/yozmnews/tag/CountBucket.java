package net.yozmnews.tag;

import java.util.Objects;

/**
 * 붙은 태그 수의 칸 하나 — docs/specs/tag.md §3
 *
 * @param tags 붙은 태그 수
 * @param orMore 마지막 칸 — {@code tags} 개 이상
 */
public record CountBucket(int tags, boolean orMore, Share share) {

	public CountBucket {
		Objects.requireNonNull(share, "share");
	}

}
