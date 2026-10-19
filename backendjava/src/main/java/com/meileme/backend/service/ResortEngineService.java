package com.meileme.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.backend.config.*;
import com.meileme.backend.model.*;
import com.meileme.backend.repository.ResortRepository;
import com.meileme.backend.websocket.GameWebSocketHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 度假村经营引擎：连续实时经营（全部经营数值在本类内计算，Python 仅负责可视化）。
 *
 * 循环：
 *   startGame() → 直接进入 RUNNING（无决策窗口、无提交动作、可随时下指令）
 *   → scheduledLoop() 每现实 1 秒推进虚拟时钟，跨日 0 点 endDay() 结算 + beginDay() 开新一天
 *   → 玩家操作入队 pendingCommands，次日 0 点 applyPendingCommands() 统一生效
 *   → 报告由玩家自选区间按需生成（aggregateRange / sendRangeReport）
 */
@Service
public class ResortEngineService {

    private final GameWebSocketHandler webSocketHandler;
    private final StringRedisTemplate redisTemplate;
    private final ResortRepository resortRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    /** 本局日志文件路径（startGame 时新建，每局一份） */
    private volatile Path gameLogPath;

    /** 「本地旅游团到访」事件生成的额外团建单：暂存到次日，作为可见待决单交给玩家自行接/拒 */
    private volatile GuestOrder bonusTeamOrder;

    // ===== 运行控制 =====
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile double simulationSpeed = 1.0;

    // ===== 地图可建造格（Python 提供的临路非道路格）=====
    private final List<BuildCell> buildableCells = new CopyOnWriteArrayList<>();
    private final Set<String> occupiedCells = new HashSet<>(); // 已被建筑占用的格 "x,y"

    /** 可建造格：逻辑坐标 + 所属地块 plotId */
    private static class BuildCell {
        final int x;
        final int y;
        final String plotId;
        BuildCell(int x, int y, String plotId) { this.x = x; this.y = y; this.plotId = plotId; }
    }

    // Redis 键
    private static final String KEY_SPEED          = "game:simulation:speed";
    private static final String KEY_RESORT_BUILD   = "game:resort:buildings";
    private static final String KEY_RESORT_STAFF   = "game:resort:staff";
    private static final String KEY_NPC_PLAN       = "game:resort:npc_plan";
    private static final String KEY_NPC_STATE      = "game:state:npc";
    private static final String KEY_LEGACY_DISPATCH = "game:dispatch:mode"; // 旧外卖系统残留键，清库时一并删除

    public ResortEngineService(GameWebSocketHandler webSocketHandler,
                               StringRedisTemplate redisTemplate,
                               ResortRepository resortRepository) {
        this.webSocketHandler = webSocketHandler;
        this.redisTemplate = redisTemplate;
        this.resortRepository = resortRepository;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isRunning() {
        return running && resortRepository.exists() && !resortRepository.get().isGameOver();
    }

    // ==================================================================
    //  一、局生命周期
    // ==================================================================

    /** 开新局：从 Python 取地图/可建造格 → 重置 Resort → 直接进入第 1 日实时运行 */
    public void startGame(String mapId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            if (mapId != null && !mapId.isEmpty()) requestBody.put("mapId", mapId);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String mapJson = restTemplate.postForObject("http://localhost:8081/api/simulation/start", request, String.class);
            Map<String, Object> mapData = objectMapper.readValue(mapJson, new TypeReference<Map<String, Object>>() {});

            // 缓存并立即推送给已连接的客户端，保证开局/重新开局时地图必定刷新
            String mapMessage = "{\"type\":\"MAP_DATA\",\"data\":" + mapJson + "}";
            webSocketHandler.setCachedMapMessage(mapMessage);
            webSocketHandler.broadcastMessage(mapMessage);

            // 解析可建造格（第三位为所属地块 plotId，缺失时兜底为 INITIAL）
            buildableCells.clear();
            occupiedCells.clear();
            Object cellsObj = mapData.get("buildableCells");
            if (cellsObj instanceof List) {
                for (Object c : (List<?>) cellsObj) {
                    if (c instanceof List && ((List<?>) c).size() >= 2) {
                        List<?> arr = (List<?>) c;
                        int x = ((Number) arr.get(0)).intValue();
                        int y = ((Number) arr.get(1)).intValue();
                        String plotId = (arr.size() >= 3 && arr.get(2) != null)
                                ? String.valueOf(arr.get(2)) : GameConfig.PLOT_INITIAL;
                        buildableCells.add(new BuildCell(x, y, plotId));
                    }
                }
            }
            System.out.println("成功从 Python 引擎获取地图，可建造格 " + buildableCells.size() + " 个。");

            // 重置运行态
            running = true;
            paused = false;
            simulationSpeed = 1.0;
            bonusTeamOrder = null;
            clearRedisState();
            resumePythonSimulation();
            try { redisTemplate.opsForValue().set(KEY_SPEED, "1.0"); } catch (Exception ignored) {}

            resortRepository.clear(); // 开新局前清空上一局残留的每日汇总历史，保证本局内存日志干净

            Resort resort = new Resort();
            resort.setVirtualTimeMs(GameConfig.BASE_VIRTUAL_TIME_MS);
            resort.setDay(1);
            resort.setSeason(GameConfig.seasonOfDay(1));
            resortRepository.save(resort);

            initGameLog();

            broadcast("GAME_STARTED", null);
            beginDay();
        } catch (Exception e) {
            System.err.println("启动度假村失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================================================================
    //  每局日志落盘（便于事后复盘）
    // ==================================================================

    /** 新建本局日志文件并写入表头；写入失败不影响游戏运行 */
    private void initGameLog() {
        try {
            Path dir = Paths.get(GameConfig.LOG_DIR);
            Files.createDirectories(dir);
            String name = "game-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log";
            gameLogPath = dir.resolve(name).toAbsolutePath();
            logToFile("=== 新对局开始（初始资金 ¥" + (long) GameConfig.INITIAL_CASH
                    + "，初始口碑 " + (long) GameConfig.INITIAL_REPUTATION + "） ===");
            System.out.println("每局日志已开启：" + gameLogPath);
        } catch (Exception e) {
            gameLogPath = null;
            System.err.println("创建局日志失败: " + e.getMessage());
        }
    }

    /** 追加一行到本局日志；未开局或写入失败时静默跳过 */
    private void logToFile(String line) {
        Path p = gameLogPath;
        if (p == null) return;
        try {
            Files.write(p, Collections.singletonList(line), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /** 把一日结算写成多行日志（收入/支出逐项拆解），供事后复盘 */
    private void logDailyReport(DailyReport r, double cashAfter) {
        StringBuilder sb = new StringBuilder();
        sb.append("第 ").append(r.getDay()).append(" 日 [").append(r.getSeasonName()).append("] ")
                .append("净利 ").append(signY(r.getNetProfit()))
                .append(" / 资金 ").append(y(cashAfter));
        if (r.getRevenueDetail() != null) {
            sb.append(" | 收入 客房 ").append(y(r.getRevenueDetail().getRoomFee()))
                    .append(" 餐饮 ").append(y(r.getRevenueDetail().getDining()))
                    .append(" 游乐 ").append(y(r.getRevenueDetail().getRecreation()))
                    .append(" 团建 ").append(y(r.getRevenueDetail().getTeamOrder()));
        }
        if (r.getExpenseDetail() != null) {
            sb.append(" | 支出 工资 ").append(y(r.getExpenseDetail().getStaffSalary()))
                    .append(" 维护 ").append(y(r.getExpenseDetail().getBuildingMaintenance()))
                    .append(" 广告 ").append(y(r.getExpenseDetail().getAdBudget()))
                    .append(" 事件 ").append(y(r.getExpenseDetail().getEventCost()))
                    .append(" 团建罚款 ").append(y(r.getExpenseDetail().getTeamPenalty()));
        }
        sb.append(" | 口碑 ").append(d1(r.getReputationBefore())).append("→").append(d1(r.getReputationAfter()))
                .append(" (").append(signD1(r.getReputationDelta())).append(")");
        logToFile(sb.toString());
        if (r.getTeamOrderResults() != null) {
            for (DailyReport.TeamOrderResult t : r.getTeamOrderResults()) {
                if ("SATISFIED".equals(t.getStatus())) {
                    logToFile("    团建结算 · [" + t.getOrderId() + "] " + sizeLabel(t.getSize())
                            + " 履约 +" + y(t.getRevenue()));
                } else {
                    logToFile("    团建结算 · [" + t.getOrderId() + "] " + sizeLabel(t.getSize())
                            + " 违约 -" + y(t.getPenalty())
                            + (t.getReason() != null ? "（" + t.getReason() + "）" : ""));
                }
            }
        }
        if (r.getEvents() != null) {
            for (GameEvent e : r.getEvents()) logToFile("    事件 · " + e.getDescription());
        }
    }

    /** 保留 1 位小数 */
    private static String d1(double v) { return String.format("%.1f", v); }

    /** 带符号的 1 位小数 */
    private static String signD1(double v) { return (v >= 0 ? "+" : "") + String.format("%.1f", v); }

    /** 团建规模枚举名 → 中文label */
    private static String sizeLabel(String sizeName) {
        try { return TeamOrderSize.valueOf(sizeName).getLabel(); } catch (Exception e) { return sizeName; }
    }

    /** 团建单必备配套文字（餐厅/温泉池/观景台），无则为「无」 */
    private static String reqText(GuestOrder o) {
        List<String> r = new ArrayList<>();
        if (o.isRequiresRestaurant()) r.add("餐厅");
        if (o.isRequiresSpa()) r.add("温泉池");
        if (o.isRequiresViewpoint()) r.add("观景台");
        return r.isEmpty() ? "无" : String.join("/", r);
    }

    private static String y(double v) { return "¥" + Math.round(v); }

    private static String signY(double v) { return (v >= 0 ? "+¥" : "-¥") + Math.abs(Math.round(v)); }

    /** 比例转百分比文本（如 0.6 → "60%"） */
    private static String pct(double frac) { return Math.round(frac * 100) + "%"; }

    /** 结束并重置（含清空历史；先把完整日志取出下发，再清空） */
    public void stopGame() {
        Resort resort = resortRepository.get();
        Map<String, Object> data = new HashMap<>();
        data.put("daysSurvived", resort != null ? resort.getDaysSurvived() : 0);
        data.put("finalCash", resort != null ? resort.getCash() : 0.0);
        data.put("dailyReports", dailyLogPayload(GameConfig.REPORT_HISTORY_LIMIT));
        logToFile("=== 对局结束（手动停止） ===");

        running = false;
        paused = false;
        try { new RestTemplate().postForObject("http://localhost:8081/api/simulation/stop", null, String.class); } catch (Exception ignored) {}
        clearRedisState();
        resortRepository.clear();
        webSocketHandler.setCachedMapMessage(null); // 清除缓存地图，避免下次连接看到上一局的旧图
        broadcast("GAME_STOPPED", data);
        gameLogPath = null; // 关闭本局日志，下一次开局另起新文件
    }

    public void pauseGame() {
        if (!running) return;
        paused = true;
        broadcast("SIMULATION_PAUSED", null);
        broadcastTimeTick(); // 立即通知前端：时钟冻结
        try { new RestTemplate().postForObject("http://localhost:8081/api/simulation/pause", null, String.class); } catch (Exception ignored) {}
    }

    public void resumeGame() {
        if (!running) return;
        paused = false;
        broadcast("SIMULATION_RESUMED", null);
        broadcastTimeTick(); // 立即通知前端：时钟恢复推进
        resumePythonSimulation();
    }

    public void setSimulationSpeed(double speed) {
        if (!GameConfig.isValidSpeed(speed)) { fail("SET_SIMULATION_SPEED", "INVALID_SPEED"); return; }
        this.simulationSpeed = speed;
        try { redisTemplate.opsForValue().set(KEY_SPEED, String.valueOf(speed)); } catch (Exception ignored) {}
        webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_SPEED_CHANGED\",\"speed\":" + speed + "}");
        broadcastTimeTick(); // 立即按新倍速刷新时钟推进速率
    }

    /** Java 不再每天 start/stop Python，此刻仅在开局/恢复时确保 Python 侧处于运行态 */
    private void resumePythonSimulation() {
        try { new RestTemplate().postForObject("http://localhost:8081/api/simulation/resume", null, String.class); } catch (Exception ignored) {}
    }

    private void clearRedisState() {
        try {
            redisTemplate.delete(Arrays.asList(KEY_RESORT_BUILD, KEY_RESORT_STAFF, KEY_NPC_PLAN, KEY_NPC_STATE, KEY_LEGACY_DISPATCH));
        } catch (Exception ignored) {}
    }

    // ==================================================================
    //  二、时钟推进与跨日
    // ==================================================================

    /**
     * 低频调度（1 秒一次）：推进虚拟时钟 + 跨日结算。
     * ⚠️ 频率必须保持 1000ms——时钟推进量依赖执行间隔，改频率会成比例改变游戏速度。
     */
    @Scheduled(fixedRate = 1000)
    public void scheduledLoop() {
        Resort resort = resortRepository.get();
        if (!running || paused || resort == null || resort.isGameOver()) return;

        // 推进虚拟时钟：现实 1 秒 = 游戏 24 分钟 × 倍速（效率 1x = 现实 1 分钟 / 游戏日）
        resort.setVirtualTimeMs(resort.getVirtualTimeMs()
                + (long) (GameConfig.VIRTUAL_MS_PER_REAL_SECOND * simulationSpeed));

        // 跨日判定
        long elapsed = resort.getVirtualTimeMs() - GameConfig.BASE_VIRTUAL_TIME_MS;
        int virtualDay = (int) (elapsed / GameConfig.GAME_DAY_VIRTUAL_MS) + 1;
        if (virtualDay > resort.getDay()) {
            endDay();
            if (!resort.isGameOver()) beginDay();
        }
    }

    // ==================================================================
    //  时钟推送：仅携带时间信息，不含建筑/员工等大数组（5Hz，让进度条顺滑）
    // ==================================================================

    @Scheduled(fixedRate = 200)
    public void tickBroadcastLoop() {
        Resort resort = resortRepository.get();
        if (!running || resort == null) return;
        broadcastTimeTick();
    }

    /** 组装当日时钟快照：游戏时刻、当日进度、当前倍速、是否暂停 */
    private Map<String, Object> buildTimeTick() {
        Resort resort = resortRepository.get();
        if (resort == null) return null;

        boolean advancing = running && !paused && !resort.isGameOver();

        long dayTotal = GameConfig.GAME_DAY_VIRTUAL_MS;
        long elapsedInDay = (resort.getVirtualTimeMs() - GameConfig.BASE_VIRTUAL_TIME_MS) % dayTotal;
        if (elapsedInDay < 0) elapsedInDay += dayTotal;

        double virtualMsPerRealSecond = advancing
                ? GameConfig.VIRTUAL_MS_PER_REAL_SECOND * simulationSpeed : 0.0;

        Map<String, Object> d = new HashMap<>();
        d.put("day", resort.getDay());
        d.put("phase", resort.getPhase().name());
        d.put("virtualTimeMs", resort.getVirtualTimeMs());
        d.put("gameDayVirtualMs", dayTotal);
        d.put("virtualMsPerRealSecond", virtualMsPerRealSecond);
        d.put("speed", simulationSpeed);
        d.put("paused", paused);
        return d;
    }

    private void broadcastTimeTick() {
        Map<String, Object> d = buildTimeTick();
        if (d != null) broadcast("TIME_TICK", d);
    }

    // ==================================================================
    //  三、跨日流程
    // ==================================================================

    /** 新的一天：切换季节、恢复停用建筑、生成当日团建单，直接进入 RUNNING */
    private void beginDay() {
        Resort resort = resortRepository.get();

        // 1. 季节（每 30 游戏天切换）
        resort.setSeason(GameConfig.seasonOfDay(resort.getDay()));

        // 2. 修复上一日遗留的停用建筑
        for (Building b : resort.getBuildings()) {
            if (b.getStatus() == Building.STATUS_DISABLED) b.setStatus(Building.STATUS_AVAILABLE);
        }

        // 3. 生成今日 0~2 笔团建大单（当天有效：接受次日生效，不操作次日自动拒绝）
        resort.getTeamOrders().clear();
        int n = GameConfig.TEAM_ORDER_MIN_PER_DAY
                + random.nextInt(GameConfig.TEAM_ORDER_MAX_PER_DAY - GameConfig.TEAM_ORDER_MIN_PER_DAY + 1);
        for (int i = 0; i < n; i++) resort.getTeamOrders().add(buildTeamOrder(randomTeamSize()));

        // 3.1 「本地旅游团到访」遗留的额外小型团建单：作为可见待决单并入今日，交玩家自行接/拒
        GuestOrder bonus = bonusTeamOrder;
        if (bonus != null) {
            bonusTeamOrder = null;
            bonus.setStatus(GuestOrder.Status.PENDING);
            bonus.setEffectiveDay(0);
            bonus.setCreatedDay(resort.getDay());
            resort.getTeamOrders().add(bonus);
        }

        // 3.2 日志：记录今日所有报价单，便于事后复盘「谁在哪天接受了哪张单」
        for (GuestOrder o : resort.getTeamOrders()) {
            logToFile("    团建报价 · [" + o.getId() + "] " + o.getSize().getLabel()
                    + " 需" + o.getRequiredRooms() + "房 收益 " + y(o.getRevenue())
                    + " 违约 " + y(o.getPenalty()) + " 必备:" + reqText(o) + " —— 第 " + resort.getDay() + " 天");
        }

        // 4. 保持 RUNNING（无决策窗口）
        resort.setPhase(Resort.DayPhase.RUNNING);

        // 5. 可视化规划（Python 依据该数量全量渲染 NPC）
        writeNpcPlan();
        syncResortToRedis();

        // 6. 广播
        Map<String, Object> dayInfo = new HashMap<>();
        dayInfo.put("day", resort.getDay());
        dayInfo.put("season", resort.getSeason());
        dayInfo.put("seasonName", resort.getSeason().getLabel());
        broadcast("DAY_STARTED", dayInfo);
        broadcast("TEAM_ORDERS_OFFERED", resort.getTeamOrders());
        broadcast("RESORT_STATE", resort);
        broadcastTimeTick();

        System.out.println("=== 🌅 第 " + resort.getDay() + " 日 / " + resort.getSeason().getLabel()
                + " → 今日团建单 " + resort.getTeamOrders().size() + " 笔 ===");
    }

    /**
     * 当日（已过完的 D 天）结算：
     *   判定 D 天生效的团建单 → 生成日报 → 资金/口碑/天数 → 存历史 → 广播事件与换日提示 → 破产判定
     *   → 应用当日排队指令 → 拒绝 D 天未决团建单 → day = D+1（beginDay 由 scheduledLoop 调用）
     *
     * ⚠️ applyPendingCommands() 必须排在 rejectStaleTeamOrders() 之前：接受指令次日才把单置 ACCEPTED，
     *    若先清未决单会把当天刚接的单一并误清。
     */
    private void endDay() {
        Resort resort = resortRepository.get();
        int closedDay = resort.getDay();

        // ① 取出今日生效的团建单（结算后即移出卡片区）
        List<GuestOrder> dueOrders = settleDueTeamOrders(closedDay);

        // ② 生成日报
        DailyReport report = computeDailyReport(closedDay, dueOrders);

        // ③ 资金 / 口碑 / 累计
        resort.setCash(GameConfig.round2(resort.getCash() + report.getNetProfit()));
        resort.setReputation(GameConfig.clamp(
                resort.getReputation() + report.getReputationDelta(),
                GameConfig.REPUTATION_MIN, GameConfig.REPUTATION_MAX));
        resort.setDaysSurvived(resort.getDaysSurvived() + 1);
        resort.setCumulativeProfit(GameConfig.round2(resort.getCumulativeProfit() + report.getNetProfit()));
        resort.setTotalGuestsServed(resort.getTotalGuestsServed() + report.getGuestsServed());

        // ④ 存入历史（供区间报告聚合）
        resortRepository.appendReport(report);

        // ⑤ 广播随机事件 + 换日轻提示（不弹日报，只在事件日志追加一行）
        for (GameEvent e : report.getEvents()) {
            broadcast("EVENT_TRIGGER", e);
        }
        Map<String, Object> settled = new HashMap<>();
        settled.put("day", closedDay);
        settled.put("netProfit", report.getNetProfit());
        settled.put("cash", resort.getCash());
        settled.put("reputation", resort.getReputation());
        broadcast("DAY_SETTLED", settled);

        System.out.println("=== 🌙 第 " + closedDay + " 日结算：收入 ¥" + report.getTotalRevenue()
                + " / 支出 ¥" + report.getTotalExpense() + " / 净利 ¥" + report.getNetProfit()
                + " / 资金 ¥" + resort.getCash() + " ===");
        logDailyReport(report, resort.getCash());

        // ⑥ 破产判定
        if (resort.getCash() <= GameConfig.BANKRUPTCY_CASH) {
            triggerBankruptcy(report);
            return;
        }

        // ⑦ 先应用指令（含把 D 天接受的团建单转入次日生效），再把 D 天剩余未决单自动拒绝
        applyPendingCommands();
        rejectStaleTeamOrders();

        // ⑧ 进入次日
        resort.setDay(closedDay + 1);
        broadcast("RESORT_STATE", resort);
    }

    /** 破产：广播末日收支明细（report 为末日结算），让玩家看清钱花在哪，便于复盘 */
    private void triggerBankruptcy(DailyReport report) {
        Resort resort = resortRepository.get();
        resort.setGameOver(true);
        resort.setPhase(Resort.DayPhase.GAME_OVER);
        resort.setEndReason("资金 ≤ 0，资不抵债");
        running = false;
        try { new RestTemplate().postForObject("http://localhost:8081/api/simulation/stop", null, String.class); } catch (Exception ignored) {}
        Map<String, Object> data = new HashMap<>();
        data.put("daysSurvived", resort.getDaysSurvived());
        data.put("finalCash", resort.getCash());
        data.put("endReason", resort.getEndReason());
        if (report != null) {
            data.put("lastDay", report.getDay());
            data.put("lastCashBefore", report.getCashBefore());
            data.put("lastNetProfit", report.getNetProfit());
            data.put("lastTotalRevenue", report.getTotalRevenue());
            data.put("lastTotalExpense", report.getTotalExpense());
            if (report.getRevenueDetail() != null) {
                Map<String, Object> rev = new LinkedHashMap<>();
                rev.put("roomFee", report.getRevenueDetail().getRoomFee());
                rev.put("dining", report.getRevenueDetail().getDining());
                rev.put("recreation", report.getRevenueDetail().getRecreation());
                rev.put("teamOrder", report.getRevenueDetail().getTeamOrder());
                data.put("revenueDetail", rev);
            }
            if (report.getExpenseDetail() != null) {
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("staffSalary", report.getExpenseDetail().getStaffSalary());
                exp.put("buildingMaintenance", report.getExpenseDetail().getBuildingMaintenance());
                exp.put("adBudget", report.getExpenseDetail().getAdBudget());
                exp.put("eventCost", report.getExpenseDetail().getEventCost());
                exp.put("teamPenalty", report.getExpenseDetail().getTeamPenalty());
                data.put("expenseDetail", exp);
            }
        }
        data.put("dailyReports", dailyLogPayload(GameConfig.REPORT_HISTORY_LIMIT)); // 本局全部每日结算日志
        broadcast("RESORT_STATE", resort);
        broadcast("BANKRUPTCY", data);
        System.out.println("=== 💀 破产！存活 " + resort.getDaysSurvived() + " 天，最终资金 ¥" + resort.getCash() + " ===");
        logToFile("=== 破产：存活 " + resort.getDaysSurvived() + " 天，最终资金 " + y(resort.getCash()) + " ===");
    }

    // ==================================================================
    //  四、待生效指令队列
    // ==================================================================

    /** 把一条操作加入待生效队列（次日 0 点统一生效） */
    private void enqueue(PendingCommand.Type type, String summary,
                         String text1, String text2, double num1, double num2) {
        Resort resort = resortRepository.get();
        // 标量类指令：同类后入覆盖前入
        if (type == PendingCommand.Type.SET_ROOM_PRICE || type == PendingCommand.Type.SET_AD_BUDGET) {
            resort.getPendingCommands().removeIf(c -> c.getType() == type);
        }
        PendingCommand c = new PendingCommand();
        c.setId("cmd-" + UUID.randomUUID().toString().substring(0, 8));
        c.setType(type);
        c.setSummary(summary);
        c.setQueuedDay(resort.getDay());
        c.setText1(text1);
        c.setText2(text2);
        c.setNum1(num1);
        c.setNum2(num2);
        resort.getPendingCommands().add(c);
        broadcast("RESORT_STATE", resort);
    }

    private boolean hasQueued(PendingCommand.Type type, String key) {
        Resort resort = resortRepository.get();
        for (PendingCommand c : resort.getPendingCommands()) {
            if (c.getType() == type && key != null && key.equals(c.getText1())) return true;
        }
        return false;
    }

    /** 次日 0 点批量生效（money 已在点击时扣或本就不扣，此处不再校验余额） */
    private void applyPendingCommands() {
        Resort resort = resortRepository.get();
        List<PendingCommand> queue = new ArrayList<>(resort.getPendingCommands());
        if (queue.isEmpty()) return;
        int nextDay = resort.getDay() + 1;

        for (PendingCommand c : queue) {
            switch (c.getType()) {
                case BUILD_BUILDING -> applyBuild(c);
                case HIRE_STAFF -> applyHire(c);
                case FIRE_STAFF -> resort.getStaffList().removeIf(s -> s.getId().equals(c.getText1()));
                case SET_ROOM_PRICE -> {
                    if (c.getNum1() > 0) resort.setStandardRoomPrice(c.getNum1());
                    if (c.getNum2() > 0) resort.setLuxuryRoomPrice(c.getNum2());
                }
                case SET_AD_BUDGET -> resort.setAdBudget(Math.max(0.0, c.getNum1()));
                case ACCEPT_TEAM_ORDER -> moveOrderToScheduled(c.getText1(), nextDay);
                case REJECT_TEAM_ORDER -> setTeamOrderStatus(c.getText1(), GuestOrder.Status.REJECTED);
                case UNLOCK_PLOT -> {
                    if (c.getText1() != null && !resort.getUnlockedPlots().contains(c.getText1())) {
                        resort.getUnlockedPlots().add(c.getText1());
                    }
                }
            }
        }
        resort.getPendingCommands().clear();
        System.out.println("=== ⚙️ 第 " + resort.getDay() + " 日排队指令生效 " + queue.size() + " 条 ===");
    }

    /** 建造指令生效：把已放置的「施工中」建筑转为正式运营（格子已在点击时预留） */
    private void applyBuild(PendingCommand c) {
        Resort resort = resortRepository.get();
        for (Building b : resort.getBuildings()) {
            if (b.getId().equals(c.getText1())) {
                b.setStatus(Building.STATUS_AVAILABLE);
                return;
            }
        }
    }

    private void applyHire(PendingCommand c) {
        Resort resort = resortRepository.get();
        StaffType type;
        try { type = StaffType.valueOf(c.getText1()); } catch (Exception e) { return; }
        SalaryMode mode = SalaryMode.fromString(c.getText2());

        Staff s = new Staff();
        s.setId("s-" + UUID.randomUUID().toString().substring(0, 8));
        s.setType(type);
        s.setSalaryMode(mode);
        s.setDailySalary(GameConfig.round2(type.getBaseSalary() * mode.getSalaryMultiplier()));
        s.setEfficiencyMultiplier(mode.getEfficiencyMultiplier());
        s.setNegativeEventProb(Math.min(1.0, (type.getBaseNegativeProb() / 100.0) * mode.getNegativeEventMultiplier()));
        int[] cell = randomCell();
        s.setPosition(new Coordinate((float) cell[0], (float) cell[1]));
        resort.getStaffList().add(s);
    }

    /** 把当日接受的团建单转入「已接单·待生效」，并设定生效日 */
    private void moveOrderToScheduled(String orderId, int effectiveDay) {
        Resort resort = resortRepository.get();
        GuestOrder found = null;
        for (GuestOrder o : resort.getTeamOrders()) {
            if (o.getId().equals(orderId)) { found = o; break; }
        }
        if (found == null) return;
        found.setStatus(GuestOrder.Status.ACCEPTED);
        found.setEffectiveDay(effectiveDay);
        resort.getTeamOrders().remove(found);
        resort.getScheduledOrders().add(found);
        logToFile("    团建接受 · [" + found.getId() + "] " + found.getSize().getLabel()
                + " —— 第 " + resort.getDay() + " 天接受，第 " + effectiveDay + " 天生效");
    }

    private void setTeamOrderStatus(String orderId, GuestOrder.Status status) {
        Resort resort = resortRepository.get();
        for (GuestOrder o : resort.getTeamOrders()) {
            if (o.getId().equals(orderId)) { o.setStatus(status); return; }
        }
    }

    /** 取出第 day 天生效的团建单（结算后即从卡片区移出） */
    private List<GuestOrder> settleDueTeamOrders(int day) {
        Resort resort = resortRepository.get();
        List<GuestOrder> due = new ArrayList<>();
        for (GuestOrder o : resort.getScheduledOrders()) {
            if (o.getEffectiveDay() == day) due.add(o);
        }
        if (!due.isEmpty()) resort.getScheduledOrders().removeAll(due);
        return due;
    }

    /** 当日剩余未决团建单：自动拒绝（无罚款） */
    private void rejectStaleTeamOrders() {
        Resort resort = resortRepository.get();
        for (GuestOrder o : resort.getTeamOrders()) {
            if (o.getStatus() == GuestOrder.Status.PENDING) o.setStatus(GuestOrder.Status.REJECTED);
        }
    }

    // ==================================================================
    //  五、区间报告
    // ==================================================================

    /** 按请求区间聚合历史日报（区间非法时回落默认值） */
    public RangeReport aggregateRange(int rangeDays) {
        if (!GameConfig.isValidReportRange(rangeDays)) rangeDays = GameConfig.REPORT_DEFAULT_RANGE;
        List<DailyReport> list = resortRepository.recent(rangeDays);

        double roomFee = 0, dining = 0, recreation = 0, teamRev = 0;
        double salary = 0, maintenance = 0, adCost = 0, eventCost = 0, teamPenalty = 0;
        double revenue = 0, expense = 0, net = 0, occSum = 0;
        int guests = 0, turned = 0, accepted = 0, defaulted = 0;
        Map<String, Integer> eventCounts = new LinkedHashMap<>();
        List<RangeReport.DayPoint> series = new ArrayList<>();
        RangeReport.DayPoint best = null, worst = null;

        for (DailyReport r : list) {
            revenue += nz(r.getTotalRevenue());
            expense += nz(r.getTotalExpense());
            net += nz(r.getNetProfit());
            occSum += nz(r.getOccupancyRate());
            guests += r.getGuestsServed();
            turned += r.getTurnedAway();

            if (r.getRevenueDetail() != null) {
                roomFee += nz(r.getRevenueDetail().getRoomFee());
                dining += nz(r.getRevenueDetail().getDining());
                recreation += nz(r.getRevenueDetail().getRecreation());
                teamRev += nz(r.getRevenueDetail().getTeamOrder());
            }
            if (r.getExpenseDetail() != null) {
                salary += nz(r.getExpenseDetail().getStaffSalary());
                maintenance += nz(r.getExpenseDetail().getBuildingMaintenance());
                adCost += nz(r.getExpenseDetail().getAdBudget());
                eventCost += nz(r.getExpenseDetail().getEventCost());
                teamPenalty += nz(r.getExpenseDetail().getTeamPenalty());
            }
            if (r.getTeamOrderResults() != null) {
                for (DailyReport.TeamOrderResult t : r.getTeamOrderResults()) {
                    if ("SATISFIED".equals(t.getStatus())) accepted++; else defaulted++;
                }
            }
            if (r.getEvents() != null) {
                for (GameEvent e : r.getEvents()) {
                    String key = e.getType() == null ? "其他" : e.getType().getLabel();
                    eventCounts.merge(key, 1, Integer::sum);
                }
            }

            RangeReport.DayPoint p = RangeReport.DayPoint.builder()
                    .day(r.getDay())
                    .seasonName(r.getSeasonName())
                    .netProfit(nz(r.getNetProfit()))
                    .revenue(nz(r.getTotalRevenue()))
                    .expense(nz(r.getTotalExpense()))
                    .occupancyRate(nz(r.getOccupancyRate()))
                    .guestsServed(r.getGuestsServed())
                    .cashAfter(nz(r.getCashAfter()))
                    .reputationAfter(nz(r.getReputationAfter()))
                    .build();
            series.add(p);
            if (best == null || p.getNetProfit() > best.getNetProfit()) best = p;
            if (worst == null || p.getNetProfit() < worst.getNetProfit()) worst = p;
        }

        int dayCount = list.size();
        return RangeReport.builder()
                .rangeDays(rangeDays)
                .fromDay(dayCount > 0 ? list.get(0).getDay() : 0)
                .toDay(dayCount > 0 ? list.get(dayCount - 1).getDay() : 0)
                .dayCount(dayCount)
                .cashStart(dayCount > 0 ? nz(list.get(0).getCashBefore()) : 0.0)
                .cashEnd(dayCount > 0 ? nz(list.get(dayCount - 1).getCashAfter()) : 0.0)
                .netProfit(GameConfig.round2(net))
                .totalRevenue(GameConfig.round2(revenue))
                .totalExpense(GameConfig.round2(expense))
                .revenueDetail(new DailyReport.RevenueDetail(
                        GameConfig.round2(roomFee), GameConfig.round2(dining),
                        GameConfig.round2(recreation), GameConfig.round2(teamRev)))
                .expenseDetail(new DailyReport.ExpenseDetail(
                        GameConfig.round2(salary), GameConfig.round2(maintenance),
                        GameConfig.round2(adCost), GameConfig.round2(eventCost),
                        GameConfig.round2(teamPenalty)))
                .avgOccupancyRate(dayCount > 0 ? GameConfig.round2(occSum / dayCount) : 0.0)
                .totalGuestsServed(guests)
                .totalTurnedAway(turned)
                .reputationStart(dayCount > 0 ? nz(list.get(0).getReputationBefore()) : 0.0)
                .reputationEnd(dayCount > 0 ? nz(list.get(dayCount - 1).getReputationAfter()) : 0.0)
                .reputationDelta(dayCount > 0
                        ? GameConfig.round2(nz(list.get(dayCount - 1).getReputationAfter())
                                            - nz(list.get(0).getReputationBefore()))
                        : 0.0)
                .eventCounts(eventCounts)
                .teamOrdersAccepted(accepted)
                .teamOrdersDefaulted(defaulted)
                .teamOrderRevenue(GameConfig.round2(teamRev))
                .teamPenalty(GameConfig.round2(teamPenalty))
                .dailySeries(series)
                .bestDay(best)
                .worstDay(worst)
                .build();
    }

    /** 只回请求方（破产后仍可查看） */
    public void sendRangeReport(WebSocketSession session, int rangeDays) {
        RangeReport report = aggregateRange(rangeDays);
        try { session.sendMessage(new TextMessage(envelope("RANGE_REPORT", report))); }
        catch (Exception ignored) {}
    }

    /** 每日结算日志载荷：逐日精简结算详情（收入/支出/口碑/事件/团建），最多近 limit 天 */
    private List<Map<String, Object>> dailyLogPayload(int limit) {
        List<DailyReport> list = resortRepository.recent(limit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DailyReport r : list) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("day", r.getDay());
            d.put("seasonName", r.getSeasonName());
            d.put("netProfit", nz(r.getNetProfit()));
            d.put("cashAfter", nz(r.getCashAfter()));
            d.put("totalRevenue", nz(r.getTotalRevenue()));
            d.put("totalExpense", nz(r.getTotalExpense()));
            d.put("occupancyRate", nz(r.getOccupancyRate()));
            d.put("guestsServed", r.getGuestsServed());
            d.put("turnedAway", r.getTurnedAway());
            d.put("reputationBefore", nz(r.getReputationBefore()));
            d.put("reputationAfter", nz(r.getReputationAfter()));
            d.put("reputationDelta", nz(r.getReputationDelta()));
            if (r.getRevenueDetail() != null) {
                Map<String, Object> rev = new LinkedHashMap<>();
                rev.put("roomFee", nz(r.getRevenueDetail().getRoomFee()));
                rev.put("dining", nz(r.getRevenueDetail().getDining()));
                rev.put("recreation", nz(r.getRevenueDetail().getRecreation()));
                rev.put("teamOrder", nz(r.getRevenueDetail().getTeamOrder()));
                d.put("revenueDetail", rev);
            }
            if (r.getExpenseDetail() != null) {
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("staffSalary", nz(r.getExpenseDetail().getStaffSalary()));
                exp.put("buildingMaintenance", nz(r.getExpenseDetail().getBuildingMaintenance()));
                exp.put("adBudget", nz(r.getExpenseDetail().getAdBudget()));
                exp.put("eventCost", nz(r.getExpenseDetail().getEventCost()));
                exp.put("teamPenalty", nz(r.getExpenseDetail().getTeamPenalty()));
                d.put("expenseDetail", exp);
            }
            List<String> events = new ArrayList<>();
            if (r.getEvents() != null) for (GameEvent e : r.getEvents()) events.add(e.getDescription());
            d.put("events", events);
            List<String> teamOrders = new ArrayList<>();
            if (r.getTeamOrderResults() != null) {
                for (DailyReport.TeamOrderResult t : r.getTeamOrderResults()) {
                    if ("SATISFIED".equals(t.getStatus())) {
                        teamOrders.add("[" + t.getOrderId() + "] " + sizeLabel(t.getSize()) + " 履约 +" + y(t.getRevenue()));
                    } else {
                        teamOrders.add("[" + t.getOrderId() + "] " + sizeLabel(t.getSize()) + " 违约 -" + y(t.getPenalty())
                                + (t.getReason() != null ? "（" + t.getReason() + "）" : ""));
                    }
                }
            }
            d.put("teamOrders", teamOrders);
            out.add(d);
        }
        return out;
    }

    /** 玩家主动请求完整日志：只回请求方 */
    public void sendDailyLog(WebSocketSession session) {
        try { session.sendMessage(new TextMessage(envelope("DAILY_LOG", dailyLogPayload(GameConfig.REPORT_HISTORY_LIMIT)))); }
        catch (Exception ignored) {}
    }

    private double nz(Double v) { return v == null ? 0.0 : v; }

    // ==================================================================
    //  六、经营操作（随时可用；全部转成「待生效指令」次日 0 点生效）
    // ==================================================================

    /** 建造：费用点击即扣，立即落地一座「施工中」幽灵建筑，次日 0 点转为正式运营 */
    public void buildBuilding(BuildingType type, String plotId) {
        if (guard("BUILD_BUILDING")) return;
        Resort resort = resortRepository.get();
        if (plotId == null || !resort.getUnlockedPlots().contains(plotId)) { fail("BUILD_BUILDING", "PLOT_NOT_UNLOCKED"); return; }
        if (resort.getCash() < type.getBuildCost()) { fail("BUILD_BUILDING", "INSUFFICIENT_FUNDS"); return; }
        if (countInPlot(plotId) >= GameConfig.buildingLimitOf(plotId)) { fail("BUILD_BUILDING", "PLOT_LIMIT_EXCEEDED"); return; }
        if (countFreeCellsInPlot(plotId) <= 0) { fail("BUILD_BUILDING", "NO_SPACE"); return; }
        BuildCell cell = pickFreeCell(plotId);
        if (cell == null) { fail("BUILD_BUILDING", "NO_SPACE"); return; }

        resort.setCash(GameConfig.round2(resort.getCash() - type.getBuildCost())); // 立即扣费
        // 立即放置「施工中」建筑并预留格子，玩家马上在地图看到施工幽灵；次日 0 点转正式
        Building b = new Building();
        b.setId("b-" + UUID.randomUUID().toString().substring(0, 8));
        b.setType(type);
        b.setPlotId(plotId);
        b.setPosition(new Coordinate((float) cell.x, (float) cell.y));
        b.setStatus(Building.STATUS_UNDER_CONSTRUCTION);
        resort.getBuildings().add(b);
        occupiedCells.add(cell.x + "," + cell.y);

        enqueue(PendingCommand.Type.BUILD_BUILDING,
                "建造 " + type.getLabel() + "（" + plotLabel(plotId) + "）",
                b.getId(), plotId, 0, 0);
    }

    /** 招聘：只入队，工资日结扣，次日 0 点上岗 */
    public void hireStaff(StaffType type, String salaryModeStr) {
        if (guard("HIRE_STAFF")) return;
        SalaryMode mode = SalaryMode.fromString(salaryModeStr);
        enqueue(PendingCommand.Type.HIRE_STAFF,
                "招聘 " + type.getLabel() + "（" + salaryModeLabel(mode) + "）",
                type.name(), salaryModeStr == null ? "normal" : salaryModeStr, 0, 0);
    }

    /** 解雇：只入队，次日 0 点离岗 */
    public void fireStaff(String staffId) {
        if (guard("FIRE_STAFF")) return;
        Resort resort = resortRepository.get();
        boolean exists = resort.getStaffList().stream().anyMatch(s -> s.getId().equals(staffId));
        if (!exists) { fail("FIRE_STAFF", "STAFF_NOT_FOUND"); return; }
        enqueue(PendingCommand.Type.FIRE_STAFF, "解雇员工 " + staffId, staffId, null, 0, 0);
    }

    public void setRoomPrice(double standardPrice, double luxuryPrice) {
        if (guard("SET_ROOM_PRICE")) return;
        if (standardPrice <= 0 && luxuryPrice <= 0) { fail("SET_ROOM_PRICE", "INVALID_PRICE"); return; }
        enqueue(PendingCommand.Type.SET_ROOM_PRICE,
                "调整房价：普通 ¥" + (long) standardPrice + " / 豪华 ¥" + (long) luxuryPrice,
                null, null, standardPrice, luxuryPrice);
    }

    public void setAdBudget(double adBudget) {
        if (guard("SET_AD_BUDGET")) return;
        double v = Math.max(0.0, adBudget);
        enqueue(PendingCommand.Type.SET_AD_BUDGET, "广告预算 ¥" + (long) v, null, null, v, 0);
    }

    /** 解锁地块：费用点击即扣，地块次日 0 点可供建造 */
    public void unlockPlot(String plotId) {
        if (guard("UNLOCK_PLOT")) return;
        Resort resort = resortRepository.get();
        if (resort.getUnlockedPlots().contains(plotId)) { fail("UNLOCK_PLOT", "ALREADY_UNLOCKED"); return; }
        if (hasQueued(PendingCommand.Type.UNLOCK_PLOT, plotId)) { fail("UNLOCK_PLOT", "ALREADY_QUEUED"); return; }
        double cost;
        if (GameConfig.PLOT_MOUNTAIN.equals(plotId)) cost = GameConfig.LAND_MOUNTAIN_COST;
        else if (GameConfig.PLOT_LAKESIDE.equals(plotId)) cost = GameConfig.LAND_LAKESIDE_COST;
        else { fail("UNLOCK_PLOT", "UNKNOWN_PLOT"); return; }
        if (resort.getCash() < cost) { fail("UNLOCK_PLOT", "INSUFFICIENT_FUNDS"); return; }
        resort.setCash(GameConfig.round2(resort.getCash() - cost)); // 立即扣费
        enqueue(PendingCommand.Type.UNLOCK_PLOT, "解锁 " + plotLabel(plotId), plotId, null, 0, 0);
    }

    /** 接受团建单：入队，次日 0 点置 ACCEPTED 并在次日日结时判定 */
    public void acceptTeamOrder(String orderId) {
        if (guard("ACCEPT_TEAM_ORDER")) return;
        GuestOrder order = findTeamOrder(orderId);
        if (order == null) { fail("ACCEPT_TEAM_ORDER", "ORDER_NOT_FOUND"); return; }
        if (order.getStatus() != GuestOrder.Status.PENDING) { fail("ACCEPT_TEAM_ORDER", "ORDER_NOT_PENDING"); return; }
        if (hasQueued(PendingCommand.Type.ACCEPT_TEAM_ORDER, orderId)) { fail("ACCEPT_TEAM_ORDER", "ALREADY_QUEUED"); return; }
        enqueue(PendingCommand.Type.ACCEPT_TEAM_ORDER,
                "接受 " + order.getSize().getLabel() + "（" + order.getRequiredRooms() + " 间房）",
                orderId, order.getSize().name(), 0, 0);
    }

    /** 拒绝团建单：入队，次日 0 点生效（不操作也会在次日 0 点自动拒绝） */
    public void rejectTeamOrder(String orderId) {
        if (guard("REJECT_TEAM_ORDER")) return;
        GuestOrder order = findTeamOrder(orderId);
        if (order == null) { fail("REJECT_TEAM_ORDER", "ORDER_NOT_FOUND"); return; }
        if (order.getStatus() != GuestOrder.Status.PENDING) { fail("REJECT_TEAM_ORDER", "ORDER_NOT_PENDING"); return; }
        if (hasQueued(PendingCommand.Type.REJECT_TEAM_ORDER, orderId)) { fail("REJECT_TEAM_ORDER", "ALREADY_QUEUED"); return; }
        enqueue(PendingCommand.Type.REJECT_TEAM_ORDER,
                "拒绝 " + order.getSize().getLabel(), orderId, order.getSize().name(), 0, 0);
    }

    private GuestOrder findTeamOrder(String orderId) {
        Resort resort = resortRepository.get();
        if (resort == null) return null;
        for (GuestOrder o : resort.getTeamOrders()) {
            if (o.getId().equals(orderId)) return o;
        }
        return null;
    }

    /** 运行期允许操作：只要本局存在且未结束 */
    private boolean guard(String action) {
        Resort resort = resortRepository.get();
        if (resort == null || resort.isGameOver()) {
            fail(action, "GAME_NOT_RUNNING");
            return true;
        }
        return false;
    }

    private void fail(String action, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("reason", reason);
        broadcast("ACTION_FAILED", data);
    }

    // ==================================================================
    //  七、当日结算（经营数值全部在此计算）
    // ==================================================================

    private DailyReport computeDailyReport(int day, List<GuestOrder> dueTeamOrders) {
        Resort resort = resortRepository.get();

        // ---- ① 随机事件 ----
        List<GameEvent> events = rollEvents(day);
        boolean rain = hasEvent(events, EventType.RAINSTORM);
        boolean influencer = hasEvent(events, EventType.INFLUENCER);
        boolean returning = hasEvent(events, EventType.RETURNING_GUEST);
        boolean complaint = hasEvent(events, EventType.COMPLAINT);
        double recreationMult = rain ? GameConfig.RAINSTORM_RECREATION_MULT : 1.0;
        double demandMult = (influencer ? GameConfig.INFLUENCER_DEMAND_MULT : 1.0)
                          * (returning ? GameConfig.RETURNING_GUEST_DEMAND_MULT : 1.0);

        // ---- ② 员工负面事件 ----
        boolean frontErr = false, cleanErr = false, chefErr = false, maintErr = false;
        for (Staff s : resort.getStaffList()) {
            if (random.nextDouble() < s.getNegativeEventProb()) {
                switch (s.getType()) {
                    case FRONT_DESK -> frontErr = true;
                    case CLEANING -> cleanErr = true;
                    case CHEF -> chefErr = true;
                    case MAINTENANCE -> maintErr = true;
                }
            }
        }
        if (frontErr) events.add(staffEvent(day, "前台服务失误：当日入住率 -" + pct(GameConfig.STAFF_NEG_FRONT_OCCUPANCY_PENALTY)));
        if (cleanErr) events.add(staffEvent(day, "保洁工作失误：口碑 -" + (long) GameConfig.STAFF_NEG_CLEANING_REP_PENALTY));
        if (chefErr) events.add(staffEvent(day, "厨师出餐失误：当日餐饮收入 -" + pct(1 - GameConfig.STAFF_NEG_CHEF_DINING_MULT)));
        if (maintErr) events.add(staffEvent(day, "维修延误：设施故障维修 " + y(GameConfig.STAFF_NEG_MAINT_FAULT_COST)));
        double occupancyFactor = frontErr ? (1.0 - GameConfig.STAFF_NEG_FRONT_OCCUPANCY_PENALTY) : 1.0;

        // ---- ③ 容量 ----
        int stdRooms = countUsable(BuildingType.STANDARD_ROOM);
        int luxRooms = countUsable(BuildingType.LUXURY_SUITE);

        // ---- ④ 团建单结算（大单优先占用客房；不满足则违约罚款） ----
        List<GuestOrder> accepted = new ArrayList<>(dueTeamOrders);
        accepted.sort(Comparator.comparingInt(GuestOrder::getRequiredRooms).reversed());

        double teamRevenue = 0.0, teamPenalty = 0.0;
        int reservedRooms = 0;
        int availableRooms = stdRooms + luxRooms;
        List<DailyReport.TeamOrderResult> teamResults = new ArrayList<>();

        for (GuestOrder o : accepted) {
            boolean hasRestaurant = countUsable(BuildingType.RESTAURANT) > 0 && hasStaff(StaffType.CHEF);
            boolean hasSpa = countUsable(BuildingType.SPA) > 0 && hasStaff(StaffType.MAINTENANCE);
            boolean hasViewpoint = countUsable(BuildingType.VIEWPOINT) > 0;
            boolean compat = (!o.isRequiresRestaurant() || hasRestaurant)
                          && (!o.isRequiresSpa() || hasSpa)
                          && (!o.isRequiresViewpoint() || hasViewpoint);
            boolean ok = compat && (availableRooms - reservedRooms) >= o.getRequiredRooms();
            if (ok) {
                reservedRooms += o.getRequiredRooms();
                teamRevenue += o.getRevenue();
                o.setStatus(GuestOrder.Status.CHECK_IN);
                teamResults.add(new DailyReport.TeamOrderResult(o.getId(), o.getSize().name(), "SATISFIED", o.getRevenue(), 0.0, null));
            } else {
                List<String> miss = new ArrayList<>();
                if (o.isRequiresRestaurant() && !hasRestaurant) miss.add("餐厅(需厨师)");
                if (o.isRequiresSpa() && !hasSpa) miss.add("温泉池(需维修)");
                if (o.isRequiresViewpoint() && !hasViewpoint) miss.add("观景台");
                String reason = !miss.isEmpty()
                        ? "缺 " + String.join("、", miss)
                        : "客房不足（可用 " + (availableRooms - reservedRooms) + " 间，需 " + o.getRequiredRooms() + " 间）";
                teamPenalty += o.getPenalty();
                o.setStatus(GuestOrder.Status.DEFAULTED);
                teamResults.add(new DailyReport.TeamOrderResult(o.getId(), o.getSize().name(), "DEFAULTED", 0.0, o.getPenalty(), reason));
            }
        }

        // 团建单优先占用普通客房
        int remStd = stdRooms, remLux = luxRooms, toConsume = reservedRooms;
        int useStd = Math.min(remStd, toConsume); remStd -= useStd; toConsume -= useStd;
        int useLux = Math.min(remLux, toConsume); remLux -= useLux;

        // ---- ⑤ 散客需求 ----
        double demand = computeBaseDemand() * demandMult * occupancyFactor;
        int roomCap = remStd * 2 + remLux * 4;
        int parkingCap = countUsable(BuildingType.PARKING) * BuildingType.PARKING.getCapacity();

        int overnight = (int) Math.min(demand, roomCap);
        int dayVisitors = (int) Math.min(Math.max(0, demand - overnight), parkingCap);
        int turnedAway = (int) Math.max(0, demand - overnight - dayVisitors); // 溢出：不接待、不扣口碑

        int luxGuests = Math.min((int) Math.round(overnight * GameConfig.LUX_GUEST_PREFERENCE), remLux * 4);
        int stdGuests = Math.min(overnight - luxGuests, remStd * 2);
        int luxUsed = (int) Math.ceil(luxGuests / 4.0);
        int stdUsed = (int) Math.ceil(stdGuests / 2.0);
        double roomFee = stdUsed * resort.getStandardRoomPrice() + luxUsed * resort.getLuxuryRoomPrice();

        // ---- ⑥ 餐饮 / 游乐 ----
        int guestsOnSite = overnight + dayVisitors;
        int diners = (hasStaff(StaffType.CHEF) && countUsable(BuildingType.RESTAURANT) > 0)
                ? Math.min(guestsOnSite, countUsable(BuildingType.RESTAURANT) * BuildingType.RESTAURANT.getCapacity()) : 0;
        double dining = diners * GameConfig.DINING_SPEND_PER_GUEST;
        if (chefErr) dining *= GameConfig.STAFF_NEG_CHEF_DINING_MULT;

        int spaCap = hasStaff(StaffType.MAINTENANCE) ? countUsable(BuildingType.SPA) * BuildingType.SPA.getCapacity() : 0;
        int viewCap = countUsable(BuildingType.VIEWPOINT) * BuildingType.VIEWPOINT.getCapacity();
        int recVisitors = Math.min(guestsOnSite, spaCap + viewCap);
        double recreation = recVisitors * GameConfig.RECREATION_SPEND_PER_GUEST * recreationMult;

        // ---- ⑦ 支出 ----
        double staffSalary = 0.0;
        for (Staff s : resort.getStaffList()) staffSalary += s.getDailySalary();
        double maintenance = 0.0;
        for (Building b : resort.getBuildings()) {
            if (b.getStatus() == Building.STATUS_UNDER_CONSTRUCTION) continue; // 施工中不计维护费
            maintenance += b.getType().getDailyMaintenance();
        }
        double eventCost = sumEventCosts(events) + (maintErr ? GameConfig.STAFF_NEG_MAINT_FAULT_COST : 0.0);

        // ---- ⑧ 汇总 ----
        double totalRevenue = roomFee + dining + recreation + teamRevenue;
        double totalExpense = staffSalary + maintenance + resort.getAdBudget() + eventCost + teamPenalty;
        double netProfit = totalRevenue - totalExpense;
        if (complaint) netProfit *= GameConfig.COMPLAINT_PROFIT_MULT;

        // ---- ⑨ 口碑（含被动自然涨口碑）----
        // 被动口碑：高入住率 / 前台+保洁+厨师+维修 齐备 / 当日无负向随机事件
        double occFrac = roomCap > 0 ? ((double) overnight / roomCap) : 0.0;
        boolean highOccupancy = occFrac >= GameConfig.REP_PASSIVE_OCCUPANCY_THRESHOLD;
        boolean staffComplete = hasStaff(StaffType.FRONT_DESK) && hasStaff(StaffType.CLEANING)
                && hasStaff(StaffType.CHEF) && hasStaff(StaffType.MAINTENANCE);
        boolean noNegativeEvent = !rain && !hasEvent(events, EventType.PIPE_BURST) && !complaint;
        double passiveRep = Math.min(
                (highOccupancy ? GameConfig.REP_PASSIVE_OCCUPANCY_BONUS : 0.0)
                        + (staffComplete ? GameConfig.REP_PASSIVE_STAFF_BONUS : 0.0)
                        + (noNegativeEvent ? GameConfig.REP_PASSIVE_QUIET_BONUS : 0.0),
                GameConfig.REP_PASSIVE_MAX);
        if (passiveRep > 0) {
            List<String> why = new ArrayList<>();
            if (highOccupancy) why.add("高入住率");
            if (staffComplete) why.add("员工齐备");
            if (noNegativeEvent) why.add("无负向事件");
            Map<String, Object> pe = new HashMap<>();
            pe.put("repDelta", passiveRep);
            events.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.PASSIVE_REP,
                    day, "口碑自然提升：+" + (long) passiveRep + "（" + String.join("、", why) + "）", pe));
        }

        double repDelta = 0.0;
        for (GameEvent e : events) {
            Object rd = e.getEffects().get("repDelta");
            if (rd instanceof Number) repDelta += ((Number) rd).doubleValue();
        }
        if (cleanErr) repDelta -= GameConfig.STAFF_NEG_CLEANING_REP_PENALTY;
        repDelta -= cleaningShortagePenalty(stdRooms + luxRooms);

        // ---- ⑩ 组装日报 ----
        double occupancyRate = roomCap > 0 ? (overnight * 100.0 / roomCap) : 0.0;
        return DailyReport.builder()
                .day(day)
                .season(resort.getSeason())
                .seasonName(resort.getSeason().getLabel())
                .cashBefore(resort.getCash())
                .cashAfter(GameConfig.round2(resort.getCash() + netProfit))
                .netProfit(GameConfig.round2(netProfit))
                .totalRevenue(GameConfig.round2(totalRevenue))
                .totalExpense(GameConfig.round2(totalExpense))
                .revenueDetail(new DailyReport.RevenueDetail(
                        GameConfig.round2(roomFee), GameConfig.round2(dining),
                        GameConfig.round2(recreation), GameConfig.round2(teamRevenue)))
                .expenseDetail(new DailyReport.ExpenseDetail(
                        GameConfig.round2(staffSalary), GameConfig.round2(maintenance),
                        GameConfig.round2(resort.getAdBudget()), GameConfig.round2(eventCost),
                        GameConfig.round2(teamPenalty)))
                .occupancyRate(GameConfig.round2(occupancyRate))
                .guestsServed(guestsOnSite)
                .turnedAway(turnedAway)
                .reputationBefore(resort.getReputation())
                .reputationAfter(GameConfig.round2(GameConfig.clamp(
                        resort.getReputation() + repDelta, GameConfig.REPUTATION_MIN, GameConfig.REPUTATION_MAX)))
                .reputationDelta(GameConfig.round2(repDelta))
                .events(events)
                .teamOrderResults(teamResults)
                .build();
    }

    /** 散客基础需求（不含当日事件与员工失误系数） */
    private double computeBaseDemand() {
        Resort resort = resortRepository.get();
        double season = resort.getSeason().getTrafficCoefficient();
        double adFactor = 1.0 + Math.min(GameConfig.AD_TRAFFIC_CAP,
                (resort.getAdBudget() / 1000.0) * GameConfig.AD_TRAFFIC_PER_1000);
        double repFactor = Math.max(0.2,
                1.0 + (resort.getReputation() - GameConfig.REP_BASELINE) * GameConfig.REP_TRAFFIC_PER_POINT);
        if (resort.getReputation() < GameConfig.LOW_REP_THRESHOLD) repFactor *= GameConfig.LOW_REP_DECAY_FACTOR;
        double frontFactor = hasStaff(StaffType.FRONT_DESK) ? 1.0 : GameConfig.NO_FRONT_DESK_FACTOR;
        return GameConfig.BASE_GUESTS_PER_DAY * season * adFactor * repFactor * frontFactor
                * priceFactor(resort);
    }

    /**
     * 房价需求系数：按「0.7×普通房价 + 0.3×豪华房价」与实际定价相对基准价的偏离，
     * 提价客流降、降价客流升（钳制在 [PRICE_FACTOR_MIN, PRICE_FACTOR_MAX]）。
     */
    private double priceFactor(Resort resort) {
        double luxW = GameConfig.LUX_GUEST_PREFERENCE, stdW = 1.0 - luxW;
        double blendedPrice = stdW * resort.getStandardRoomPrice() + luxW * resort.getLuxuryRoomPrice();
        double blendedBase  = stdW * GameConfig.BASE_STANDARD_ROOM_PRICE + luxW * GameConfig.BASE_LUXURY_ROOM_PRICE; // 400
        double ratio = blendedBase > 0 ? blendedPrice / blendedBase : 1.0;
        double factor = 1.0 - GameConfig.PRICE_ELASTICITY * (ratio - 1.0);
        return GameConfig.clamp(factor, GameConfig.PRICE_FACTOR_MIN, GameConfig.PRICE_FACTOR_MAX);
    }

    /** 按概率触发当日随机事件（自动执行，玩家无法干预） */
    private List<GameEvent> rollEvents(int day) {
        List<GameEvent> list = new ArrayList<>();

        if (random.nextDouble() < GameConfig.EVENT_RAINSTORM_PROB) {
            Map<String, Object> e = new HashMap<>();
            e.put("recreationMult", GameConfig.RAINSTORM_RECREATION_MULT);
            e.put("repDelta", GameConfig.RAINSTORM_REP_DELTA);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.RAINSTORM,
                    day, "暴雨：温泉/观景台当日收益 -" + pct(1 - GameConfig.RAINSTORM_RECREATION_MULT) + "，口碑 " + (long) GameConfig.RAINSTORM_REP_DELTA, e));
        }
        if (random.nextDouble() < GameConfig.EVENT_PIPE_BURST_PROB) {
            // 随机停用 3 间客房（当日不可用，次日自动恢复）
            List<Building> rooms = new ArrayList<>();
            for (Building b : resortRepository.get().getBuildings()) {
                if (b.getType().isRoom() && b.getStatus() == Building.STATUS_AVAILABLE) rooms.add(b);
            }
            Collections.shuffle(rooms, random);
            int k = Math.min(GameConfig.PIPE_BURST_DISABLED_ROOMS, rooms.size());
            for (int i = 0; i < k; i++) rooms.get(i).setStatus(Building.STATUS_DISABLED);
            Map<String, Object> e = new HashMap<>();
            e.put("cost", GameConfig.PIPE_BURST_COST);
            e.put("disabledRooms", k);
            e.put("repDelta", GameConfig.PIPE_BURST_REP_DELTA);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.PIPE_BURST,
                    day, "水管爆裂：维修 " + y(GameConfig.PIPE_BURST_COST) + "，" + k + " 间客房不可用，口碑 " + (long) GameConfig.PIPE_BURST_REP_DELTA, e));
        }
        if (random.nextDouble() < GameConfig.EVENT_INFLUENCER_PROB) {
            Map<String, Object> e = new HashMap<>();
            e.put("demandMult", GameConfig.INFLUENCER_DEMAND_MULT);
            e.put("repDelta", GameConfig.INFLUENCER_REP_DELTA);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.INFLUENCER,
                    day, "网红打卡：散客客流 ×" + GameConfig.INFLUENCER_DEMAND_MULT + "，口碑 +" + (long) GameConfig.INFLUENCER_REP_DELTA, e));
        }
        if (random.nextDouble() < GameConfig.EVENT_COMPLAINT_PROB) {
            Map<String, Object> e = new HashMap<>();
            e.put("repDelta", GameConfig.COMPLAINT_REP_DELTA);
            e.put("profitMult", GameConfig.COMPLAINT_PROFIT_MULT);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.COMPLAINT,
                    day, "游客集体投诉：口碑 " + (long) GameConfig.COMPLAINT_REP_DELTA + "，当日净利润 -" + pct(1 - GameConfig.COMPLAINT_PROFIT_MULT), e));
        }
        if (random.nextDouble() < GameConfig.EVENT_LOCAL_TOUR_PROB) {
            // 本地旅游团到访：不自动接单，改为次日推送 1 笔可见的小型团建单，由玩家自行接/拒
            GuestOrder extra = buildTeamOrder(TeamOrderSize.SMALL);
            bonusTeamOrder = extra;
            Map<String, Object> e = new HashMap<>();
            e.put("extraTeamSize", "SMALL");
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.LOCAL_TOUR,
                    day, "本地旅游团到访：次日新增 1 笔小型团建订单，可在订单区自行接/拒", e));
        }
        if (random.nextDouble() < GameConfig.EVENT_MINOR_FAULT_PROB) {
            Map<String, Object> e = new HashMap<>();
            e.put("cost", GameConfig.MINOR_FAULT_COST);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.MINOR_FAULT,
                    day, "设施小故障：维修 " + y(GameConfig.MINOR_FAULT_COST), e));
        }
        if (random.nextDouble() < GameConfig.EVENT_POSITIVE_REVIEW_PROB) {
            Map<String, Object> e = new HashMap<>();
            e.put("repDelta", GameConfig.POSITIVE_REVIEW_REP_DELTA);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.POSITIVE_REVIEW,
                    day, "好评如潮：口碑 +" + (long) GameConfig.POSITIVE_REVIEW_REP_DELTA, e));
        }
        if (random.nextDouble() < GameConfig.EVENT_RETURNING_GUEST_PROB) {
            Map<String, Object> e = new HashMap<>();
            e.put("demandMult", GameConfig.RETURNING_GUEST_DEMAND_MULT);
            e.put("repDelta", GameConfig.RETURNING_GUEST_REP_DELTA);
            list.add(new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.RETURNING_GUEST,
                    day, "回头客推荐：散客客流 ×" + GameConfig.RETURNING_GUEST_DEMAND_MULT + "，口碑 +" + (long) GameConfig.RETURNING_GUEST_REP_DELTA, e));
        }
        return list;
    }

    private GameEvent staffEvent(int day, String description) {
        return new GameEvent("ev-" + UUID.randomUUID().toString().substring(0, 8), EventType.STAFF_ERROR,
                day, description, new HashMap<>());
    }

    private double sumEventCosts(List<GameEvent> events) {
        double sum = 0.0;
        for (GameEvent e : events) {
            Object c = e.getEffects().get("cost");
            if (c instanceof Number) sum += ((Number) c).doubleValue();
        }
        return sum;
    }

    private boolean hasEvent(List<GameEvent> events, EventType type) {
        for (GameEvent e : events) if (e.getType() == type) return true;
        return false;
    }

    /** 保洁不足的口碑惩罚（返回正数，调用处以减法计入） */
    private double cleaningShortagePenalty(int roomUnits) {
        if (roomUnits <= 0) return 0.0;
        double required = Math.ceil(roomUnits / GameConfig.CLEANING_ROOMS_PER_STAFF);
        double effective = 0.0;
        for (Staff s : resortRepository.get().getStaffList()) {
            if (s.getType() == StaffType.CLEANING) effective += s.getEfficiencyMultiplier();
        }
        return effective < required ? GameConfig.CLEANING_SHORTAGE_REP_PENALTY : 0.0;
    }

    // ==================================================================
    //  八、辅助
    // ==================================================================

    private TeamOrderSize randomTeamSize() {
        TeamOrderSize[] all = TeamOrderSize.values();
        return all[random.nextInt(all.length)];
    }

    private GuestOrder buildTeamOrder(TeamOrderSize size) {
        Resort resort = resortRepository.get();
        GuestOrder o = new GuestOrder();
        o.setId("team-" + UUID.randomUUID().toString().substring(0, 8));
        o.setOrderType(GuestOrder.OrderType.TEAM);
        o.setStatus(GuestOrder.Status.PENDING);
        o.setSize(size);
        o.setRequiredRooms(size.getRequiredRooms());
        o.setGuestCount(size.getRequiredRooms() * 2);
        o.setRevenue(size.getRevenue());
        o.setPenalty(size.getPenalty());
        o.setRequiresRestaurant(size.isRequiresRestaurant());
        o.setRequiresSpa(size.isRequiresSpa());
        o.setRequiresViewpoint(size.isRequiresViewpoint());
        o.setCreatedDay(resort.getDay());
        return o;
    }

    private String plotLabel(String plotId) {
        if (GameConfig.PLOT_MOUNTAIN.equals(plotId)) return "山间地块";
        if (GameConfig.PLOT_LAKESIDE.equals(plotId)) return "湖畔地块";
        return "初始地块";
    }

    private String salaryModeLabel(SalaryMode mode) {
        switch (mode) {
            case HIGH: return "高薪";
            case LOW:  return "廉价";
            default:   return "标准";
        }
    }

    private int countUsable(BuildingType type) {
        Resort resort = resortRepository.get();
        if (resort == null) return 0;
        int c = 0;
        for (Building b : resort.getBuildings()) {
            if (b.getType() == type && b.getStatus() == Building.STATUS_AVAILABLE) c++;
        }
        return c;
    }

    private int countInPlot(String plotId) {
        Resort resort = resortRepository.get();
        int c = 0;
        for (Building b : resort.getBuildings()) {
            if (plotId.equals(b.getPlotId())) c++;
        }
        return c;
    }

    private int countFreeCellsInPlot(String plotId) {
        int c = 0;
        for (BuildCell cell : buildableCells) {
            if (!plotId.equals(cell.plotId)) continue;
            if (!occupiedCells.contains(cell.x + "," + cell.y)) c++;
        }
        return c;
    }

    private boolean hasStaff(StaffType type) {
        Resort resort = resortRepository.get();
        if (resort == null) return false;
        for (Staff s : resort.getStaffList()) if (s.getType() == type) return true;
        return false;
    }

    /** 在指定地块内挑选一个空闲可建造格（plotId 为空则不限地块） */
    private BuildCell pickFreeCell(String plotId) {
        List<BuildCell> free = new ArrayList<>();
        for (BuildCell c : buildableCells) {
            if (plotId != null && !plotId.isEmpty() && !plotId.equals(c.plotId)) continue;
            if (!occupiedCells.contains(c.x + "," + c.y)) free.add(c);
        }
        if (free.isEmpty()) return null;
        return free.get(random.nextInt(free.size()));
    }

    private int[] randomCell() {
        if (buildableCells.isEmpty()) return new int[]{0, 0};
        BuildCell c = buildableCells.get(random.nextInt(buildableCells.size()));
        return new int[]{c.x, c.y};
    }

    /** 下发可视化规划：游客数量（全量渲染）/ 员工数量 */
    private void writeNpcPlan() {
        try {
            Map<String, Object> plan = new HashMap<>();
            plan.put("guests", (int) Math.round(computeBaseDemand()));
            plan.put("staff", resortRepository.get().getStaffList().size());
            redisTemplate.opsForValue().set(KEY_NPC_PLAN, objectMapper.writeValueAsString(plan));
        } catch (Exception ignored) {}
    }

    /** 将建筑/员工锚点写入 Redis，供 Python 做纯可视化寻路 */
    private void syncResortToRedis() {
        Resort resort = resortRepository.get();
        if (resort == null) return;
        try {
            Map<String, String> buildings = new HashMap<>();
            for (Building b : resort.getBuildings()) {
                if (b.getPosition() == null) continue;
                if (b.getStatus() == Building.STATUS_UNDER_CONSTRUCTION) continue; // 施工中不做寻路锚点
                Map<String, Object> v = new HashMap<>();
                v.put("type", b.getType().name());
                v.put("x", b.getPosition().getX());
                v.put("y", b.getPosition().getY());
                buildings.put(b.getId(), objectMapper.writeValueAsString(v));
            }
            Map<String, String> staff = new HashMap<>();
            for (Staff s : resort.getStaffList()) {
                if (s.getPosition() == null) continue;
                Map<String, Object> v = new HashMap<>();
                v.put("type", s.getType().name());
                v.put("x", s.getPosition().getX());
                v.put("y", s.getPosition().getY());
                staff.put(s.getId(), objectMapper.writeValueAsString(v));
            }
            redisTemplate.delete(Arrays.asList(KEY_RESORT_BUILD, KEY_RESORT_STAFF));
            if (!buildings.isEmpty()) redisTemplate.<String, String>opsForHash().putAll(KEY_RESORT_BUILD, buildings);
            if (!staff.isEmpty()) redisTemplate.<String, String>opsForHash().putAll(KEY_RESORT_STAFF, staff);
        } catch (Exception e) {
            System.err.println("同步 Redis 锚点失败: " + e.getMessage());
        }
    }

    /** 10Hz → 5Hz：将 Python 计算的 NPC 坐标转发给前端 */
    @Scheduled(fixedRate = 200)
    public void syncFromPython() {
        Resort resort = resortRepository.get();
        if (!running || paused || resort == null) return;
        try {
            String npc = redisTemplate.opsForValue().get(KEY_NPC_STATE);
            if (npc != null && !npc.isEmpty()) {
                webSocketHandler.broadcastMessage("{\"type\":\"NPC_UPDATE\",\"data\":" + npc + "}");
            }
        } catch (Exception ignored) {}
    }

    /** 新连接会话全量同步 */
    public void syncStateToSession(WebSocketSession session) {
        try {
            Resort resort = resortRepository.get();
            if (!running || resort == null) return;
            session.sendMessage(new TextMessage(envelope("RESORT_STATE", resort)));
            Map<String, Object> tick = buildTimeTick(); // 刷新页面后时钟可立即恢复
            if (tick != null) session.sendMessage(new TextMessage(envelope("TIME_TICK", tick)));
            if (!resort.getTeamOrders().isEmpty()) {
                session.sendMessage(new TextMessage(envelope("TEAM_ORDERS_OFFERED", resort.getTeamOrders())));
            }
        } catch (Exception ignored) {}
    }

    private String toJson(Object o) {
        if (o == null) return "null";
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "null"; }
    }

    private String envelope(String type, Object data) {
        return "{\"type\":\"" + type + "\",\"data\":" + toJson(data) + "}";
    }

    private void broadcast(String type, Object data) {
        webSocketHandler.broadcastMessage(envelope(type, data));
    }
}
