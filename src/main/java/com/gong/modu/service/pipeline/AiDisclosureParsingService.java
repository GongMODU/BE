package com.gong.modu.service.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gong.modu.domain.dto.anthropic.AnthropicMessageDto;
import com.gong.modu.domain.dto.pipeline.AiDisclosureParsingResult;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.service.AnthropicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
// Claude API로 공시 원문에서 정규식이 놓친 IPO 핵심값을 보완 추출하는 서비스 클래스
public class AiDisclosureParsingService {

    private final AnthropicService anthropicService;
    private final ObjectMapper objectMapper;

    // AI 보완 파싱에 사용할 최대 출력 토큰 수
    @Value("${anthropic.max-tokens.parser:2048}")
    private int maxTokens;

    // AI에게 공시 일부 문맥을 보내고 IPO 핵심값을 JSON으로 추출
    public AiDisclosureParsingResult parseWithAi(String contextText) {
        if (contextText == null || contextText.isBlank()) {
            return null;
        }

        // 프롬프트
        String prompt = buildPrompt(contextText);

        try {
            List<AnthropicMessageDto> messages = List.of(
                    AnthropicMessageDto.builder()
                            .role("user")
                            .content(prompt)
                            .build()
            );

            // 클로드 API 호출
            String aiText = anthropicService.call(messages, maxTokens);

            String json = extractJson(aiText);

            return objectMapper.readValue(json, AiDisclosureParsingResult.class);
        } catch (CustomException e) {
            // AnthropicService에서 던진 CustomException(크레딧 한도 등)은 원형 그대로 다시 던져
            // 원본 에러 코드와 메시지가 응답에 노출되도록 함
            log.warn("[AI Parser] CustomException from Anthropic: code={}, message={}",
                    e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            // Claude 응답 JSON 변환 실패 또는 기타 예외
            // 원본 에러 클래스와 메시지를 로깅해 실제 원인(rate limit, network 등)을 추적 가능하게 함
            log.warn("[AI Parser] AI 파싱 실패: exception={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new CustomException(ErrorCode.AI_DISCLOSURE_PARSING_FAILED);
        }
    }

    // 프롬프트를 구성하는 메서드
    private String buildPrompt(String contextText) {
        return """
                너는 한국 DART 공모주 관련 공시(증권신고서, 투자설명서, 증권발행실적보고서, 신규상장 공시 등)에서 공모주 핵심 정보를 추출하는 parser다.

                아래 원칙을 반드시 지켜라.

                1. 반드시 JSON만 반환한다.
                2. 설명 문장, 마크다운 코드블록, ```json 같은 표시는 절대 쓰지 않는다.
                3. 문맥에서 확인되거나 계산 가능한 값을 채운다.
                4. 문맥에서 확인할 수 없거나 계산도 불가능한 값은 null로 둔다.
                5. 정정 전 값과 정정 후 값이 함께 있으면 정정 후 값을 우선한다.
                6. 날짜는 반드시 yyyy-MM-dd 형식으로 반환한다.
                7. 금액은 원 단위 숫자만 반환한다. 예: 39,000원은 39000
                8. 주식 수는 주 단위 숫자만 반환한다. 예: 65,450,000주는 65450000
                9. 경쟁률은 '1732.83 대 1'이면 1732.83으로 반환한다.
                10. 비율은 반드시 0~1 사이 소수로 반환한다. 예: 45.36%%는 0.4536
                11. lockupExpiryDate(보호예수·의무보유 해제일): 여러 대상의 해제일이 있을 경우, 최대주주·발행사 임원 등 대주주에 해당하는 해제일(통상 상장 후 6개월)을 대표값으로 사용한다. 명시된 날짜가 없어도 문맥에서 "상장 후 N개월" 형태로 기간을 확인할 수 있으면 lockupPeriodMonths에 그 개월 수를 숫자로 반환하고 lockupExpiryDate는 null로 둔다. 어떤 값도 확인 불가면 둘 다 null.
                12. institutionalCompetitionRate(기관경쟁률): 수요예측 참여내역 표에서 합계 행의 경쟁률 값을 사용한다. 표에 '1,732.83'처럼 합계 경쟁률이 있으면 반드시 추출한다.
                13. lockupRatio(의무보유확약 비율): 문맥에 확약비율이 명시되어 있으면 그 값을 사용하고, 명시되지 않은 경우 (확약 수량 합계) ÷ (수요예측 총 신청수량)으로 계산해서 반환한다.
                14. demandForecastStart/demandForecastEnd(수요예측 기간): 일정표의 '수요예측 기간' 또는 '수요예측일' 행뿐만 아니라, 본문 산문에서도 추출한다. 예를 들어 '공동대표주관회사는 2021년 7월 13일부터 7월 14일까지 수요예측을 실시하였습니다', 'OOO일 양일간 수요예측을 진행' 같은 과거형 서술 문장에서 시작일과 종료일을 추출한다. 표가 없어도 산문에서 확인되면 채운다.
                15. listingDate(상장예정일): 가장 중요한 필드 중 하나이므로 반드시 적극적으로 찾아라. 문맥 맨 앞 [CRITICAL_KEYWORDS] 블록에서 '상장예정일', '매매개시일', '상장일' 키워드 주변을 먼저 확인한다. 일정표의 행, 본문 산문의 'OOO일 상장 예정', 'OOO일부터 매매가 개시될 예정', '상장일(매매개시일) YYYY년 MM월 DD일', '상장(예정)일: ...' 같은 어떤 형태로든 나타나면 추출한다. 정정 후 값 우선. 한국 거래소는 청약 종료 후 통상 5~7영업일 뒤 상장하므로 청약일이 정해진 공시라면 상장일도 함께 명시되어 있는 경우가 대부분이다. 진짜로 어디에도 없을 때만 null.
                16. subscriptionStart/subscriptionEnd(청약 기간): 일정표의 '청약기일' 또는 '청약일' 행에서 일반투자자(일반청약자) 기준 청약 시작일과 종료일을 추출한다. 청약일이 하루뿐이면 시작일과 종료일을 같은 날짜로 둔다.
                17. brokerNames(주관사·인수증권사): 대표주관회사, 공동대표주관회사, 인수회사 등 이 공모에 참여한 증권사명을 문자열 배열로 추출한다. 예: ["KB증권", "한국투자증권"]. '주식회사' 같은 수식어는 빼고 증권사명만 간결하게 넣고, 중복은 제거한다. 없으면 빈 배열이 아니라 null로 둔다.
                18. reason 필드에는 주요 값을 어떤 근거로 추출/계산했는지 간단히 한 문장으로 기재한다.

                반환 JSON 형식은 반드시 아래와 같아야 한다.

                {
                  "demandForecastStart": null,
                  "demandForecastEnd": null,
                  "subscriptionStart": null,
                  "subscriptionEnd": null,
                  "refundDate": null,
                  "listingDate": null,
                  "lockupExpiryDate": null,
                  "lockupPeriodMonths": null,
                  "offerPriceMin": null,
                  "offerPriceMax": null,
                  "offerPrice": null,
                  "shareCount": null,
                  "totalListedShares": null,
                  "institutionalCompetitionRate": null,
                  "lockupRatio": null,
                  "protectiveCustodyRatio": null,
                  "brokerNames": null,
                  "reason": null
                }

                분석할 공시 문맥은 아래와 같다.

                [공시 문맥 시작]
                %s
                [공시 문맥 끝]
                """.formatted(contextText);
    }

    // Claude 응답에서 JSON 객체 부분만 잘라내는 메서드
    private String extractJson(String aiText) {
        if (aiText == null || aiText.isBlank())
            throw new CustomException(ErrorCode.AI_DISCLOSURE_PARSING_FAILED);

        int start = aiText.indexOf("{");
        int end = aiText.lastIndexOf("}");

        if (start < 0 || end < 0 || end <= start)
            throw new CustomException(ErrorCode.AI_DISCLOSURE_PARSING_FAILED);

        return aiText.substring(start, end + 1);
    }
}
