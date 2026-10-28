package net.yozmnews.source.domain.feed;

import java.util.List;

import net.yozmnews.source.domain.profile.PreCorrection;

/**
 * 파싱 전에 피드 원문에 프로필의 사전 보정을 적용한다.
 */
final class PreCorrector {

	private PreCorrector() {
	}

	/** 프로필에 적힌 순서대로 적용한다. 문자 인코딩 강제는 디코딩이 이미 썼다 */
	static String apply(String text, List<PreCorrection> corrections) {
		String corrected = text;
		for (PreCorrection correction : corrections) {
			corrected = switch (correction) {
				case PreCorrection.ReplaceUndefinedEntities replace -> EntityReferences.repair(corrected);
				case PreCorrection.StripInvalidControlChars strip -> XmlCharacters.strip(corrected);
				case PreCorrection.ForceEncoding force -> corrected;
			};
		}
		return corrected;
	}

}
