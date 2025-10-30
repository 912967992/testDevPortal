package com.lu.ddwyydemo04;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@MapperScan("com.lu.ddwyydemo04.dao")
@SpringBootApplication
public class DdwyyDemo04Application {

    public static void main(String[] args) {
        SpringApplication.run(DdwyyDemo04Application.class, args);
    }

}
