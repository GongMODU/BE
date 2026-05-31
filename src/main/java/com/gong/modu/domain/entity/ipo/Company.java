package com.gong.modu.domain.entity.ipo;

import com.gong.modu.domain.entity.BaseTimeEntity;
import com.gong.modu.domain.enums.ipo.CorpClass;
import com.gong.modu.domain.enums.ipo.MarketType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

// 기업 기본정보를 저장하는 엔티티
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "companies")
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 8) // DART corp_code는 8자리 문자열이므로 최대 길이 제한
    @Column(name = "corp_code", length = 8, unique = true) // DART 고유번호이므로 unique
    private String corpCode; // DART에서 회사를 식별하는 고유번호

    @NotBlank
    @Size(max = 200)
    @Column(name = "corp_name", length = 200, nullable = false)
    private String corpName; // 한글 기업명

    @Size(max = 200)
    @Column(name = "corp_name_eng", length = 200) // 영문명은 없는 경우가 있으므로 NULL 허용
    private String corpNameEng; // 영문 기업명

    @Size(max = 6) // 국내 종목코드는 일반적으로 6자리
    @Column(name = "stock_code", length = 6, unique = true) // 상장 기업의 종목코드는 중복되면 안되므로 unique
    private String stockCode; // KRX/KIS에서 사용하는 종목코드

    @Enumerated(EnumType.STRING)
    @Column(name = "corp_cls", length = 1) // DART 법인구분은 한 글자 코드
    private CorpClass corpClass; // DART 기준 법인 구분

    @Size(max = 200)
    @Column(name = "stock_name", length = 200) // 종목명은 상장 전에는 없을 수 있으므로 NULL 허용
    private String stockName; // 거래소에서 사용하는 종목명

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", length = 20) // KOSPI, KOSDAQ, KONEX 값
    private MarketType marketType; // 상장 시장 구분

    @Size(max = 20)
    @Column(name = "industry_code", length = 20) // 업종 코드는 API 제공 여부에 따라 NULL일 수 있음
    private String industryCode; // 기업의 업종 코드

    @Column(name = "established_at") // 설립일은 API에서 없거나 파싱되지 않을 수 있어 NULL 허용
    private LocalDate establishedAt; // 회사 설립일

    @Column(name = "is_spac", nullable = false)
    @Builder.Default
    private boolean isSpac = false;

    public boolean isSpac() {
        return isSpac;
    }

    // DART/KRX/KIS 수집 결과로 기업 기본정보를 갱신하는 메서드
    public void updateBasicInfo(
                                 String corpName, // 새 기업명
                                 String corpNameEng, // 새 영문 기업명
                                 String stockCode, // 새 종목코드
                                 CorpClass corpClass, // 새 법인구분
                                 String stockName, // 새 종목명
                                 MarketType marketType, // 새 시장구분
                                 String industryCode, // 새 업종 코드
                                 LocalDate establishedAt, // 새 설립일
                                 boolean isSpac // 스팩 여부
    ) {
        this.corpName = corpName;
        this.corpNameEng = corpNameEng;
        this.stockCode = stockCode;
        this.corpClass = corpClass;
        this.stockName = stockName;
        this.marketType = marketType;
        this.industryCode = industryCode;
        this.establishedAt = establishedAt;
        this.isSpac = isSpac;
    }

    public static boolean detectSpac(String corpName) {
        if (corpName == null) return false;
        return corpName.contains("스팩")
                || corpName.contains("기업인수목적")
                || corpName.toUpperCase().contains("SPAC");
    }
}
