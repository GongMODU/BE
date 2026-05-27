package com.gong.modu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 운영 환경에서 IPO 관련 admin API(/api/admin/ipo/**)에 접근할 때
// X-IPO-ADMIN-KEY 헤더 값을 검사하는 필터.
// 기존 AdminApiKeyFilter(YouTube 전용)와 동일 패턴이며 헤더/키만 분리해 보안 격리한다.
public class IpoAdminApiKeyFilter extends OncePerRequestFilter {

    // 클라이언트가 보내야 하는 헤더 이름
    private static final String IPO_ADMIN_KEY_HEADER = "X-IPO-ADMIN-KEY";

    // application.properties 의 admin.ipo.key 값
    @Value("${admin.ipo.key}")
    private String adminIpoKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /api/admin/ipo/** 경로가 아니면 이 필터 적용 X
        return !uri.startsWith("/api/admin/ipo/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestKey = request.getHeader(IPO_ADMIN_KEY_HEADER);

        // 헤더가 없거나, 서버에 저장된 admin 키와 다르면 차단
        if (requestKey == null || !requestKey.equals(adminIpoKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                    {
                      "message": "Invalid IPO admin key"
                    }
                    """);
            return;
        }

        // 키가 맞으면 다음 필터/컨트롤러로 요청 넘김
        filterChain.doFilter(request, response);
    }
}
