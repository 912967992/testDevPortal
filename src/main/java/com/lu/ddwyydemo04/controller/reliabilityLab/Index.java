package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
public class Index {
    @GetMapping("/")
    public String root(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 检查session中是否有用户信息
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            // 未登录，重定向到钉钉免登页面（index.html）
            // 添加 from=redirect 参数，防止无限循环
            response.sendRedirect("/index.html?from=redirect");
            return null;
        }
        // 已登录，返回主页
        return "home";
    }

//    @GetMapping("/reliablityLab/index") // 处理页面跳转请求
//    public String loginUsageRate() {
//        // 返回跳转页面的视图名称
//        return "reliablityLab/reliabilityIndex";
//    }


    // 跳转到物联网温箱监控页面模板（reliabilityIndex.html）
    @GetMapping("/reliabilityIndex")
    public String reliabilityMonitor() {
        return "reliablityLab/reliabilityIndex";
    }

}
