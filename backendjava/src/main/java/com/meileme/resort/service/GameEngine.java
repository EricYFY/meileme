package com.meileme.resort.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.resort.config.AbilityType;
import com.meileme.resort.config.Achievement;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.CustomerSegment;
import com.meileme.resort.config.EvolutionLine;
import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.InteriorType;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.PendingCommandType;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.PositionType;
import com.meileme.resort.config.Region;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.DailySettle;
import com.meileme.resort.model.DemandResult;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.PendingCommand;
import com.meileme.resort.model.StaffInstance;
import com.meileme.resort.repository.GameStateRepository;
import com.meileme.resort.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏引擎（挂机经营主循环编排）。
 *
 * <p>每日循环 6 步：①按区域生成各客群潜在客流 ②算吸引力并生成各客群意向客流 ③权重分配客流至各房型
 * ④床位容量截断得最终入住人数 ⑤算配套当日服务人次并结算房屋收益 + 配套收益
 * ⑥更新资金池 / 员工在岗时长 / 建筑状态，完成当日挂机结算。</p>
 * <p>日常运营<b>无固定支出</b>，当日净利润 = 当日总收入，因此不存在破产。</p>
 *
 * <p>所有玩家操作统一进入「待生效指令队列」，于<b>次日 0 点</b>统一生效并二次校验。</p>
 */
@Service
public class GameEngine {

    private final GameStateRepository repository;
    private final ConfigRegistry registry;
    private final DemandService demandService;
    private final FinanceService financeService;
    private final StaffService staffService;
    private final ComfortService comfortService;
    private final ConstructionService constructionService;
    private final EvolutionService evolutionService;
    private final InteriorService interiorService;
    private final SkillTreeService skillTree;
    private final AchievementService achievementService;
    private final RegionService regionService;
    private final StatsService statsService;
    private final IdGenerator idGenerator;

    private final ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private GameWebSocketHandler wsHandler;

    public GameEngine(GameStateRepository repository, ConfigRegistry registry,
                      DemandService demandService, FinanceService financeService,
                      StaffService staffService, ComfortService comfortService,
                      ConstructionService constructionService,
                      EvolutionService evolutionService, InteriorService interiorService,
                      SkillTreeService skillTree, AchievementService achievementService,
                      RegionService regionService, StatsService statsService, IdGenerator idGenerator,
                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.registry = registry;
        this.demandService = demandService;
        this.financeService = financeService;
        this.staffService = staffService;
        this.comfortService = comfortService;
        this.constructionService = constructionService;
        this.evolutionService = evolutionService;
        this.interiorService = interiorService;
        this.skillTree = skillTree;
        this.achievementService = achievementService;
        this.regionService = regionService;
        this.statsService = statsService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    // ===================== 生命周期 =====================

    public boolean isRunning() {
        GameState s = repository.get();
        return s != null && s.isRunning();
    }

    /** 开新局：清空上一局残留（直接重建 GameState），并初始化区域 / 地块 */
    public void startGame() {
        GameState state = new GameState();
        state.setFunds(GameConfig.STARTING_FUNDS);
        state.setDay(1);
        state.setDayProgress(0);
        state.setSpeed(GameConfig.DEFAULT_SPEED);
        state.setPaused(false);
        state.setRunning(true);
        state.setStartDateDisplay(GameConfig.DEFAULT_START_DATE);
        state.setStartDate(LocalDate.parse(GameConfig.DEFAULT_START_DATE));

        List<String> initMessages = regionService.initialize(state);
        statsService.rebuild(state);
        refreshPreview();
        repository.save(state);

        broadcast("DAY_STARTED", Map.of("day", state.getDay(), "date", state.currentDateDisplay(),
                "unlocks", initMessages));
        sendConfig();
        broadcastState();
    }

    /** 结束对局 */
    public void stopGame() {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        s.setRunning(false);
        broadcast("GAME_STOPPED", Map.of("reason", "STOPPED"));
    }

    public void pauseGame() {
        GameState s = repository.get();
        if (s != null) {
            s.setPaused(true);
            broadcast("TIME_TICK", tickPayload(s));
        }
    }

    public void resumeGame() {
        GameState s = repository.get();
        if (s != null) {
            s.setPaused(false);
            broadcast("TIME_TICK", tickPayload(s));
        }
    }

    public void setSimulationSpeed(double speed) {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        s.setSpeed(speed);
        broadcast("TIME_TICK", tickPayload(s));
    }

    // ===================== 时钟推进 =====================

    @Scheduled(fixedRate = GameConfig.TICK_INTERVAL_MS)
    public void scheduledLoop() {
        GameState s = repository.get();
        if (s == null || !s.isRunning() || s.isPaused()) {
            return;
        }
        double seconds = GameConfig.TICK_INTERVAL_MS / 1000.0;
        s.setDayProgress(s.getDayProgress() + seconds * s.getSpeed() / GameConfig.REAL_SECONDS_PER_GAME_DAY);
        int guard = 0;
        while (s.getDayProgress() >= 1.0 && guard++ < 64) {
            s.setDayProgress(s.getDayProgress() - 1.0);
            closeDay();
            beginDay();
        }
    }

    @Scheduled(fixedRate = 200)
    public void tickBroadcastLoop() {
        GameState s = repository.get();
        if (s == null || !s.isRunning()) {
            return;
        }
        broadcast("TIME_TICK", tickPayload(s));
    }

    /** 完成当日挂机结算 */
    private void closeDay() {
        GameState s = repository.get();
        DailySettle settle;
        try {
            DemandResult demand = demandService.compute(s);
            settle = financeService.settle(s, demand);
        } catch (Exception e) {
            settle = new DailySettle();
            settle.setDay(s.getDay());
            settle.setDateDisplay(s.currentDateDisplay());
            settle.setFundsAfter(s.getFunds());
            settle.getEvents().add("结算异常：" + e.getMessage());
        }

        // 员工在岗时长累计（不自动升级，仅提示可培训）
        settle.getEvents().addAll(staffService.advanceOneDay(s));

        // 统计刷新 → 成就解锁（地块需玩家付费解锁）
        statsService.rebuild(s);
        for (Achievement a : achievementService.evaluateAndUnlock(s)) {
            settle.getUnlocks().add("🏆 成就达成：" + a.getDisplayName());
        }

        s.appendHistory(settle);
        s.setLastSettle(settle);

        broadcast("DAY_SETTLED", Map.of("settle", settle, "funds", s.getFunds(), "day", s.getDay()));
    }

    /** 开启新的一天：推进日期并统一应用「待生效指令」 */
    private void beginDay() {
        GameState s = repository.get();
        s.setDay(s.getDay() + 1);
        applyPendingCommands();
        statsService.rebuild(s);
        List<String> unlocks = new ArrayList<>();
        for (Achievement a : achievementService.evaluateAndUnlock(s)) {
            unlocks.add("🏆 成就达成：" + a.getDisplayName());
        }
        refreshPreview();
        broadcast("DAY_STARTED", Map.of("day", s.getDay(), "date", s.currentDateDisplay(), "unlocks", unlocks));
        broadcastState();
    }

    /** 刷新今日预计结算（开新局 / 次日 0 点应用指令后调用），纯投影不动资金 */
    private void refreshPreview() {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        try {
            DemandResult demand = demandService.compute(s);
            s.setTodayPreview(financeService.projection(s, demand));
        } catch (Exception e) {
            s.setTodayPreview(null);
        }
    }

    // ===================== 指令入队（立即校验，次日生效） =====================

    public void requestBuild(WebSocketSession session, MotherBuildingType mother, String plotId) {
        GameState s = repository.get();
        String reason = constructionService.validateBuild(s, mother, plotId);
        if (reason != null) {
            fail(session, "BUILD_BUILDING", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildingType", mother.name());
        payload.put("plotId", plotId);
        enqueue(session, PendingCommandType.BUILD, payload,
                "新建 " + mother.getDisplayName() + " @ " + plotId);
    }

    public void requestFormLevelUp(WebSocketSession session, String buildingId) {
        GameState s = repository.get();
        BuildingInstance b = s == null ? null : s.findAliveBuilding(buildingId);
        String reason = evolutionService.validateFormLevelUp(s, b);
        if (reason != null) {
            fail(session, "FORM_LEVEL_UP", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildingId", buildingId);
        enqueue(session, PendingCommandType.FORM_LEVEL_UP, payload,
                b.getForm().getDisplayName() + " 形态升级 Lv" + (b.getFormLevel() + 1));
    }

    public void requestBranchEvolve(WebSocketSession session, String buildingId, String targetFormId) {
        GameState s = repository.get();
        BuildingInstance b = s == null ? null : s.findAliveBuilding(buildingId);
        BuildingForm target = registry.form(targetFormId);
        String reason = evolutionService.validateBranchEvolve(s, b, target);
        if (reason != null) {
            fail(session, "BRANCH_EVOLVE", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildingId", buildingId);
        payload.put("targetFormId", targetFormId);
        enqueue(session, PendingCommandType.BRANCH_EVOLVE, payload,
                b.getForm().getDisplayName() + " → " + target.getDisplayName());
    }

    public void requestMergeEvolve(WebSocketSession session, String buildingIdA, String buildingIdB) {
        GameState s = repository.get();
        if (s == null) {
            fail(session, "MERGE_EVOLVE", "GAME_NOT_RUNNING");
            return;
        }
        BuildingInstance a = s.findAliveBuilding(buildingIdA);
        BuildingInstance b = s.findAliveBuilding(buildingIdB);
        if (a == null || b == null) {
            fail(session, "MERGE_EVOLVE", "BUILDING_NOT_FOUND");
            return;
        }
        var recipe = evolutionService.matchRecipe(a, b);
        String reason = evolutionService.validateMerge(s, a, b, recipe);
        if (reason != null) {
            fail(session, "MERGE_EVOLVE", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildingIdA", buildingIdA);
        payload.put("buildingIdB", buildingIdB);
        enqueue(session, PendingCommandType.MERGE_EVOLVE, payload,
                "合并 " + a.getForm().getDisplayName() + " + " + b.getForm().getDisplayName()
                        + " → " + recipe.getResultForm().getDisplayName());
    }

    public void requestInteriorUpgrade(WebSocketSession session, String buildingId, InteriorType type) {
        GameState s = repository.get();
        BuildingInstance b = s == null ? null : s.findAliveBuilding(buildingId);
        String reason = interiorService.validateUpgrade(s, b, type);
        if (reason != null) {
            fail(session, "INTERIOR_UPGRADE", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildingId", buildingId);
        payload.put("interiorType", type.name());
        enqueue(session, PendingCommandType.INTERIOR_UPGRADE, payload,
                b.getForm().getDisplayName() + " 内装升级：" + type.getDisplayName());
    }

    public void requestHire(WebSocketSession session, PositionType position) {
        GameState s = repository.get();
        String reason = staffService.validateHire(s, position);
        if (reason != null) {
            fail(session, "HIRE_STAFF", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("position", position.name());
        enqueue(session, PendingCommandType.HIRE_STAFF, payload,
                "招聘 " + position.getDisplayName() + " Lv1");
    }

    public void requestTrain(WebSocketSession session, String staffId) {
        GameState s = repository.get();
        StaffInstance staff = s == null ? null : s.findAliveStaff(staffId);
        String reason = staffService.validateTrain(s, staff);
        if (reason != null) {
            fail(session, "TRAIN_STAFF", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("staffId", staffId);
        enqueue(session, PendingCommandType.TRAIN_STAFF, payload,
                "培训 " + staff.getPosition().getDisplayName() + " Lv" + staff.getLevel() + " → Lv"
                        + (staff.getLevel() + 1));
    }

    public void requestFire(WebSocketSession session, String staffId) {
        GameState s = repository.get();
        StaffInstance staff = s == null ? null : s.findAliveStaff(staffId);
        String reason = staffService.validateFire(s, staff);
        if (reason != null) {
            fail(session, "FIRE_STAFF", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("staffId", staffId);
        enqueue(session, PendingCommandType.FIRE_STAFF, payload,
                "解雇 " + staff.getPosition().getDisplayName() + " Lv" + staff.getLevel());
    }

    public void requestSkillUnlock(WebSocketSession session, String skillId) {
        GameState s = repository.get();
        String reason = skillTree.validateUnlock(s, skillId);
        if (reason != null) {
            fail(session, "UNLOCK_SKILL", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skillId", skillId);
        enqueue(session, PendingCommandType.SKILL_UNLOCK, payload,
                "解锁技能 " + registry.skill(skillId).getDisplayName());
    }

    public void requestUnlockPlot(WebSocketSession session, String plotId) {
        GameState s = repository.get();
        Plot plot = registry.plot(plotId);
        String reason = regionService.validateUnlock(s, plot);
        if (reason != null) {
            fail(session, "UNLOCK_PLOT", reason);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plotId", plotId);
        enqueue(session, PendingCommandType.UNLOCK_PLOT, payload,
                "解锁地块 " + plot.getDisplayName() + "（花费 " + (long) plot.getUnlockCost() + "）");
    }

    private void enqueue(WebSocketSession session, PendingCommandType type,
                         Map<String, Object> payload, String description) {
        GameState s = repository.get();
        PendingCommand cmd = new PendingCommand(
                idGenerator.next("CMD"), type, s.getDay(), s.getDay() + 1, payload, description);
        s.getPendingCommands().add(cmd);
        Map<String, Object> ack = Map.of(
                "action", type.name(),
                "commandId", cmd.getId(),
                "description", description,
                "effectiveDay", cmd.getEffectiveDay());
        send(session, "ACTION_QUEUED", ack);
        broadcastState();
    }

    // ===================== 次日生效 =====================

    /** 统一应用待生效指令（次日 0 点）并二次校验 */
    private void applyPendingCommands() {
        GameState s = repository.get();
        List<PendingCommand> queue = new ArrayList<>(s.getPendingCommands());
        s.getPendingCommands().clear();

        for (PendingCommand cmd : queue) {
            String reason;
            switch (cmd.getType()) {
                case BUILD: {
                    MotherBuildingType mother = parseMother(cmd.str("buildingType"));
                    reason = constructionService.validateBuild(s, mother, cmd.str("plotId"));
                    if (reason == null) {
                        constructionService.build(s, mother, cmd.str("plotId"));
                    }
                    break;
                }
                case FORM_LEVEL_UP: {
                    BuildingInstance b = s.findAliveBuilding(cmd.str("buildingId"));
                    reason = evolutionService.validateFormLevelUp(s, b);
                    if (reason == null) {
                        evolutionService.formLevelUp(s, b);
                    }
                    break;
                }
                case BRANCH_EVOLVE: {
                    BuildingInstance b = s.findAliveBuilding(cmd.str("buildingId"));
                    BuildingForm target = registry.form(cmd.str("targetFormId"));
                    reason = evolutionService.validateBranchEvolve(s, b, target);
                    if (reason == null) {
                        evolutionService.branchEvolve(s, b, target);
                    }
                    break;
                }
                case MERGE_EVOLVE: {
                    BuildingInstance a = s.findAliveBuilding(cmd.str("buildingIdA"));
                    BuildingInstance b = s.findAliveBuilding(cmd.str("buildingIdB"));
                    var recipe = evolutionService.matchRecipe(a, b);
                    reason = evolutionService.validateMerge(s, a, b, recipe);
                    if (reason == null) {
                        evolutionService.merge(s, a, b, recipe);
                    }
                    break;
                }
                case INTERIOR_UPGRADE: {
                    BuildingInstance b = s.findAliveBuilding(cmd.str("buildingId"));
                    InteriorType type = parseInterior(cmd.str("interiorType"));
                    reason = interiorService.validateUpgrade(s, b, type);
                    if (reason == null) {
                        interiorService.upgrade(s, b, type);
                    }
                    break;
                }
                case HIRE_STAFF: {
                    PositionType position = parsePosition(cmd.str("position"));
                    reason = staffService.validateHire(s, position);
                    if (reason == null) {
                        staffService.hire(s, position);
                    }
                    break;
                }
                case TRAIN_STAFF: {
                    StaffInstance staff = s.findAliveStaff(cmd.str("staffId"));
                    reason = staffService.validateTrain(s, staff);
                    if (reason == null) {
                        staffService.train(s, staff);
                    }
                    break;
                }
                case FIRE_STAFF: {
                    StaffInstance staff = s.findAliveStaff(cmd.str("staffId"));
                    reason = staffService.validateFire(s, staff);
                    if (reason == null) {
                        staffService.fire(s, staff);
                    }
                    break;
                }
                case SKILL_UNLOCK: {
                    String skillId = cmd.str("skillId");
                    reason = skillTree.validateUnlock(s, skillId);
                    if (reason == null) {
                        skillTree.unlock(s, skillId);
                    }
                    break;
                }
                case UNLOCK_PLOT: {
                    Plot plot = registry.plot(cmd.str("plotId"));
                    reason = regionService.validateUnlock(s, plot);
                    if (reason == null) {
                        regionService.unlock(s, plot);
                    }
                    break;
                }
                default:
                    reason = "UNKNOWN_COMMAND";
            }
            if (reason != null) {
                broadcast("ACTION_FAILED", Map.of(
                        "action", cmd.getType().name(),
                        "commandId", cmd.getId(),
                        "description", cmd.getDescription() == null ? "" : cmd.getDescription(),
                        "reason", reason));
            }
        }
    }

    // ===================== 状态同步与报表 =====================

    /** 新连接会话全量同步 */
    public void syncStateToSession(WebSocketSession session) {
        GameState s = repository.get();
        if (s == null) {
            send(session, "GAME_STOPPED", Map.of("reason", "NO_GAME"));
            return;
        }
        sendConfig(session);
        send(session, "RESORT_STATE", statePayload(s));
        send(session, "TIME_TICK", tickPayload(s));
    }

    /** 静态配置下发（供前端渲染形态 / 地块 / 技能 / 成就列表） */
    public void sendConfig() {
        broadcast("CONFIG", configPayload());
    }

    public void sendConfig(WebSocketSession session) {
        send(session, "CONFIG", configPayload());
    }

    /** 全量每日日志（游戏结束时用于复盘） */
    public void sendDailyLog(WebSocketSession session) {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        send(session, "DAILY_LOG", Map.of("history", s.getDailyHistory()));
    }

    /** 区间报告：过去 1 / 7 / 30 / 365 天聚合，仅回请求方 */
    public void sendRangeReport(WebSocketSession session, int rangeDays) {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        List<DailySettle> history = s.getDailyHistory();
        int from = Math.max(0, history.size() - rangeDays);
        List<DailySettle> slice = new ArrayList<>(history.subList(from, history.size()));

        double revenue = 0;
        double guests = 0;
        double overflow = 0;
        int rooms = 0;
        int occupied = 0;
        for (DailySettle d : slice) {
            revenue += d.getTotalRevenue();
            guests += d.getTotalServed();
            overflow += d.getOverflowGuests();
            rooms += d.getTotalRooms();
            occupied += d.getOccupiedRooms();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rangeDays", rangeDays);
        payload.put("days", slice.size());
        payload.put("totalRevenue", revenue);
        payload.put("netProfit", revenue);
        payload.put("totalGuests", guests);
        payload.put("overflowGuests", overflow);
        payload.put("avgOccupancyRate", rooms <= 0 ? 0 : (double) occupied / rooms);
        payload.put("slices", slice);
        send(session, "RANGE_REPORT", payload);
    }

    // ===================== 载荷构造 =====================

    private Map<String, Object> tickPayload(GameState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", s.getDay());
        m.put("date", s.currentDateDisplay());
        m.put("progress", s.getDayProgress());
        m.put("speed", s.getSpeed());
        m.put("paused", s.isPaused());
        m.put("funds", s.getFunds());
        return m;
    }

    private Map<String, Object> statePayload(GameState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("funds", s.getFunds());
        m.put("day", s.getDay());
        m.put("date", s.currentDateDisplay());
        m.put("startDate", s.getStartDateDisplay());
        m.put("running", s.isRunning());
        m.put("dayProgress", s.getDayProgress());
        m.put("speed", s.getSpeed());
        m.put("paused", s.isPaused());
        m.put("buildings", s.aliveBuildings());
        m.put("tombstoneCount", s.getBuildings().size() - s.aliveBuildings().size());
        m.put("staffs", s.aliveStaffs());
        m.put("skillPoints", s.getSkillPoints());
        m.put("unlockedSkills", s.getUnlockedSkills());
        m.put("unlockedPlots", s.getUnlockedPlots());
        m.put("unlockedRegions", s.getUnlockedRegions());
        m.put("unlockedAchievements", s.getUnlockedAchievements());
        m.put("pendingCommands", s.getPendingCommands());
        m.put("stats", s.getStats());
        m.put("lastSettle", s.getLastSettle());
        m.put("historySize", s.getDailyHistory().size());
        m.put("todayPreview", s.getTodayPreview());
        m.put("milestones", s.getMilestones());
        m.put("abilitySupply", comfortService.totalAbilitySupply(s));
        m.put("abilityDemand", comfortService.totalAbilityDemand(s));
        m.put("abilityRatios", comfortService.supplyRatios(s));
        // 单间实际房屋收益构成（与日结同一套乘数，供前端展示「当前房屋收益（每间）」）
        Map<String, Map<String, Object>> roomRevenue = new LinkedHashMap<>();
        double roomPriceMult = skillTree.multiplier(s, SkillEffectKey.ROOM_PRICE_MULT);
        for (BuildingInstance b : s.aliveAccommodationBuildings()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("base", b.getForm().pricePerRoomOf(b.getFormLevel()));
            x.put("priceMult", roomPriceMult);
            x.put("mergeBonus", b.getForm().getLine() == EvolutionLine.MERGE
                    ? skillTree.flat(s, SkillEffectKey.MERGE_BONUS) : 0d);
            x.put("interiorBonus", b.interiorRevenueBonus());
            x.put("final", financeService.pricePerRoom(s, b));
            roomRevenue.put(b.getId(), x);
        }
        m.put("roomRevenue", roomRevenue);
        Map<String, Double> compensations = new LinkedHashMap<>();
        for (StaffInstance st : s.aliveStaffs()) {
            compensations.put(st.getId(), staffService.fireCompensation(s, st));
        }
        m.put("staffCompensations", compensations);
        Map<String, Map<String, Object>> training = new LinkedHashMap<>();
        for (StaffInstance st : s.aliveStaffs()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("daysAtCurrentLevel", st.getDaysAtCurrentLevel());
            x.put("requiredDays", staffService.trainingRequiredDays(s, st));
            x.put("trainCost", staffService.trainingCost(st));
            x.put("ready", staffService.trainingReady(s, st));
            x.put("maxLevel", st.getLevel() >= com.meileme.resort.config.GameConfig.STAFF_MAX_LEVEL);
            training.put(st.getId(), x);
        }
        m.put("staffTraining", training);
        return m;
    }

    private Map<String, Object> configPayload() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regions", regionList());
        m.put("buildingForms", registry.allForms());
        m.put("mergeRecipes", registry.allRecipes());
        m.put("plots", registry.allPlots());
        m.put("achievements", registry.allAchievements());
        m.put("skills", registry.allSkills());
        m.put("mothers", motherList());
        m.put("segments", segmentList());
        m.put("abilities", abilityList());
        m.put("positions", positionList());
        m.put("interiors", interiorList());
        m.put("speedOptions", GameConfig.SPEED_OPTIONS);
        m.put("formMaxLevel", GameConfig.FORM_MAX_LEVEL);
        m.put("staffMaxLevel", GameConfig.STAFF_MAX_LEVEL);
        return m;
    }

    private List<Map<String, Object>> regionList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Region r : Region.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", r.name());
            x.put("displayName", r.getDisplayName());
            x.put("baseDailyFlow", r.getBaseDailyFlow());
            Map<String, Double> ratios = new LinkedHashMap<>();
            for (CustomerSegment seg : CustomerSegment.values()) {
                ratios.put(seg.name(), r.getSegmentRatios().get(seg));
            }
            x.put("segmentRatios", ratios);
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> motherList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MotherBuildingType mt : MotherBuildingType.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", mt.name());
            x.put("displayName", mt.getDisplayName());
            x.put("category", mt.getCategory().name());
            x.put("phase1Buildable", mt.isPhase1Buildable());
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> segmentList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CustomerSegment seg : CustomerSegment.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", seg.name());
            x.put("displayName", seg.getDisplayName());
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> abilityList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AbilityType a : AbilityType.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", a.name());
            x.put("displayName", a.getDisplayName());
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> positionList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PositionType p : PositionType.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", p.name());
            x.put("displayName", p.getDisplayName());
            List<String> abilities = new ArrayList<>();
            for (AbilityType a : p.getAbilities()) {
                abilities.add(a.name());
            }
            x.put("abilities", abilities);
            x.put("abilityValueByLevel", p.getAbilityValueByLevel());
            x.put("hireCost", p.getHireCost());
            x.put("upgradeDaysLv1ToLv2", p.getUpgradeDaysLv1ToLv2());
            x.put("upgradeDaysLv2ToLv3", p.getUpgradeDaysLv2ToLv3());
            x.put("compensationBase", p.getCompensationBase());
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> interiorList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (InteriorType it : InteriorType.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", it.name());
            x.put("displayName", it.getDisplayName());
            x.put("category", it.getCategory().name());
            x.put("categoryDisplayName", it.getCategory().getDisplayName());
            x.put("comfortPerLevel", it.getComfortPerLevel());
            x.put("capacityPerLevel", it.getCapacityPerLevel());
            x.put("revenueBonusPerLevel", it.getRevenueBonusPerLevel());
            Map<String, Double> affinities = new LinkedHashMap<>();
            for (CustomerSegment seg : CustomerSegment.values()) {
                affinities.put(seg.name(), it.segmentAffinityOf(seg));
            }
            x.put("segmentAffinityPerLevel", affinities);
            x.put("upgradeCostByLevel", it.getUpgradeCostByLevel());
            list.add(x);
        }
        return list;
    }

    private void broadcastState() {
        GameState s = repository.get();
        if (s != null) {
            broadcast("RESORT_STATE", statePayload(s));
        }
    }

    // ===================== 广播工具 =====================

    private void broadcast(String type, Map<String, Object> payload) {
        if (wsHandler == null) {
            return;
        }
        wsHandler.broadcastMessage(toJson(type, payload));
    }

    private void send(WebSocketSession session, String type, Map<String, Object> payload) {
        if (wsHandler == null || session == null) {
            return;
        }
        wsHandler.sendToSession(session, toJson(type, payload));
    }

    private void fail(WebSocketSession session, String action, String reason) {
        send(session, "ACTION_FAILED", Map.of("action", action, "reason", reason));
    }

    private String toJson(String type, Map<String, Object> payload) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", type);
            msg.putAll(payload);
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\"}";
        }
    }

    // ===================== 解析工具 =====================

    private MotherBuildingType parseMother(String name) {
        try {
            return MotherBuildingType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    private InteriorType parseInterior(String name) {
        try {
            return InteriorType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    private PositionType parsePosition(String name) {
        try {
            return PositionType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
