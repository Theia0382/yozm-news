import { Link, useParams } from "react-router";
import { EvaluationDetailLoader } from "@/features/admin/EvaluationDetail";

/** 평가 하나의 상세 화면 */
export function EvaluationDetailPage() {
  const { id } = useParams();
  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold">평가 상세</h2>
      <Link to="/admin/evaluations" className="text-sm underline">
        평가 결과로
      </Link>
      <EvaluationDetailLoader key={id} id={Number(id)} />
    </div>
  );
}
