package com.meileme.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.backend.model.Coordinate;
import com.meileme.backend.model.Order;
import com.meileme.backend.model.Rider;
import com.meileme.backend.model.Merchant;
import com.meileme.backend.repository.MerchantRepository;
import com.meileme.backend.websocket.GameWebSocketHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GameEngineService {

    private final GameWebSocketHandler webSocketHandler;
    private final StringRedisTemplate redisTemplate;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    
    private final List<Order> activeOrders = new ArrayList<>();
    private List<Rider> currentRidersState = new ArrayList<>();
    private final Random random = new Random();
    
    // ★ Java 本地维护的"已占用"骑手集合，防止因 Redis 读取延迟导致同一骑手被重复派单
    private final Set<String> busyRiderIds = new HashSet<>();
    
    // 统计计数器
    private final AtomicInteger completedOrderCount = new AtomicInteger(0);
    private final AtomicInteger expiredOrderCount = new AtomicInteger(0);
    
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
        try {
            // 通过 HTTP POST 从 Python 服务启动并获取地图和商家数据
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Integer> requestBody = new HashMap<>();
            requestBody.put("merchantCount", merchantCount);
            requestBody.put("riderCount", riderCount);
            
            HttpEntity<Map<String, Integer>> request = new HttpEntity<>(requestBody, headers);
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
            
            this.isRunning = true;
            this.isPaused = false;
            
            // 广播系统启动和地图信息
            webSocketHandler.broadcastMessage(cachedMessage);
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
            webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_STARTED\"}");
            
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
            System.out.println("已通知 Python 引擎恢复物理模拟");
        } catch (Exception e) {
            System.err.println("通知 Python 引擎恢复失败: " + e.getMessage());
        }
        webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_RESUMED\"}");
        System.out.println("模拟已恢复");
    }

    public void stopSimulation() {
        this.isRunning = false;
        this.isPaused = false;
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForObject("http://localhost:8081/api/simulation/stop", null, String.class);
            System.out.println("已通知 Python 引擎停止模拟");
        } catch (Exception e) {
            System.err.println("通知 Python 引擎停止模拟失败: " + e.getMessage());
        }

        // 清空业务状态与计数器
        activeOrders.clear();
        currentRidersState.clear();
        busyRiderIds.clear();
        residentialCells.clear();
        merchantRepository.clear();
        completedOrderCount.set(0);
        expiredOrderCount.set(0);
        webSocketHandler.setCachedMapMessage(null);

        // 广播模拟结束事件
        webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_STOPPED\"}");
        System.out.println("模拟已结束，状态已全部重置");
    }

    // 1. 每 1 秒执行一次基于商家综合评分的分布式出单判定
    @Scheduled(fixedRate = 1000)
    public void generateOrder() {
        if (!isRunning || isPaused) return;
        List<Merchant> merchants = merchantRepository.findAll();
        if (merchants.isEmpty() || residentialCells.isEmpty()) return;

        boolean anyNewOrder = false;

        for (Merchant m : merchants) {
            // 基础出单概率 0.15，按 (rating / 5.0)^2 缩放
            // 5.0 分商家每秒产生概率为 0.15 (平均 6.6 秒一单)
            // 3.0 分商家每秒产生概率为 0.15 * 0.36 = 0.054 (平均 18.5 秒一单)
            double ratingRatio = Math.max(0.1, m.getRating()) / 5.0;
            double orderProb = 0.15 * Math.pow(ratingRatio, 2);

            if (random.nextDouble() < orderProb) {
                // 产生一单
                Coordinate pickup = m.getLocation();
                int[] cell = residentialCells.get(random.nextInt(residentialCells.size()));
                Coordinate delivery = new Coordinate(cell[0], cell[1]);

                Order order = new Order(m.getId(), pickup, delivery);
                activeOrders.add(order);

                // 更新商家的进行中订单数
                m.setOngoingOrders(m.getOngoingOrders() + 1);
                merchantRepository.save(m);

                broadcastState("ORDER_CREATED", order);
                anyNewOrder = true;
            }
        }

        if (anyNewOrder) {
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
        }
    }

    // 2. 每秒执行一次自动派单 & 超时订单清理
    @Scheduled(fixedRate = 1000)
    public void assignOrders() {
        if (!isRunning || isPaused) return;

        // ★ 检查待处理订单是否超时（超过 1 分钟 / 60,000ms 未被接单则置为失效并移除）
        long now = System.currentTimeMillis();
        Iterator<Order> iter = activeOrders.iterator();
        boolean merchantUpdated = false;
        while (iter.hasNext()) {
            Order o = iter.next();
            if (o.getStatus() == 0 && (now - o.getCreateTime() > 60000)) {
                iter.remove();
                int expCount = expiredOrderCount.incrementAndGet();
                
                if (o.getMerchantId() != null) {
                    merchantRepository.findById(o.getMerchantId()).ifPresent(m -> {
                        m.setOngoingOrders(Math.max(0, m.getOngoingOrders() - 1));
                        merchantRepository.save(m);
                    });
                    merchantUpdated = true;
                }

                Map<String, Object> expPayload = new HashMap<>();
                expPayload.put("id", o.getId());
                expPayload.put("expiredCount", expCount);
                broadcastState("ORDER_EXPIRED", expPayload);
                System.out.println(">>> 订单 " + o.getId().substring(0, 8) + " 超过1分钟未接单，已标记为失效移除 (累计失效: " + expCount + ")");
            }
        }

        if (merchantUpdated) {
            broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
        }

        List<Order> unassignedOrders = activeOrders.stream().filter(o -> o.getStatus() == 0).collect(Collectors.toList());
        if (unassignedOrders.isEmpty() || currentRidersState.isEmpty()) return;

        for (Order order : unassignedOrders) {
            // 找出最近的空闲骑手（必须不在 busyRiderIds 中）
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
                
                // ★ 立刻锁定这个骑手，防止在下一次 syncFromPython 覆盖前被重复派单
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
                System.out.println(">>> 派单：骑手 " + bestRider.getId() + " → 订单 " + order.getId().substring(0, 8));
            }
        }
    }

    // 3. 高频同步 (10Hz)：从 Redis 提取 Python 引擎计算好的骑手坐标，并处理到达事件
    @Scheduled(fixedRate = 100)
    public void syncFromPython() {
        if (!isRunning || isPaused) return;
        try {
            // 1. 读取骑手物理坐标
            String ridersJson = redisTemplate.opsForValue().get("game:state:riders");
            if (ridersJson != null) {
                currentRidersState = objectMapper.readValue(ridersJson, new TypeReference<List<Rider>>() {});
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
            // 打印错误以便排查 JSON 序列化等问题
            System.err.println("同步 Python 状态发生异常: " + e.getMessage());
            e.printStackTrace();
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
                
                // 广播订单状态变化给前端
                broadcastState("ORDER_STATUS_CHANGED", currentOrder);
                System.out.println(">>> 骑手 " + riderId + " 已取餐，订单 " + orderId + " 进入配送中");
                
            } else if (status == 2) { // 骑手到达送餐点 → 订单完成
                currentOrder.setStatus(3);
                
                updateMerchantRating(currentOrder);
                activeOrders.remove(currentOrder);
                
                int compCount = completedOrderCount.incrementAndGet();
                Map<String, Object> compPayload = new HashMap<>();
                compPayload.put("id", currentOrder.getId());
                compPayload.put("completedCount", compCount);
                broadcastState("ORDER_COMPLETED", compPayload);

                // 解放骑手
                busyRiderIds.remove(riderId);
                redisTemplate.opsForHash().put("game:rider:status", riderId, "0");
                redisTemplate.opsForHash().put("game:rider:orders", riderId, "null");
                redisTemplate.opsForHash().put("game:rider:targets", riderId, "null");
                System.out.println(">>> 骑手 " + riderId + " 已送达，订单 " + orderId + " 完成，释放骑手 (累计完成: " + compCount + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMerchantRating(Order order) {
        if (order.getMerchantId() == null) return;
        Merchant merchant = merchantRepository.findById(order.getMerchantId()).orElse(null);
        if (merchant == null) return;

        // 1. 配送时效分 S_delivery: 根据耗时 T (秒)
        long durationMs = System.currentTimeMillis() - order.getCreateTime();
        double durationSec = durationMs / 1000.0;
        double sDelivery;
        if (durationSec <= 15.0) {
            sDelivery = 5.0;
        } else if (durationSec <= 30.0) {
            sDelivery = 5.0 - ((durationSec - 15.0) / 15.0) * 1.0; // 4.0 ~ 5.0
        } else if (durationSec <= 60.0) {
            sDelivery = 4.0 - ((durationSec - 30.0) / 30.0) * 2.0; // 2.0 ~ 4.0
        } else {
            sDelivery = 1.0;
        }

        // 2. 顾客对餐品质量的随机打分 S_quality: 90% 概率在 [3.5, 5.0]，10% 概率在 [1.0, 3.0]
        double sQuality;
        if (random.nextDouble() < 0.9) {
            sQuality = 3.5 + random.nextDouble() * 1.5;
        } else {
            sQuality = 1.0 + random.nextDouble() * 2.0;
        }

        // 3. 单次订单综合得分 S_order = 0.4 * sDelivery + 0.6 * sQuality
        double sOrder = 0.4 * sDelivery + 0.6 * sQuality;

        // 4. 指数平滑更新商家综合评分 (EMA)
        double oldRating = merchant.getRating();
        double newRating = oldRating * 0.85 + sOrder * 0.15;
        newRating = Math.max(0.1, Math.min(5.0, newRating));
        float roundedRating = (float) (Math.round(newRating * 10.0) / 10.0);

        merchant.setRating(roundedRating);
        merchant.setCompletedOrders(merchant.getCompletedOrders() + 1);
        merchant.setOngoingOrders(Math.max(0, merchant.getOngoingOrders() - 1));
        merchantRepository.save(merchant);

        System.out.println(String.format(">>> [评分更新] 商家 %s: 耗时 %.1fs -> 配送分 %.1f, 质量分 %.1f, 本单综合 %.1f, 商家评分 %.1f -> %.1f",
                merchant.getId(), durationSec, sDelivery, sQuality, sOrder, oldRating, roundedRating));

        broadcastState("MERCHANT_UPDATE", merchantRepository.findAll());
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
