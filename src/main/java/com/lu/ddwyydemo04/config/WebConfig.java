package com.lu.ddwyydemo04.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.storage.imagepath}")
    private String imagepath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源的路径
//        registry.addResourceHandler("/imageDirectory/**")
//                .addResourceLocations("file:imageDirectory/");
        registry.addResourceHandler("/imageDirectory/**")
                .addResourceLocations("file:"+imagepath +"/");
    }
}
