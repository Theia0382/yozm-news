-- 설정 행(user_settings)의 자유 텍스트 관심사 칸을 지운다 — 관심 설정은 그 칸을 받지 않고 앱이 다시 읽지 않는다(ADR-0314).
-- 사용자가 쓴 값이지만 기능을 없앤 설정 칸이라 마이그레이션이 지울 수 있는 예외다(CONTRIBUTING.md §7)
ALTER TABLE user_settings DROP COLUMN interest_text;
