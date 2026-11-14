package net.yozmnews.credential;

import net.yozmnews.platform.UserId;

/**
 * 사용자의 키 유무 — docs/specs/credential.md §3 · credential-R18
 */
public interface LlmKeys {

	/** 그 사용자의 저장한 키가 하나라도 있는가 */
	boolean present(UserId user);

}
