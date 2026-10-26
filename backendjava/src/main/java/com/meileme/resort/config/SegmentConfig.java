package com.meileme.resort.config;

import lombok.Builder;
import lombok.Getter;

/**
 * 客群配置模型（数据驱动，来源于 admin-config.json / default-config.json）。
 */
@Getter
@Builder(toBuilder = true)
public class SegmentConfig {

    private final String id;
    private final String displayName;
    private final String description;
    private final String color;
    private final String icon;
}
