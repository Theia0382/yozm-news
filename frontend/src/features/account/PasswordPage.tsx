import { Navigate } from "react-router";
import { PasswordChangeForm } from "@/features/account/PasswordChangeForm";
import { RecoveryCodeFlow } from "@/features/account/RecoveryCodeFlow";
import { useMe } from "@/features/session/sessionQueries";
import { NARROW_WIDTH, PAGE_FRAME } from "@/shared/ui/widths";

/**
 * 첫 로그인의 비밀번호 변경 — 남이 정한 초기 비밀번호를 쓰는 계정은 이 화면만 쓴다. 바꾸면 복구 코드를 보이고 첫 화면으로 간다. 강제
 * 변경 대상이 아니면 설정으로 보낸다(코드를 보이는 동안은 보내지 않는다).
 */
export function PasswordPage() {
  const me = useMe();

  return (
    <section className={`${PAGE_FRAME} ${NARROW_WIDTH} space-y-4`}>
      <h1 className="text-2xl font-bold">비밀번호 변경</h1>
      <RecoveryCodeFlow>
        {(onRecoveryCode) =>
          me.data?.passwordChangeRequired === false ? (
            <Navigate to="/settings" replace />
          ) : (
            <>
              <p className="text-sm">
                운영자가 정해 준 비밀번호를 쓰고 있습니다. 계속하려면 비밀번호를
                새로 정하세요.
              </p>
              <PasswordChangeForm onChanged={onRecoveryCode} />
            </>
          )
        }
      </RecoveryCodeFlow>
    </section>
  );
}
