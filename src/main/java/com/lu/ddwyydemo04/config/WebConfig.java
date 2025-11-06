package com.lu.ddwyydemo04.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.storage.imagepath}")
    private String imagepath;

    @Autowired
    private DingTalkAuthInterceptor dingTalkAuthInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源的路径
//        registry.addResourceHandler("/imageDirectory/**")
//                .addResourceLocations("file:imageDirectory/");
        registry.addResourceHandler("/imageDirectory/**")
                .addResourceLocations("file:"+imagepath +"/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dingTalkAuthInterceptor)
                .addPathPatterns(
                        "/reliablityLab/**",      // reliabilityLab相关页面
                        "/iot/**"                  // IoT数据相关接口
                )
                .excludePathPatterns(
                        "/",                      // 主页
                        "/reliabilityIndex",      // reliabilityIndex页面（不需要身份验证，从本地缓存或URL参数获取）
                        "/dingtalk/**",           // 钉钉免登相关接口
                        "/api/getUserInfo",       // 获取用户信息接口
                        "/getJsapiConfig",        // 获取JSAPI配置接口
                        "/iot/createCommand",     // 创建命令接口（在reliabilityIndex页面使用）
                        "/iot/getExcuteCommand",  // 获取执行命令接口（在reliabilityIndex页面使用）
                        "/iot/data",              // 接收IoT数据接口（可能不需要认证）
                        "/iot/data/latest",       // 获取最新数据接口
                        "/css/**",                // 静态资源
                        "/js/**",
                        "/json/**",
                        "/imageDirectory/**",
                        "/error",                 // 错误页面
                        "/favicon.ico"           // 网站图标
                );
    }
}
