package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 景区顶层固定属性。
 *
 * <p>景区自带固定客群比例与每日基础客流基数；全局无随机事件、无四季系统，
 * 仅有时间日期系统（每局起始日为纯展示字段，后台配置）。</p>
 */
@Getter
@AllArgsConstructor
public class ScenicConfig {

    /** 景区名称 */
    private final String name;
    /** 每日基础客流基数（潜在客流总量） */
    private final double baseDailyFlow;
    /** 固定客群比例（各客群占比，合计应为 1.0） */
    private final Map<CustomerSegment, Double> segmentRatios;

    /** 指定客群的原始潜在客流 */
    public double potentialOf(CustomerSegment segment) {
        Double ratio = segmentRatios.get(segment);
        return baseDailyFlow * (ratio == null ? 0d : ratio);
    }
}
