package net.yozmnews.user.domain.summary;

import java.util.List;
import java.util.Objects;

/**
 * 정리 출력을 읽은 결과. docs/specs/user.md §5
 */
public sealed interface SummaryRead {

	/**
	 * 읽었다.
	 *
	 * @param whole 사용자 스토리라인 전체의 글
	 * @param tops 꼭대기마다의 글 — 별칭 순. 꼭대기가 하나면 사용자 스토리라인 전체의 글 하나다
	 */
	record Read(SummaryText whole, List<SummaryText> tops) implements SummaryRead {

		public Read {
			Objects.requireNonNull(whole, "whole");
			tops = List.copyOf(tops);
		}

	}

	/** 읽지 못했다 — 까닭은 출력 원문을 담지 않는다 */
	record Unreadable(String reason) implements SummaryRead {

		public Unreadable {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
