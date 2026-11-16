package net.yozmnews.storyline.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 판정 시도 하나의 결말 — {@link StorylineLinker#link} 가 낸다 · docs/specs/storyline.md §4 · storyline-R35.
 */
sealed interface LinkingOutcome {

	/** 판정을 마쳤다 — 쓸 것을 모두 썼다 */
	record Done() implements LinkingOutcome {

	}

	/**
	 * 띠 안 후보의 확률이 모자라 묻는다 — 아무것도 쓰지 않았다.
	 * @param target 붙이기 띠인가, 짝 띠인가
	 * @param article 판정할 사건의 대표 기사 — 요청의 state
	 * @param eventArticles 판정할 사건의 기사 수 — 물은 기록에 남는다
	 * @param flows 물을 후보 — 점수 순
	 */
	record Ask(Target target, long eventId, ArticleId article, int eventArticles, List<AskedFlow> flows)
			implements LinkingOutcome {

		public Ask {
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(article, "article");
			flows = List.copyOf(flows);
			if (flows.isEmpty()) {
				throw new IllegalArgumentException("물을 후보가 없다");
			}
		}

	}

	/** 묻는 띠 */
	enum Target {

		/** 붙이기 — 후보는 스토리라인 */
		LINK,

		/** 짝 — 후보는 이웃 사건 */
		PAIR

	}

	/**
	 * 물을 후보 하나.
	 * @param flowId 스토리라인 ID(붙이기) 또는 이웃 사건 ID(짝)
	 * @param articleIds 서술 기사 — 비었으면 물을 서술이 없다(확률 없이 기록한다)
	 * @param size 후보의 크기 — 스토리라인은 구성 사건 수, 이웃 사건은 기사 수
	 */
	record AskedFlow(long flowId, List<ArticleId> articleIds, int size) {

		public AskedFlow {
			articleIds = List.copyOf(articleIds);
		}

	}

}
