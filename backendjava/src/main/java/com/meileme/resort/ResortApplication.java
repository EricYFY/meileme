package com.meileme.resort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 度假村经营玩法领域全部位于 com.meileme.resort.*
@SpringBootApplication(scanBasePackages = "com.meileme.resort")
@EnableScheduling // 开启定时任务支持，用于游戏 Tick 与时钟广播
public class ResortApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResortApplication.class, args);
    }
}