package com.meileme.resort.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** 实例 ID 生成器（建筑 / 员工 / 内装 / 指令通用）。 */
@Component
public class IdGenerator {

    private final AtomicLong counter = new AtomicLong(1);

    public String next(String prefix) {
        return prefix + "_" + counter.getAndIncrement();
    }
}
