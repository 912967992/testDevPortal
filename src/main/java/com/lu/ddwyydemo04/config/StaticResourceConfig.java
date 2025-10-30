package com.lu.ddwyydemo04.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    @Value("${file.storage.imagepath}")
    private String imagepath;

    //确保Spring Boot应用程序正确配置了静态资源服务，以便能够访问到存储在本地的图片文件。在Spring Boot的配置类中添加如下配置：
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/imageDirectory/**")
//                .addResourceLocations("file:C:/imageDirectory/");
        registry.addResourceHandler("/imageDirectory/**")
                .addResourceLocations("file:"+imagepath +"/");
    }
}
