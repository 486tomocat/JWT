package com.example.filter;

import com.example.utils.SnowflakeIdUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全链路 traceId。
 *
 * 一次请求会打出很多行日志（过滤器、业务、SQL），并发时根本分不清哪几行属于同一次请求。
 * 这里在请求入口生成一个唯一 ID 放进 MDC，logback 的日志格式里带上它，
 * 「同一次请求的所有日志」就能按这个 ID 串起来；
 * 同时写进响应头 —— 排查线上问题时，让用户/F12 把 X-Trace-Id 贴过来即可精确检索。
 *
 * 优先级设为最高：必须排在 JwtAuthorizeFilter、FlowLimitFilter 之前，
 * 否则那两个过滤器打的日志就不带 traceId 了。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 上游（网关 / 前端）可以带这个头把整条链路串起来；没带就自己生成 */
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Resource
    SnowflakeIdUtils snowflakeIdUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = String.valueOf(snowflakeIdUtils.nextId());
        }

        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 仍然在 MDC 里，所以这条耗时日志也带 traceId
            log.info("请求完成: {} {} -> {}({}ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), System.currentTimeMillis() - start);
            // Tomcat 的线程是复用的，不清理会让下一个请求带上别人的 traceId
            MDC.remove(MDC_KEY);
        }
    }
}
