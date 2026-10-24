package com.example.blog.handler;

import com.example.blog.common.BusinessException;
import com.example.blog.common.ErrorCode;
import com.example.blog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：Ajax 请求返回 JSON，页面请求返回错误页
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {}", e.getMessage());
        return isAjax(request)
                ? ResponseEntity.ok(Result.fail(e.getCode(), e.getMessage()))
                : errorPage(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return isAjax(request)
                ? ResponseEntity.ok(Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg))
                : errorPage(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /** 无权限 */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("无权限访问: {}", request.getRequestURI());
        return isAjax(request)
                ? ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Result.fail(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage()))
                : errorPage(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
    }

    /**
     * 静态资源未找到。Spring 6.1+ 抛 NoResourceFoundException，
     * 若不单独处理会被下面的兜底分支吞成 500，导致「图片不存在」变成服务器错误。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return isAjax(request)
                ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail(ErrorCode.NOT_FOUND.getCode(), "资源不存在"))
                : errorPage(ErrorCode.NOT_FOUND.getCode(), "资源不存在");
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {}", request.getRequestURI(), e);
        return isAjax(request)
                ? ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()))
                : errorPage(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    /** 判定是否 Ajax：/api 前缀、X-Requested-With 头、或明确接受 JSON */
    private boolean isAjax(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    /**
     * 渲染错误页。必须显式设置 HTTP 状态码，
     * 否则不存在的文章会「页面显示 404 但响应仍是 200」，对 SEO 和爬虫都是错的。
     */
    private ModelAndView errorPage(Integer code, String message) {
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("code", code);
        mv.addObject("message", message);
        mv.setStatus(toHttpStatus(code));
        return mv;
    }

    private HttpStatus toHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(code);
        } catch (IllegalArgumentException e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
