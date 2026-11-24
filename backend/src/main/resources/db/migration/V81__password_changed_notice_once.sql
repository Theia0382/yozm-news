-- user 모듈 — 비밀번호 알림은 비밀번호 바뀜 이벤트마다 하나(docs/specs/user.md §6 · user-R74).

-- 같은 변경(사용자 · 바꾼 시각)의 알림은 하나다 — 같은 이벤트를 거듭 받아도 하나만 남는다
CREATE UNIQUE INDEX user_notices_password_changed_key
  ON user_notices (user_id, (content ->> 'changedAt'))
  WHERE kind = 'PASSWORD_CHANGED';
