import { Link } from "react-router";
import { RecoveryCodeFlow } from "@/features/account/RecoveryCodeFlow";
import { SignUpForm } from "@/features/account/SignUpForm";
import { useSignUpPolicy } from "@/features/account/signUpQueries";
import { useApiClient } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 공개 가입 화면 — 운영자가 공개 가입을 열었을 때만 입력을 보인다. 가입하면 복구 코드를 보이고 첫 화면으로 간다.
 */
export function SignUpPage() {
  const client = useApiClient();
  const policy = useSignUpPolicy();

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-4`}>
      <h1 className="text-2xl font-bold">가입</h1>
      <RecoveryCodeFlow>
        {(onRecoveryCode) => {
          if (policy.isPending) {
            return (
              <p className="text-muted-foreground">가입 정책을 확인하는 중…</p>
            );
          }
          if (policy.isError) {
            return (
              <div className="space-y-3">
                <p role="alert">가입 정책을 확인하지 못했습니다.</p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => policy.refetch()}
                >
                  다시 시도
                </Button>
              </div>
            );
          }
          if (!policy.data.open) {
            return (
              <p>
                지금은 가입을 받지 않습니다. 운영자에게 초대 링크를 받으세요.{" "}
                <Link to="/login" className="underline">
                  로그인
                </Link>
              </p>
            );
          }
          return (
            <SignUpForm
              submit={(fields) =>
                client.POST("/api/sign-ups", { body: fields })
              }
              onSignedUp={onRecoveryCode}
            />
          );
        }}
      </RecoveryCodeFlow>
    </section>
  );
}
