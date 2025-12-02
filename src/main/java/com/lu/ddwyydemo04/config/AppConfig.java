package com.lu.ddwyydemo04.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling  // 启用定时任务调度
public class AppConfig implements WebMvcConfigurer {

    @Autowired
    private DingTalkAuthInterceptor dingTalkAuthInterceptor;

    //服务器文件大小上传限制修改的地方也要改这里！！！
    @Bean
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        multipartResolver.setMaxUploadSize(100 * 1024 * 1024); // 50MB
        multipartResolver.setMaxUploadSizePerFile(100 * 1024 * 1024); // 50MB per file
        multipartResolver.setMaxInMemorySize(1024 * 1024); // 1MB
        return multipartResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dingTalkAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/home",           // 公开主页，不需要验证
                        "/index.html",      // 登录页面
                        "/dingtalk/**",     // 钉钉相关接口
                        "/css/**",          // 静态资源
                        "/js/**",           // 静态资源
                        "/imageDirectory/**", // 图片资源
                        "/api/**",          // API接口（如果需要公开访问）
                        "/iot/**",          // IoT接口（如果需要公开访问）
                        "/reliabilityIndex",  // 可靠性监控页面（如果需要公开访问）
                        "/dataManagement"    // 数据管理页面（不需要session验证，只看本地缓存）
                );
    }
}
