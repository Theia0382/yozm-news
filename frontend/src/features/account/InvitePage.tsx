import { useQuery } from "@tanstack/react-query";
import { useLocation } from "react-router";
import { invitationStateText } from "@/features/account/accountTexts";
import { RecoveryCodeFlow } from "@/features/account/RecoveryCodeFlow";
import { SignUpForm } from "@/features/account/SignUpForm";
import { useMe } from "@/features/session/sessionQueries";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 초대 가입 화면 — 토큰은 주소의 # 뒤(서버 로그 · Referer 에 실리지 않는다)에서 읽는다. 쓸 수 있는 초대면 가입 입력을, 아니면 그
 * 까닭을 보인다. 가입하면 복구 코드를 보이고 첫 화면으로 간다.
 */
export function InvitePage() {
  const client = useApiClient();
  const me = useMe();
  const { hash } = useLocation();
  const token = hash.startsWith("#") ? hash.slice(1) : hash;
  // 변경 요청이라 요청 보호 토큰(로그인 상태 조회가 받는다)이 있은 뒤에 묻는다
  const check = useQuery({
    queryKey: ["invitation-check", token],
    queryFn: async () => {
      const { data, response } = await client.POST("/api/invitation-checks", {
        body: { token },
      });
      if (!response.ok || !data) {
        throw new Error(`초대를 확인하지 못했습니다 (HTTP ${response.status})`);
      }
      return data.state;
    },
    enabled: token !== "" && me.isSuccess,
    retry: false,
  });

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-4`}>
      <h1 className="text-2xl font-bold">초대받아 가입</h1>
      <RecoveryCodeFlow>
        {(onRecoveryCode) => {
          if (token === "") {
            return <p role="alert">{invitationStateText("UNKNOWN")}</p>;
          }
          if (check.isError || me.isError) {
            return (
              <div className="space-y-3">
                <p role="alert">초대를 확인하지 못했습니다.</p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={async () => {
                    await me.refetch();
                    await check.refetch();
                  }}
                >
                  다시 시도
                </Button>
              </div>
            );
          }
          if (check.isPending) {
            return <p className="text-muted-foreground">초대를 확인하는 중…</p>;
          }
          if (check.data !== "VALID") {
            return <p role="alert">{invitationStateText(check.data)}</p>;
          }
          return (
            <SignUpForm
              submit={(fields) =>
                client.POST("/api/invitation-acceptances", {
                  body: { token, ...fields },
                })
              }
              onSignedUp={onRecoveryCode}
            />
          );
        }}
      </RecoveryCodeFlow>
    </section>
  );
}
