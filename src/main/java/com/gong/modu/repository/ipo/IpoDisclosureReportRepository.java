package com.gong.modu.repository.ipo;

import com.gong.modu.domain.entity.ipo.IpoDisclosureReport;
import com.gong.modu.domain.enums.ipo.IpoEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 공시 원문 요약 조회 Repository
public interface IpoDisclosureReportRepository extends JpaRepository<IpoDisclosureReport, Long> {

    // 특정 공모 이벤트의 공시 목록 조회
    List<IpoDisclosureReport> findByIpoEventId(Long ipoEventId);

    // DART 접수번호 기준 공시 조회
    Optional<IpoDisclosureReport> findByRceptNo(String rceptNo);

    // 특정 공모 이벤트 안에서 특정 접수번호 공시 조회
    Optional<IpoDisclosureReport> findByIpoEventIdAndRceptNo(Long ipoEventId, String rceptNo);

    // 공시명에 특정 키워드가 포함된 공시 목록 조회
    List<IpoDisclosureReport> findByReportNameContaining(String keyword);

    // original_text가 아직 없는 공시를 일부만 조회
    List<IpoDisclosureReport> findByOriginalTextIsNull(Pageable pageable);

    // 특정 공모 이벤트의 공시 접수번호만 조회 (LOB 칼럼 미적재)
    // PostgreSQL OID 기반 @Lob 칼럼은 트랜잭션 없이 읽으면 예외가 발생하므로,
    // 컨트롤러처럼 트랜잭션 컨텍스트가 없는 곳에서는 이 projection 메서드를 사용
    @Query("SELECT r.rceptNo FROM IpoDisclosureReport r WHERE r.ipoEvent.id = :ipoEventId")
    List<String> findRceptNosByIpoEventId(@Param("ipoEventId") Long ipoEventId);

    // 특정 상태(예: UPCOMING)의 IPO에 속한 모든 공시의 (ipoEventId, rceptNo) projection 조회
    // 일괄 재파싱 스케줄러용. LOB 미적재 + lazy 연관 안 건드림
    @Query("SELECT r.ipoEvent.id, r.rceptNo FROM IpoDisclosureReport r WHERE r.ipoEvent.status = :status")
    List<Object[]> findIpoEventIdAndRceptNoByIpoStatus(@Param("status") IpoEventStatus status);

    // 재파싱이 필요한 모든 IPO의 공시 (ipoEventId, rceptNo) projection 조회
    // 조건: status가 LISTED가 아니거나, listingDate가 추정값이거나, listingDate가 NULL
    //       (= 진짜 완료된 IPO만 제외하고 데이터 갱신이 의미 있는 모든 IPO를 포함)
    // 잘못 LISTED로 마킹된 IPO(stale 추정값 때문)도 catch-22에서 빠져나올 수 있도록 함
    @Query("SELECT r.ipoEvent.id, r.rceptNo FROM IpoDisclosureReport r " +
            "WHERE r.ipoEvent.status != :listedStatus " +
            "   OR r.ipoEvent.listingDateEstimated = true " +
            "   OR r.ipoEvent.listingDate IS NULL")
    List<Object[]> findReportsForActiveIpos(@Param("listedStatus") IpoEventStatus listedStatus);
}
