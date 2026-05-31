# 재무제표 요약 인계 문서 (3.1.3)

## 개요

기능명세서 **3.1.3 공시 리포트 요약**의 "재무제표 요약" 부분에 사용할 데이터 파이프라인이 백엔드에 구현되어 있습니다. `company_financial_highlights` 테이블에 연도별 재무 하이라이트(매출액·영업이익·당기순이익·자산·부채·자본 총계)가 적재됩니다. 프론트에서 호출할 API 2개도 준비되어 있어 그대로 사용하시면 됩니다.

## 데이터 소스 (참고용 — 호출 측은 신경 쓸 필요 없음)

회사마다 다음 두 경로 중 하나로 채워집니다. 응답 형식은 동일합니다.

| 경로 | 출처 | 단위 |
|---|---|---|
| **DART 재무제표 API** | 사업보고서가 DART 에 등록된 큰 회사 (예: 피스피스스튜디오, 스트라드비젼) | 원 |
| **공시 원문 fallback** | DART 사업보고서 미등록 신규 IPO. 증권신고서·투자설명서에서 직접 추출 | 원 (백만원/천원 단위 자동 정규화) |

스케줄러(매일 새벽 4시)가 자동으로 동기화하며, 홈 노출 범위(최근 3개월 ~ 향후 4주)의 IPO 가 대상.

## API 2종 — 어느 걸 쓸지

### 1. `GET /api/ipo/{ipoEventId}/financials`

**용도**: 데이터가 확실히 있다는 가정 하에 차트만 그릴 때. 또는 데이터 없으면 그 영역을 아예 안 그릴 때.

**응답**: `IpoFinancialResponse[]` — 최신 2개 사업연도, `bsnsYear` 오름차순. 데이터 없으면 `[]` (404 아님).

### 2. `GET /api/ipo/{ipoEventId}/financials/status` ← **공시 리포트 요약 화면에서 권장**

**용도**: 데이터 없을 때도 사용자에게 사유를 보여줘야 할 때. 기능명세서 3.1.3 의 "재무제표 요약" 카드는 "데이터 없음" 케이스도 다뤄야 하므로 이쪽 추천.

**응답 예시**:
```json
{
  "financials": [
    {
      "year": "2023",
      "revenue": 12345000000,
      "operatingProfit": -2946000000,
      "netIncome": -1844000000,
      "totalAssets": 10940000000,
      "totalLiabilities": 1300000000,
      "totalEquity": 9640000000
    },
    {
      "year": "2024",
      "revenue": 15000000000,
      "operatingProfit": -1500000000,
      "netIncome": -1000000000,
      "totalAssets": 12000000000,
      "totalLiabilities": 1500000000,
      "totalEquity": 10500000000
    }
  ],
  "available": true,
  "unavailableReason": null,
  "message": "재무 차트 데이터를 조회했습니다."
}
```

## Null / 빈 데이터 처리 가이드

### 케이스 1: `available=false` (전체 미가용)

`financials = []`, `unavailableReason` 채워짐, `message` 채워짐. 프론트는 `unavailableReason` 으로 분기:

| `unavailableReason` | 의미 | 프론트 표시 예시 |
|---|---|---|
| `SPAC` | 스팩이라 영업 매출 자체가 0. 정상 상황 | "스팩은 영업 활동을 하지 않아 매출이 표시되지 않습니다" — 그 옆에 공모 자금/투자금 안내 |
| `DISCLOSURE_NOT_PARSED` | 공시 원문 파싱 전 (일시적) | "공시 원문 파싱이 완료되면 표시됩니다" — 회색 처리 + 추후 자동 갱신 |
| `FINANCIAL_TABLE_NOT_FOUND` | 원문은 있는데 재무표 못 찾음 | "공시에서 재무제표 표를 찾지 못했습니다" — 비활성 처리 |
| `NO_FINANCIAL_DATA` | 그 외 | "재무 데이터가 아직 등록되지 않았습니다" |

`message` 필드를 그대로 노출해도 무방하지만 톤 매니지먼트는 디자인에 맞춰 수정 가능.

### 케이스 2: `available=true` 이나 일부 필드만 `null`

DART API 경로는 모든 필드 채워지지만, **공시 원문 fallback 경로는 일부 필드가 `null` 일 수 있습니다.**

예: 회사 4(매드업)의 2023년치는 매출액만 있고 영업이익은 null 같은 부분 누락 가능.

**프론트 처리 권장:**
- 차트: null 필드는 0 으로 치환하지 말고 **bar 자체를 그리지 않거나, "데이터 없음" 점선/회색 처리**
- 텍스트 라벨: `null` 인 항목은 "-" 또는 빈 칸으로 표기
- 0과 null 의 의미가 다름:
  - `revenue = 0` → 실제로 매출 0 (SPAC 또는 영업 전 단계)
  - `revenue = null` → 데이터 미발견

### 케이스 3: 음수 값

손실/자본잠식 케이스. 정상 데이터입니다.
- `operatingProfit < 0` → 영업손실
- `netIncome < 0` → 순손실
- `totalEquity < 0` → 자본잠식 (드물지만 IPO 직전 회사에서 발생 가능)

빨간색 표시 등으로 시각적 구분 권장.

## 스웨거

- `IpoController` (`/api/ipo/...`) 에 `@Operation` 으로 두 API 의 사용처/필드/미가용 사유가 모두 명시되어 있습니다.
- DTO (`IpoFinancialResponse`, `IpoFinancialStatusResponse`) 각 필드에 `@Schema(description, example, nullable)` 로 의미와 null 가능성이 표시되어 있어, **스웨거 UI 에서 한 줄씩 펼쳐 보면 의도 확인 가능**합니다.
- `FinancialDataUnavailableReason` enum 도 4가지 값의 의미가 스키마에 포함되어 있습니다.

스웨거 URL: `https://modu-be.o-r.kr/swagger-ui/index.html` → "Ipo" 태그 → `/{ipoEventId}/financials` / `/{ipoEventId}/financials/status`

## 호출 예시

```bash
# 정상 케이스 (피스피스스튜디오, ipoEventId 는 환경마다 다름)
curl https://modu-be.o-r.kr/api/ipo/{ipoEventId}/financials/status

# SPAC 케이스 — available=false, unavailableReason=SPAC 응답
curl https://modu-be.o-r.kr/api/ipo/{SPAC_ipoEventId}/financials/status
```

## 현재 데이터 가용성 (참고 — 2026-05-27 기준 배포 DB)

| 회사 ID | 회사명 | 가용 상태 |
|---|---|---|
| 1, 7, 8 | 스팩 3종 | `unavailableReason=SPAC` |
| 5 | 피스피스스튜디오 | 3년치 (DART API) ✓ |
| 9 | 스트라드비젼 | 3년치 (DART API) ✓ |
| 2, 3, 4, 6, 10 | 그 외 일반 회사 | `unavailableReason=FINANCIAL_TABLE_NOT_FOUND` |

신규 IPO 가 추가되면 새벽 스케줄러가 자동으로 채워줍니다.

## 알려진 제한

### Fallback parser 정확도 한계

DART 재무제표 API 에 사업보고서가 등록되지 않은 신규 IPO 는 공시 원문에서 직접 추출(fallback)을 시도합니다.
그러나 공시 원문은 HTML 표를 텍스트로 평탄화한 형태라 다음 한계가 있습니다:

- 표 구조가 사라져 "매출액" 라인 매칭 실패 시 다른 항목 값을 잘못 매핑할 수 있음
- 한 문서에 "백만원"·"천원" 단위가 섞여 있어 단위 오감지로 자릿수가 어긋날 수 있음

이런 잘못된 값을 사용자에게 노출하는 것보다 "데이터 없음"이 안전하므로, 백엔드에서 다음 sanity check 를 통과하지 못한 행은 저장하지 않습니다:

| 체크 | 조건 |
|---|---|
| 매출액 존재 | `revenue == null` 이면 행 폐기 (표 매핑 신뢰 불가) |
| 매출액 현실성 | 음수 또는 1억원 미만이면 폐기 |
| 자릿수 폭증 | 단일 항목이 100조원 초과면 폐기 (단위 오감지 차단) |
| 영업이익 대 매출액 비율 | `\|operatingProfit\| > revenue × 10` 이면 폐기 (컬럼 매핑 오류 차단) |

결과적으로 응답에 들어오는 데이터는 검증을 통과한 값이지만, 그 대신 **일부 회사는 영영 `FINANCIAL_TABLE_NOT_FOUND` 로 분기될 수 있습니다.** 프론트는 이 케이스를 자연스럽게 처리해주세요.

### 후속 개선 (별도 이슈)

더 많은 회사를 fallback 으로 커버하려면 AI 기반 추출(공시 텍스트를 Claude 에 주고 JSON 으로 재무 데이터를 받는 방식)이 필요합니다. 토큰 비용이 추가되어 별도 PR 로 검토 예정.
