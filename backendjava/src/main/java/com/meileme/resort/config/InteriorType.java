package com.meileme.resort.config;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 内装类型。
 *
 * <p>内装提供四类收益：<b>舒适度、承载容量、指定客群吸引力加成、所在建筑收益加成</b>；
 * 全部按内装等级线性叠加，数值集中在本枚举配置。</p>
 *
 * <p>内装<strong>按母本适配</strong>：住宿客房使用床位 / 餐桌 / 泡池 / 沙发 / 灯具 / 软装；
 * 配套建筑（餐厅 / 温泉 / 观景台）各自拥有专属内装，互不通用。可使用
 * {@link #isApplicableTo(MotherBuildingType)} 判断某内装是否能安装到指定母本。</p>
 */
@Getter
public enum InteriorType {

    // ===================== 住宿客房内装（仅住宿母本可用） =====================
    /** 床位（承载型） */
    BED("床位", InteriorCategory.CARRY, 3.0, 1.0,
            affinity(CustomerSegment.FAMILY, 0.10, CustomerSegment.BUSINESS, 0.05), 0.020,
            MotherBuildingType.ACCOMMODATION_MOTHERS),
    /** 餐桌（承载型） */
    DINING_TABLE("餐桌", InteriorCategory.CARRY, 2.0, 2.0,
            affinity(CustomerSegment.FAMILY, 0.06, CustomerSegment.BUSINESS, 0.04,
                    CustomerSegment.ROMANTIC, 0.04, CustomerSegment.WELLNESS, 0.03), 0.015,
            MotherBuildingType.ACCOMMODATION_MOTHERS),
    /** 泡池（承载型） */
    HOT_SPRING_TUB("泡池", InteriorCategory.CARRY, 3.0, 1.5,
            affinity(CustomerSegment.ROMANTIC, 0.12, CustomerSegment.WELLNESS, 0.10,
                    CustomerSegment.FAMILY, 0.04), 0.030,
            MotherBuildingType.ACCOMMODATION_MOTHERS),
    /** 沙发（普通型） */
    SOFA("沙发", InteriorCategory.NORMAL, 2.0, 0.0,
            affinity(CustomerSegment.BUSINESS, 0.08, CustomerSegment.FAMILY, 0.04), 0.010,
            MotherBuildingType.ACCOMMODATION_MOTHERS),
    /** 灯具（普通型） */
    LIGHTING("灯具", InteriorCategory.NORMAL, 1.5, 0.0,
            affinity(CustomerSegment.ROMANTIC, 0.10, CustomerSegment.WELLNESS, 0.05), 0.010,
            MotherBuildingType.ACCOMMODATION_MOTHERS),
    /** 软装（普通型） */
    DECOR("软装", InteriorCategory.NORMAL, 2.5, 0.0,
            affinity(CustomerSegment.ROMANTIC, 0.08, CustomerSegment.WELLNESS, 0.06,
                    CustomerSegment.FAMILY, 0.03), 0.025,
            MotherBuildingType.ACCOMMODATION_MOTHERS),

    // ===================== 餐厅内装 =====================
    /** 厨房设备（承载型：不扩餐位，提升舒适度与收益） */
    KITCHEN("厨房设备", InteriorCategory.CARRY, 2.5, 0.0,
            affinity(CustomerSegment.FAMILY, 0.08, CustomerSegment.BUSINESS, 0.06), 0.025,
            EnumSet.of(MotherBuildingType.RESTAURANT)),
    /** 餐位桌椅（承载型：扩充服务容量） */
    DINING_SEAT("餐位桌椅", InteriorCategory.CARRY, 2.0, 8.0,
            affinity(CustomerSegment.FAMILY, 0.06, CustomerSegment.BUSINESS, 0.04), 0.020,
            EnumSet.of(MotherBuildingType.RESTAURANT)),

    // ===================== 温泉泡池内装 =====================
    /** 汤池（承载型：提升沉浸舒适度与收益） */
    BATH_POOL("汤池", InteriorCategory.CARRY, 3.0, 0.0,
            affinity(CustomerSegment.WELLNESS, 0.14, CustomerSegment.ROMANTIC, 0.08), 0.030,
            EnumSet.of(MotherBuildingType.HOT_SPRING_POOL)),
    /** 汗蒸房（普通型） */
    SAUNA_ROOM("汗蒸房", InteriorCategory.NORMAL, 2.0, 0.0,
            affinity(CustomerSegment.WELLNESS, 0.10), 0.020,
            EnumSet.of(MotherBuildingType.HOT_SPRING_POOL)),
    /** 更衣休憩区（承载型：扩充服务容量） */
    SPA_LOUNGE("更衣休憩区", InteriorCategory.CARRY, 1.5, 6.0,
            affinity(CustomerSegment.WELLNESS, 0.06, CustomerSegment.ROMANTIC, 0.05,
                    CustomerSegment.FAMILY, 0.03), 0.015,
            EnumSet.of(MotherBuildingType.HOT_SPRING_POOL)),

    // ===================== 观景台内装 =====================
    /** 观景望远镜（普通型） */
    TELESCOPE("观景望远镜", InteriorCategory.NORMAL, 1.5, 0.0,
            affinity(CustomerSegment.ROMANTIC, 0.12, CustomerSegment.WELLNESS, 0.06), 0.020,
            EnumSet.of(MotherBuildingType.VIEWING_PLATFORM)),
    /** 观景座椅（承载型：扩充服务容量） */
    VIEW_SEAT("观景座椅", InteriorCategory.CARRY, 1.5, 10.0,
            affinity(CustomerSegment.FAMILY, 0.05, CustomerSegment.WELLNESS, 0.04), 0.015,
            EnumSet.of(MotherBuildingType.VIEWING_PLATFORM)),
    /** 遮阳茶座（普通型） */
    SUNSHADE("遮阳茶座", InteriorCategory.NORMAL, 1.0, 0.0,
            affinity(CustomerSegment.ROMANTIC, 0.05, CustomerSegment.FAMILY, 0.04), 0.010,
            EnumSet.of(MotherBuildingType.VIEWING_PLATFORM));

    /** 展示名 */
    private final String displayName;
    /** 内装分类 */
    private final InteriorCategory category;
    /** 每级提供的舒适度加成 */
    private final double comfortPerLevel;
    /** 每级提供的承载容量加成（配套内装作用于服务容量，普通型为 0） */
    private final double capacityPerLevel;
    /** 每级提供的指定客群吸引力加成（未列出的客群为 0） */
    private final Map<CustomerSegment, Double> segmentAffinityPerLevel;
    /** 每级提供的所在建筑收益加成比例 */
    private final double revenueBonusPerLevel;
    /** 可安装本内装的母本集合 */
    private final Set<MotherBuildingType> applicableMothers;
    /** 各等级升级花费（下标 0 = 升到 Lv1，即首次安装） */
    private final double[] upgradeCostByLevel;
    /** 各等级升级工期（游戏日，下标 0 = 升到 Lv1） */
    private final int[] upgradeDaysByLevel;

    InteriorType(String displayName, InteriorCategory category, double comfortPerLevel, double capacityPerLevel,
                 Map<CustomerSegment, Double> segmentAffinityPerLevel, double revenueBonusPerLevel,
                 Set<MotherBuildingType> applicableMothers) {
        this.displayName = displayName;
        this.category = category;
        this.comfortPerLevel = comfortPerLevel;
        this.capacityPerLevel = capacityPerLevel;
        this.segmentAffinityPerLevel = segmentAffinityPerLevel;
        this.revenueBonusPerLevel = revenueBonusPerLevel;
        this.applicableMothers = Collections.unmodifiableSet(EnumSet.copyOf(applicableMothers));
        this.upgradeCostByLevel = defaultCosts(name());
        this.upgradeDaysByLevel = defaultDays(name());
    }

    /** 指定客群每级吸引力加成 */
    public double segmentAffinityOf(CustomerSegment segment) {
        return segment == null ? 0d : segmentAffinityOf(segment.name());
    }

    /** 指定客群每级吸引力加成（字符串 ID） */
    public double segmentAffinityOf(String segmentId) {
        if (segmentId == null) return 0d;
        try {
            CustomerSegment seg = CustomerSegment.valueOf(segmentId);
            return segmentAffinityPerLevel.getOrDefault(seg, 0d);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    /** 该内装是否可安装到指定母本 */
    public boolean isApplicableTo(MotherBuildingType mother) {
        return mother != null && applicableMothers.contains(mother);
    }

    /** 该内装是否可安装到指定建筑形态与母本（形态声明了可用内装列表时以形态为准；否则按母本或大类匹配） */
    public boolean isApplicableTo(BuildingForm form, MotherBuildingType mother) {
        if (form != null && form.getAvailableInteriors() != null && !form.getAvailableInteriors().isEmpty()) {
            return form.isInteriorAllowed(name());
        }
        if (mother != null) {
            return applicableMothers.contains(mother);
        }
        if (form != null && form.isAccommodation()) {
            return applicableMothers.contains(MotherBuildingType.STANDARD_ROOM);
        }
        return true;
    }

    /** 构造客群吸引力加成表：参数为「客群, 数值」交替 */
    private static Map<CustomerSegment, Double> affinity(Object... pairs) {
        Map<CustomerSegment, Double> map = new EnumMap<>(CustomerSegment.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((CustomerSegment) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    /** 指定等级的内装升级花费（下标 = level − 1） */
    public double upgradeCostOf(int level) {
        int idx = level - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx > upgradeCostByLevel.length - 1) {
            idx = upgradeCostByLevel.length - 1;
        }
        return upgradeCostByLevel[idx];
    }

    /** 指定等级的内装升级工期（游戏日，下标 = level − 1） */
    public int upgradeDaysOf(int level) {
        int idx = level - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx > upgradeDaysByLevel.length - 1) {
            idx = upgradeDaysByLevel.length - 1;
        }
        return upgradeDaysByLevel[idx];
    }

    /** 各内装类型的等级工期表（集中配置，禁止散落硬编码） */
    private static int[] defaultDays(String typeName) {
        switch (typeName) {
            case "BED":
                return new int[]{2, 3, 4};
            case "DINING_TABLE":
                return new int[]{2, 3, 3};
            case "HOT_SPRING_TUB":
            case "BATH_POOL":
                return new int[]{3, 4, 5};
            case "SOFA":
                return new int[]{1, 2, 3};
            case "LIGHTING":
                return new int[]{1, 2, 2};
            case "KITCHEN":
                return new int[]{2, 3, 4};
            case "DINING_SEAT":
                return new int[]{2, 3, 3};
            case "SAUNA_ROOM":
                return new int[]{2, 3, 4};
            case "SPA_LOUNGE":
                return new int[]{2, 2, 3};
            case "TELESCOPE":
            case "SUNSHADE":
                return new int[]{1, 2, 2};
            case "VIEW_SEAT":
            case "DECOR":
            default:
                return new int[]{2, 2, 3};
        }
    }

    /** 各内装类型的等级花费表（集中配置，禁止散落硬编码） */
    private static double[] defaultCosts(String typeName) {
        switch (typeName) {
            case "BED":
                return new double[]{800, 1500, 2500};
            case "DINING_TABLE":
                return new double[]{600, 1200, 2000};
            case "HOT_SPRING_TUB":
            case "BATH_POOL":
                return new double[]{1200, 2200, 3600};
            case "SOFA":
                return new double[]{400, 800, 1400};
            case "LIGHTING":
                return new double[]{300, 600, 1000};
            case "KITCHEN":
                return new double[]{900, 1600, 2600};
            case "DINING_SEAT":
                return new double[]{700, 1300, 2200};
            case "SAUNA_ROOM":
                return new double[]{800, 1500, 2500};
            case "SPA_LOUNGE":
                return new double[]{600, 1200, 2000};
            case "TELESCOPE":
                return new double[]{800, 1500, 2400};
            case "VIEW_SEAT":
                return new double[]{500, 1000, 1600};
            case "SUNSHADE":
            case "DECOR":
            default:
                return new double[]{500, 1000, 1800};
        }
    }
}