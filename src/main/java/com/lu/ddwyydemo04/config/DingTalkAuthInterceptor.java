package com.lu.ddwyydemo04.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 钉钉免登拦截器
 * 检查用户是否已经完成钉钉免登认证
 */
@Component
public class DingTalkAuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER_ID = "userId";
    private static final String SESSION_USERNAME = "username";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取session
        HttpSession session = request.getSession(false);
        
        // 检查session中是否有用户信息
        if (session == null || session.getAttribute(SESSION_USER_ID) == null) {
            // 未登录，重定向到钉钉免登页面
            String requestURI = request.getRequestURI();
            String queryString = request.getQueryString();
            // 构建完整的请求URL，包含查询参数
            String fullURI = requestURI;
            if (queryString != null && !queryString.isEmpty()) {
                fullURI += "?" + queryString;
            }
            // 将重定向URL作为参数传递
            response.sendRedirect("/dingtalk/login?redirect=" + java.net.URLEncoder.encode(fullURI, "UTF-8"));
            return false;
        }
        
        return true;
    }
}

