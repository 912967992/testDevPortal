package com.lu.ddwyydemo04.controller.reliabilityLab;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class Index {
    @GetMapping("/")
    public String root() {
        return "home";
    }

    @GetMapping("/reliablityLab/index") // 处理页面跳转请求
    public String loginUsageRate() {
        // 返回跳转页面的视图名称
        return "reliablityLab/reliabilityIndex";
    }


    // 跳转到物联网温箱监控页面模板（reliabilityIndex.html）
    @GetMapping("/reliabilityIndex")
    public String reliabilityMonitor() {
        return "reliablityLab/reliabilityIndex";
    }

}
