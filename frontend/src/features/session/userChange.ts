import { matchQuery, type QueryClient } from "@tanstack/react-query";
import {
  type Me,
  ME_QUERY_KEY,
  SESSION_QUERY_KEY,
} from "@/features/session/sessionQueries";

/** 감시가 아직 로그인 상태를 읽지 않았다 */
const NOT_YET = Symbol("not yet");

/**
 * 로그인 상태 조회가 앞서 읽은 것과 다른 사용자(익명 포함)를 돌려주면 로그인 상태 조회 밖의 캐시를 모두 비운다 — 같은 창의 로그인 · 가입 ·
 * 복구 재설정과, 같은 브라우저의 다른 탭에서 바뀐 사용자(탭이 다시 보일 때 다시 읽는다)를 함께 덮는다. 처음 읽은 결과는 견줄 앞 사용자가 없어
 * 비우지 않는다. 앱의 QueryClient 마다 한 번 건다(docs/specs/user.md user-R20).
 *
 * 캐시에서 지우지 않고 처음 상태로 되돌린다(reset) — 지금 화면이 관찰하는 조회를 지우면 그 화면은 캐시에서 떨어진 조회에 묶인 채 앞 사용자의
 * 값을 계속 그린다. 되돌리면 관찰하는 조회는 새 사용자로 다시 받고, 관찰하지 않는 조회는 데이터가 비어 다음에 처음부터 받는다.
 * @returns 감시를 멈추는 함수
 */
export function forgetOtherUsersData(queryClient: QueryClient): () => void {
  let seen: number | null | typeof NOT_YET = NOT_YET;
  return queryClient.getQueryCache().subscribe((event) => {
    if (
      event.type !== "updated" ||
      event.action.type !== "success" ||
      !matchQuery({ queryKey: ME_QUERY_KEY, exact: true }, event.query)
    ) {
      return;
    }
    const me = event.query.state.data as Me;
    const user = me.result === "SIGNED_IN" ? me.userId : null;
    if (seen !== NOT_YET && user !== seen) {
      void queryClient.resetQueries({
        predicate: (query) => query.queryKey[0] !== SESSION_QUERY_KEY[0],
      });
    }
    seen = user;
  });
}
