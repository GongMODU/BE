package com.gong.modu.client;

import com.gong.modu.domain.dto.dart.DartCompanyResponse;
import com.gong.modu.domain.dto.dart.DartDisclosureSearchResponse;
import com.gong.modu.domain.dto.dart.DartEquitySecuritiesReportResponse;
import com.gong.modu.domain.dto.dart.DartFinancialStatementResponse;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

// DART API 호출을 전담하는 Client 클래스
@Slf4j
@Component
@RequiredArgsConstructor
public class DartApiClient {

    // DART 응답 status 코드 - 정상 응답
    private static final String DART_STATUS_OK = "000";

    // DART 응답 status 코드 - 조회 결과 없음 (오류가 아닌 정상적인 빈 응답)
    private static final String DART_STATUS_NO_DATA = "013";

    private final WebClient dartWebClient;

    @Value("${external.dart.api-key}")
    private String apiKey;

    // DART 공시검색 API 호출
    // 목적: 특정 기간의 공모 관련 공시 검색, rcept_no 확보
    public DartDisclosureSearchResponse searchDisclosure(
            String beginDate, // 검색 시작일, yyyyMMdd
            String endDate, // 검색 종료일, yyyyMMdd
            String corpCode, // 특정 기업만 검색할 경우 사용하는 DART 기업 고유번호
            String disclosureType, // 공시 유형 필터
            int pageNo, // 페이지네이션
            int pageCount // 페이지네이션
    ) {
        try {
            DartDisclosureSearchResponse response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/list.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("bgn_de", beginDate)
                            .queryParam("end_de", endDate)
                            .queryParamIfPresent("corp_code", Optional.ofNullable(corpCode))
                            .queryParamIfPresent("pblntf_ty", Optional.ofNullable(disclosureType))
                            .queryParam("page_no", pageNo)
                            .queryParam("page_count", pageCount)
                            .build())
                    .retrieve()
                    .bodyToMono(DartDisclosureSearchResponse.class)
                    .block();

            // WebClient 호출은 성공했지만 응답 body가 비어 있는 경우
            if (response==null) {
                throw new CustomException(ErrorCode.EXTERNAL_API_EMPTY_RESPONSE);
            }

            // 내부 status를 검증
            validateDartStatus(response.getStatus(), response.getMessage());

            return response;

        } catch (WebClientRequestException e) {
            log.warn("[DART API] 요청 실패", e);
            throw new CustomException(ErrorCode.DART_API_ERROR);
        }
    }

    // DART 기업개황 API 호출
    // 목적: corpCode 기준 기업명, 영문명, 종목명, 종목코드, 법인구분, 업종코드, 설립일 확보, companies 테이블 보강
    public DartCompanyResponse getCompany(String corpCode) {
        try {
            DartCompanyResponse response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/company.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .bodyToMono(DartCompanyResponse.class)
                    .block();

            if (response == null) {
                throw new CustomException(ErrorCode.EXTERNAL_API_EMPTY_RESPONSE);
            }

            validateDartStatus(response.getStatus(), response.getMessage());
            return response;

        } catch (WebClientResponseException e) {
            log.warn("[DART API] HTTP 응답 오류 status={}", e.getStatusCode(), e);
            throw new CustomException(ErrorCode.DART_API_ERROR);
        }
    }

    // DART 단일회사 전체 재무제표 API 호출
    // 목적: 매출액, 영업이익, 순이익, 자산총계, 부채총계, 자본총계 추출, company_financial_highlights 테이블 저장의 원천 데이터 확보
    public DartFinancialStatementResponse getFinancialStatements(
            String corpCode,
            String businessYear,
            String reportCode, // 11011: 사업보고서, 11012: 반기보고서, 11013: 1분기보고서, 11014: 3분기보고서
            String fsDiv // CFS: 연결재무제표, OFS: 개별재무제표
    ) {
        try {
            DartFinancialStatementResponse response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/fnlttSinglAcntAll.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", businessYear)
                            .queryParam("reprt_code", reportCode)
                            .queryParam("fs_div", fsDiv)
                            .build())
                    .retrieve()
                    .bodyToMono(DartFinancialStatementResponse.class)
                    .block();

            if (response == null) {
                throw new CustomException(ErrorCode.EXTERNAL_API_EMPTY_RESPONSE);
            }

            validateDartStatus(response.getStatus(), response.getMessage());
            return response;

        } catch (WebClientResponseException e) {
            log.warn("[DART API] HTTP 응답 오류 status={}", e.getStatusCode(), e);
            throw new CustomException(ErrorCode.DART_API_ERROR);
        }
    }

    // DART 지분증권 증권신고서 주요정보 API 호출
    // 목적: 청약기일, 납입기일, 청약공고일, 배정공고일, 인수기관명 등 확보, ipo_events, ipo_offerings, ipo_event_brokers 저장의 원천 데이터로 사용
    public DartEquitySecuritiesReportResponse getEquitySecuritiesReport(
            String corpCode,
            String beginDate,
            String endDate
    ) {
        try {
            DartEquitySecuritiesReportResponse response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/estkRs.json")
                            .queryParam("crtfc_key", apiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", beginDate)
                            .queryParam("end_de", endDate)
                            .build())
                    .retrieve()
                    .bodyToMono(DartEquitySecuritiesReportResponse.class)
                    .block();

            if (response == null) {
                throw new CustomException(ErrorCode.EXTERNAL_API_EMPTY_RESPONSE);
            }

            validateDartStatus(response.getStatus(), response.getMessage());
            return response;

        } catch (WebClientResponseException e) {
            log.warn("[DART API] HTTP 응답 오류 status={}", e.getStatusCode(), e);
            throw new CustomException(ErrorCode.DART_API_ERROR);
        }
    }

    // DART 응답 status 검증 메서드
    // "000"은 정상, "013"은 조회 결과 없음(정상적인 빈 응답)이므로 예외로 처리하지 않음
    // 그 외 상태값은 실제 status/message를 로그로 남긴 뒤 예외 처리
    private void validateDartStatus(String status, String message) {
        if (DART_STATUS_OK.equals(status) || DART_STATUS_NO_DATA.equals(status)) {
            return;
        }

        log.warn("[DART API] 비정상 응답 status={}, message={}", status, message);
        throw new CustomException(ErrorCode.DART_API_ERROR);
    }

    // DART 공시서류원본파일 API를 호출하여 공시 원문 ZIP 파일을 byte[]로 다운로드하는 메서드
    public byte[] downloadDisclosureDocumentZip(String rceptNo) {
        try {
            byte[] response = dartWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/document.xml") // 공시서류원본파일 API 경로
                            .queryParam("crtfc_key", apiKey) // DART 인증키
                            .queryParam("rcept_no", rceptNo) // 다운로드할 공시의 접수번호
                            .build())
                    .retrieve()
                    .bodyToMono(byte[].class) // HTTP 응답 body를 byte[]로 받음
                    .block();

            if (response == null || response.length == 0) {
                throw new CustomException(ErrorCode.EXTERNAL_API_EMPTY_RESPONSE);
            }

            return response; // zip 바이너리 반환
        } catch (WebClientResponseException e) {
            log.warn("[DART API] HTTP 응답 오류 status={}", e.getStatusCode(), e);
            throw new CustomException(ErrorCode.DART_API_ERROR);
        } catch (WebClientRequestException e) {
            log.warn("[DART API] 요청 실패", e);
            throw new CustomException(ErrorCode.DART_API_ERROR);
        }
    }
}
