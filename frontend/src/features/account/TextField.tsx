import type { InputHTMLAttributes } from "react";

type TextFieldProps = {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange">;

/** 계정 화면의 한 줄 입력 — 이름표와 입력 칸 */
export function TextField({
  label,
  value,
  onValueChange,
  ...input
}: TextFieldProps) {
  return (
    <label className="block space-y-1">
      <span className="text-sm">{label}</span>
      <input
        {...input}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        className="w-full rounded-md border border-input bg-background px-2 py-1"
      />
    </label>
  );
}
