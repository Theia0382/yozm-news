"""도구가 읽는 운영자 API 필드(USED_FIELDS)가 저장소의 화면용 API 명세(api/openapi.json)에 있고 필수인지 대조한다."""

import json
import unittest
from pathlib import Path

from admin_api import USED_FIELDS

SPEC = Path(__file__).resolve().parents[2] / "api" / "openapi.json"


def problems(spec: dict) -> list[str]:
    schemas = spec.get("components", {}).get("schemas", {})
    found = []
    for schema, fields in USED_FIELDS.items():
        if schema not in schemas:
            found.append(f"스키마 {schema} 가 명세에 없다")
            continue
        properties = schemas[schema].get("properties", {})
        required = set(schemas[schema].get("required", []))
        for name in fields:
            if name not in properties:
                found.append(f"{schema}.{name} 가 명세에 없다")
            elif name not in required:
                found.append(f"{schema}.{name} 가 필수가 아니다")
    return found


class ContractTest(unittest.TestCase):

    def test_used_fields_exist_and_are_required(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        self.assertEqual(problems(spec), [])


if __name__ == "__main__":
    unittest.main()
