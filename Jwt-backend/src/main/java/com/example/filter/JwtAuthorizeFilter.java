package com.example.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.utils.Const;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每次请求校验 Authorization 头中的 JWT：
 * 校验通过就把用户信息放进 SecurityContext，并把 userId 挂到 request 上供 Controller 使用。
 *
 * 阶段 3 新增「封禁即时生效」：
 * 令牌是无状态的，签发后服务端本来无法单方面废除它。
 * 所以管理员封禁时会在 Redis 写一个 banned:block:{uid} 标记，
 * 这里每请求查一次；命中就把这张令牌直接拉黑，本次请求按未登录处理。
 * 代价是每个请求多一次 Redis 查询——这是「JWT 无状态」换来的必然账。
 */
@Component
public class JwtAuthorizeFilter extends OncePerRequestFilter {

    @Resource
    JwtUtils utils;

    @Resource
    StringRedisTemplate template;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        DecodedJWT jwt = utils.resolveJwt(authorization);
        if (jwt != null) {
            Integer id = utils.toId(jwt);
            if (Boolean.TRUE.equals(template.hasKey(Const.BANNED_BLOCK + id))) {
                // 已被封禁：顺手把令牌写进黑名单，避免它继续被反复尝试
                utils.invalidateJwt(authorization);
            } else {
                UserDetails user = utils.toUser(jwt);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(Const.ATTR_USER_ID, id);
            }
        }
        filterChain.doFilter(request, response);
    }
}
