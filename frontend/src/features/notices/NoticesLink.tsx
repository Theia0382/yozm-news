import { useEffect, useRef } from "react";
import { Link, useLocation } from "react-router";
import { useUnreadNotices } from "@/features/notices/noticeQueries";

/** 머리줄 수 표시의 상한 — 넘으면 "99+" */
const MAX_SHOWN = 99;

/**
 * 머리줄의 알림 링크 — 안 읽은 알림이 있으면 수를 붙인다. 로그인 틀은 화면을 옮겨도 다시 그려지지 않으므로, 경로가 바뀌면 안 읽은
 * 수를 다시 읽는다(첫 그림의 조회와 겹치지 않게 바뀐 때만).
 */
export function NoticesLink() {
  // 첫 화면 묶음에 들어가므로 cn · 공용 버튼을 들이지 않는다 — RequireSignIn 과 같은 이유
  const unread = useUnreadNotices();
  const { pathname } = useLocation();
  const shownPath = useRef(pathname);
  const { refetch } = unread;

  useEffect(() => {
    if (shownPath.current !== pathname) {
      shownPath.current = pathname;
      void refetch();
    }
  }, [pathname, refetch]);

  const count = unread.data ?? 0;
  return (
    <Link
      to="/notices"
      className="underline"
      aria-label={count > 0 ? `알림, 안 읽음 ${count}개` : "알림"}
    >
      {count > 0
        ? `알림 ${count > MAX_SHOWN ? `${MAX_SHOWN}+` : count}`
        : "알림"}
    </Link>
  );
}
