package com.janus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 后台管理 Java 后端启动类。
 *
 * @author ppp
 */
@EnableScheduling
@SpringBootApplication
public class JanusApplication {

    public static void main(String[] args) {
        SpringApplication.run(JanusApplication.class, args);
    }
}