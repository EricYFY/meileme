package com.meileme.resort.model;

import com.meileme.resort.config.BuildingCategory;
import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.Region;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单局游戏运行时状态（内存态，可由仓储整体保存 / 清空）。
 *
 * <p>纯挂机经营，局内进度重开全部清空。</p>
 */
@Data
public class GameState {

    // ===================== 基础 =====================

    /** 资金池 */
    private double funds = GameConfig.STARTING_FUNDS;

    /** 当前游戏日（从 1 开始） */
    private int day = 1;

    /** 起始日期展示值（纯展示字段，不挂业务语义） */
    private String startDateDisplay = GameConfig.DEFAULT_START_DATE;

    /** 当前日期（由起始日期 + 日期偏移推导，仅用于展示） */
    private LocalDate startDate = LocalDate.parse(GameConfig.DEFAULT_START_DATE);

    /** 对局是否运行中 */
    private boolean running = false;

    // ===================== 时间推进 =====================

    /** 当前当日内进度（0 ~ 1） */
    private double dayProgress = 0;

    /** 当前倍速 */
    private double speed = GameConfig.DEFAULT_SPEED;

    /** 是否暂停 */
    private boolean paused = false;

    // ===================== 实体 =====================

    /** 建筑实例列表（含逻辑删除的墓碑记录） */
    private List<BuildingInstance> buildings = new ArrayList<>();

    /** 员工实例列表（含已解雇的逻辑删除记录） */
    private List<StaffInstance> staffs = new ArrayList<>();

    /** 进行中的工程（建造 / 升级 / 进化 / 合并 / 内装） */
    private List<ConstructionProject> projects = new ArrayList<>();

    // ===================== 成长与解锁 =====================

    /** 可用技能点 */
    private int skillPoints = 0;

    /** 已解锁技能 ID */
    private Set<String> unlockedSkills = new LinkedHashSet<>();

    /** 已解锁地块 ID */
    private Set<String> unlockedPlots = new LinkedHashSet<>();

    /** 已解锁大区域 */
    private Set<Region> unlockedRegions = new LinkedHashSet<>();

    /** 已达成成就 ID */
    private Set<String> unlockedAchievements = new LinkedHashSet<>();

    // ===================== 统计与日志 =====================

    /** 全局累计统计（成就判定） */
    private GameStats stats = new GameStats();

    /** 待生效指令队列（次日 0 点统一生效） */
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** 逐日结算历史（最多保留 365 天） */
    private List<DailySettle> dailyHistory = new ArrayList<>();

    /** 最近一次日结快照 */
    private DailySettle lastSettle;

    /** 今日预计结算（投影，不扣资金；开新局 / 次日 0 点刷新） */
    private DailyPreview todayPreview;

    /** 里程碑列表（地块解锁等成长节点） */
    private List<Milestone> milestones = new ArrayList<>();

    // ===================== 便捷方法 =====================

    /** 当前日期展示值（起始日期 + (day − 1) 天） */
    public String currentDateDisplay() {
        LocalDate d = startDate == null ? LocalDate.parse(GameConfig.DEFAULT_START_DATE) : startDate;
        return d.plusDays(day - 1L).toString();
    }

    /** 存活建筑列表 */
    public List<BuildingInstance> aliveBuildings() {
        List<BuildingInstance> list = new ArrayList<>();
        for (BuildingInstance b : buildings) {
            if (b.isAlive()) {
                list.add(b);
            }
        }
        return list;
    }

    /** 指定地块内存活建筑数 */
    public int aliveBuildingCountInPlot(String plotId) {
        int n = 0;
        for (BuildingInstance b : buildings) {
            if (b.isAlive() && plotId.equals(b.getPlotId())) {
                n++;
            }
        }
        return n;
    }

    /** 存活住宿建筑列表 */
    public List<BuildingInstance> aliveAccommodationBuildings() {
        List<BuildingInstance> list = new ArrayList<>();
        for (BuildingInstance b : buildings) {
            if (b.isAlive() && b.getMother().getCategory() == BuildingCategory.ACCOMMODATION) {
                list.add(b);
            }
        }
        return list;
    }

    /** 存活配套建筑列表 */
    public List<BuildingInstance> aliveSupportBuildings() {
        List<BuildingInstance> list = new ArrayList<>();
        for (BuildingInstance b : buildings) {
            if (b.isAlive() && b.getMother().getCategory() == BuildingCategory.SUPPORT) {
                list.add(b);
            }
        }
        return list;
    }

    /** 存活员工列表 */
    public List<StaffInstance> aliveStaffs() {
        List<StaffInstance> list = new ArrayList<>();
        for (StaffInstance s : staffs) {
            if (s.isAlive()) {
                list.add(s);
            }
        }
        return list;
    }

    /** 按 ID 查找存活建筑（未找到返回 null） */
    public BuildingInstance findAliveBuilding(String buildingId) {
        if (buildingId == null) {
            return null;
        }
        for (BuildingInstance b : buildings) {
            if (b.isAlive() && buildingId.equals(b.getId())) {
                return b;
            }
        }
        return null;
    }

    /** 按 ID 查找存活员工（未找到返回 null） */
    public StaffInstance findAliveStaff(String staffId) {
        if (staffId == null) {
            return null;
        }
        for (StaffInstance s : staffs) {
            if (s.isAlive() && staffId.equals(s.getId())) {
                return s;
            }
        }
        return null;
    }

    /** 该建筑是否被进行中的工程占用（作为主目标或合并源） */
    public ConstructionProject projectOfBuilding(String buildingId) {
        if (buildingId == null) {
            return null;
        }
        for (ConstructionProject p : projects) {
            if (buildingId.equals(p.getBuildingId()) || buildingId.equals(p.getSecondBuildingId())) {
                return p;
            }
        }
        return null;
    }

    /** 指定地块内进行中的「新建工程」数量（占用建造名额） */
    public int buildProjectCountInPlot(String plotId) {
        int n = 0;
        for (ConstructionProject p : projects) {
            if (p.getType() == com.meileme.resort.config.ProjectType.BUILD
                    && plotId != null && plotId.equals(p.getPlotId())) {
                n++;
            }
        }
        return n;
    }

    /** 追加每日结算日志，超出上限裁剪最旧一条 */
    public void appendHistory(DailySettle settle) {
        dailyHistory.add(settle);
        while (dailyHistory.size() > GameConfig.REPORT_HISTORY_LIMIT) {
            dailyHistory.remove(0);
        }
    }
}
