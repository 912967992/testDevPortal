package com.lu.ddwyydemo04.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

@Configuration
@EnableScheduling  // 启用定时任务调度
public class AppConfig {
    //服务器文件大小上传限制修改的地方也要改这里！！！
    @Bean
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        multipartResolver.setMaxUploadSize(100 * 1024 * 1024); // 50MB
        multipartResolver.setMaxUploadSizePerFile(100 * 1024 * 1024); // 50MB per file
        multipartResolver.setMaxInMemorySize(1024 * 1024); // 1MB
        return multipartResolver;
    }
}
