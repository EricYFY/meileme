package com.meileme.resort.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.meileme.resort.config.ProjectType;
import com.meileme.resort.config.Region;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.ConstructionProject;
import com.meileme.resort.model.DailySettle;
import com.meileme.resort.model.DemandResult;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.PendingCommand;
import com.meileme.resort.model.StaffInstance;
import com.meileme.resort.repository.GameStateRepository;
import com.meileme.resort.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

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

    public synchronized boolean isRunning() {
        GameState s = repository.get();
        return s != null && s.isRunning();
    }

    /** 开新局：清空上一局残留（直接重建 GameState），并初始化区域 / 地块 */
    public synchronized void startGame() {
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
        repository.save(state);
        refreshPreview();

        broadcast("DAY_STARTED", Map.of("day", state.getDay(), "date", state.currentDateDisplay(),
                "unlocks", initMessages));
        sendConfig();
        broadcastState();
    }

    /** 结束对局 */
    public synchronized void stopGame() {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        s.setRunning(false);
        broadcast("GAME_STOPPED", Map.of("reason", "STOPPED"));
    }

    public synchronized void pauseGame() {
        GameState s = repository.get();
        if (s != null) {
            s.setPaused(true);
            broadcast("TIME_TICK", tickPayload(s));
        }
    }

    public synchronized void resumeGame() {
        GameState s = repository.get();
        if (s != null) {
            s.setPaused(false);
            broadcast("TIME_TICK", tickPayload(s));
        }
    }

    public synchronized void setSimulationSpeed(double speed) {
        setSimulationSpeed(null, speed);
    }

    public synchronized void setSimulationSpeed(WebSocketSession session, double speed) {
        GameState s = repository.get();
        if (s == null) {
            fail(session, "SET_SIMULATION_SPEED", "GAME_NOT_RUNNING");
            return;
        }
        boolean valid = false;
        for (double opt : GameConfig.SPEED_OPTIONS) {
            if (Math.abs(opt - speed) < 1e-4) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            fail(session, "SET_SIMULATION_SPEED", "INVALID_SPEED");
            return;
        }
        s.setSpeed(speed);
        broadcast("TIME_TICK", tickPayload(s));
    }

    // ===================== 时钟推进 =====================

    @Scheduled(fixedRate = 200)
    public synchronized void scheduledLoop() {
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
    public synchronized void tickBroadcastLoop() {
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
            System.err.println("[GameEngine] 日结异常: " + e.getMessage());
            e.printStackTrace();
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
            String ptStr = a.getRewardSkillPoints() > 0 ? "（技能点 +" + a.getRewardSkillPoints() + "）" : "";
            settle.getUnlocks().add("🏆 成就达成：" + a.getDisplayName() + ptStr);
        }

        s.appendHistory(settle);
        s.setLastSettle(settle);

        broadcast("DAY_SETTLED", Map.of("settle", settle, "funds", s.getFunds(), "day", s.getDay()));
    }

    /** 开启新的一天：推进日期并统一应用「待生效指令」 */
    private void beginDay() {
        GameState s = repository.get();
        s.setDay(s.getDay() + 1);
        s.getStats().setTotalAssets(s.getFunds());
        // 工程工期从次日起累计，达标后在本日 0 点生效
        List<String> projectDone = progressProjects(s);
        applyPendingCommands();
        statsService.rebuild(s);
        List<String> unlocks = new ArrayList<>(projectDone);
        for (Achievement a : achievementService.evaluateAndUnlock(s)) {
            String ptStr = a.getRewardSkillPoints() > 0 ? "（技能点 +" + a.getRewardSkillPoints() + "）" : "";
            unlocks.add("🏆 成就达成：" + a.getDisplayName() + ptStr);
        }
        refreshPreview();
        broadcast("DAY_STARTED", Map.of("day", s.getDay(), "date", s.currentDateDisplay(), "unlocks", unlocks));
        broadcastState();
    }

    /** 推进所有进行中工程：每过一天剩余工期 −1；归零则完成并生效（失败则退还已付费用） */
    private List<String> progressProjects(GameState s) {
        List<String> done = new ArrayList<>();
        List<ConstructionProject> finished = new ArrayList<>();
        for (ConstructionProject p : s.getProjects()) {
            p.setRemainingDays(p.getRemainingDays() - 1);
            if (p.getRemainingDays() <= 0) {
                finished.add(p);
            }
        }
        for (ConstructionProject p : finished) {
            String reason = completeProject(s, p);
            s.getProjects().remove(p);
            if (reason == null) {
                done.add("✅ " + p.getDisplayName() + " 已完成");
                broadcast("PROJECT_COMPLETED", projectView(p));
            } else {
                s.setFunds(s.getFunds() + p.getPaidCost());
                Map<String, Object> failPayload = new LinkedHashMap<>();
                failPayload.put("project", projectView(p));
                failPayload.put("reason", reason);
                failPayload.put("refund", p.getPaidCost());
                broadcast("PROJECT_FAILED", failPayload);
            }
        }
        return done;
    }

    /** 应用工程结果；返回 null 表示成功，否则为失败原因（失败会退还开工费用） */
    private String completeProject(GameState s, ConstructionProject p) {
        switch (p.getType()) {
            case BUILD: {
                Plot plot = registry.plot(p.getPlotId());
                if (plot == null || !s.getUnlockedPlots().contains(plot.getId())) {
                    return "PLOT_NOT_UNLOCKED";
                }
                String cellReason = constructionService.validateCell(s, p.getPlotId(), p.getCellX(), p.getCellY(), p.getId());
                if (cellReason != null) {
                    return cellReason;
                }
                constructionService.completeBuild(s, p.getMother(), p.getPlotId(), p.getCellX(), p.getCellY());
                return null;
            }
            case FORM_LEVEL_UP: {
                BuildingInstance b = s.findAliveBuilding(p.getBuildingId());
                if (b == null) {
                    return "BUILDING_NOT_FOUND";
                }
                if (b.getFormLevel() >= b.getForm().getMaxLevel()) {
                    return "FORM_LEVEL_MAX_REACHED";
                }
                evolutionService.completeFormLevelUp(b);
                return null;
            }
            case BRANCH_EVOLVE: {
                BuildingInstance b = s.findAliveBuilding(p.getBuildingId());
                BuildingForm target = registry.form(p.getTargetFormId());
                if (b == null) {
                    return "BUILDING_NOT_FOUND";
                }
                if (target == null) {
                    return "TARGET_FORM_NOT_FOUND";
                }
                if (target.getEvolveFrom() != b.getForm() || b.getFormLevel() < b.getForm().getMaxLevel()) {
                    return "EVOLVE_PATH_NOT_ALLOWED";
                }
                evolutionService.completeBranchEvolve(b, target);
                return null;
            }
            case MERGE_EVOLVE: {
                BuildingInstance a = s.findAliveBuilding(p.getBuildingId());
                BuildingInstance b = s.findAliveBuilding(p.getSecondBuildingId());
                var recipe = evolutionService.matchRecipe(a, b);
                if (recipe == null) {
                    return "MERGE_RECIPE_NOT_MATCHED";
                }
                evolutionService.completeMerge(s, a, b, recipe);
                return null;
            }
            case INTERIOR_UPGRADE: {
                BuildingInstance b = s.findAliveBuilding(p.getBuildingId());
                if (b == null) {
                    return "BUILDING_NOT_FOUND";
                }
                if (p.getInteriorType() == null) {
                    return "INTERIOR_NOT_FOUND";
                }
                com.meileme.resort.model.InteriorInstance inst = b.findInterior(p.getInteriorType());
                int cur = inst == null ? 0 : inst.getLevel();
                if (cur >= b.getForm().maxInteriorLevelOf(b.getFormLevel())) {
                    return "INTERIOR_LEVEL_CAP_REACHED";
                }
                interiorService.completeUpgrade(s, b, p.getInteriorType());
                return null;
            }
            default:
                return "UNKNOWN_PROJECT";
        }
    }

    private Map<String, Object> projectView(ConstructionProject p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("type", p.getType().name());
        m.put("projectType", p.getType().name());
        m.put("typeDisplayName", p.getType().getDisplayName());
        m.put("displayName", p.getDisplayName());
        m.put("plotId", p.getPlotId());
        m.put("cellX", p.getCellX());
        m.put("cellY", p.getCellY());
        m.put("buildingId", p.getBuildingId());
        m.put("secondBuildingId", p.getSecondBuildingId());
        m.put("mother", p.getMother() == null ? null : p.getMother().name());
        m.put("targetFormId", p.getTargetFormId());
        m.put("interiorType", p.getInteriorType() == null ? null : p.getInteriorType().name());
        m.put("targetLevel", p.getTargetLevel());
        m.put("totalDays", p.getTotalDays());
        m.put("remainingDays", p.getRemainingDays());
        m.put("elapsedDays", p.elapsedDays());
        m.put("startedDay", p.getStartedDay());
        m.put("finishDay", p.finishDay());
        return m;
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

    // ===================== 工程开工（立即扣费，按工期完成后生效） =====================

    public synchronized void requestBuild(WebSocketSession session, MotherBuildingType mother, String plotId, int cellX, int cellY) {
        GameState s = repository.get();
        String reason = constructionService.validateBuild(s, mother, plotId, cellX, cellY);
        if (reason != null) {
            fail(session, "BUILD_BUILDING", reason);
            return;
        }
        ConstructionProject p = new ConstructionProject();
        p.setType(ProjectType.BUILD);
        p.setMother(mother);
        p.setPlotId(plotId);
        p.setCellX(cellX);
        p.setCellY(cellY);
        p.setDisplayName("新建 " + mother.getDisplayName());
        p.setTotalDays(constructionService.projectDays(mother));
        startProject(session, s, p, constructionService.buildCost(s, mother));
    }

    public synchronized void requestFormLevelUp(WebSocketSession session, String buildingId) {
        GameState s = repository.get();
        BuildingInstance b = s == null ? null : s.findAliveBuilding(buildingId);
        String reason = evolutionService.validateFormLevelUp(s, b);
        if (reason != null) {
            fail(session, "FORM_LEVEL_UP", reason);
            return;
        }
        ConstructionProject p = new ConstructionProject();
        p.setType(ProjectType.FORM_LEVEL_UP);
        p.setBuildingId(buildingId);
        p.setPlotId(b.getPlotId());
        p.setTargetLevel(b.getFormLevel() + 1);
        p.setDisplayName(b.getForm().getDisplayName() + " 升级至 Lv" + (b.getFormLevel() + 1));
        p.setTotalDays(evolutionService.formLevelUpDays(b.getForm(), b.getFormLevel()));
        startProject(session, s, p, evolutionService.formLevelUpCost(s, b));
    }

    public synchronized void requestBranchEvolve(WebSocketSession session, String buildingId, String targetFormId) {
        GameState s = repository.get();
        BuildingInstance b = s == null ? null : s.findAliveBuilding(buildingId);
        BuildingForm target = registry.form(targetFormId);
        String reason = evolutionService.validateBranchEvolve(s, b, target);
        if (reason != null) {
            fail(session, "BRANCH_EVOLVE", reason);
            return;
        }
        ConstructionProject p = new ConstructionProject();
        p.setType(ProjectType.BRANCH_EVOLVE);
        p.setBuildingId(buildingId);
        p.setPlotId(b.getPlotId());
        p.setTargetFormId(targetFormId);
        p.setDisplayName(b.getForm().getDisplayName() + " → " + target.getDisplayName());
        p.setTotalDays(evolutionService.branchEvolveDays(target));
        startProject(session, s, p, evolutionService.branchEvolveCost(s, target));
    }

    public synchronized void requestMergeEvolve(WebSocketSession session, String buildingIdA, String buildingIdB) {
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
        ConstructionProject p = new ConstructionProject();
        p.setType(ProjectType.MERGE_EVOLVE);
        p.setBuildingId(buildingIdA);
        p.setSecondBuildingId(buildingIdB);
        p.setPlotId(a.getPlotId());
        p.setTargetFormId(recipe.getResultForm().getId());
        p.setDisplayName("合并 " + a.getForm().getDisplayName() + " + " + b.getForm().getDisplayName()
                + " → " + recipe.getResultForm().getDisplayName());
        p.setTotalDays(evolutionService.mergeEvolveDays(recipe));
        startProject(session, s, p, evolutionService.mergeCost(s, recipe));
    }

    public synchronized void requestInteriorUpgrade(WebSocketSession session, String buildingId, InteriorType type) {
        GameState s = repository.get();
        BuildingInstance b = s == null ? null : s.findAliveBuilding(buildingId);
        String reason = interiorService.validateUpgrade(s, b, type);
        if (reason != null) {
            fail(session, "INTERIOR_UPGRADE", reason);
            return;
        }
        int nextLevel = (b.findInterior(type) == null ? 0 : b.findInterior(type).getLevel()) + 1;
        ConstructionProject p = new ConstructionProject();
        p.setType(ProjectType.INTERIOR_UPGRADE);
        p.setBuildingId(buildingId);
        p.setPlotId(b.getPlotId());
        p.setInteriorType(type);
        p.setTargetLevel(nextLevel);
        p.setDisplayName(b.getForm().getDisplayName() + " 内装 " + type.getDisplayName() + " → Lv" + nextLevel);
        p.setTotalDays(interiorService.upgradeDays(type, nextLevel));
        startProject(session, s, p, interiorService.upgradeCost(s, type, nextLevel));
    }

    /** 创建工程：立即扣费并进入进行中状态，自次日开始累计工期 */
    private void startProject(WebSocketSession session, GameState s, ConstructionProject p, double cost) {
        p.setId(idGenerator.next("PJT"));
        p.setRemainingDays(p.getTotalDays());
        p.setStartedDay(s.getDay());
        p.setPaidCost(cost);
        s.setFunds(s.getFunds() - cost);
        s.getProjects().add(p);
        send(session, "PROJECT_STARTED", projectView(p));
        broadcastState();
    }

    // ===================== 指令入队（立即校验，次日生效） =====================

    public synchronized void requestHire(WebSocketSession session, PositionType position) {
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

    public synchronized void requestTrain(WebSocketSession session, String staffId) {
        GameState s = repository.get();
        StaffInstance staff = s == null ? null : s.findAliveStaff(staffId);
        String reason = staffService.validateTrain(s, staff);
        if (reason != null) {
            fail(session, "TRAIN_STAFF", reason);
            return;
        }
        staffService.startTrain(s, staff);
        send(session, "STAFF_TRAINING_STARTED", Map.of(
                "staffId", staffId,
                "targetLevel", staff.getTrainingTargetLevel(),
                "totalDays", staff.getTrainingTotalDays()
        ));
        broadcastState();
    }

    public synchronized void requestFire(WebSocketSession session, String staffId) {
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

    public synchronized void requestSkillUnlock(WebSocketSession session, String skillId) {
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

    public synchronized void requestUnlockPlot(WebSocketSession session, String plotId) {
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

    /** 管理员查询全量可配置项（含当前是否为自定义配置标识） */
    public synchronized void sendAdminConfig(WebSocketSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("config", registry.getRawConfigMap());
        m.put("isCustom", registry.isCustomConfig());
        send(session, "ADMIN_CONFIG_DATA", m);
    }

    /** 管理员保存新配置：写入 admin-config.json 并热重载生效 */
    public synchronized void saveAdminConfig(WebSocketSession session, com.fasterxml.jackson.databind.JsonNode node) {
        try {
            if (!node.has("config")) {
                fail(session, "SAVE_ADMIN_CONFIG", "CONFIG_PAYLOAD_MISSING");
                return;
            }
            Map<String, Object> map = objectMapper.convertValue(node.get("config"), new TypeReference<>() {});
            registry.saveAdminConfig(map);
            // 广播全量配置更新
            broadcast("CONFIG", configPayload());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("config", registry.getRawConfigMap());
            m.put("isCustom", true);
            m.put("filePath", registry.resolveAdminConfigFile().getAbsolutePath());
            send(session, "ADMIN_CONFIG_SAVED", m);
            log.info("管理员配置已保存并广播生效: {}", m.get("filePath"));
        } catch (Exception e) {
            log.error("保存管理员配置异常", e);
            fail(session, "SAVE_ADMIN_CONFIG", "SAVE_FAILED: " + e.getMessage());
        }
    }

    /** 管理员恢复默认配置：删除 admin-config.json 并重载默认 */
    public synchronized void resetAdminConfig(WebSocketSession session) {
        try {
            registry.resetToDefault();
            broadcast("CONFIG", configPayload());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("config", registry.getRawConfigMap());
            m.put("isCustom", false);
            send(session, "ADMIN_CONFIG_RESET", m);
            log.info("管理员配置已重置为默认并广播生效");
        } catch (Exception e) {
            log.error("重置管理员配置异常", e);
            fail(session, "RESET_ADMIN_CONFIG", "RESET_FAILED: " + e.getMessage());
        }
    }

    /** 全量每日日志（游戏结束时用于复盘） */
    public synchronized void sendDailyLog(WebSocketSession session) {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        send(session, "DAILY_LOG", Map.of("history", s.getDailyHistory()));
    }

    /** 区间报告：过去 1 / 7 / 30 / 365 天聚合，仅回请求方 */
    public synchronized void sendRangeReport(WebSocketSession session, int rangeDays) {
        GameState s = repository.get();
        if (s == null) {
            return;
        }
        int effectiveRange = rangeDays <= 0 ? 7 : rangeDays;
        List<DailySettle> history = s.getDailyHistory();
        int from = Math.max(0, history.size() - effectiveRange);
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
        payload.put("rangeDays", effectiveRange);
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
        // 进行中的工程（建造 / 升级 / 进化 / 合并 / 内装）
        List<Map<String, Object>> projects = new ArrayList<>();
        for (ConstructionProject p : s.getProjects()) {
            projects.add(projectView(p));
        }
        m.put("projects", projects);
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
            x.put("trainingDays", staffService.trainingDays(s, st));
            x.put("trainCost", staffService.trainingCost(st));
            x.put("ready", staffService.trainingReady(s, st));
            x.put("training", st.isTraining());
            x.put("trainingDaysRemaining", st.getTrainingDaysRemaining());
            x.put("trainingTotalDays", st.getTrainingTotalDays());
            x.put("trainingTargetLevel", st.getTrainingTargetLevel());
            int maxLv = st.getPosition() != null ? st.getPosition().getMaxLevel() : GameConfig.STAFF_MAX_LEVEL;
            if (registry != null && st.getPosition() != null) {
                com.meileme.resort.config.PositionConfig pcfg = registry.getPosition(st.getPosition().name());
                if (pcfg != null && pcfg.getAbilityValueByLevel() != null && pcfg.getAbilityValueByLevel().length > 0) {
                    maxLv = pcfg.getAbilityValueByLevel().length;
                }
            }
            x.put("maxLevel", st.getLevel() >= maxLv);
            training.put(st.getId(), x);
        }
        m.put("staffTraining", training);
        // 单建筑综合舒适度分解与分客群吸引力贡献（供中观地图与微观详情展示）
        Map<String, Map<String, Object>> buildingComfort = new LinkedHashMap<>();
        Map<AbilityType, Double> ratios = comfortService.supplyRatios(s);
        double attractMult = skillTree.multiplier(s, SkillEffectKey.SEGMENT_ATTRACTION_MULT);
        for (BuildingInstance b : s.aliveBuildings()) {
            if (b.getForm() == null) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            double rawBaseComfort = b.getForm().getBaseComfort();
            double levelComfortRatio = GameConfig.FORM_LEVEL_COMFORT_STEP * (b.getFormLevel() - GameConfig.FORM_MIN_LEVEL);
            double baseComfort = b.getForm().comfortOf(b.getFormLevel());
            double interiorComfort = comfortService.interiorComfortOf(s, b);
            double staffFactor = comfortService.staffFactorOf(b, ratios);
            double totalComfort = (baseComfort + interiorComfort) * staffFactor;
            x.put("rawBaseComfort", rawBaseComfort);
            x.put("levelComfortRatio", levelComfortRatio);
            x.put("baseComfort", baseComfort);
            x.put("interiorComfort", interiorComfort);
            x.put("staffFactor", staffFactor);
            x.put("totalComfort", totalComfort);
            x.put("shortages", comfortService.shortageAbilitiesOf(b, ratios));

            double baseCapacity = b.isAccommodation() ? b.getForm().getCapacityPerRoom() : b.getForm().getServiceCapacity();
            double interiorCapacity = comfortService.interiorCapacityBonusOf(s, b);
            double totalCapacity = baseCapacity + interiorCapacity;
            x.put("baseCapacity", baseCapacity);
            x.put("interiorCapacity", interiorCapacity);
            x.put("totalCapacity", totalCapacity);

            Map<String, Double> segAttraction = new LinkedHashMap<>();
            for (String segId : activeSegmentIds()) {
                double attr = (b.getForm().affinityOf(segId) + b.interiorAffinityBonusOf(segId, registry))
                        * totalComfort * attractMult;
                segAttraction.put(segId, attr);
            }
            x.put("segmentAttraction", segAttraction);
            buildingComfort.put(b.getId(), x);
        }
        m.put("buildingComfort", buildingComfort);
        return m;
    }

    private List<String> activeSegmentIds() {
        if (registry != null && !registry.allSegments().isEmpty()) {
            List<String> list = new ArrayList<>();
            for (com.meileme.resort.config.SegmentConfig sc : registry.allSegments()) {
                list.add(sc.getId());
            }
            return list;
        }
        List<String> list = new ArrayList<>();
        for (CustomerSegment cs : CustomerSegment.values()) {
            list.add(cs.name());
        }
        return list;
    }

    private Map<String, Object> configPayload() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regions", regionList());
        m.put("buildingForms", registry.allForms());
        m.put("mergeRecipes", registry.allRecipes());
        m.put("plots", registry.allPlots());
        m.put("plotMaps", registry.allPlotMaps());
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
        if (registry != null && !registry.allRegions().isEmpty()) {
            for (com.meileme.resort.config.RegionConfig rc : registry.allRegions()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", rc.getId());
                x.put("displayName", rc.getDisplayName());
                x.put("baseDailyFlow", rc.getBaseDailyFlow());
                x.put("segmentRatios", rc.getSegmentRatios() != null ? new LinkedHashMap<>(rc.getSegmentRatios()) : Collections.emptyMap());
                list.add(x);
            }
            return list;
        }
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
            x.put("buildDays", mt.getBuildDays());
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> segmentList() {
        if (registry != null && !registry.allSegments().isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (com.meileme.resort.config.SegmentConfig sc : registry.allSegments()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", sc.getId());
                x.put("displayName", sc.getDisplayName());
                x.put("description", sc.getDescription());
                x.put("color", sc.getColor());
                x.put("icon", sc.getIcon());
                list.add(x);
            }
            return list;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (CustomerSegment seg : CustomerSegment.values()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", seg.name());
            x.put("displayName", seg.getDisplayName());
            x.put("description", "");
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
        if (registry != null && !registry.allPositions().isEmpty()) {
            for (com.meileme.resort.config.PositionConfig pc : registry.allPositions()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", pc.getId());
                x.put("displayName", pc.getDisplayName());
                List<String> abilities = new ArrayList<>();
                for (AbilityType a : pc.getAbilities()) {
                    abilities.add(a.name());
                }
                x.put("abilities", abilities);
                x.put("abilityValueByLevel", pc.getAbilityValueByLevel());
                x.put("abilityValues", pc.getAbilityValueByLevel());
                Map<String, double[]> aoOut = new LinkedHashMap<>();
                if (pc.getAbilityOutputs() != null) {
                    for (Map.Entry<AbilityType, double[]> entry : pc.getAbilityOutputs().entrySet()) {
                        aoOut.put(entry.getKey().name(), entry.getValue());
                    }
                }
                x.put("abilityOutputs", aoOut);
                x.put("hireCost", pc.getHireCost());
                x.put("upgradeDaysByLevel", pc.getUpgradeDaysByLevel());
                x.put("trainDaysByLevel", pc.getTrainDaysByLevel());
                x.put("trainCostByLevel", pc.getTrainCostByLevel());
                x.put("compensationBase", pc.getCompensationBase());
                list.add(x);
            }
            return list;
        }
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
            x.put("upgradeDaysByLevel", p.getUpgradeDaysByLevel());
            x.put("trainDaysByLevel", p.getTrainDaysByLevel());
            x.put("trainCostByLevel", p.getTrainCostByLevel());
            x.put("compensationBase", p.getCompensationBase());
            list.add(x);
        }
        return list;
    }

    private List<Map<String, Object>> interiorList() {
        if (registry != null && !registry.allInteriors().isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (com.meileme.resort.config.InteriorConfig ic : registry.allInteriors()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", ic.getId());
                x.put("displayName", ic.getDisplayName());
                x.put("category", ic.getCategory() == null ? "" : ic.getCategory().name());
                x.put("categoryDisplayName", ic.getCategory() == null ? "" : ic.getCategory().getDisplayName());
                x.put("comfortPerLevel", ic.getComfortPerLevel());
                x.put("capacityPerLevel", ic.getCapacityPerLevel());
                x.put("revenueBonusPerLevel", ic.getRevenueBonusPerLevel());
                x.put("segmentAffinityPerLevel", ic.getSegmentAffinityPerLevel());
                x.put("upgradeCostByLevel", ic.getUpgradeCostByLevel());
                x.put("upgradeDaysByLevel", ic.getUpgradeDaysByLevel());
                x.put("applicableMothers", ic.getApplicableMothers());
                list.add(x);
            }
            return list;
        }
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
            x.put("upgradeDaysByLevel", it.getUpgradeDaysByLevel());
            List<String> mothers = new ArrayList<>();
            for (MotherBuildingType m : it.getApplicableMothers()) {
                mothers.add(m.name());
            }
            x.put("applicableMothers", mothers);
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
            msg.putAll(payload);
            msg.put("type", type);
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\"}";
        }
    }

    // ===================== 解析工具 =====================

    private PositionType parsePosition(String name) {
        try {
            return PositionType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
