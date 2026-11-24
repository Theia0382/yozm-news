package net.yozmnews.digest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.InputEvent;
import net.yozmnews.digest.domain.input.InputItem;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.event.EventId;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;

/**
 * 생성 흐름 시험의 입력 — 항목 · 사건 · 기사만 시험이 정하고 프롬프트 재료는 고정 값이다(제목 "시험 기사 {기사 ID}", 요약문 · 출처 없음,
 * 프로필 정치 · 서울 · 보통). 생성 흐름이 재료를 읽지 않는 시험(후보 행 · 상태 · 인용)에서 쓴다.
 */
public final class DigestInputs {

	/** 고정 프로필 */
	public static final DigestProfile PROFILE = new DigestProfile(List.of("정치"), List.of(),
			ZoneId.of("Asia/Seoul"), SummaryLength.NORMAL, ReasoningEffort.MEDIUM);

	/** {@link #of} 가 주요 뉴스 항목으로 두는 앞 기사 수 — 나머지는 헤드라인 항목 */
	public static final int TOP_STORIES = 3;

	private DigestInputs() {
	}

	/**
	 * 기사마다 사건 하나(사건 ID = 기사 ID)와 항목 하나 — 그 순서로, 앞 {@link #TOP_STORIES} 개는 주요 뉴스 항목, 나머지는 헤드라인 항목.
	 * 사건의 선정 이유는 그 후보의 선정 이유다
	 */
	public static DigestInput of(List<DigestCandidate> candidates) {
		List<InputItem> items = new ArrayList<>(candidates.size());
		for (DigestCandidate candidate : candidates) {
			ItemKind kind = (items.size() < TOP_STORIES) ? ItemKind.TOP_STORY : ItemKind.HEADLINE;
			items.add(item(kind, event(candidate.articleId().value(), candidate.reason(), candidate.articleId().value())));
		}
		return new DigestInput(PROFILE, items);
	}

	/** 항목들을 그 순서로 담은 입력 */
	public static DigestInput items(InputItem... items) {
		return new DigestInput(PROFILE, List.of(items));
	}

	/** 항목 하나 — 사건들을 그 순서로 */
	public static InputItem item(ItemKind kind, InputEvent... events) {
		return new InputItem(kind, List.of(events), null);
	}

	/** 사건 하나 — 기사들을 그 순서로, 기사마다 선정 이유는 사건의 것 */
	public static InputEvent event(long event, SelectionReason reason, long... articles) {
		return new InputEvent(new EventId(event), reason,
				Arrays.stream(articles).mapToObj(article -> article(article, reason)).toList());
	}

	/** 기사 하나 — 고정 재료 */
	public static CandidateArticle article(long article, SelectionReason reason) {
		return new CandidateArticle(new DigestCandidate(new ArticleId(article), reason), "시험 기사 " + article, null,
				List.of(), Instant.parse("2026-11-15T00:00:00Z"), List.of(), List.of());
	}

}
