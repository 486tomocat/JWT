package com.example.config;

import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.vo.response.AuthorizeVO;
import com.example.filter.JwtAuthorizeFilter;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Configuration
public class SecurityConfiguration {

    @Resource
    JwtUtils utils;

    @Resource
    JwtAuthorizeFilter jwtAuthorizeFilter;

    @Resource
    AccountService service;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(conf -> conf
                        .requestMatchers("/api/auth/**", "/error").permitAll()
                        // Swagger 文档页与 JSON：不放行会被 anyRequest() 拦成 401
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        // 管理后台需要 admin 角色（hasRole 会自动补 ROLE_ 前缀）
                        .requestMatchers("/api/admin/**").hasRole(Const.ROLE_ADMIN)
                        // 其余所有接口都必须登录（user 或 admin）
                        .anyRequest().hasAnyRole(Const.ROLE_DEFAULT, Const.ROLE_ADMIN)
                )
                .formLogin(conf -> conf
                        .loginProcessingUrl("/api/auth/login")
                        // 登录凭据是邮箱，不是用户名 —— 不改这里的话，
                        // Spring Security 会去表单里找 username 字段，永远取不到值
                        .usernameParameter("email")
                        .failureHandler(this::onAuthenticationFailure)
                        .successHandler(this::onAuthenticationSuccess)
                )
                .logout(conf -> conf
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(this::onLogoutSuccess)
                )
                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint(this::onUnauthorized)
                        .accessDeniedHandler(this::onAccessDeny)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(conf -> conf
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthorizeFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        User user = (User) authentication.getPrincipal();
        Account account = service.findAccountByNameOrEmail(user.getUsername());
        // 防御性判空：正常路径下走不到这里（loadUserByUsername 查不到会抛
        // UsernameNotFoundException，认证根本不会成功）。但"认证通过"到"这里查库"
        // 之间存在时间窗口，账号若恰好在此刻被删除，下面 account.isBanned() 就会 NPE。
        if (account == null) {
            response.getWriter().write(RestBean.unauthorized("账户不存在，请重新注册").asJsonString());
            return;
        }
        // 阶段 3：被封禁的账号连令牌都拿不到（已经拿到的那张由 JwtAuthorizeFilter 拦截）
        if (account.isBanned()) {
            response.getWriter().write(RestBean.forbidden("登录失败，此账户已被封禁").asJsonString());
            return;
        }
        // 关键修复：这里必须用数据库里真实的 id，不能用常量 1，
        // 否则所有用户的令牌 id 都是 1，论坛发帖/鉴权会全部串号。
        String token = utils.createJwt(user, account.getId(), account.getUsername());
        if (token == null) {
            response.getWriter().write(RestBean.forbidden("登录验证频繁，请稍后再试").asJsonString());
            return;
        }
        AuthorizeVO vo = new AuthorizeVO();
        vo.setExpires(utils.expireTime());
        vo.setRole(account.getRole());
        vo.setToken(token);
        vo.setUsername(account.getUsername());
        response.getWriter().write(RestBean.success(vo).asJsonString());
    }

    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                @Nullable Authentication authentication) throws IOException, ServletException {
        response.setContentType("application/json;charset=utf-8");
        PrintWriter writer = response.getWriter();
        String authorization = request.getHeader("Authorization");
        if (utils.invalidateJwt(authorization)) {
            writer.write(RestBean.success().asJsonString());
        } else {
            writer.write(RestBean.failure(400, "退出登录失败").asJsonString());
        }
    }

    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.setContentType("application/json;charset=utf-8");

        // 反查一次库，把「邮箱没注册」和「密码错了」分开提示。
        // 教科书做法是统一提示以防用户名枚举，但本站的用户名/邮箱在论坛里本来就公开，枚举没有意义，
        // 而用户更常遇到的是「忘了自己改过什么」—— 明确告诉他比含糊更有用。
        String email = request.getParameter("email");
        boolean exists = email != null && !email.isBlank()
                && service.findAccountByNameOrEmail(email.trim()) != null;

        String message = exists ? "密码错误，请重新输入" : "该邮箱尚未注册";
        response.getWriter().write(RestBean.unauthorized(message).asJsonString());
    }

    public void onAccessDeny(HttpServletRequest request,
                             HttpServletResponse response,
                             AccessDeniedException exception) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(RestBean.forbidden(exception.getMessage()).asJsonString());
    }

    public void onUnauthorized(HttpServletRequest request,
                               HttpServletResponse response,
                               AuthenticationException exception) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(RestBean.unauthorized(exception.getMessage()).asJsonString());
    }
}
