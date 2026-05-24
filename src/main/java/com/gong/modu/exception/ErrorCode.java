package com.gong.modu.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EXPIRED_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    EXPIRED_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 링크가 만료되었습니다."),
    USED_RESET_TOKEN(HttpStatus.BAD_REQUEST, "이미 사용된 비밀번호 재설정 링크입니다."),
    RESET_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "비밀번호 재설정 토큰을 찾을 수 없습니다."),
    SOCIAL_LOGIN_NOT_SUPPORTED_FOR_PASSWORD_RESET(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호 재설정을 지원하지 않습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    NICKNAME_UNCHANGED(HttpStatus.BAD_REQUEST, "현재 닉네임과 동일합니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인 비밀번호가 일치하지 않습니다."),
    SOCIAL_LOGIN_NOT_SUPPORTED_FOR_PASSWORD_UPDATE(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),

    // Investment Profile
    PERSONA_TYPE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "페르소나 유형 데이터를 찾을 수 없습니다."),

    // Claude API
    CLAUDE_API_ERROR(HttpStatus.BAD_GATEWAY, "Claude API 호출 중 오류가 발생했습니다."),
    CLAUDE_CREDIT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Claude API 크레딧 한도($25)에 도달했습니다. 관리자에게 문의하세요."),

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // YouTube
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "재생목록을 찾을 수 없습니다."),
    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "YouTube 영상을 찾을 수 없습니다."),
    VIDEO_POOL_EMPTY(HttpStatus.NOT_FOUND, "수집된 YouTube 영상 후보가 없습니다."),
    TRANSCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "자막이 있는 YouTube 영상을 찾지 못했습니다."),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "YouTube 채널을 찾을 수 없습니다."),
    SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 유튜브 요약 정보를 찾을 수 없습니다."),

    // 외부 공모주 API
    // DART 호출 실패 시 사용하는 에러 (HTTP 오류, DART 내부 status 오류, 응답 구조 이상 등을 묶어 처리)
    DART_API_ERROR(HttpStatus.BAD_GATEWAY, "DART API 호출 중 오류가 발생했습니다."),
    // KIS 주식 시세 API 호출 실패 시 사용하는 에러 (현재가 조회, 기간별 시세 조회 등에서 KIS가 실패 응답을 주거나 호출 자체가 실패 시)
    KIS_API_ERROR(HttpStatus.BAD_GATEWAY, "KIS API 호출 중 오류가 발생했습니다."),
    // KIS 접근 토큰 발급 실패 시 사용하는 에러 (KIS는 주가 API 호출 전에 access token을 발급받아야 하므로 토큰 발급 실패는 일반 KIS API 조회 실패와 분리함)
    KIS_TOKEN_ERROR(HttpStatus.BAD_GATEWAY, "KIS 접근 토큰 발급 중 오류가 발생했습니다."),
    // 외부 API 호출은 성공했지만 response body가 null인 경우 사용 (HTTP 상태는 정상이어도 실제 응답 객체가 비어 있으면 이후 DTO 접근에서 NullPointerException 발생할 수 있음)
    EXTERNAL_API_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "외부 API 응답이 비어 있습니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "기업 정보를 찾을 수 없습니다."),
    IPO_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "공모주 정보를 찾을 수 없습니다."),
    DISCLOSURE_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 공모주의 공시 리포트를 찾을 수 없습니다."),
    FINANCIAL_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "기업 재무 정보를 찾을 수 없습니다."),
    STOCK_CODE_NOT_FOUND(HttpStatus.BAD_REQUEST, "종목코드가 없어 주가를 조회할 수 없습니다."),

    // DART 원문 공시 ZIP을 받았지만 텍스트 추출 또는 파싱에 실패한 경우
    DISCLOSURE_PARSING_FAILED(HttpStatus.BAD_GATEWAY, "공시 원문 파싱 중 오류가 발생했습니다."),

    // AI를 이용한 공시 원문 보완 파싱이 실패한 경우 (Claude 응답은 왔지만 JSON 파싱 실패, 필드 변환 실패 등이 발생한 경우)
    AI_DISCLOSURE_PARSING_FAILED(HttpStatus.BAD_GATEWAY, "AI 공시 보완 파싱 중 오류가 발생했습니다."),

    // 관심 공모주
    ALREADY_INTERESTED_IPO(HttpStatus.CONFLICT, "이미 관심 공모주로 등록된 종목입니다."),
    INTERESTED_IPO_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 공모주 등록 내역을 찾을 수 없습니다."),

    // 청약 이력
    SUBSCRIPTION_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "청약 이력을 찾을 수 없습니다."),
    SUBSCRIPTION_HISTORY_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 청약 이력만 조회/수정/삭제할 수 있습니다."),
    SUBSCRIPTION_HISTORY_INVALID_INPUT(HttpStatus.BAD_REQUEST, "청약 이력 입력 값이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}