package com.lu.ddwyydemo04.controller.DQE;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DQEIndexController {

    @GetMapping("/DQEIndex") // 处理页面跳转请求
    public String loginDQEIndex() {
        // 返回跳转页面的视图名称
        return "DQEIndex";
    }

    @GetMapping("/projectSchedule") // 处理页面跳转请求
    public String loginProjectSchedule() {
        // 返回跳转页面的视图名称
        return "DQE/projectSchedule";
    }

    @GetMapping("/problemMoudle") // 处理页面跳转请求
    public String loginProblemMoudle() {

        // 返回跳转页面的视图名称
        return "DQE/problemMoudle";
    }

    @GetMapping("/sampleData") // 处理页面跳转请求
    public String loginSampleData() {
        // 返回跳转页面的视图名称
        return "DQE/sampleData";
    }

    @GetMapping("/otherMoudle") // 处理页面跳转请求
    public String loginOtherMoudle() {
        // 返回跳转页面的视图名称
        return "DQE/otherMoudle";
    }

    @GetMapping("/dataBoard") // 处理页面跳转请求
    public String logindataBoard() {
        // 返回跳转页面的视图名称
        return "DQE/dataBoard";
    }
}
