package com.meileme.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 新玩法领域位于 com.meileme.resort.*，故扫描根包上提为 com.meileme
@SpringBootApplication(scanBasePackages = "com.meileme")
@EnableScheduling // 开启定时任务支持，用于游戏 Tick 与时钟广播
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
