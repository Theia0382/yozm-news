import { createContext, useContext } from "react";
import type { AdminClient } from "@/features/admin/adminClient";

/** 운영자 화면이 함께 쓰는 클라이언트 — AdminLayout 이 토큰을 확인한 뒤 내려 준다 */
export const AdminSessionContext = createContext<AdminClient | null>(null);

export function useAdminClient(): AdminClient {
  const client = useContext(AdminSessionContext);
  if (!client) {
    throw new Error("운영자 화면은 AdminLayout 안에서만 쓴다");
  }
  return client;
}

/** 운영자 데이터의 조회 키 — 토큰이 바뀌면 이 접두로 한꺼번에 버린다 */
export const ADMIN_QUERY_KEY = ["admin"] as const;
