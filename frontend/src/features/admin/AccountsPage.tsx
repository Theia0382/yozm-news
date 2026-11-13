import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import { CopyButton } from "@/features/account/CopyButton";
import { TextField } from "@/features/account/TextField";
import {
  failureText,
  type Schemas,
  unwrap,
} from "@/features/admin/adminClient";
import { ADMIN_QUERY_KEY, useAdminClient } from "@/features/admin/adminSession";
import {
  changeActorLabels,
  invitationStateLabels,
} from "@/features/admin/labels";
import { REVIEW_TABLE } from "@/features/admin/tables";
import { formatDateTime } from "@/shared/lib/dateTime";
import { Button } from "@/shared/ui/button";

type Invitation = Schemas["InvitationRowResponse"];
type Issued = Schemas["InvitationIssueResponse"];

/** 결과 판별자의 안내 — 성공은 status, 하지 않은 것은 alert */
type Outcome = { role: "status" | "alert"; text: string };

const creationOutcomes = {
  CREATED: {
    role: "status",
    text: "계정을 만들었습니다. 첫 로그인에서 비밀번호를 바꿔야 합니다.",
  },
  EMAIL_TAKEN: {
    role: "alert",
    text: "이미 있는 이메일이라 만들지 않았습니다.",
  },
} as const satisfies Record<
  Schemas["AccountCreationResponse"]["result"],
  Outcome
>;

const resetOutcomes = {
  RESET: {
    role: "status",
    text: "초기 비밀번호를 다시 정했습니다. 그 계정의 로그인은 모두 끝났습니다.",
  },
  NOT_PENDING: {
    role: "alert",
    text: "이미 비밀번호를 바꾼 계정이라 바꾸지 않았습니다.",
  },
} as const satisfies Record<
  Schemas["InitialPasswordResetResponse"]["result"],
  Outcome
>;

const revocationOutcomes = {
  REVOKED: { role: "status", text: "초대를 취소했습니다." },
  NOT_REVOCABLE: {
    role: "alert",
    text: "유효한 초대가 아니라 취소하지 않았습니다.",
  },
} as const satisfies Record<
  Schemas["InvitationRevocationResponse"]["result"],
  Outcome
>;

const policyChangeOutcomes = {
  CHANGED: { role: "status", text: "공개 가입을 바꿨습니다." },
  UNCHANGED: { role: "status", text: "이미 그 값이라 바꾸지 않았습니다." },
} as const satisfies Record<
  Schemas["SignUpPolicyChangeResponse"]["result"],
  Outcome
>;

function OutcomeText({ outcome }: { outcome: Outcome }) {
  return <p role={outcome.role}>{outcome.text}</p>;
}

/** 계정 화면 조회 키의 접두 */
const ACCOUNTS_KEY = [...ADMIN_QUERY_KEY, "accounts"];
const POLICY_KEY = [...ACCOUNTS_KEY, "sign-up-policy"];
const INVITATIONS_KEY = [...ACCOUNTS_KEY, "invitations"];

/** 초대 링크 — 이 화면을 연 주소로 만든다. 토큰은 # 뒤라 서버 로그 · Referer 에 실리지 않는다 */
function invitationLink(token: string): string {
  return `${window.location.origin}/invite#${token}`;
}

/**
 * 계정 — 공개 가입 정책(켬 · 끔과 이력), 초대 링크 발급 · 목록 · 취소, 계정 만들기, 첫 로그인 전 계정의 초기 비밀번호 다시 정하기. 초대와
 * 운영자가 만든 계정은 언제나 일반 사용자다.
 */
export function AccountsPage() {
  return (
    <div className="space-y-10">
      <h2 className="text-xl font-semibold">계정</h2>
      <SignUpPolicySection />
      <InvitationsSection />
      <AccountCreationSection />
      <InitialPasswordResetSection />
    </div>
  );
}

function SignUpPolicySection() {
  const client = useAdminClient();
  const queries = useQueryClient();
  const status = useQuery({
    queryKey: POLICY_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/sign-up-policy")),
  });
  const change = useMutation({
    mutationFn: (open: boolean) =>
      unwrap(client.PUT("/api/admin/sign-up-policy", { body: { open } })),
    onSuccess: () => queries.invalidateQueries({ queryKey: POLICY_KEY }),
  });

  return (
    <section aria-labelledby="sign-up-policy" className="space-y-3">
      <h3 id="sign-up-policy" className="text-lg font-semibold">
        공개 가입
      </h3>
      <p className="text-sm text-muted-foreground">
        열면 누구나 로그인 화면에서 가입할 수 있습니다. 닫혀 있어도 초대
        링크로는 가입할 수 있습니다. 공개 가입에는 요청 제한 · 봇 차단이
        없습니다.
      </p>
      {status.error && <p role="alert">{failureText(status.error)}</p>}
      {status.data && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <p>지금: {status.data.open ? "열림" : "닫힘"}</p>
            <Button
              type="button"
              variant="outline"
              disabled={change.isPending}
              onClick={() => change.mutate(!status.data.open)}
            >
              {status.data.open ? "공개 가입 닫기" : "공개 가입 열기"}
            </Button>
          </div>
          {change.error && <p role="alert">{failureText(change.error)}</p>}
          {change.data && (
            <OutcomeText outcome={policyChangeOutcomes[change.data.result]} />
          )}
          <section aria-label="공개 가입 이력" className="space-y-1 text-sm">
            <h4 className="font-medium">이력 — 최근 것부터</h4>
            <ul>
              {status.data.changes.map((entry) => (
                <li key={entry.changedAt + String(entry.open)}>
                  {formatDateTime(entry.changedAt)} ·{" "}
                  {changeActorLabels[entry.actor]} ·{" "}
                  {entry.previousOpen === null
                    ? ""
                    : `${openText(entry.previousOpen)} → `}
                  {openText(entry.open)}
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </section>
  );
}

function openText(open: boolean): string {
  return open ? "열림" : "닫힘";
}

function InvitationsSection() {
  const client = useAdminClient();
  const queries = useQueryClient();
  const [memo, setMemo] = useState("");
  const list = useQuery({
    queryKey: INVITATIONS_KEY,
    queryFn: () => unwrap(client.GET("/api/admin/invitations")),
  });
  const issue = useMutation({
    // 메모의 앞뒤 공백 · 빈 값은 서버의 메모 규칙이 정한다 — 입력 그대로 보낸다
    mutationFn: (memo: string) =>
      unwrap(client.POST("/api/admin/invitations", { body: { memo } })),
    onSuccess: () => {
      setMemo("");
      return queries.invalidateQueries({ queryKey: INVITATIONS_KEY });
    },
  });
  const revoke = useMutation({
    mutationFn: (id: number) =>
      unwrap(
        client.POST("/api/admin/invitations/{id}/revoke", {
          params: { path: { id } },
        }),
      ),
    onSettled: () => queries.invalidateQueries({ queryKey: INVITATIONS_KEY }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    issue.mutate(memo);
  }

  return (
    <section aria-labelledby="invitations" className="space-y-3">
      <h3 id="invitations" className="text-lg font-semibold">
        초대
      </h3>
      <p className="text-sm text-muted-foreground">
        초대 링크 하나로 한 사람이 가입합니다. 링크는 만든 때 한 번만 보이고,
        만료 시각이 지나면 쓸 수 없습니다.
      </p>
      <form onSubmit={submit} className="max-w-sm space-y-3">
        <TextField
          label="메모 (선택 — 누구에게 보낼 링크인지)"
          name="memo"
          value={memo}
          onValueChange={setMemo}
        />
        <Button type="submit" disabled={issue.isPending}>
          초대 링크 만들기
        </Button>
      </form>
      {issue.error && <p role="alert">{failureText(issue.error)}</p>}
      {issue.data && <IssuedInvitation issued={issue.data} />}
      {revoke.error && <p role="alert">{failureText(revoke.error)}</p>}
      {revoke.data && (
        <OutcomeText outcome={revocationOutcomes[revoke.data.result]} />
      )}
      {list.error && <p role="alert">{failureText(list.error)}</p>}
      {list.data &&
        (list.data.invitations.length === 0 ? (
          <p className="text-sm">발급한 초대가 없습니다.</p>
        ) : (
          <InvitationTable
            rows={list.data.invitations}
            revoking={revoke.isPending}
            onRevoke={(id) => revoke.mutate(id)}
          />
        ))}
    </section>
  );
}

function IssuedInvitation({ issued }: { issued: Issued }) {
  const link = invitationLink(issued.token);
  return (
    <div className="space-y-2 rounded-md border p-3">
      <p className="text-sm">
        초대 링크 — 주소까지 확인하고 전하세요. 이 화면을 떠나면 다시 볼 수
        없습니다. 만료: {formatDateTime(issued.expiresAt)}
      </p>
      <p
        aria-label="초대 링크"
        className="font-mono text-sm break-all select-all"
      >
        {link}
      </p>
      <CopyButton value={link} />
    </div>
  );
}

function InvitationTable({
  rows,
  revoking,
  onRevoke,
}: {
  rows: Invitation[];
  revoking: boolean;
  onRevoke: (id: number) => void;
}) {
  return (
    // relative — 표 안 숨은 칸 머리(sr-only, absolute)가 이 상자를 기준으로 놓여 페이지 폭을 넓히지 않는다
    <div className="relative overflow-x-auto">
      <table
        aria-label="최근 초대"
        className={`${REVIEW_TABLE} w-full text-left text-sm`}
      >
        <thead>
          <tr>
            <th className="font-medium">발급</th>
            <th className="font-medium">메모</th>
            <th className="font-medium">상태</th>
            <th className="font-medium">만료</th>
            <th className="font-medium">가입한 계정</th>
            <th className="font-medium">
              <span className="sr-only">동작</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>{formatDateTime(row.createdAt)}</td>
              <td>{row.memo ?? ""}</td>
              <td>{invitationStateLabels[row.state]}</td>
              <td>{formatDateTime(row.expiresAt)}</td>
              <td>{row.usedByEmail ?? ""}</td>
              <td>
                {row.state === "VALID" && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={revoking}
                    onClick={() => onRevoke(row.id)}
                  >
                    취소
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AccountCreationSection() {
  const client = useAdminClient();
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [initialPassword, setInitialPassword] = useState("");
  const create = useMutation({
    mutationFn: () =>
      unwrap(
        client.POST("/api/admin/accounts", {
          body: { email, displayName, initialPassword },
        }),
      ),
    onSuccess: (data) => {
      if (data.result === "CREATED") {
        setInitialPassword("");
      }
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <section aria-labelledby="account-creation" className="space-y-3">
      <h3 id="account-creation" className="text-lg font-semibold">
        계정 만들기
      </h3>
      <p className="text-sm text-muted-foreground">
        일반 사용자 계정을 만듭니다. 초기 비밀번호를 그 사람에게 따로 전하면, 첫
        로그인에서 비밀번호를 새로 정합니다.
      </p>
      <form onSubmit={submit} className="max-w-sm space-y-3">
        <TextField
          label="이메일"
          type="email"
          name="email"
          autoComplete="off"
          required
          value={email}
          onValueChange={setEmail}
        />
        <TextField
          label="표시 이름"
          name="displayName"
          autoComplete="off"
          required
          value={displayName}
          onValueChange={setDisplayName}
        />
        <TextField
          label="초기 비밀번호"
          type="password"
          name="initialPassword"
          autoComplete="new-password"
          required
          value={initialPassword}
          onValueChange={setInitialPassword}
        />
        <Button type="submit" disabled={create.isPending}>
          계정 만들기
        </Button>
      </form>
      {create.error && <p role="alert">{failureText(create.error)}</p>}
      {create.data && (
        <OutcomeText outcome={creationOutcomes[create.data.result]} />
      )}
    </section>
  );
}

function InitialPasswordResetSection() {
  const client = useAdminClient();
  const [email, setEmail] = useState("");
  const [initialPassword, setInitialPassword] = useState("");
  const reset = useMutation({
    mutationFn: () =>
      unwrap(
        client.PUT("/api/admin/accounts/initial-password", {
          body: { email, initialPassword },
        }),
      ),
    onSuccess: (data) => {
      if (data.result === "RESET") {
        setInitialPassword("");
      }
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    reset.mutate();
  }

  return (
    <section aria-labelledby="initial-password-reset" className="space-y-3">
      <h3 id="initial-password-reset" className="text-lg font-semibold">
        초기 비밀번호 다시 정하기
      </h3>
      <p className="text-sm text-muted-foreground">
        아직 첫 로그인에서 비밀번호를 바꾸지 않은 계정만 다시 정합니다. 그
        계정의 로그인은 모두 끝납니다.
      </p>
      <form onSubmit={submit} className="max-w-sm space-y-3">
        <TextField
          label="이메일"
          type="email"
          name="email"
          autoComplete="off"
          required
          value={email}
          onValueChange={setEmail}
        />
        <TextField
          label="새 초기 비밀번호"
          type="password"
          name="initialPassword"
          autoComplete="new-password"
          required
          value={initialPassword}
          onValueChange={setInitialPassword}
        />
        <Button type="submit" disabled={reset.isPending}>
          다시 정하기
        </Button>
      </form>
      {reset.error && <p role="alert">{failureText(reset.error)}</p>}
      {reset.data && <OutcomeText outcome={resetOutcomes[reset.data.result]} />}
    </section>
  );
}
