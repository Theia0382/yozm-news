package net.yozmnews.credential.domain.key;

/**
 * KEK 버전 하나를 가리키는 키의 수 — 기동 검사가 읽는다(credential-R6).
 *
 * @param kekVersion KEK 버전
 * @param keys 그 버전으로 DEK 를 감싼 키의 수
 */
public record KekVersionUse(int kekVersion, long keys) {
}
