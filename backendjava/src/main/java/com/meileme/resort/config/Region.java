package com.meileme.resort.config;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

/**
 * 大区域：每个大区域下划分多个地块。
 *
 * <p>各区域的每日基础客流与客群占比<b>独立配置</b>（区域之间不共享客流池），
 * 区域内的全部建筑共同竞争本区域的客流。地块解锁按「上一地块已解锁 + 达成成就」链式推进，
 * 区域内首个地块解锁即视为该区域解锁。</p>
 */
@Getter
public enum Region {

    /** 宁姆格福（开局区域） */
    LIMGRAVE("宁姆格福", 100, 0.40, 0.20, 0.30, 0.10),
    /** 湖之利耶尼亚（宁姆格福全部地块推进后解锁） */
    LIURNIA("湖之利耶尼亚", 200, 0.20, 0.10, 0.40, 0.30);

    private final String displayName;
    /** 每日基础客流基数（本区域潜在客流总量） */
    private final double baseDailyFlow;
    /** 固定客群比例（家庭 / 商务 / 浪漫 / 康养） */
    private final Map<CustomerSegment, Double> segmentRatios;

    Region(String displayName, double baseDailyFlow,
           double familyRatio, double businessRatio, double romanticRatio, double wellnessRatio) {
        this.displayName = displayName;
        this.baseDailyFlow = baseDailyFlow;
        Map<CustomerSegment, Double> ratios = new EnumMap<>(CustomerSegment.class);
        ratios.put(CustomerSegment.FAMILY, familyRatio);
        ratios.put(CustomerSegment.BUSINESS, businessRatio);
        ratios.put(CustomerSegment.ROMANTIC, romanticRatio);
        ratios.put(CustomerSegment.WELLNESS, wellnessRatio);
        this.segmentRatios = ratios;
    }

    /** 本区域内指定客群的潜在客流 = 区域基础客流 × 该客群占比 */
    public double potentialOf(CustomerSegment segment) {
        Double ratio = segmentRatios.get(segment);
        return baseDailyFlow * (ratio == null ? 0d : ratio);
    }
}
