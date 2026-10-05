package com.example.controller.exception;

import com.example.entity.RestBean;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局参数校验异常处理。
 *
 * 这里只做一件事：把注解里写的 message 原样送到前端。
 * 不做的话，前端只能弹一句笼统的「请求参数有误」，用户根本不知道是哪一项、错在哪 ——
 * 而每个注解上其实早就写好了「手机号格式不正确」这种话，只是没人往外送。
 *
 * ⚠️ 两类校验失败走的是**不同异常**，必须分别处理：
 *   1) @RequestBody + @Valid        → MethodArgumentNotValidException（Spring MVC 抛的）
 *   2) @RequestParam + 类上 @Validated → ConstraintViolationException（jakarta 抛的）
 * 原来只处理了 jakarta 的 ValidationException（即第 2 类），第 1 类会落到 Spring 默认处理，
 * 返回的是另一套 JSON（HTTP 400 + Spring 的字段结构），前端的 axios 拦截器认不出来，
 * 只会提示「发生了一些错误，请联系管理员」。
 */
@Slf4j
@RestControllerAdvice
public class ValidationController {

    /** 请求体校验失败：@RequestBody + @Valid */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestBean<Void> handleBodyValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("请求参数有误");
        log.warn("请求体校验失败: {}", message);
        return RestBean.failure(400, message);
    }

    /** 请求参数 / 路径变量校验失败：@RequestParam + 类上 @Validated */
    @ExceptionHandler(ConstraintViolationException.class)
    public RestBean<Void> handleParamValidation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("请求参数有误");
        log.warn("请求参数校验失败: {}", message);
        return RestBean.failure(400, message);
    }

    /** 兜底：其它 jakarta 校验异常 */
    @ExceptionHandler(ValidationException.class)
    public RestBean<Void> validateException(ValidationException exception) {
        log.warn("Resolve [{}: {}]", exception.getClass().getName(), exception.getMessage());
        return RestBean.failure(400, "请求参数有误");
    }
}
