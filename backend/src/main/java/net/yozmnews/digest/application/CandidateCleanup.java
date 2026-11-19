package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;

import net.yozmnews.digest.domain.candidate.CandidateRetention;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보 행 보존 정리 — docs/specs/digest.md §4 · digest-R11
 */
@Service
public class CandidateCleanup {

	private static final Logger logger = LoggerFactory.getLogger(CandidateCleanup.class);

	private final DigestCandidateRepository candidates;

	private final InstantSource clock;

	CandidateCleanup(DigestCandidateRepository candidates, InstantSource clock) {
		this.candidates = candidates;
		this.clock = clock;
	}

	/**
	 * 보존 기한이 지난 다이제스트의 후보 행을 지운다.
	 * @return 지운 행 수
	 */
	@Transactional
	public int run() {
		Instant cutoff = CandidateRetention.cutoff(this.clock.instant());
		int deleted = this.candidates.deleteOfDigestsCreatedBefore(cutoff);
		if (deleted > 0) {
			logger.info("보존 기한이 지난 다이제스트 후보 행 {}건을 지웠다 — 기준 {}", deleted, cutoff);
		}
		return deleted;
	}

}
