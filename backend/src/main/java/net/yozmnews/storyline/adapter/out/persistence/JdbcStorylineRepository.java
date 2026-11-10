package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.platform.VectorText;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import net.yozmnews.storyline.domain.storyline.StorylineSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link StorylineRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 중심은 차원 없는 {@code halfvec} 이고 {@link VectorText} 의 텍스트 표현으로 쓰고 읽는다. 후보의 코사인은
 * {@code 1 − (중심 <=> 벡터)} 를 스토리라인마다 센다(인덱스를 쓰지 않는 정확 검색) — 거리가 NaN(영 벡터)인 행은 뺀다. 핵심 말 상위 수는
 * 매개변수이고 말 순은 바이트 순({@code COLLATE "C"})이다. 모듈 잠금은 트랜잭션 advisory 잠금 하나다.
 */
@Repository
class JdbcStorylineRepository implements StorylineRepository {

	/** advisory lock 의 첫 키 — 이 모듈의 판정 잠금을 다른 잠금과 가른다('STRY') */
	static final int LINKING_LOCK_NAMESPACE = 0x53545259;

	private final JdbcClient jdbc;

	JdbcStorylineRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void lockLinking() {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, 0)")
			.param("namespace", LINKING_LOCK_NAMESPACE)
			.query()
			.singleRow();
	}

	@Override
	public List<Long> storylinesOf(long eventId) {
		return this.jdbc.sql("select storyline_id from storyline_members where event_id = :event order by storyline_id")
			.param("event", eventId)
			.query(Long.class)
			.list();
	}

	@Override
	public Map<Long, List<Long>> members(Collection<Long> storylineIds) {
		if (storylineIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<Long>> members = new LinkedHashMap<>();
		this.jdbc.sql("""
				select storyline_id, event_id from storyline_members where storyline_id = any(:ids)
				order by storyline_id, event_id
				""").param("ids", storylineIds.toArray(Long[]::new)).query((ResultSet rs) -> {
			members.computeIfAbsent(rs.getLong("storyline_id"), id -> new ArrayList<>()).add(rs.getLong("event_id"));
		});
		Map<Long, List<Long>> result = new LinkedHashMap<>();
		members.forEach((storyline, events) -> result.put(storyline, List.copyOf(events)));
		return Collections.unmodifiableMap(result);
	}

	@Override
	public Set<Long> affiliated(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(new HashSet<>(this.jdbc
			.sql("select distinct event_id from storyline_members where event_id = any(:ids)")
			.param("ids", eventIds.toArray(Long[]::new))
			.query(Long.class)
			.list()));
	}

	@Override
	public List<StorylineSnapshot> candidates(String modelVersion, float[] vector, Instant from, Instant to,
			double minCosine, int keyTerms) {
		return this.jdbc.sql("""
				select s.id, s.centroid::text as centroid, s.first_article_at, s.last_article_at,
				       array(select k.term from storyline_key_terms k where k.storyline_id = s.id
				             order by k.article_count desc, k.term collate "C" limit :terms) as key_terms
				from storylines s
				where s.model_version = :model
				  and s.last_article_at >= :from and s.first_article_at <= :to
				  and (s.centroid <=> cast(:vector as halfvec)) <> 'NaN'
				  and 1 - (s.centroid <=> cast(:vector as halfvec)) >= :min
				order by s.id
				""")
			.param("terms", keyTerms)
			.param("model", modelVersion)
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.param("vector", VectorText.of(vector))
			.param("min", minCosine)
			.query((rs, row) -> new StorylineSnapshot(rs.getLong("id"), VectorText.parse(rs.getString("centroid")),
					instant(rs, "first_article_at"), instant(rs, "last_article_at"), strings(rs.getArray("key_terms"))))
			.list();
	}

	@Override
	public long open(Representation representation, Instant now) {
		long id = this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values (:model, cast(:centroid as halfvec), :count, :first, :last, :representative, :now, :now)
				returning id
				""")
			.param("model", representation.modelVersion())
			.param("centroid", VectorText.of(representation.centroid()))
			.param("count", representation.articleCount())
			.param("first", timestamp(representation.firstArticleAt()))
			.param("last", timestamp(representation.lastArticleAt()))
			.param("representative", representation.representativeEventId())
			.param("now", timestamp(now))
			.query(Long.class)
			.single();
		writeTerms(id, representation.keyTerms());
		return id;
	}

	@Override
	public void save(long storylineId, Representation representation, Instant now) {
		this.jdbc.sql("""
				update storylines set model_version = :model, centroid = cast(:centroid as halfvec), article_count = :count,
				                      first_article_at = :first, last_article_at = :last,
				                      representative_event_id = :representative, updated_at = :now
				where id = :id
				""")
			.param("id", storylineId)
			.param("model", representation.modelVersion())
			.param("centroid", VectorText.of(representation.centroid()))
			.param("count", representation.articleCount())
			.param("first", timestamp(representation.firstArticleAt()))
			.param("last", timestamp(representation.lastArticleAt()))
			.param("representative", representation.representativeEventId())
			.param("now", timestamp(now))
			.update();
		this.jdbc.sql("delete from storyline_key_terms where storyline_id = :id").param("id", storylineId).update();
		writeTerms(storylineId, representation.keyTerms());
	}

	@Override
	public void addMember(long storylineId, long eventId, LinkJudgement judgement) {
		Score score = judgement.score();
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, score, cosine, key_term_overlap,
				                               criteria_id, linked_at)
				values (:storyline, :event, :by, :score, :cosine, :overlap, :criteria, :at)
				""")
			.param("storyline", storylineId)
			.param("event", eventId)
			.param("by", judgement.linkedBy().name())
			.param("score", (score != null) ? score.score() : null, Types.DOUBLE)
			.param("cosine", (score != null) ? score.cosine() : null, Types.DOUBLE)
			.param("overlap", (score != null) ? score.overlap() : null, Types.DOUBLE)
			.param("criteria", judgement.criteriaId(), Types.BIGINT)
			.param("at", timestamp(judgement.linkedAt()))
			.update();
	}

	@Override
	public void moveMember(long storylineId, long fromEventId, long toEventId, Instant now) {
		this.jdbc.sql("""
				update storyline_members
				set event_id = :to, linked_by = 'MERGE', score = null, cosine = null, key_term_overlap = null,
				    criteria_id = null, linked_at = :now
				where storyline_id = :storyline and event_id = :from
				""")
			.param("storyline", storylineId)
			.param("from", fromEventId)
			.param("to", toEventId)
			.param("now", timestamp(now))
			.update();
	}

	@Override
	public void removeMember(long storylineId, long eventId) {
		this.jdbc.sql("delete from storyline_members where storyline_id = :storyline and event_id = :event")
			.param("storyline", storylineId)
			.param("event", eventId)
			.update();
	}

	@Override
	public void delete(long storylineId) {
		// 핵심 말 · 소속은 FK ON DELETE CASCADE 로 함께 지워진다
		this.jdbc.sql("delete from storylines where id = :id").param("id", storylineId).update();
	}

	private void writeTerms(long storylineId, Map<String, Integer> terms) {
		if (terms.isEmpty()) {
			return;
		}
		List<String> words = new ArrayList<>(terms.keySet());
		this.jdbc.sql("""
				insert into storyline_key_terms (storyline_id, term, article_count)
				select :id, t.term, t.count from unnest(cast(:terms as text[]), cast(:counts as integer[])) as t(term, count)
				""")
			.param("id", storylineId)
			.param("terms", words.toArray(String[]::new))
			.param("counts", words.stream().map(terms::get).toArray(Integer[]::new))
			.update();
	}

	private static List<String> strings(Array array) throws SQLException {
		return List.of((String[]) array.getArray());
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
