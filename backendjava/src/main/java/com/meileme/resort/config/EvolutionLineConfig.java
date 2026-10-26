package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 建筑形态进化路线配置模型。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionLineConfig {
    private String id;
    private String sourceFormId;
    private String targetFormId;
    private int requiredLevel;
    private double cost;
    private int days;
}
