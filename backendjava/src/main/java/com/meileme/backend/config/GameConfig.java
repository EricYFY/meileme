package com.meileme.backend.config;

/**
 * 全局游戏数值配置：所有经营数值的唯一来源，业务代码禁止硬编码。
 */
public final class GameConfig {
    private GameConfig() {}

    // ===== 局基础 =====
    public static final double INITIAL_CASH       = 20000.0;
    public static final double INITIAL_REPUTATION = 70.0;
    public static final double REPUTATION_MIN     = 0.0;
    public static final double REPUTATION_MAX     = 100.0;
    public static final double BANKRUPTCY_CASH    = 0.0; // 资金 <= 0 破产

    // ===== 每局日志落盘 =====
    public static final String LOG_DIR = "logs"; // 每局一份 game-<时间戳>.log（相对后端工作目录，控制台会打印绝对路径）

    // ===== 时间 =====
    public static final long BASE_VIRTUAL_TIME_MS       = 1782835200000L; // 2026-07-01 00:00:00
    public static final long GAME_DAY_VIRTUAL_MS        = 24L * 3600L * 1000L;
    public static final long VIRTUAL_MS_PER_REAL_SECOND = 1440000L;       // 现实1秒=游戏24分钟 ⇒ 现实1分钟=1游戏日
    public static final int  SEASON_LENGTH_DAYS         = 30;             // 每 30 游戏天切换季节

    // ===== 实时经营：指令延迟生效与报告 =====
    public static final double[] SPEED_OPTIONS      = {0.5, 1.0, 2.0, 4.0, 8.0, 16.0}; // 可选全局倍速
    public static final int[]    REPORT_RANGES      = {1, 7, 30, 365};    // 报告可选区间(游戏天)
    public static final int      REPORT_HISTORY_LIMIT = 365;              // 每日汇总历史保留上限
    public static final int      REPORT_DEFAULT_RANGE = 7;                // 非法区间回落值

    /** 倍速是否在白名单内 */
    public static boolean isValidSpeed(double speed) {
        for (double s : SPEED_OPTIONS) {
            if (Math.abs(s - speed) < 1e-6) return true;
        }
        return false;
    }

    /** 报告区间是否在白名单内 */
    public static boolean isValidReportRange(int days) {
        for (int d : REPORT_RANGES) {
            if (d == days) return true;
        }
        return false;
    }

    // ===== 散客客流 =====
    public static final double BASE_GUESTS_PER_DAY   = 60.0;
    public static final double REP_BASELINE          = 70.0; // 口碑基准(该点客流系数=1.0)
    public static final double REP_TRAFFIC_PER_POINT = 0.01; // 口碑每 ±1 点 → 客流 ±1%
    public static final double REP_FACTOR_MIN        = 0.30; // 口碑客流系数保底（平滑衰减下限，避免口碑死亡螺旋）
    public static final double AD_TRAFFIC_PER_1000   = 0.15; // 每 1000 元广告 +15%
    public static final double AD_TRAFFIC_CAP        = 0.80; // 广告加成上限 +80%
    public static final double LUX_GUEST_PREFERENCE  = 0.30; // 过夜客中 30% 偏好豪华套房
    public static final double NO_FRONT_DESK_FACTOR  = 0.75; // 缺前台 → 入住率 -25%

    // ===== 房价需求弹性（定价影响客流：提价客流降、降价客流升）=====
    public static final double BASE_STANDARD_ROOM_PRICE = 280.0; // 普通房基准价（价格系数的中性点）
    public static final double BASE_LUXURY_ROOM_PRICE   = 680.0; // 豪华房基准价
    public static final double PRICE_ELASTICITY         = 1.5;   // 价格弹性 E：越大，价格偏离对客流影响越剧烈
    public static final double PRICE_FACTOR_MIN         = 0.2;   // 价格系数下限（定价过高时客流的最低倍数）
    public static final double PRICE_FACTOR_MAX         = 1.8;   // 价格系数上限（定价过低时客流的最高倍数）

    // ===== 单客消费 =====
    public static final double DINING_SPEND_PER_GUEST     = 80.0;
    public static final double RECREATION_SPEND_PER_GUEST = 60.0;

    // ===== 团建大单每日数量 =====
    public static final int TEAM_ORDER_MIN_PER_DAY = 0; // 每日最少团建单数
    public static final int TEAM_ORDER_MAX_PER_DAY = 2; // 每日最多团建单数
    public static final double TEAM_SUCCESS_REPUTATION_BONUS = 3.0; // 当日已生效团建单全部履约成功 → 口碑 +3

    // ===== 员工负面事件效果 =====
    public static final double STAFF_NEG_FRONT_OCCUPANCY_PENALTY = 0.10; // 前台失误：当日入住率 -10%
    public static final double STAFF_NEG_CLEANING_REP_PENALTY    = 2.0;  // 保洁失误：口碑 -2（下调 -3 → -2）
    public static final double STAFF_NEG_CHEF_DINING_MULT        = 0.70; // 厨师失误：当日餐饮 -30%
    public static final double STAFF_NEG_MAINT_FAULT_COST        = 500.0;// 维修失误：设施故障维修 ¥500

    // ===== 保洁配置 =====
    public static final double CLEANING_ROOMS_PER_STAFF          = 10.0; // 每 10 间客房需 1 名保洁
    public static final double CLEANING_SHORTAGE_REP_PENALTY     = 2.0;  // 保洁不足每日口碑惩罚

    // ===== 被动口碑（每日自然涨口碑，提供稳定的正向通道） =====
    public static final double REP_PASSIVE_OCCUPANCY_THRESHOLD = 0.80; // 入住率达该比例即算「高入住率」
    public static final double REP_PASSIVE_OCCUPANCY_BONUS     = 1.0;  // 高入住率：口碑 +1
    public static final double REP_PASSIVE_STAFF_BONUS         = 1.0;  // 前台/保洁/厨师/维修 四类岗位齐备：口碑 +1
    public static final double REP_PASSIVE_QUIET_BONUS         = 1.0;  // 当日无任何负向事件：口碑 +1
    public static final double REP_PASSIVE_MAX                 = 3.0;  // 单日被动口碑加成上限

    // ===== 随机事件触发概率 =====
    public static final double EVENT_RAINSTORM_PROB       = 0.10; // 暴雨
    public static final double EVENT_PIPE_BURST_PROB      = 0.02; // 水管爆裂
    public static final double EVENT_INFLUENCER_PROB      = 0.10; // 网红打卡（正向，上调 0.07 → 0.10）
    public static final double EVENT_COMPLAINT_PROB       = 0.03; // 游客集体投诉
    public static final double EVENT_LOCAL_TOUR_PROB      = 0.05; // 本地旅游团到访
    public static final double EVENT_MINOR_FAULT_PROB     = 0.05; // 设施小故障
    public static final double EVENT_POSITIVE_REVIEW_PROB = 0.06; // 好评如潮（正向，新增）
    public static final double EVENT_RETURNING_GUEST_PROB = 0.05; // 回头客推荐（正向，新增）

    // ===== 事件数值 =====
    public static final double RAINSTORM_RECREATION_MULT = 0.40;  // 暴雨: 游乐收益 -60% ⇒ ×0.40
    public static final double RAINSTORM_REP_DELTA       = -3.0;  // 口碑 -3（下调 -5 → -3）
    public static final double PIPE_BURST_COST           = 1200.0;
    public static final int    PIPE_BURST_DISABLED_ROOMS = 3;     // 3 间客房不可用(当日)
    public static final double PIPE_BURST_REP_DELTA      = -4.0;  // 口碑 -4（下调 -8 → -4）
    public static final double INFLUENCER_DEMAND_MULT    = 1.60;
    public static final double INFLUENCER_REP_DELTA      = 8.0;   // 口碑 +8（上调 +6 → +8）
    public static final double COMPLAINT_REP_DELTA       = -6.0;  // 口碑 -6（下调 -12 → -6）
    public static final double COMPLAINT_PROFIT_MULT     = 0.85;  // 当日净利润 -15%
    public static final double MINOR_FAULT_COST          = 500.0;
    public static final double POSITIVE_REVIEW_REP_DELTA   = 8.0;  // 好评如潮：口碑 +8
    public static final double RETURNING_GUEST_REP_DELTA   = 5.0;  // 回头客推荐：口碑 +5
    public static final double RETURNING_GUEST_DEMAND_MULT = 1.20; // 回头客推荐：散客客流 ×1.2

    // ===== 地块解锁 =====
    public static final String PLOT_INITIAL   = "INITIAL";
    public static final String PLOT_MOUNTAIN  = "MOUNTAIN";
    public static final String PLOT_LAKESIDE  = "LAKESIDE";
    public static final double LAND_MOUNTAIN_COST = 35000.0;
    public static final int    LAND_MOUNTAIN_LIMIT = 12;
    public static final double LAND_LAKESIDE_COST = 60000.0;
    public static final int    LAND_LAKESIDE_LIMIT = 16;
    public static final int    LAND_INITIAL_LIMIT  = 8;

    /** 地块的建筑数量上限 */
    public static int buildingLimitOf(String plotId) {
        if (PLOT_MOUNTAIN.equals(plotId)) return LAND_MOUNTAIN_LIMIT;
        if (PLOT_LAKESIDE.equals(plotId)) return LAND_LAKESIDE_LIMIT;
        return LAND_INITIAL_LIMIT;
    }

    // ===== 建筑停用 / 启用 / 拆除 =====
    public static final double REBUILD_RATIO           = 0.3; // 手动启用经费 = 建造成本 × 该比例（次日生效时才扣）
    public static final double DEMOLISH_RESIDUAL_RATIO = 0.3; // 拆除返还残值 = 建造成本 × 该比例

    /** 第 day 天的季节：1~30 春 → 31~60 夏 → 61~90 秋 → 91~120 冬 → 循环 */
    public static Season seasonOfDay(int day) {
        int idx = ((day - 1) / SEASON_LENGTH_DAYS) % 4;
        return Season.values()[idx];
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
