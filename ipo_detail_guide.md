# 공모주 상세 정보 API 구현 가이드 (기능명세서 3.1.3)

## 1. 개요

기능명세서 **3.1.3 공모주 상세 정보 및 공시 리포트 요약** 중 **상세 정보(청약/예측/기업 탭)** API를 만드는 작업입니다.
데이터 구성(엔티티, 응답 DTO)과 재사용 컴포넌트는 이미 준비돼 있고, **조회 서비스(비즈니스 코드)만 작성**하면 됩니다.

이미 준비된 것:
- 응답 DTO: `IpoDetailResponse` (`domain/dto/ipo/IpoDetailResponse.java`)
- 신호등 계산: `IpoSignalCalculator` (`service/ipo/IpoSignalCalculator.java`)
- 공시 리포트 요약 API: `GET /api/ipo/{ipoEventId}/disclosure` (이미 구현됨 — 상세 화면의 공시 요약 영역은 이 API를 그대로 사용)
- 재무 차트 API: `GET /api/ipo/{ipoEventId}/financials` (이미 구현됨)

만들어야 할 것:
- `IpoDetailQueryService` — 상세 정보를 조립하는 조회 서비스
- `IpoController`에 `GET /api/ipo/{ipoEventId}/detail` 엔드포인트 추가

## 2. 응답 DTO 구조 (`IpoDetailResponse`)

```
IpoDetailResponse
├─ ipoEventId, companyName, signalLevel, riskScore
├─ subscription (청약 탭)
├─ forecast      (예측 탭)
└─ company       (기업 탭)
```

공시 리포트 요약은 이 DTO에 포함하지 않습니다. 프론트가 `/disclosure` API를 별도 호출합니다.

## 3. 필드별 데이터 출처

| DTO 필드 | 출처 엔티티 | 비고 |
|---|---|---|
| `ipoEventId`, `companyName` | `IpoEvent`, `IpoEvent.company.corpName` | |
| `signalLevel`, `riskScore` | `IpoSignalCalculator.calculate(event.getMetric())` | 재사용 |
| **subscription** | | |
| `subscriptionStartDate`, `subscriptionEndDate`, `listingDate`, `refundDate` | `IpoEvent` | |
| `generalSubscriptionRate`, `proportionalCompetitionRate` | `IpoMetric` | |
| `equalAllocationShares`, `generalAllocationShares` | `IpoOffering` | |
| **forecast** | | |
| `offerPrice`, `offerPriceMin`, `offerPriceMax` | `IpoOffering` | |
| `demandForecastStart`, `demandForecastEnd` | `IpoEvent` | |
| `lockupRatio`, `institutionalCompetitionRate` | `IpoMetric` | |
| **company** | | |
| `revenue`, `netIncome` | `CompanyFinancialHighlight` | 최근 사업연도 1건 |
| `shareCount`, `totalListedShares` | `IpoOffering` | |
| `protectiveCustodyRatio` | `IpoMetric` | |
| `brokerNames` | `IpoEventBroker` | `findByIpoEventId` 후 `brokerName` 추출 |

## 4. 서비스 구현 스켈레톤

`service/ipo/IpoDetailQueryService.java` 로 작성하세요. 기존 `IpoFinancialQueryService` 의 패턴(`@Service`, `@Transactional(readOnly = true)`, `CustomException`)을 따르면 됩니다.

```java
@Service
@RequiredArgsConstructor
public class IpoDetailQueryService {

    private final IpoEventRepository ipoEventRepository;
    private final IpoEventBrokerRepository ipoEventBrokerRepository;
    private final CompanyFinancialHighlightRepository financialRepository;
    private final IpoSignalCalculator ipoSignalCalculator;

    @Transactional(readOnly = true)
    public IpoDetailResponse getDetail(Long ipoEventId) {
        // 1) 이벤트 조회 (없으면 404)
        IpoEvent event = ipoEventRepository.findById(ipoEventId)
                .orElseThrow(() -> new CustomException(ErrorCode.IPO_EVENT_NOT_FOUND));

        // 2) 연관 엔티티 접근 — IpoEvent 의 @OneToOne 관계로 navigate 가능
        IpoOffering offering = event.getOffering();   // null 가능
        IpoMetric metric = event.getMetric();         // null 가능

        // 3) 신호등 계산 (IpoSignalCalculator 재사용)
        IpoSignalCalculator.SignalResult signal = ipoSignalCalculator.calculate(metric);

        // 4) 증권사 목록
        List<String> brokerNames = ipoEventBrokerRepository.findByIpoEventId(ipoEventId).stream()
                .map(IpoEventBroker::getBrokerName)
                .distinct()
                .toList();

        // 5) 최근 사업연도 재무 1건 (ANNUAL, CFS 우선) — IpoFinancialQueryService 로직 참고
        //    매출액·순이익만 필요하므로 가장 최신 연도 1건만 사용

        // 6) 위 값들을 IpoDetailResponse 및 하위 탭 DTO 의 builder 로 조립해 반환
    }
}
```

컨트롤러(`IpoController`)에 추가:

```java
@GetMapping("/{ipoEventId}/detail")
public ResponseEntity<IpoDetailResponse> getDetail(@PathVariable Long ipoEventId) {
    return ResponseEntity.ok(detailQueryService.getDetail(ipoEventId));
}
```

## 5. 주의사항

- `IpoOffering`, `IpoMetric` 은 아직 파싱/수집이 안 된 공모주의 경우 **null 일 수 있습니다.** 각 필드 매핑 시 null 가드를 두세요 (`offering == null ? null : offering.getXxx()`).
- 재무 데이터는 손실/자본잠식으로 **음수가 가능**하므로 `Long` 타입을 유지합니다. 동일 연도에 CFS(연결)·OFS(개별)가 함께 있으면 CFS 우선 — `IpoFinancialQueryService.getFinancials` 의 처리 로직을 참고하세요.
- 비율 필드(`lockupRatio`, `protectiveCustodyRatio`)는 0~1 소수입니다. 화면 표시(%) 변환은 프론트 담당입니다.
- 공시 리포트 요약 영역은 별도 API(`/disclosure`)이므로 이 서비스에서 다루지 않습니다.
