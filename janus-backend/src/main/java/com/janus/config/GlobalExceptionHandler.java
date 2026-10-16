package com.janus.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.janus.common.result.R;
import com.janus.common.result.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：统一转为「HTTP 状态码 + code:-1 包裹体」，禁止堆栈出站。
 *
 * @author ppp
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：按异常携带的状态码返回 */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<R<Void>> handleService(ServiceException e) {
        return ResponseEntity.status(e.getStatus()).body(R.fail(e.getMessage()));
    }

    /** Sa-Token 未登录：401（前端据此触发无感刷新，契约必需） */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(401).body(R.fail("Unauthorized Exception"));
    }

    /** Sa-Token 无角色：403 */
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException e) {
        return ResponseEntity.status(403).body(R.fail("无权限执行此操作"));
    }

    /** Sa-Token 无权限点：403 */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<R<Void>> handleNotPermission(NotPermissionException e) {
        return ResponseEntity.status(403).body(R.fail("无权限执行此操作"));
    }

    /** 参数校验失败：400 + 首条校验消息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(msg));
    }

    /** 缺少必填请求参数：400（原先落到兜底 500，语义不对） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(R.fail("缺少请求参数: " + e.getParameterName()));
    }

    /** multipart 请求缺少指定文件部件：400 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<R<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(R.fail("缺少上传文件: " + e.getRequestPartName()));
    }

    /**
     * 静态资源 404：区分两类请求分别处理。
     *
     * <p>SPA history 深链兜底（/auth/login、/dashboard 等无扩展名路径）：
     * 前端构建产物由 Spring Boot 静态资源处理器在根路径直出（/jse、/js、/css、/_app.config.js 等），
     * 资源处理器找不到文件时抛 NoResourceFoundException，这里把「非接口」的无扩展名 404
     * 统一转发到 index.html，由前端路由接管。
     * 注意不能用 @GetMapping("/xxx/**") 兜底 Controller 实现：Controller 映射优先级高于静态资源
     * 处理器，会把 /jse、/js 等真实 JS 文件也转发成 index.html（text/html），浏览器报
     * "Expected a JavaScript module ... MIME type text/html"。
     *
     * <p>接口路径（/api、/sso、/v3）与带扩展名的文件请求：保持 JSON 404 契约，
     * 前端 fetch 拿到状态码而非 HTML；陈旧 hash 的失效 chunk 也按 404 处理。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.contains(".") && !uri.startsWith("/api") && !uri.startsWith("/sso") && !uri.startsWith("/v3")) {
            log.debug("SPA 深链兜底: {} -> /index.html", uri);
            return new ModelAndView("forward:/index.html");
        }
        log.warn("资源不存在: {}", uri);
        return ResponseEntity.status(404).body(R.fail("Not Found"));
    }

    /**
     * SSO 消息处理异常（验签失败 / 无效 ticket / client 不合法等）：
     * 返回 HTTP 200 + 错误 SaResult（SSO 消息协议约定，client 端读 code/msg 感知失败）；
     * 若返回 500，client 端 RestTemplate 直接抛 HttpServerErrorException，丢失错误语义。
     */
    @ExceptionHandler({cn.dev33.satoken.sso.exception.SaSsoException.class,
            cn.dev33.satoken.sign.exception.SaSignException.class})
    public cn.dev33.satoken.util.SaResult handleSso(cn.dev33.satoken.exception.SaTokenException e) {
        log.warn("SSO 消息处理失败: {}", e.getMessage());
        return cn.dev33.satoken.util.SaResult.error(e.getMessage());
    }

    /** 兜底：500，仅记日志，不泄漏内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.internalServerError().body(R.fail("服务器内部错误"));
    }
}
