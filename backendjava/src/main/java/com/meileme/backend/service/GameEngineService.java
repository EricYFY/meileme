package com.meileme.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.backend.model.Coordinate;
import com.meileme.backend.model.Merchant;
import com.meileme.backend.model.Order;
import com.meileme.backend.model.Rider;
import com.meileme.backend.repository.MerchantRepository;
import com.meileme.backend.websocket.GameWebSocketHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class GameEngineService {

    private final GameWebSocketHandler webSocketHandler;
    private final StringRedisTemplate redisTemplate;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    // 内存中的活跃订单列表 (已送达或已失效的订单不再常驻内存)
    private final List<Order> activeOrders = new CopyOnWriteArrayList<>();
    
    // 当前所有骑手的最新物理状态缓存
    private List<Rider> currentRidersState = new CopyOnWriteArrayList<>();

    // 骑手财务账本映射 (riderId -> Rider财务数据)
    private final Map<String, Rider> riderFinancialMap = new ConcurrentHashMap<>();

    // 记录正在执行任务或已被预指派的骑手ID集合，防止高频调度下重复派单
    private final Set<String> busyRiderIds = ConcurrentHashMap.newKeySet();

    // 订单统计计数器
    private final AtomicInteger completedOrderCount = new AtomicInteger(0);
    private final AtomicInteger expiredOrderCount = new AtomicInteger(0);
    private final AtomicInteger unassignedExpiredCount = new AtomicInteger(0); // 超时未接单失效计数
    private final AtomicInteger mealTimeoutExpiredCount = new AtomicInteger(0); // 超时未出餐失效计数

    // === 平台财务总账本 ===
    private final AtomicReference<Double> totalRevenue = new AtomicReference<>(0.0);   // 平台总收入 (商家加盟费 + 订单抽成)
    private final AtomicReference<Double> totalExpenses = new AtomicReference<>(0.0);  // 平台总支出 (骑手底薪 + 提成)
    private final AtomicReference<Double> totalFines = new AtomicReference<>(0.0);     // 平台总罚款 (超时违约赔偿)

    // === 动态财务费率配置 (支持热更新即时生效) ===
    private volatile double platformTakeRate = 0.25; // 平台抽成比例 (默认 25%, 即 0.25)
    private volatile double riderBonusMin = 3.0;     // 骑手提成最小值 (0.0 ~ 20.0)
    private volatile double riderBonusMax = 8.0;     // 骑手提成最大值 (0.0 ~ 20.0)

    // 虚拟时间与跨天日结追踪 (2026-07-01 00:00:00 开始，现实 1 秒 = 游戏 2 分钟)
    public static final long BASE_VIRTUAL_TIME_MS = 1782835200000L;
    private volatile long gameVirtualTimeMs = BASE_VIRTUAL_TIME_MS;
    private volatile int currentVirtualDay = 1;

    // 是否正在运行 / 是否暂停
    private boolean isRunning = false;
    private boolean isPaused = false;

    public GameEngineService(GameWebSocketHandler webSocketHandler, StringRedisTemplate redisTemplate, MerchantRepository merchantRepository) {
        this.webSocketHandler = webSocketHandler;
        this.redisTemplate = redisTemplate;
        this.merchantRepository = merchantRepository;
        this.objectMapper = new ObjectMapper();
    }

    // 缓存住宅区坐标列表，用于在商家 5000m (50格) 周边生成订单送达点
    private List<int[]> residentialCells = new ArrayList<>();

    public void startSimulation(int merchantCount, int riderCount) {
        startSimulation(merchantCount, riderCount, null);
    }

    public void startSimulation(int merchantCount, int riderCount, String mapId) {
        try {
            // 通过 HTTP POST 从 Python 服务启动并获取地图和商家数据
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("merchantCount", merchantCount);
            requestBody.put("riderCount", riderCount);
            if (mapId != null && !mapId.isEmpty()) {
                requestBody.put("mapId", mapId);
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String mapJson = restTemplate.postForObject("http://localhost:8081/api/simulation/start", request, String.class);
            Map<String, Object> mapData = objectMapper.readValue(mapJson, new TypeReference<Map<String, Object>>() {});
            
            // 组装并缓存地图数据，供前端按需拉取
            String cachedMessage = "{\"type\":\"MAP_DATA\",\"data\":" + mapJson + "}";
            webSocketHandler.setCachedMapMessage(cachedMessage);

            merchantRepository.clear();
            List<Map<String, Object>> merchantsData = (List<Map<String, Object>>) mapData.get("merchants");
            for (Map<String, Object> mData : merchantsData) {
                float x = ((Number) mData.get("x")).floatValue();
                float y = ((Number) mData.get("y")).floatValue();
                Merchant merchant = new Merchant(new Coordinate(x, y));
                merchant.setId((String) mData.get("id"));
                
                // 初始化各商家的独立经营成本 (第 1 天起步扣除)
                merchant.setDailyFranchiseFee(50.0);
                merchant.setRentCost(100.0);
                merchant.setLaborCost(150.0);
                merchant.setAdSpend(20.0);
                merchant.setMaterialCostRate(0.50);
                merchant.setMinOrderPrice(15.0);
                merchant.setMaxOrderPrice(35.0);

                merchant.setTotalFranchisePaid(50.0);
                merchant.setTotalFixedCost(250.0); // 租金100 + 人工150
                merchant.setTotalAdSpend(20.0);
                merchant.recalculateTotalIncome();
                merchantRepository.save(merchant);
            }
            
            // 解析住宅区格子坐标
            List<List<Integer>> resCells = (List<List<Integer>>) mapData.get("residentialCells");
            if (resCells != null) {
                residentialCells.clear();
                for (List<Integer> cell : resCells) {
                    residentialCells.add(new int[]{cell.get(0), cell.get(1)});
                }
            }
            
            System.out.println("成功从 Python 引擎获取地图并初始化了 " + merchantsData.size() + " 个商家，" + residentialCells.size() + " 个住宅区格子。");
            
            // 清空旧数据与计数器
            activeOrders.clear();
            busyRiderIds.clear();
            completedOrderCount.set(0);
            expiredOrderCount.set(0);
            unassignedExpiredCount.set(0);
            mealTimeoutExpiredCount.set(0);
            riderFinancialMap.clear();

            // ★ 清空 Redis 中的骑手控制状态 Hash，杜绝脏数据污染
            try {
                redisTemplate.delete(Arrays.asList("game:rider:status", "game:rider:targets", "game:rider:orders", "game:events:reach_target"));
            } catch (Exception e) {}

            // 初始化骑手财务数据（发放第一天底薪 ¥100.00）
            for (int i = 1; i <= riderCount; i++) {
                String rId = String.format("rider-%03d", i);
                Rider r = new Rider();
                r.setId(rId);
                r.setBaseSalary(100.0);
                r.setBonus(0.0);
                r.setTotalSalary(100.0);
                riderFinancialMap.put(rId, r);
            }

            // 初始化平台财务总账
            // 总收入 = 商家加盟费 (¥50 * 商家数)
            // 总支出 = 骑手底薪 (¥100 * 骑手数)
            totalRevenue.set(50.0 * merchantsData.size());
            totalExpenses.set(100.0 * riderCount);
            totalFines.set(0.0);

            this.currentVirtualDay = 1;
            this.gameVirtualTimeMs = BASE_VIRTUAL_TIME_MS;
            this.isRunning = true;
            this.isPaused = false;
            
            // 广播系统启动和地图信息
            webSocketHandler.broadcastMessage(cachedMessage);
            webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_STARTED\"}");
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
            broadcastFinancialSummary();
            
        } catch (Exception e) {
            System.err.println("未能连接到 Python 引擎启动模拟: " + e.getMessage());
        }
    }

    public void pauseSimulation() {
        this.isPaused = true;
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForObject("http://localhost:8081/api/simulation/pause", null, String.class);
            System.out.println("已通知 Python 引擎暂停物理模拟");
        } catch (Exception e) {
            System.err.println("通知 Python 引擎暂停失败: " + e.getMessage());
        }
        webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_PAUSED\"}");
        System.out.println("模拟已暂停");
    }

    public void resumeSimulation() {
        this.isPaused = false;
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForObject("http://localhost:8081/api/simulation/resume", null, String.class);
            System.out.println("已通知 Python 引擎继续物理模拟");
        } catch (Exception e) {
            System.err.println("通知 Python 引擎继续失败: " + e.getMessage());
        }
        webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_RESUMED\"}");
        System.out.println("模拟已继续");
    }

    public void stopSimulation() {
        this.isRunning = false;
        this.isPaused = false;
        activeOrders.clear();
        busyRiderIds.clear();
        completedOrderCount.set(0);
        expiredOrderCount.set(0);
        unassignedExpiredCount.set(0);
        mealTimeoutExpiredCount.set(0);
        this.currentVirtualDay = 1;
        this.gameVirtualTimeMs = BASE_VIRTUAL_TIME_MS;
        totalRevenue.set(0.0);
        totalExpenses.set(0.0);
        totalFines.set(0.0);

        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForObject("http://localhost:8081/api/simulation/stop", null, String.class);
            System.out.println("已通知 Python 引擎停止物理模拟并清理状态");
        } catch (Exception e) {
            System.err.println("通知 Python 引擎停止失败: " + e.getMessage());
        }
        webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_STOPPED\"}");
        System.out.println("模拟已结束并重置");
    }

    // 1. 低频定时器 (1s)：推进虚拟时间、跨天日结结算、订单生成、失效检查与派单
    @Scheduled(fixedRate = 1000)
    public void scheduledLoop() {
        if (!isRunning || isPaused) return;

        // 现实 1 秒 = 游戏 120 秒 (2分钟)
        gameVirtualTimeMs += 120 * 1000L;

        // 1. 跨天日结检测 (基于权威虚拟时间偏移量计算天数)
        checkDailySettlement();

        // 2. 检查未被派单超时的订单
        checkUnassignedOrderExpiration();

        // 3. 动态扫描商家并生成新订单 (结合高峰期与5000m约束)
        generateOrdersFromMerchants();

        // 4. 派单调度
        dispatchOrdersToRiders();

        // 5. 周期性广播财务大盘
        broadcastFinancialSummary();
    }

    private void checkDailySettlement() {
        long elapsedVirtualMs = gameVirtualTimeMs - BASE_VIRTUAL_TIME_MS;
        int virtualDay = (int) (elapsedVirtualMs / (24 * 3600 * 1000L)) + 1;

        if (virtualDay > currentVirtualDay) {
            int daysPassed = virtualDay - currentVirtualDay;
            currentVirtualDay = virtualDay;
            System.out.println("=== 🌙 跨天日结结算 (虚拟第 " + currentVirtualDay + " 天 00:00:00) ===");

            // 1. 发放骑手每日底薪 ¥100.00 / 天
            double totalRiderBaseSalaryAdded = 0.0;
            for (Rider r : riderFinancialMap.values()) {
                double addSalary = 100.0 * daysPassed;
                r.setBaseSalary(r.getBaseSalary() + addSalary);
                r.setTotalSalary(r.getBaseSalary() + r.getBonus());
                totalRiderBaseSalaryAdded += addSalary;
            }
            final double finalRiderSalaryAdded = totalRiderBaseSalaryAdded;
            totalExpenses.updateAndGet(e -> e + finalRiderSalaryAdded);

            // 2. 扣除商家每日加盟费、门面租金+人工固定成本、广告成本
            double totalMerchantFranchiseAdded = 0.0;
            for (Merchant m : merchantRepository.findAll()) {
                double addFranchise = m.getDailyFranchiseFee() * daysPassed;
                double addFixed = (m.getRentCost() + m.getLaborCost()) * daysPassed;
                double addAd = m.getAdSpend() * daysPassed;

                m.setTotalFranchisePaid(Math.round((m.getTotalFranchisePaid() + addFranchise) * 100.0) / 100.0);
                m.setTotalFixedCost(Math.round((m.getTotalFixedCost() + addFixed) * 100.0) / 100.0);
                m.setTotalAdSpend(Math.round((m.getTotalAdSpend() + addAd) * 100.0) / 100.0);
                m.recalculateTotalIncome();
                merchantRepository.save(m);

                totalMerchantFranchiseAdded += addFranchise;
            }
            final double finalFranchiseAdded = totalMerchantFranchiseAdded;
            totalRevenue.updateAndGet(r -> r + finalFranchiseAdded);

            // 广播最新状态
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
            broadcastFinancialSummary();
        }
    }

    private void checkUnassignedOrderExpiration() {
        long now = System.currentTimeMillis();
        List<Order> expiredList = new ArrayList<>();
        
        for (Order order : activeOrders) {
            // 状态 0: 超过 60 秒未能成功指派骑手接单
            if (order.getStatus() == 0 && (now - order.getCreateTime()) > 60000) {
                order.setStatus(5); // 5: EXPIRED 已失效
                expiredList.add(order);
            }
        }
        
        if (!expiredList.isEmpty()) {
            activeOrders.removeAll(expiredList);
            unassignedExpiredCount.addAndGet(expiredList.size());
            int expCount = expiredOrderCount.addAndGet(expiredList.size());
            
            // ★ 平台违约责任：超时无人接单，平台赔偿客户 ¥20.00/单，计入平台总罚款/赔偿支出账本
            double unassignedPenalty = 20.0 * expiredList.size();
            totalFines.updateAndGet(f -> Math.round((f + unassignedPenalty) * 100.0) / 100.0);

            for (Order expiredOrder : expiredList) {
                if (expiredOrder.getMerchantId() != null) {
                    Merchant m = merchantRepository.findById(expiredOrder.getMerchantId()).orElse(null);
                    if (m != null) {
                        m.setOngoingOrders(Math.max(0, m.getOngoingOrders() - 1));
                        merchantRepository.save(m);
                    }
                }

                Map<String, Object> expPayload = new HashMap<>();
                expPayload.put("id", expiredOrder.getId());
                expPayload.put("reason", "UNASSIGNED_TIMEOUT");
                expPayload.put("expiredCount", expCount);
                expPayload.put("unassignedExpiredCount", unassignedExpiredCount.get());
                expPayload.put("mealTimeoutExpiredCount", mealTimeoutExpiredCount.get());
                broadcastState("ORDER_EXPIRED", expPayload);
                System.out.println(">>> 订单无人接单超时失效：" + expiredOrder.getId().substring(0, 8) + " (平台赔偿 ¥20.00，累计失效: " + expCount + ", 未接单: " + unassignedExpiredCount.get() + ", 等餐超时: " + mealTimeoutExpiredCount.get() + ")");
            }

            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
            broadcastFinancialSummary();
        }
    }

    private void generateOrdersFromMerchants() {
        if (residentialCells.isEmpty()) return;

        // 计算当前虚拟时间的小时数 (0 ~ 23) 与高峰期系数 K_time
        long elapsedVirtualMs = gameVirtualTimeMs - BASE_VIRTUAL_TIME_MS;
        int virtualHour = (int) ((elapsedVirtualMs / (3600 * 1000L)) % 24);
        
        double kTime;
        if (virtualHour >= 11 && virtualHour < 13) {
            kTime = 2.5; // 午饭最大高峰 (11:00-13:00)
        } else if (virtualHour >= 17 && virtualHour < 19) {
            kTime = 2.0; // 晚饭中等高峰 (17:00-19:00)
        } else if (virtualHour >= 6 && virtualHour < 9) {
            kTime = 1.2; // 早饭小高峰 (06:00-09:00)
        } else if ((virtualHour >= 14 && virtualHour < 15) || (virtualHour >= 21 && virtualHour < 22)) {
            kTime = 1.0; // 下午上班 (14:00-15:00) & 睡前夜宵 (21:00-22:00)
        } else if (virtualHour >= 22 || virtualHour < 6) {
            kTime = 0.1; // 深夜低谷 (22:00-06:00)
        } else {
            kTime = 0.5; // 常规平峰时段
        }

        boolean merchantUpdated = false;
        for (Merchant m : merchantRepository.findAll()) {
            // 1. 严格筛选商家周边 10km (100格) 范围内的临路住宅区格子
            float mx = m.getLocation().getX();
            float my = m.getLocation().getY();
            List<int[]> nearbyCells = new ArrayList<>();
            for (int[] cell : residentialCells) {
                float dx = cell[0] - mx;
                float dy = cell[1] - my;
                if (dx * dx + dy * dy <= 100.0f * 100.0f) { // 100格半径 = 10000米 (10km)
                    nearbyCells.add(cell);
                }
            }
            if (nearbyCells.isEmpty()) {
                // 若周边100格内无住宅，降级选用全局最近住宅区
                nearbyCells = residentialCells;
            }

            // 2. 出单概率模型：基准出单率提升为4倍 (0.32)，综合考虑评分、广告投入与高峰期系数
            float rating = m.getRating();
            double ratingFactor = Math.pow(rating / 5.0, 2);
            double adFactor = 1.0 + 0.5 * Math.log(1.0 + m.getAdSpend() / 10.0);
            double prob = 0.32 * kTime * ratingFactor * adFactor;
            
            if (random.nextDouble() < prob) {
                int[] cell = nearbyCells.get(random.nextInt(nearbyCells.size()));
                Coordinate pickupLoc = new Coordinate(mx, my);
                Coordinate deliveryLoc = new Coordinate(cell[0], cell[1]);
                
                // 3. 商家自定义客单价区间生成单价
                double minP = m.getMinOrderPrice();
                double maxP = m.getMaxOrderPrice();
                if (minP > maxP) maxP = minP;
                double rawPrice = minP + random.nextDouble() * (maxP - minP);
                double orderPrice = Math.round(rawPrice * 100.0) / 100.0;
                
                // 4. 订单耗材成本
                double materialCost = Math.round(orderPrice * m.getMaterialCostRate() * 100.0) / 100.0;

                // 5. 备餐时间模型：租金+人工越高，备餐越快
                double totalFixed = m.getRentCost() + m.getLaborCost();
                double basePrep = Math.max(2.0, 1500.0 / (totalFixed + 50.0));
                double prepDuration = Math.max(1.5, basePrep + (random.nextDouble() * 2.0 - 1.0));
                prepDuration = Math.round(prepDuration * 10.0) / 10.0;

                Order order = new Order(pickupLoc, deliveryLoc);
                order.setMerchantId(m.getId());
                order.setOrderPrice(orderPrice);
                order.setMaterialCost(materialCost);
                order.setPrepDurationSec(prepDuration);
                order.setPrepFinishTime(System.currentTimeMillis() + (long)(prepDuration * 1000L));
                order.setStatus(0); // 0: CREATED 备餐中 / 待派单
                activeOrders.add(order);
                
                m.setOngoingOrders(m.getOngoingOrders() + 1);
                merchantRepository.save(m);
                merchantUpdated = true;

                broadcastState("ORDER_CREATED", order);
            }
        }

        if (merchantUpdated) {
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
        }
    }

    private void dispatchOrdersToRiders() {
        List<Order> unassignedOrders = activeOrders.stream().filter(o -> o.getStatus() == 0).collect(Collectors.toList());
        if (unassignedOrders.isEmpty() || currentRidersState.isEmpty()) return;

        for (Order order : unassignedOrders) {
            Rider bestRider = null;
            float minDistance = Float.MAX_VALUE;

            for (Rider rider : currentRidersState) {
                if (rider.getStatus() == 0 && !busyRiderIds.contains(rider.getId())) {
                    float dx = rider.getCurrentPosition().getX() - order.getPickupLocation().getX();
                    float dy = rider.getCurrentPosition().getY() - order.getPickupLocation().getY();
                    float dist = dx*dx + dy*dy;
                    if (dist < minDistance) {
                        minDistance = dist;
                        bestRider = rider;
                    }
                }
            }

            if (bestRider != null) {
                order.setStatus(1); // 1: DISPATCHED 骑手前往商家
                busyRiderIds.add(bestRider.getId());
                
                bestRider.setStatus(1);
                bestRider.setCurrentOrderId(order.getId());
                
                try {
                    redisTemplate.opsForHash().put("game:rider:status", bestRider.getId(), "1");
                    redisTemplate.opsForHash().put("game:rider:orders", bestRider.getId(), order.getId());
                    redisTemplate.opsForHash().put("game:rider:targets", bestRider.getId(), objectMapper.writeValueAsString(order.getPickupLocation()));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                broadcastState("RIDER_ASSIGNED", bestRider);
            }
        }
    }

    // 2. 高频同步 (10Hz)：从 Redis 提取 Python 引擎计算好的骑手坐标，并处理到达事件与等餐超时
    @Scheduled(fixedRate = 100)
    public void syncFromPython() {
        if (!isRunning || isPaused) return;
        try {
            // 1. 监控所有“到店等餐”中的订单 (status == 2): 检查是否备餐完成或等餐超15s失效
            checkMealWaitingStatusAndTimeout();

            // 2. 读取骑手物理坐标并合并财务数据
            String ridersJson = redisTemplate.opsForValue().get("game:state:riders");
            if (ridersJson != null) {
                List<Rider> freshRiders = objectMapper.readValue(ridersJson, new TypeReference<List<Rider>>() {});
                for (Rider r : freshRiders) {
                    Rider fin = riderFinancialMap.get(r.getId());
                    if (fin != null) {
                        r.setBaseSalary(fin.getBaseSalary());
                        r.setBonus(fin.getBonus());
                        r.setTotalSalary(fin.getTotalSalary());
                    }
                }
                currentRidersState = freshRiders;
                broadcastState("RIDER_UPDATE", currentRidersState);
            }

            // 3. 读取 Python 推送的到达事件 (队列)
            String eventJson = redisTemplate.opsForList().rightPop("game:events:reach_target");
            while (eventJson != null) {
                Map<String, Object> event = objectMapper.readValue(eventJson, new TypeReference<Map<String, Object>>() {});
                String riderId = (String) event.get("riderId");
                String orderId = (String) event.get("orderId");
                int status = (int) event.get("status");

                handleRiderReachTarget(riderId, orderId, status);
                
                eventJson = redisTemplate.opsForList().rightPop("game:events:reach_target");
            }
            
        } catch (Exception e) {
            System.err.println("同步 Python 状态发生异常: " + e.getMessage());
        }
    }

    private void checkMealWaitingStatusAndTimeout() {
        long now = System.currentTimeMillis();
        for (Order order : activeOrders) {
            if (order.getStatus() == 2) { // 2: WAITING_MEAL (在店等餐)
                double waitSec = (now - order.getRiderArriveMerchantTime()) / 1000.0;
                order.setWaitDurationSec(Math.round(waitSec * 10.0) / 10.0);

                // 判定 A: 餐品已准备完成 -> 骑手取餐出发！
                if (now >= order.getPrepFinishTime()) {
                    order.setStatus(3); // 3: DELIVERING (配送中)
                    order.setPickupTime(now);

                    Rider rider = currentRidersState.stream()
                            .filter(r -> order.getId().equals(r.getCurrentOrderId()))
                            .findFirst().orElse(null);
                    if (rider != null) {
                        rider.setStatus(2);
                        try {
                            redisTemplate.opsForHash().put("game:rider:status", rider.getId(), "2");
                            redisTemplate.opsForHash().put("game:rider:targets", rider.getId(), objectMapper.writeValueAsString(order.getDeliveryLocation()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    broadcastState("ORDER_STATUS_CHANGED", order);
                    System.out.println(">>> 订单 " + order.getId().substring(0, 8) + " 餐品已备齐，骑手出发配送");
                }
                // 判定 B: 骑手在店等餐超过 15 秒 (游戏内30分钟) -> 订单失效，罚款商家订单原价，该单按1.0最低分计入评分！
                else if (waitSec >= 15.0) {
                    order.setStatus(5); // 5: EXPIRED (等餐超时失效)
                    activeOrders.remove(order);
                    mealTimeoutExpiredCount.incrementAndGet();
                    int expCount = expiredOrderCount.incrementAndGet();

                    // 释放骑手
                    Rider rider = currentRidersState.stream()
                            .filter(r -> order.getId().equals(r.getCurrentOrderId()))
                            .findFirst().orElse(null);
                    if (rider != null) {
                        busyRiderIds.remove(rider.getId());
                        rider.setStatus(0);
                        rider.setCurrentOrderId(null);
                        try {
                            redisTemplate.opsForHash().put("game:rider:status", rider.getId(), "0");
                            redisTemplate.opsForHash().put("game:rider:orders", rider.getId(), "null");
                            redisTemplate.opsForHash().put("game:rider:targets", rider.getId(), "null");
                        } catch (Exception e) {}
                    }

                    // 罚款商家该单原价，并将该单评分按 1.0 最低分平滑计入商家评分
                    if (order.getMerchantId() != null) {
                        Merchant m = merchantRepository.findById(order.getMerchantId()).orElse(null);
                        if (m != null) {
                            m.setTotalFines(Math.round((m.getTotalFines() + order.getOrderPrice()) * 100.0) / 100.0);
                            m.setOngoingOrders(Math.max(0, m.getOngoingOrders() - 1));
                            
                            // 该单按最低分 1.0 计入
                            double oldRating = m.getRating();
                            double newRating = oldRating * 0.90 + 1.0 * 0.10;
                            m.setRating((float)(Math.round(Math.max(1.0, Math.min(5.0, newRating)) * 10.0) / 10.0));
                            m.recalculateTotalIncome();
                            merchantRepository.save(m);
                        }
                    }

                    // 平台超时违约账本记录
                    totalFines.updateAndGet(f -> Math.round((f + order.getOrderPrice()) * 100.0) / 100.0);

                    Map<String, Object> expPayload = new HashMap<>();
                    expPayload.put("id", order.getId());
                    expPayload.put("reason", "MEAL_TIMEOUT");
                    expPayload.put("expiredCount", expCount);
                    expPayload.put("unassignedExpiredCount", unassignedExpiredCount.get());
                    expPayload.put("mealTimeoutExpiredCount", mealTimeoutExpiredCount.get());
                    broadcastState("ORDER_EXPIRED", expPayload);
                    broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
                    broadcastFinancialSummary();
                    System.out.println(">>> 💥 骑手在店等餐超 15s 导致订单失效：" + order.getId().substring(0, 8) + "，罚款商家原价 ¥" + order.getOrderPrice() + "，该单按1.0最低分计入 (未接单: " + unassignedExpiredCount.get() + ", 等餐超时: " + mealTimeoutExpiredCount.get() + ")");
                }
            }
        }
    }

    private void handleRiderReachTarget(String riderId, String orderId, int status) {
        Order currentOrder = activeOrders.stream().filter(o -> o.getId().equals(orderId)).findFirst().orElse(null);
        if (currentOrder == null) return;

        try {
            if (status == 1) { // 骑手到达取餐点 (商家)
                long now = System.currentTimeMillis();
                currentOrder.setRiderArriveMerchantTime(now);
                currentOrder.setPickupDurationSec(Math.round(((now - currentOrder.getCreateTime()) / 1000.0) * 10.0) / 10.0);

                // 检查餐品是否已经出餐完毕
                if (now >= currentOrder.getPrepFinishTime()) {
                    // 餐品已备齐，直接取餐出发！
                    currentOrder.setStatus(3); // 3: DELIVERING (配送中)
                    currentOrder.setPickupTime(now);
                    currentOrder.setWaitDurationSec(0.0);

                    redisTemplate.opsForHash().put("game:rider:status", riderId, "2");
                    redisTemplate.opsForHash().put("game:rider:targets", riderId, objectMapper.writeValueAsString(currentOrder.getDeliveryLocation()));
                    broadcastState("ORDER_STATUS_CHANGED", currentOrder);
                    System.out.println(">>> 骑手 " + riderId + " 已取餐，订单 " + orderId.substring(0, 8) + " 进入配送中");
                } else {
                    // 餐品仍在制作中，骑手在店等待
                    currentOrder.setStatus(2); // 2: WAITING_MEAL (在店等餐)
                    broadcastState("ORDER_STATUS_CHANGED", currentOrder);
                    System.out.println(">>> 骑手 " + riderId + " 已到店，正在等待商家出餐 (订单 " + orderId.substring(0, 8) + ")");
                }
                
            } else if (status == 2) { // 骑手到达送餐点 → 订单完成
                currentOrder.setStatus(4); // 4: COMPLETED
                
                // 执行商家评分更新与财务收益结算
                settleOrderFinancialsAndRating(currentOrder, riderId);
                
                activeOrders.remove(currentOrder);
                
                int compCount = completedOrderCount.incrementAndGet();
                Map<String, Object> compPayload = new HashMap<>();
                compPayload.put("id", currentOrder.getId());
                compPayload.put("completedCount", compCount);
                broadcastState("ORDER_COMPLETED", compPayload);

                // 释放骑手
                busyRiderIds.remove(riderId);
                redisTemplate.opsForHash().put("game:rider:status", riderId, "0");
                redisTemplate.opsForHash().put("game:rider:orders", riderId, "null");
                redisTemplate.opsForHash().put("game:rider:targets", riderId, "null");
                System.out.println(">>> 骑手 " + riderId + " 已送达，订单 " + orderId.substring(0, 8) + " 完成 (累计完成: " + compCount + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void settleOrderFinancialsAndRating(Order order, String riderId) {
        long now = System.currentTimeMillis();
        order.setDeliverTime(now);
        double totalSec = (now - order.getCreateTime()) / 1000.0;
        order.setTotalDurationSec(Math.round(totalSec * 100.0) / 100.0);
        long pTime = order.getPickupTime() > 0 ? order.getPickupTime() : now;
        order.setDeliveryDurationSec(Math.round(((now - pTime) / 1000.0) * 100.0) / 100.0);

        // 1. 耗材质量评分 S_quality: 耗材成本率越高，餐品品质与得分越高
        Merchant merchant = null;
        if (order.getMerchantId() != null) {
            merchant = merchantRepository.findById(order.getMerchantId()).orElse(null);
        }
        double matRate = (merchant != null) ? merchant.getMaterialCostRate() : 0.5;
        double muQuality = 1.0 + matRate * 4.5;
        double sQuality = muQuality + random.nextGaussian() * 0.35;
        sQuality = Math.max(1.0, Math.min(5.0, Math.round(sQuality * 10.0) / 10.0));
        order.setQualityScore(sQuality);

        // 2. 配送时效分 S_time: 根据总耗时 T_total (秒)
        double sTime;
        if (totalSec <= 12.0) {
            sTime = 5.0; // 极速送达
        } else if (totalSec <= 25.0) {
            sTime = 5.0 - ((totalSec - 12.0) / 13.0) * 1.5;
        } else if (totalSec <= 45.0) {
            sTime = 3.5 - ((totalSec - 25.0) / 20.0) * 2.5;
        } else {
            sTime = 1.0;
        }
        sTime = Math.max(1.0, Math.min(5.0, Math.round(sTime * 10.0) / 10.0));
        order.setTimeScore(sTime);

        // 3. 单次订单综合体验分 S_order = 0.5 * S_quality + 0.5 * S_time
        double sOrder = Math.round((0.5 * sQuality + 0.5 * sTime) * 10.0) / 10.0;
        order.setFinalScore(sOrder);

        // 4. === 骑手单笔提成结算 (基于动态可配置区间 [riderBonusMin, riderBonusMax]) ===
        double speedRatio = Math.max(0.0, Math.min(1.0, (25.0 - totalSec) / 25.0));
        double rawBonus = riderBonusMin + (riderBonusMax - riderBonusMin) * speedRatio;
        final double riderBonus = Math.round(rawBonus * 100.0) / 100.0;

        Rider rFin = riderFinancialMap.get(riderId);
        if (rFin != null) {
            rFin.setBonus(Math.round((rFin.getBonus() + riderBonus) * 100.0) / 100.0);
            rFin.setTotalSalary(Math.round((rFin.getBaseSalary() + rFin.getBonus()) * 100.0) / 100.0);
        }
        totalExpenses.updateAndGet(e -> Math.round((e + riderBonus) * 100.0) / 100.0);

        // 5. === 商家与平台单笔订单收入结算 (基于动态抽成比例 platformTakeRate 与耗材成本) ===
        double orderPrice = order.getOrderPrice();
        double materialCost = order.getMaterialCost();
        final double platformTake = Math.round((orderPrice * platformTakeRate) * 100.0) / 100.0; // 动态平台抽成
        
        totalRevenue.updateAndGet(r -> Math.round((r + platformTake) * 100.0) / 100.0);

        // 6. 更新商家财务流水、耗材成本与评分 (EWMA)
        if (merchant != null) {
            merchant.setTotalRevenue(Math.round((merchant.getTotalRevenue() + orderPrice) * 100.0) / 100.0);
            merchant.setTotalCommissionPaid(Math.round((merchant.getTotalCommissionPaid() + platformTake) * 100.0) / 100.0);
            merchant.setTotalMaterialCost(Math.round((merchant.getTotalMaterialCost() + materialCost) * 100.0) / 100.0);
            merchant.setCompletedOrders(merchant.getCompletedOrders() + 1);
            merchant.setOngoingOrders(Math.max(0, merchant.getOngoingOrders() - 1));

            // 平滑更新评分
            double oldRating = merchant.getRating();
            double newRating = oldRating * 0.90 + sOrder * 0.10;
            newRating = Math.max(1.0, Math.min(5.0, newRating));
            merchant.setRating((float)(Math.round(newRating * 10.0) / 10.0));
            
            merchant.recalculateTotalIncome();
            merchantRepository.save(merchant);
        }

        // 广播财务总账与商家、骑手最新数据
        broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
        broadcastFinancialSummary();
    }

    public void updateFinancialConfig(double takeRate, double bonusMin, double bonusMax) {
        this.platformTakeRate = Math.max(0.0, Math.min(1.0, takeRate));
        double minB = Math.max(0.0, Math.min(20.0, bonusMin));
        double maxB = Math.max(0.0, Math.min(20.0, bonusMax));
        if (minB > maxB) {
            double temp = minB;
            minB = maxB;
            maxB = temp;
        }
        this.riderBonusMin = minB;
        this.riderBonusMax = maxB;
        System.out.println(String.format(">>> [财务配置热更新] 抽成比例: %.1f%%, 骑手提成区间: [¥%.1f, ¥%.1f]", this.platformTakeRate * 100, this.riderBonusMin, this.riderBonusMax));
        broadcastFinancialSummary();
    }

    public void updateMerchantConfig(String merchantId, double minPrice, double maxPrice, double matRate, double rent, double labor, double adSpend, double franchise) {
        Merchant m = merchantRepository.findById(merchantId).orElse(null);
        if (m != null) {
            m.setMinOrderPrice(Math.max(1.0, minPrice));
            m.setMaxOrderPrice(Math.max(m.getMinOrderPrice(), maxPrice));
            m.setMaterialCostRate(Math.max(0.05, Math.min(0.95, matRate)));
            m.setRentCost(Math.max(0.0, rent));
            m.setLaborCost(Math.max(0.0, labor));
            m.setAdSpend(Math.max(0.0, adSpend));
            m.setDailyFranchiseFee(Math.max(0.0, franchise));
            m.recalculateTotalIncome();
            merchantRepository.save(m);
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
            System.out.println(">>> [商家参数更新] 商家 " + merchantId + " 配置已生效");
        }
    }

    private void broadcastFinancialSummary() {
        double rev = Math.round(totalRevenue.get() * 100.0) / 100.0;
        double exp = Math.round(totalExpenses.get() * 100.0) / 100.0;
        double fin = Math.round(totalFines.get() * 100.0) / 100.0;
        double net = Math.round((rev - exp - fin) * 100.0) / 100.0;

        Map<String, Object> financialData = new HashMap<>();
        financialData.put("totalRevenue", rev);
        financialData.put("totalExpenses", exp);
        financialData.put("totalFines", fin);
        financialData.put("netProfit", net);
        financialData.put("virtualDay", currentVirtualDay);
        financialData.put("gameVirtualTimeMs", gameVirtualTimeMs);
        financialData.put("platformTakeRate", platformTakeRate);
        financialData.put("riderBonusMin", riderBonusMin);
        financialData.put("riderBonusMax", riderBonusMax);
        financialData.put("completedOrderCount", completedOrderCount.get());
        financialData.put("expiredOrderCount", expiredOrderCount.get());
        financialData.put("unassignedExpiredCount", unassignedExpiredCount.get());
        financialData.put("mealTimeoutExpiredCount", mealTimeoutExpiredCount.get());

        broadcastState("FINANCIAL_UPDATE", financialData);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void syncStateToSession(org.springframework.web.socket.WebSocketSession session) {
        if (!isRunning) return;
        try {
            // 1. 发送 SIMULATION_STARTED
            session.sendMessage(new org.springframework.web.socket.TextMessage("{\"type\":\"SIMULATION_STARTED\"}"));
            if (isPaused) {
                session.sendMessage(new org.springframework.web.socket.TextMessage("{\"type\":\"SIMULATION_PAUSED\"}"));
            }

            // 2. 发送商家全量数据
            List<Merchant> merchants = merchantRepository.findAll();
            if (!merchants.isEmpty()) {
                session.sendMessage(new org.springframework.web.socket.TextMessage(objectMapper.writeValueAsString(new MessageWrapper("MERCHANT_UPDATE", merchants))));
            }

            // 3. 发送财务总账数据
            double rev = Math.round(totalRevenue.get() * 100.0) / 100.0;
            double exp = Math.round(totalExpenses.get() * 100.0) / 100.0;
            double fin = Math.round(totalFines.get() * 100.0) / 100.0;
            double net = Math.round((rev - exp - fin) * 100.0) / 100.0;

            Map<String, Object> financialData = new HashMap<>();
            financialData.put("totalRevenue", rev);
            financialData.put("totalExpenses", exp);
            financialData.put("totalFines", fin);
            financialData.put("netProfit", net);
            financialData.put("virtualDay", currentVirtualDay);
            financialData.put("gameVirtualTimeMs", gameVirtualTimeMs);
            financialData.put("platformTakeRate", platformTakeRate);
            financialData.put("riderBonusMin", riderBonusMin);
            financialData.put("riderBonusMax", riderBonusMax);
            financialData.put("completedOrderCount", completedOrderCount.get());
            financialData.put("expiredOrderCount", expiredOrderCount.get());
            financialData.put("unassignedExpiredCount", unassignedExpiredCount.get());
            financialData.put("mealTimeoutExpiredCount", mealTimeoutExpiredCount.get());
            session.sendMessage(new org.springframework.web.socket.TextMessage(objectMapper.writeValueAsString(new MessageWrapper("FINANCIAL_UPDATE", financialData))));

            // 4. 发送所有活跃订单
            if (!activeOrders.isEmpty()) {
                session.sendMessage(new org.springframework.web.socket.TextMessage(objectMapper.writeValueAsString(new MessageWrapper("ORDERS_SYNC", new ArrayList<>(activeOrders)))));
            }

            System.out.println("<<< [自动同步] 已向会话 " + session.getId() + " 同步全量运行状态 (商家 " + merchants.size() + " 个, 活跃订单 " + activeOrders.size() + " 个)");
        } catch (Exception e) {
            System.err.println("向会话同步初始状态失败: " + e.getMessage());
        }
    }

    private void broadcastState(String type, Object data) {
        try {
            MessageWrapper msg = new MessageWrapper(type, data);
            webSocketHandler.broadcastMessage(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {}
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MessageWrapper {
        private String type;
        private Object data;
    }
}
