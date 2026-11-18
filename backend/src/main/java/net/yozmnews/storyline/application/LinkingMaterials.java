package net.yozmnews.storyline.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventTermCounts;
import net.yozmnews.storyline.domain.linking.Compared;
import net.yozmnews.storyline.domain.linking.LinkingAsk;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingScoring;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.linking.Subject;
import net.yozmnews.storyline.domain.linking.TermWeights;

/**
 * 사건을 스토리라인의 표현과 견줄 때의 재료 — 잇기({@link StorylineLinker})와 상위를 열 때의 평가({@link GroupOpening})가 함께 쓴다 ·
 * docs/specs/storyline.md §4.
 * <p>
 * 판단은 {@code domain/linking} 이 한다 — 여기는 읽어서 넘기는 일만 한다.
 */
final class LinkingMaterials {

	private LinkingMaterials() {
	}

	/** 판정할 사건의 말의 무게 — 그 사건의 구간(storyline-R9)과 겹치는 사건으로 센 수에서(storyline-R8) */
	static TermWeights weights(EventDirectory events, Subject subject, LinkingCriteria criteria) {
		EventTermCounts counts = events.termCounts(subject.keyTerms(),
				LinkingScoring.windowFrom(subject.firstArticleAt(), criteria.window()),
				LinkingScoring.windowTo(subject.lastArticleAt(), criteria.window()));
		return TermWeights.of(counts.events(), counts.eventsByTerm());
	}

	/** 견줄 것마다 점수 — 점수를 셀 수 없는 것은 빠진다 */
	static List<Scored> score(Subject subject, List<Compared> compared, LinkingCriteria criteria, TermWeights weights) {
		List<Scored> scored = new ArrayList<>();
		for (Compared other : compared) {
			LinkingScoring.score(subject, other, criteria.window(), weights)
				.ifPresent(score -> scored.add(new Scored(other.id(), score)));
		}
		return scored;
	}

	/** 구성 사건 가운데 프로필이 읽힌 것 — 넘긴 순서 */
	static List<EventProfile> readable(List<Long> members, Map<EventId, EventProfile> profiles) {
		return members.stream().map(member -> profiles.get(new EventId(member))).filter(p -> p != null).toList();
	}

	/**
	 * 띠 후보를 물을 때 그 후보를 서술할 구성 사건 — 판정할 사건과 모델 버전이 같은 구성 사건 가운데 {@link LinkingAsk#describe} 가 고른다.
	 * @param members 후보의 구성 사건
	 * @param profiles 읽은 구성 사건의 프로필 — 없는 사건은 서술에 들지 않는다
	 */
	static List<Long> describe(EventProfile subjectProfile, Subject subject, Collection<Long> members,
			Map<EventId, EventProfile> profiles) {
		Map<Long, float[]> centroids = new TreeMap<>();
		for (long member : members) {
			EventProfile profile = profiles.get(new EventId(member));
			if (profile != null && profile.modelVersion().equals(subjectProfile.modelVersion())) {
				centroids.put(member, profile.centroid());
			}
		}
		return LinkingAsk.describe(subject, subjectProfile.id().value(), centroids);
	}

}
