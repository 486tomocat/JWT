package com.example.filter;

import com.example.entity.RestBean;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 全局限流过滤器：按 IP 做「period 秒内最多 limit 次，超出封禁 block 秒」。
 * 阈值全部来自配置文件，方便不同环境调整。
 *
 * 注意：这里的 synchronized(ip.intern()) 只在「单机」有效，
 * 多实例部署时应该把限流逻辑改成 Redis 原子脚本（Lua）。
 */
@Component
@Order(Const.ORDER_LIMIT)
public class FlowLimitFilter extends HttpFilter {

    @Value("${spring.web.flow.limit}")
    int limit;

    @Value("${spring.web.flow.period}")
    int period;

    @Value("${spring.web.flow.block}")
    int block;

    @Resource
    StringRedisTemplate template;

    @Resource
    FlowUtils flowUtils;

    @Override
    protected void doFilter(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain chain) throws IOException, ServletException {
        // 预检请求（OPTIONS）不做限流，否则跨域会失效
        if ("OPTIONS".equals(request.getMethod()) || this.tryCount(request.getRemoteAddr())) {
            chain.doFilter(request, response);
        } else {
            this.writeBlockMessage(response);
        }
    }

    private boolean tryCount(String ip) {
        String blockKey = Const.FLOW_LIMIT_BLOCK + ip;
        // 先看是否还在封禁期，在的话直接拒绝
        if (Boolean.TRUE.equals(template.hasKey(blockKey))) {
            return false;
        }
        String counterKey = Const.FLOW_LIMIT_COUNTER + ip;
        return flowUtils.limitPeriodCheck(counterKey, blockKey, block, limit, period);
    }

    private void writeBlockMessage(HttpServletResponse response) throws IOException {
        // 429 Too Many Requests 是限流的标准状态码
        response.setStatus(429);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(RestBean.failure(429, "操作频繁，请稍后再试").asJsonString());
    }
}
