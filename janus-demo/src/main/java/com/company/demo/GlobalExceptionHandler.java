package com.company.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * demo 全局异常处理：打印堆栈并返回统一 JSON。
 *
 * <p>没有它，未捕获异常会被 Spring 默认 /error 吞掉（无日志、只见 500），
 * 排障非常困难。业务系统接入时务必保留类似配置。</p>
 *
 * @author ppp
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handle(Exception e) {
        log.error("未捕获异常: {} {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Map.of(
                "error", "internal_error",
                "message", e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    /**
     * 静态资源不存在（favicon、路径拼错等）：属正常 404，降级 WARN 不打堆栈，
     * 避免浏览器自动请求 /favicon.ico 之类噪声刷 ERROR 日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNoResource(NoResourceFoundException e) {
        log.warn("静态资源不存在: {}", e.getResourcePath());
        return Map.of("error", "not_found", "message", e.getMessage());
    }
}
