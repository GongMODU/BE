package com.gong.modu.service.pipeline;

import com.gong.modu.client.DartApiClient;
import com.gong.modu.domain.dto.dart.DartCompanyResponse;
import com.gong.modu.domain.entity.ipo.Company;
import com.gong.modu.domain.enums.ipo.CorpClass;
import com.gong.modu.domain.enums.ipo.MarketType;
import com.gong.modu.exception.CustomException;
import com.gong.modu.exception.ErrorCode;
import com.gong.modu.repository.ipo.CompanyRepository;
import com.gong.modu.util.ExternalDateParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
// DART 기업개황 API에서 기업 정보를 가져와 companies 테이블에 저장하거나 갱신하는 클래스
public class DartCompanySyncService {

    private final DartApiClient dartApiClient;
    private final CompanyRepository companyRepository;

    @Transactional
    // 특정 Dart 기업 고유번호를 기준으로 기업 정보를 동기화하는 메서드
    public Company syncCompany(String corpCode) {

        DartCompanyResponse response = dartApiClient.getCompany(corpCode); // DART 기업개황 API 호출

        validateCompanyResponse(response);

        CorpClass corpClass = parseCorpClass(response.getCorpCls());
        MarketType marketType = mapMarketType(corpClass);
        LocalDate establishedAt = ExternalDateParser.parseFlexibleDate(response.getEstDt());

        // 상장 전 기업은 종목코드/종목명이 없어 DART가 빈 문자열을 내려주므로 null로 정규화
        String stockCode = emptyToNull(response.getStockCode());
        String stockName = emptyToNull(response.getStockName());

        boolean isSpac = Company.detectSpac(response.getCorpName());

        return companyRepository.findByCorpCode(response.getCorpCode())
                .map(existing -> {
                    existing.updateBasicInfo( // 기존 Company 기본 정보를 최신 DART 응답값으로 갱신
                            response.getCorpName(),
                            response.getCorpNameEng(),
                            stockCode,
                            corpClass,
                            stockName,
                            marketType,
                            response.getIndutyCode(),
                            establishedAt,
                            isSpac
                    );

                    return existing; // 갱신된 기존 엔티티 반환
                }).orElseGet(() -> companyRepository.save( // Optional 안에 값이 없을 경우
                        Company.builder()
                                .corpCode(response.getCorpCode())
                                .corpName(response.getCorpName())
                                .corpNameEng(response.getCorpNameEng())
                                .stockCode(stockCode)
                                .corpClass(corpClass)
                                .stockName(stockName)
                                .marketType(marketType)
                                .industryCode(response.getIndutyCode())
                                .establishedAt(establishedAt)
                                .isSpac(isSpac)
                                .build()
                ));
    }

    // DART가 빈 문자열로 내려주는 선택 값을 null로 정규화하는 메서드
    // stock_code에는 unique 제약이 있어 빈 문자열이 중복되면 제약 위반이 발생하므로 null 처리가 필요함
    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private CorpClass parseCorpClass(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return CorpClass.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private MarketType mapMarketType(CorpClass corpClass) {
        if (corpClass == null) {
            return null;
        }

        return switch (corpClass) {
            case Y -> MarketType.KOSPI;
            case K -> MarketType.KOSDAQ;
            case N -> MarketType.KONEX;
            case E -> null;
        };
    }

    private void validateCompanyResponse(DartCompanyResponse response) {
        if (response.getCorpCode() == null || response.getCorpCode().isBlank()
                || response.getCorpName() == null || response.getCorpName().isBlank()) {
            throw new CustomException(ErrorCode.DART_API_ERROR);
        }
    }
}
