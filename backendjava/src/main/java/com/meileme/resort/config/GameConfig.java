package com.meileme.resort.config;

/**
 * 全局数值配置。
 *
 * <p>项目长期约定：<b>所有游戏数值集中于此（或各配置类 / 枚举），禁止在业务代码中散落硬编码。</b>
 * 本期为「数值后期配置」阶段，下列数值为合理占位默认值，后续调参只改本类。</p>
 */
public final class GameConfig {

    private GameConfig() {
    }

    // ===================== 时间与流速 =====================

    /** 一个游戏日的虚拟毫秒数（24 游戏小时） */
    public static final long GAME_DAY_VIRTUAL_MS = 24L * 60 * 60 * 1000;
    /** 现实多少秒 = 游戏 1 天 */
    public static double REAL_SECONDS_PER_GAME_DAY = 60d;
    /** 现实每秒推进的虚拟毫秒数 */
    public static double VIRTUAL_MS_PER_REAL_SECOND = GAME_DAY_VIRTUAL_MS / REAL_SECONDS_PER_GAME_DAY;
    /** 引擎时钟 Tick 间隔（毫秒） */
    public static long TICK_INTERVAL_MS = 1000L;
    /** 默认倍速 */
    public static double DEFAULT_SPEED = 1.0;
    /** 可选倍速档位 */
    public static double[] SPEED_OPTIONS = {0.5, 1, 2, 4, 8, 16};

    // ===================== 开局 =====================

    /** 初始资金池 */
    public static double STARTING_FUNDS = 50000;
    /** 每局起始日期的默认展示值（纯展示字段，不挂业务语义） */
    public static String DEFAULT_START_DATE = "2026-07-01";
    /** 每日结算日志最多保留天数（环形上限） */
    public static int REPORT_HISTORY_LIMIT = 365;

    // ===================== 建筑形态与等级 =====================

    /** 形态等级上限 */
    public static int FORM_MAX_LEVEL = 3;
    /** 形态等级下限 */
    public static int FORM_MIN_LEVEL = 1;
    /** 形态每提升 1 级，对「综合舒适度」的加成步长 */
    public static double FORM_LEVEL_COMFORT_STEP = 0.15;

    // ===================== 客流与转化 =====================

    /** 分客群「吸引力分值 → 转化率」的除数：转化率 = 吸引力 / 该值，并钳制上下限 */
    public static double CONVERSION_DENOMINATOR = 400d;
    /** 转化率上限 */
    public static double MAX_CONVERSION_RATE = 0.90;
    /** 转化率下限 */
    public static double MIN_CONVERSION_RATE = 0.0;
    /** 建筑形态未声明某客群匹配系数时的兜底系数 */
    public static double DEFAULT_SEGMENT_AFFINITY = 0.5;

    // ===================== 内装 =====================

    /** 内装等级上限的兜底值（当形态未配置时使用） */
    public static int INTERIOR_MAX_LEVEL_FALLBACK = 3;

    // ===================== 人力 =====================

    /** 能力供给不足时，相关建筑舒适度的最低保留比例 */
    public static double STAFF_SHORTAGE_MIN_COMFORT_RATIO = 0.30;
    /** 员工等级上限 */
    public static int STAFF_MAX_LEVEL = 3;
    /** 员工等级下限 */
    public static int STAFF_MIN_LEVEL = 1;
    /** 解雇补偿：在岗累计天数每 1 天带来的补偿增量比例 */
    public static double FIRE_COMPENSATION_PER_DAY_RATIO = 0.001;

    // ===================== 技能树 / 区块 =====================

    /** 每解锁一个地块获得的技能点 */
    public static int SKILL_POINT_PER_PLOT = 1;

    // ===================== 地块地图 =====================

    /** 地块地图随机生成的基础种子（配合地块序号派生，保证六张地图固定可复现） */
    public static long MAP_SEED = 20261022L;

    /** 动态应用全局配置参数 */
    public static void applyGlobalConfig(java.util.Map<String, Object> map) {
        if (map == null) return;
        if (map.containsKey("startingFunds")) STARTING_FUNDS = ((Number) map.get("startingFunds")).doubleValue();
        if (map.containsKey("defaultStartDate")) DEFAULT_START_DATE = String.valueOf(map.get("defaultStartDate"));
        if (map.containsKey("reportHistoryLimit")) REPORT_HISTORY_LIMIT = ((Number) map.get("reportHistoryLimit")).intValue();
        if (map.containsKey("realSecondsPerGameDay")) {
            REAL_SECONDS_PER_GAME_DAY = ((Number) map.get("realSecondsPerGameDay")).doubleValue();
            VIRTUAL_MS_PER_REAL_SECOND = GAME_DAY_VIRTUAL_MS / REAL_SECONDS_PER_GAME_DAY;
        }
        if (map.containsKey("tickIntervalMs")) TICK_INTERVAL_MS = ((Number) map.get("tickIntervalMs")).longValue();
        if (map.containsKey("defaultSpeed")) DEFAULT_SPEED = ((Number) map.get("defaultSpeed")).doubleValue();
        if (map.containsKey("conversionDenominator")) CONVERSION_DENOMINATOR = ((Number) map.get("conversionDenominator")).doubleValue();
        if (map.containsKey("maxConversionRate")) MAX_CONVERSION_RATE = ((Number) map.get("maxConversionRate")).doubleValue();
        if (map.containsKey("minConversionRate")) MIN_CONVERSION_RATE = ((Number) map.get("minConversionRate")).doubleValue();
        if (map.containsKey("defaultSegmentAffinity")) DEFAULT_SEGMENT_AFFINITY = ((Number) map.get("defaultSegmentAffinity")).doubleValue();
        if (map.containsKey("formMaxLevel")) FORM_MAX_LEVEL = ((Number) map.get("formMaxLevel")).intValue();
        if (map.containsKey("formMinLevel")) FORM_MIN_LEVEL = ((Number) map.get("formMinLevel")).intValue();
        if (map.containsKey("formLevelComfortStep")) FORM_LEVEL_COMFORT_STEP = ((Number) map.get("formLevelComfortStep")).doubleValue();
        if (map.containsKey("staffShortageMinComfortRatio")) STAFF_SHORTAGE_MIN_COMFORT_RATIO = ((Number) map.get("staffShortageMinComfortRatio")).doubleValue();
        if (map.containsKey("staffMaxLevel")) STAFF_MAX_LEVEL = ((Number) map.get("staffMaxLevel")).intValue();
        if (map.containsKey("staffMinLevel")) STAFF_MIN_LEVEL = ((Number) map.get("staffMinLevel")).intValue();
        if (map.containsKey("fireCompensationPerDayRatio")) FIRE_COMPENSATION_PER_DAY_RATIO = ((Number) map.get("fireCompensationPerDayRatio")).doubleValue();
        if (map.containsKey("mapSeed")) MAP_SEED = ((Number) map.get("mapSeed")).longValue();
        if (map.containsKey("skillPointPerPlot")) SKILL_POINT_PER_PLOT = ((Number) map.get("skillPointPerPlot")).intValue();
    }
}
