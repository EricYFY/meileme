package com.meileme.resort.config;

import lombok.Getter;

/** 客群分类。景区的固定客群比例配置在 {@link ScenicConfig}。 */
@Getter
public enum CustomerSegment {

    FAMILY("家庭"),
    BUSINESS("商务"),
    ROMANTIC("浪漫"),
    WELLNESS("康养");

    private final String displayName;

    CustomerSegment(String displayName) {
        this.displayName = displayName;
    }
}
