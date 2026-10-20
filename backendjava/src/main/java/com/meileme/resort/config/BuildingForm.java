package com.meileme.resort.config;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 建筑形态。
 *
 * <p>形态自带「客群影响系数、每间可接待人数、固定房价、维护费、舒适度、能力需求」等固有属性；
 * 每个形态自带等级 Lv1~Lv3，<b>形态满级后才可进行下一阶段分支进化</b>。</p>
 *
 * <p>本期只设计「标准客房」母本的进化树（家庭亲子线 / 商务行政线），其余母本形态仅登记备用。</p>
 */
@Getter
public class BuildingForm {

    /** 形态唯一 ID（指令与存档中的引用键） */
    private final String id;
    /** 展示名 */
    private final String displayName;
    /** 所属母本 */
    private final MotherBuildingType mother;
    /** 进化线（BASE / FAMILY / BUSINESS / MERGE） */
    private final EvolutionLine line;
    /** 进化阶段（0 = 母本基础形态） */
    private final int tier;
    /** 每间可接待人数（住宿形态；配套形态为 0） */
    private final double capacityPerRoom;
    /** 每间固定房价，下标 0 = Lv1（住宿形态） */
    private final double[] pricePerRoomByLevel;
    /** 配套服务容量上限（配套形态；住宿形态为 0） */
    private final double serviceCapacity;
    /** 配套固定单人消费单价，下标 0 = Lv1（配套形态） */
    private final double[] unitPriceByLevel;
    /** 新建该形态建筑的一次性花费（仅基础形态 > 0） */
    private final double buildCost;
    /** 分支进化到本形态的一次性花费（tier = 0 时为 0） */
    private final double evolveCost;
    /** 形态等级升级花费：[Lv1→Lv2, Lv2→Lv3] */
    private final double[] formLevelUpCost;
    /** 各等级每日维护费，下标 0 = Lv1 */
    private final double[] maintenanceByLevel;
    /** 形态基础舒适度（Lv1 时基准值） */
    private final double baseComfort;
    /** 客群影响系数（形态固有属性，内装不提供） */
    private final Map<CustomerSegment, Double> segmentAffinity;
    /** 各形态等级下的内装等级上限：[Lv1, Lv2, Lv3] */
    private final int[] maxInteriorLevel;
    /** 分支进化前驱形态（tier = 0 时为 null） */
    private final BuildingForm evolveFrom;
    /** 该形态每个能力的基础需求（供给不足仅降低舒适度、不削承载） */
    private final Map<AbilityType, Double> abilityDemand;

    private BuildingForm(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.mother = b.mother;
        this.line = b.line;
        this.tier = b.tier;
        this.capacityPerRoom = b.capacityPerRoom;
        this.pricePerRoomByLevel = b.pricePerRoomByLevel;
        this.serviceCapacity = b.serviceCapacity;
        this.unitPriceByLevel = b.unitPriceByLevel;
        this.buildCost = b.buildCost;
        this.evolveCost = b.evolveCost;
        this.formLevelUpCost = b.formLevelUpCost;
        this.maintenanceByLevel = b.maintenanceByLevel;
        this.baseComfort = b.baseComfort;
        this.segmentAffinity = b.segmentAffinity;
        this.maxInteriorLevel = b.maxInteriorLevel;
        this.evolveFrom = b.evolveFrom;
        this.abilityDemand = b.abilityDemand;
    }

    /** 是否住宿类形态 */
    public boolean isAccommodation() {
        return mother.getCategory() == BuildingCategory.ACCOMMODATION;
    }

    /** 是否配套类形态 */
    public boolean isSupport() {
        return mother.getCategory() == BuildingCategory.SUPPORT;
    }

    /** 指定形态等级下的每间房价 */
    public double pricePerRoomOf(int formLevel) {
        return pick(pricePerRoomByLevel, formLevel);
    }

    /** 指定形态等级下的配套单人单价 */
    public double unitPriceOf(int formLevel) {
        return pick(unitPriceByLevel, formLevel);
    }

    /** 指定形态等级下的每日维护费 */
    public double maintenanceOf(int formLevel) {
        return pick(maintenanceByLevel, formLevel);
    }

    /** 从指定形态等级升级到下一级的费用；已满级返回 0 */
    public double formLevelUpCostOf(int formLevel) {
        int idx = formLevel - GameConfig.FORM_MIN_LEVEL;
        if (idx < 0 || idx >= formLevelUpCost.length) {
            return 0;
        }
        return formLevelUpCost[idx];
    }

    /** 指定形态等级下的内装等级上限 */
    public int maxInteriorLevelOf(int formLevel) {
        int idx = formLevel - GameConfig.FORM_MIN_LEVEL;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= maxInteriorLevel.length) {
            idx = maxInteriorLevel.length - 1;
        }
        return maxInteriorLevel[idx];
    }

    /** 形态等级对「分配权重」的加成：1 + step × (level − 1) */
    public double levelWeightOf(int formLevel) {
        return 1 + GameConfig.FORM_LEVEL_WEIGHT_STEP * (formLevel - GameConfig.FORM_MIN_LEVEL);
    }

    /** 形态等级对「基础舒适度」的加成后结果 */
    public double comfortOf(int formLevel) {
        return baseComfort * (1 + GameConfig.FORM_LEVEL_COMFORT_STEP * (formLevel - GameConfig.FORM_MIN_LEVEL));
    }

    /** 该形态对指定客群的匹配系数（未声明则取兜底值） */
    public double affinityOf(CustomerSegment segment) {
        Double v = segmentAffinity.get(segment);
        return v == null ? GameConfig.DEFAULT_SEGMENT_AFFINITY : v;
    }

    /** 该形态对指定能力的基础需求 */
    public double abilityDemandOf(AbilityType ability) {
        Double v = abilityDemand.get(ability);
        return v == null ? 0d : v;
    }

    private static double pick(double[] arr, int level) {
        if (arr == null || arr.length == 0) {
            return 0d;
        }
        int idx = level - GameConfig.FORM_MIN_LEVEL;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= arr.length) {
            idx = arr.length - 1;
        }
        return arr[idx];
    }

    // ======================= 构建器 =======================

    public static Builder builder(String id, String displayName, MotherBuildingType mother) {
        return new Builder(id, displayName, mother);
    }

    /** 形态构建器（避免超长位置参数，集中配置数值） */
    public static class Builder {
        private final String id;
        private final String displayName;
        private final MotherBuildingType mother;
        private EvolutionLine line = EvolutionLine.BASE;
        private int tier = 0;
        private double capacityPerRoom = 0;
        private double[] pricePerRoomByLevel = new double[]{0, 0, 0};
        private double serviceCapacity = 0;
        private double[] unitPriceByLevel = new double[]{0, 0, 0};
        private double buildCost = 0;
        private double evolveCost = 0;
        private double[] formLevelUpCost = new double[]{0, 0};
        private double[] maintenanceByLevel = new double[]{0, 0, 0};
        private double baseComfort = 50;
        private Map<CustomerSegment, Double> segmentAffinity = new EnumMap<>(CustomerSegment.class);
        private int[] maxInteriorLevel = new int[]{
                GameConfig.INTERIOR_MAX_LEVEL_FALLBACK,
                GameConfig.INTERIOR_MAX_LEVEL_FALLBACK,
                GameConfig.INTERIOR_MAX_LEVEL_FALLBACK};
        private BuildingForm evolveFrom = null;
        private Map<AbilityType, Double> abilityDemand = new EnumMap<>(AbilityType.class);

        private Builder(String id, String displayName, MotherBuildingType mother) {
            this.id = id;
            this.displayName = displayName;
            this.mother = mother;
        }

        public Builder line(EvolutionLine line) {
            this.line = line;
            return this;
        }

        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        public Builder capacityPerRoom(double v) {
            this.capacityPerRoom = v;
            return this;
        }

        public Builder pricePerRoom(double lv1, double lv2, double lv3) {
            this.pricePerRoomByLevel = new double[]{lv1, lv2, lv3};
            return this;
        }

        public Builder serviceCapacity(double v) {
            this.serviceCapacity = v;
            return this;
        }

        public Builder unitPrice(double lv1, double lv2, double lv3) {
            this.unitPriceByLevel = new double[]{lv1, lv2, lv3};
            return this;
        }

        public Builder buildCost(double v) {
            this.buildCost = v;
            return this;
        }

        public Builder evolveCost(double v) {
            this.evolveCost = v;
            return this;
        }

        public Builder formLevelUpCost(double lv1ToLv2, double lv2ToLv3) {
            this.formLevelUpCost = new double[]{lv1ToLv2, lv2ToLv3};
            return this;
        }

        public Builder maintenance(double lv1, double lv2, double lv3) {
            this.maintenanceByLevel = new double[]{lv1, lv2, lv3};
            return this;
        }

        public Builder baseComfort(double v) {
            this.baseComfort = v;
            return this;
        }

        public Builder affinity(CustomerSegment segment, double v) {
            this.segmentAffinity.put(segment, v);
            return this;
        }

        public Builder maxInteriorLevel(int lv1, int lv2, int lv3) {
            this.maxInteriorLevel = new int[]{lv1, lv2, lv3};
            return this;
        }

        public Builder evolveFrom(BuildingForm form) {
            this.evolveFrom = form;
            return this;
        }

        public Builder abilityDemand(AbilityType ability, double v) {
            this.abilityDemand.put(ability, v);
            return this;
        }

        public BuildingForm build() {
            Map<CustomerSegment, Double> aff = new EnumMap<>(CustomerSegment.class);
            aff.putAll(this.segmentAffinity);
            Map<AbilityType, Double> dem = new EnumMap<>(AbilityType.class);
            dem.putAll(this.abilityDemand);
            this.segmentAffinity = Collections.unmodifiableMap(new LinkedHashMap<>(aff));
            this.abilityDemand = Collections.unmodifiableMap(new LinkedHashMap<>(dem));
            return new BuildingForm(this);
        }
    }
}
