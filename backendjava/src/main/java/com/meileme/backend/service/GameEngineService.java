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

    // 内存中的活跃订单列表 (已送达的订单不再常驻内存)
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

    // === 平台财务总账本 ===
    private final AtomicReference<Double> totalRevenue = new AtomicReference<>(0.0);   // 平台总收入 (商家佣金 + 订单抽成)
    private final AtomicReference<Double> totalExpenses = new AtomicReference<>(0.0);  // 平台总支出 (骑手底薪 + 提成)
    private final AtomicReference<Double> totalFines = new AtomicReference<>(0.0);     // 平台总罚款 (失效订单赔偿)

    // === 动态财务费率配置 (支持热更新即时生效) ===
    private volatile double platformTakeRate = 0.15; // 平台抽成比例 (0.0 ~ 1.0，即 0% ~ 100%)
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

    // 缓存住宅区坐标列表，用于随机生成订单送达点
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
                // 初始第一天收取商家入驻佣金 ¥50.00
                merchant.setCommission(50.0);
                merchant.setOrderRevenue(0.0);
                merchant.setTotalIncome(-50.0);
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
            // 总收入 = 商家佣金 (¥50 * 商家数)
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

        // 2. 检查超时未接单的失效订单
        checkOrderExpiration();

        // 3. 动态扫描商家并生成新订单
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

            // 2. 扣除商家每日入驻佣金 ¥50.00 / 天
            double totalMerchantCommissionAdded = 0.0;
            for (Merchant m : merchantRepository.findAll()) {
                double addComm = 50.0 * daysPassed;
                m.setCommission(m.getCommission() + addComm);
                m.setTotalIncome(m.getOrderRevenue() - m.getCommission());
                merchantRepository.save(m);
                totalMerchantCommissionAdded += addComm;
            }
            final double finalMerchantCommAdded = totalMerchantCommissionAdded;
            totalRevenue.updateAndGet(r -> r + finalMerchantCommAdded);

            // 广播最新状态
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
            broadcastFinancialSummary();
        }
    }

    private void checkOrderExpiration() {
        long now = System.currentTimeMillis();
        List<Order> expiredList = new ArrayList<>();
        
        for (Order order : activeOrders) {
            if (order.getStatus() == 0 && (now - order.getCreateTime()) > 60000) {
                order.setStatus(4); // 4: EXPIRED 已失效
                expiredList.add(order);
            }
        }
        
        if (!expiredList.isEmpty()) {
            activeOrders.removeAll(expiredList);
            int expCount = expiredOrderCount.addAndGet(expiredList.size());
            
            // 平台超时赔付罚款 (每单 ¥20.00)
            double finesAdded = 20.0 * expiredList.size();
            totalFines.updateAndGet(f -> f + finesAdded);

            for (Order expiredOrder : expiredList) {
                Map<String, Object> expPayload = new HashMap<>();
                expPayload.put("id", expiredOrder.getId());
                expPayload.put("expiredCount", expCount);
                broadcastState("ORDER_EXPIRED", expPayload);
                System.out.println(">>> 订单超时失效：" + expiredOrder.getId().substring(0, 8) + " (累计失效: " + expCount + "，罚款 +¥20.0)");
            }

            broadcastFinancialSummary();
        }
    }

    private void generateOrdersFromMerchants() {
        if (residentialCells.isEmpty()) return;

        boolean merchantUpdated = false;
        for (Merchant m : merchantRepository.findAll()) {
            float rating = m.getRating();
            double prob = 0.15 * Math.pow(rating / 5.0, 2);
            
            if (random.nextDouble() < prob) {
                int[] cell = residentialCells.get(random.nextInt(residentialCells.size()));
                Coordinate pickupLoc = new Coordinate(m.getLocation().getX(), m.getLocation().getY());
                Coordinate deliveryLoc = new Coordinate(cell[0], cell[1]);
                
                Order order = new Order(pickupLoc, deliveryLoc);
                order.setMerchantId(m.getId());
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
                order.setStatus(1);
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

    // 2. 高频同步 (10Hz)：从 Redis 提取 Python 引擎计算好的骑手坐标，并处理到达事件
    @Scheduled(fixedRate = 100)
    public void syncFromPython() {
        if (!isRunning || isPaused) return;
        try {
            // 1. 读取骑手物理坐标并合并财务数据
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

            // 2. 读取 Python 推送的到达事件 (队列)
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

    private void handleRiderReachTarget(String riderId, String orderId, int status) {
        Order currentOrder = activeOrders.stream().filter(o -> o.getId().equals(orderId)).findFirst().orElse(null);
        if (currentOrder == null) return;

        try {
            if (status == 1) { // 骑手到达取餐点 → 订单变为"配送中"
                currentOrder.setStatus(2);
                redisTemplate.opsForHash().put("game:rider:status", riderId, "2");
                redisTemplate.opsForHash().put("game:rider:targets", riderId, objectMapper.writeValueAsString(currentOrder.getDeliveryLocation()));
                
                broadcastState("ORDER_STATUS_CHANGED", currentOrder);
                System.out.println(">>> 骑手 " + riderId + " 已取餐，订单 " + orderId.substring(0, 8) + " 进入配送中");
                
            } else if (status == 2) { // 骑手到达送餐点 → 订单完成
                currentOrder.setStatus(3);
                
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
        long durationMs = System.currentTimeMillis() - order.getCreateTime();
        double durationSec = durationMs / 1000.0;

        // 1. 配送时效分 S_delivery: 根据耗时 T (秒)
        double sDelivery;
        if (durationSec <= 15.0) {
            sDelivery = 5.0;
        } else if (durationSec <= 30.0) {
            sDelivery = 5.0 - ((durationSec - 15.0) / 15.0) * 1.0;
        } else if (durationSec <= 60.0) {
            sDelivery = 4.0 - ((durationSec - 30.0) / 30.0) * 2.0;
        } else {
            sDelivery = 1.0;
        }

        // 2. 顾客对餐品质量的随机打分 S_quality
        double sQuality = (random.nextDouble() < 0.9) ? (3.5 + random.nextDouble() * 1.5) : (1.0 + random.nextDouble() * 2.0);

        // 3. 单次订单综合得分 S_order = 0.4 * sDelivery + 0.6 * sQuality
        double sOrder = 0.4 * sDelivery + 0.6 * sQuality;

        // 4. === 骑手单笔提成结算 (基于动态可配置区间 [riderBonusMin, riderBonusMax]) ===
        double speedRatio = Math.max(0.0, Math.min(1.0, (30.0 - durationSec) / 30.0));
        double rawBonus = riderBonusMin + (riderBonusMax - riderBonusMin) * speedRatio;
        final double riderBonus = Math.round(rawBonus * 100.0) / 100.0;

        Rider rFin = riderFinancialMap.get(riderId);
        if (rFin != null) {
            rFin.setBonus(Math.round((rFin.getBonus() + riderBonus) * 100.0) / 100.0);
            rFin.setTotalSalary(Math.round((rFin.getBaseSalary() + rFin.getBonus()) * 100.0) / 100.0);
        }
        totalExpenses.updateAndGet(e -> Math.round((e + riderBonus) * 100.0) / 100.0);

        // 5. === 商家与平台单笔订单收入结算 (基于动态可配置抽成比例 platformTakeRate) ===
        // 菜品总额 OrderValue 约 ¥24.00 ~ ¥36.00
        double orderValue = 30.0 * (0.6 + 0.4 * (sQuality + sDelivery) / 10.0);
        final double platformTake = Math.round((orderValue * platformTakeRate) * 100.0) / 100.0; // 动态平台抽成
        final double merchantIncome = Math.round((orderValue * (1.0 - platformTakeRate)) * 100.0) / 100.0; // 商家净得

        totalRevenue.updateAndGet(r -> Math.round((r + platformTake) * 100.0) / 100.0);

        // 6. 更新商家评分与订单收入
        if (order.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(order.getMerchantId()).orElse(null);
            if (merchant != null) {
                double oldRating = merchant.getRating();
                double newRating = oldRating * 0.85 + sOrder * 0.15;
                newRating = Math.max(0.1, Math.min(5.0, newRating));
                float roundedRating = (float) (Math.round(newRating * 10.0) / 10.0);

                merchant.setRating(roundedRating);
                merchant.setCompletedOrders(merchant.getCompletedOrders() + 1);
                merchant.setOngoingOrders(Math.max(0, merchant.getOngoingOrders() - 1));
                merchant.setOrderRevenue(Math.round((merchant.getOrderRevenue() + merchantIncome) * 100.0) / 100.0);
                merchant.setTotalIncome(Math.round((merchant.getOrderRevenue() - merchant.getCommission()) * 100.0) / 100.0);
                merchantRepository.save(merchant);
            }
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

        broadcastState("FINANCIAL_UPDATE", financialData);
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
