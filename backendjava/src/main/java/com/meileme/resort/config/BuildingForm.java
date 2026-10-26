package com.meileme.resort.config;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 建筑形态。
 *
 * <p>形态自带「客群影响系数、每间可接待人数、固定房屋收益、舒适度、能力需求」等固有属性；
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
    /** 建筑大类（住宿类 / 配套类；自主拥有，不受母体限制） */
    private final BuildingCategory category;
    /** 进化阶段（0 = 基础形态） */
    private final int tier;
    /** 每间可接待人数（住宿形态；配套形态为 0） */
    private final double capacityPerRoom;
    /** 每间固定房屋收益，下标 0 = Lv1（住宿形态） */
    private final double[] pricePerRoomByLevel;
    /** 配套服务容量上限（配套形态；住宿形态为 0） */
    private final double serviceCapacity;
    /** 配套固定单人收益，下标 0 = Lv1（配套形态） */
    private final double[] unitPriceByLevel;
    /** 分支进化到本形态的一次性花费（tier = 0 时为 0） */
    private final double evolveCost;
    /** 形态等级升级花费：[Lv1→Lv2, Lv2→Lv3] */
    private final double[] formLevelUpCost;
    /** 形态等级升级工期（游戏日）：[Lv1→Lv2, Lv2→Lv3] */
    private final int[] formLevelUpDays;
    /** 分支进化到本形态的工期（游戏日；基础形态 / 合并产物为 0） */
    private final int evolveDays;
    /** 形态基础舒适度（Lv1 时基准值） */
    private final double baseComfort;
    /** 客群影响系数（形态固有属性，内装不提供） */
    private final Map<String, Double> segmentAffinity;
    /** 各形态等级下的内装等级上限：[Lv1, Lv2, Lv3] */
    private final int[] maxInteriorLevel;
    /** 该形态每个能力的基础需求（供给不足仅降低舒适度、不削承载） */
    private final Map<AbilityType, Double> abilityDemand;
    /** 该形态允许包含/安装的内装 ID 列表 */
    private final List<String> availableInteriors;
    /** 该形态承载能力加成所对应的内装 ID（至多对应一个内装，如 BED / DINING_TABLE） */
    private final String capacityInterior;

    private BuildingForm(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.category = b.category != null ? b.category : BuildingCategory.ACCOMMODATION;
        this.tier = b.tier;
        this.capacityPerRoom = b.capacityPerRoom;
        this.pricePerRoomByLevel = b.pricePerRoomByLevel;
        this.serviceCapacity = b.serviceCapacity;
        this.unitPriceByLevel = b.unitPriceByLevel;
        this.evolveCost = b.evolveCost;
        this.formLevelUpCost = b.formLevelUpCost;
        this.formLevelUpDays = b.formLevelUpDays;
        this.evolveDays = b.evolveDays;
        this.baseComfort = b.baseComfort;
        this.segmentAffinity = Collections.unmodifiableMap(new LinkedHashMap<>(b.segmentAffinity));
        this.maxInteriorLevel = b.maxInteriorLevel;
        this.abilityDemand = b.abilityDemand;
        this.availableInteriors = Collections.unmodifiableList(new ArrayList<>(b.availableInteriors));
        this.capacityInterior = b.capacityInterior;
    }

    /** 是否为合并形态 */
    public boolean isMerged() {
        return id != null && id.toUpperCase().contains("MERGE");
    }

    /** 是否住宿类形态 */
    public boolean isAccommodation() {
        return category == BuildingCategory.ACCOMMODATION;
    }

    /** 是否配套类形态（优先以自身 category 为准，避免依赖母体） */
    public boolean isSupport() {
        return category == BuildingCategory.SUPPORT;
    }

    /** 当前形态是否允许/拥有该内装 */
    public boolean isInteriorAllowed(String interiorId) {
        if (availableInteriors != null && !availableInteriors.isEmpty()) {
            return availableInteriors.contains(interiorId);
        }
        return true;
    }

    /** 指定形态等级下的每间房屋收益 */
    public double pricePerRoomOf(int formLevel) {
        return pick(pricePerRoomByLevel, formLevel);
    }

    /** 指定形态等级下的配套单人收益 */
    public double unitPriceOf(int formLevel) {
        return pick(unitPriceByLevel, formLevel);
    }

    /** 从指定形态等级升级到下一级的费用；已满级返回 0 */
    public double formLevelUpCostOf(int formLevel) {
        int idx = formLevel - GameConfig.FORM_MIN_LEVEL;
        if (idx < 0 || idx >= formLevelUpCost.length) {
            return 0;
        }
        return formLevelUpCost[idx];
    }

    /** 从指定形态等级升级到下一级的工期（游戏日）；已满级返回 0 */
    public int formLevelUpDaysOf(int formLevel) {
        int idx = formLevel - GameConfig.FORM_MIN_LEVEL;
        if (idx < 0 || idx >= formLevelUpDays.length) {
            return 0;
        }
        return formLevelUpDays[idx];
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

    /** 该形态当前配置的最大等级（基于 formLevelUpCost 长度动态推导） */
    public int getMaxLevel() {
        return formLevelUpCost == null ? GameConfig.FORM_MIN_LEVEL : formLevelUpCost.length + GameConfig.FORM_MIN_LEVEL;
    }

    /** 形态等级对「基础舒适度」的加成后结果 */
    public double comfortOf(int formLevel) {
        return baseComfort * (1 + GameConfig.FORM_LEVEL_COMFORT_STEP * (formLevel - GameConfig.FORM_MIN_LEVEL));
    }

    /** 该形态对指定客群的匹配系数（未声明则取兜底值） */
    public double affinityOf(String segmentId) {
        if (segmentId == null) {
            return GameConfig.DEFAULT_SEGMENT_AFFINITY;
        }
        Double v = segmentAffinity.get(segmentId);
        return v == null ? GameConfig.DEFAULT_SEGMENT_AFFINITY : v;
    }

    /** 该形态对指定客群的匹配系数（枚举兼容） */
    public double affinityOf(CustomerSegment segment) {
        return segment == null ? GameConfig.DEFAULT_SEGMENT_AFFINITY : affinityOf(segment.name());
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

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName, BuildingCategory.ACCOMMODATION);
    }

    public static Builder builder(String id, String displayName, BuildingCategory category) {
        return new Builder(id, displayName, category);
    }

    /** 形态构建器（避免超长位置参数，集中配置数值） */
    public static class Builder {
        private final String id;
        private final String displayName;
        private BuildingCategory category;
        private int tier = 0;
        private double capacityPerRoom = 0;
        private double[] pricePerRoomByLevel = new double[]{0, 0, 0};
        private double serviceCapacity = 0;
        private double[] unitPriceByLevel = new double[]{0, 0, 0};
        private double evolveCost = 0;
        private double[] formLevelUpCost = new double[]{0, 0};
        private int[] formLevelUpDays = new int[]{1, 1};
        private int evolveDays = 0;
        private double baseComfort = 50;
        private Map<String, Double> segmentAffinity = new LinkedHashMap<>();
        private int[] maxInteriorLevel = new int[]{
                GameConfig.INTERIOR_MAX_LEVEL_FALLBACK,
                GameConfig.INTERIOR_MAX_LEVEL_FALLBACK,
                GameConfig.INTERIOR_MAX_LEVEL_FALLBACK};
        private Map<AbilityType, Double> abilityDemand = new EnumMap<>(AbilityType.class);
        private List<String> availableInteriors = new ArrayList<>();
        private String capacityInterior = null;

        private Builder(String id, String displayName, BuildingCategory category) {
            this.id = id;
            this.displayName = displayName;
            this.category = category != null ? category : BuildingCategory.ACCOMMODATION;
        }

        public Builder category(BuildingCategory category) {
            this.category = category;
            return this;
        }

        public Builder line(Object ignored) {
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

        public Builder pricePerRoom(double... prices) {
            this.pricePerRoomByLevel = prices;
            return this;
        }

        public Builder serviceCapacity(double v) {
            this.serviceCapacity = v;
            return this;
        }

        public Builder unitPrice(double... prices) {
            this.unitPriceByLevel = prices;
            return this;
        }

        public Builder buildCost(double ignored) {
            return this;
        }

        public Builder evolveCost(double v) {
            this.evolveCost = v;
            return this;
        }

        public Builder evolveFrom(BuildingForm ignored) {
            return this;
        }

        public Builder formLevelUpCost(double... costs) {
            this.formLevelUpCost = costs;
            return this;
        }

        /** 形态等级升级工期（游戏日）：各等级阶段 */
        public Builder formLevelUpDays(int... days) {
            this.formLevelUpDays = days;
            return this;
        }

        /** 分支进化到本形态的工期（游戏日） */
        public Builder evolveDays(int v) {
            this.evolveDays = v;
            return this;
        }

        public Builder baseComfort(double v) {
            this.baseComfort = v;
            return this;
        }

        public Builder affinity(CustomerSegment segment, double v) {
            if (segment != null) {
                this.segmentAffinity.put(segment.name(), v);
            }
            return this;
        }

        public Builder affinity(String segmentId, double v) {
            if (segmentId != null) {
                this.segmentAffinity.put(segmentId, v);
            }
            return this;
        }

        public Builder maxInteriorLevel(int... levels) {
            this.maxInteriorLevel = levels;
            return this;
        }


        public Builder abilityDemand(AbilityType ability, double v) {
            this.abilityDemand.put(ability, v);
            return this;
        }

        public Builder availableInteriors(List<String> list) {
            if (list != null) {
                this.availableInteriors = new ArrayList<>(list);
            }
            return this;
        }

        public Builder capacityInterior(String id) {
            this.capacityInterior = id;
            return this;
        }

        public BuildingForm build() {
            Map<AbilityType, Double> dem = new EnumMap<>(AbilityType.class);
            dem.putAll(this.abilityDemand);
            this.segmentAffinity = Collections.unmodifiableMap(new LinkedHashMap<>(this.segmentAffinity));
            this.abilityDemand = Collections.unmodifiableMap(new LinkedHashMap<>(dem));
            return new BuildingForm(this);
        }
    }
}
