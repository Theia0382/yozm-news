-- user 모듈 — 실패 기록의 종류(로그인 · 복구 코드)와 비밀번호 알림(docs/specs/user.md §6).

-- 실패 기록은 종류마다 따로 센다 — 같은 일정 · 같은 키(제출된 정규화 이메일의 다이제스트), 다른 기록. 있는 기록은 로그인이다
ALTER TABLE login_failures ADD COLUMN attempt TEXT NOT NULL DEFAULT 'SIGN_IN' CHECK (attempt IN ('SIGN_IN', 'RECOVERY'));
ALTER TABLE login_failures ALTER COLUMN attempt DROP DEFAULT;
ALTER TABLE login_failures DROP CONSTRAINT login_failures_pkey;
ALTER TABLE login_failures ADD PRIMARY KEY (attempt, email_digest);

-- 비밀번호가 바뀌었다는 알림 — 바뀔 때마다 하나(모으지 않는다)
ALTER TABLE user_notices DROP CONSTRAINT user_notices_kind_check;
ALTER TABLE user_notices ADD CONSTRAINT user_notices_kind_check CHECK (kind IN ('FEED_STATUS', 'PASSWORD_CHANGED'));
