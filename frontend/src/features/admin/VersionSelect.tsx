import { useId } from "react";
import { failureText } from "@/features/admin/adminClient";
import {
  type useTagSetVersions,
  versionLabel,
} from "@/features/admin/tagSetVersions";

/** 태그 체계 버전 고르기 — 목록을 읽지 못하면 그 문구를 보인다 */
export function VersionSelect({
  versions,
  value,
  onChange,
}: {
  versions: ReturnType<typeof useTagSetVersions>;
  value: number | null;
  onChange: (version: number) => void;
}) {
  const id = useId();
  if (versions.error) {
    return <p role="alert">{failureText(versions.error)}</p>;
  }
  if (!versions.data) {
    return null;
  }
  return (
    <label htmlFor={id} className="flex items-center gap-2 text-sm">
      태그 체계 버전
      <select
        id={id}
        value={value ?? ""}
        onChange={(event) => onChange(Number(event.target.value))}
        className="rounded-md border border-input px-2 py-1"
      >
        {versions.data.versions.map((item) => (
          <option key={item.id} value={item.id}>
            {versionLabel(item)}
          </option>
        ))}
      </select>
    </label>
  );
}
