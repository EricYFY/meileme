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
    
    // 是否正在运行
    private boolean isRunning = false;

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
            
            // 广播系统启动和地图信息
            webSocketHandler.broadcastMessage(cachedMessage);
            webSocketHandler.broadcastMessage("{\"type\":\"SIMULATION_STARTED\"}");
            
        } catch (Exception e) {
            System.err.println("未能连接到 Python 引擎启动模拟: " + e.getMessage());
        }
    }

    public void stopSimulation() {
        this.isRunning = false;
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

    // 1. 每 4 秒生成一个新订单
    @Scheduled(fixedRate = 4000)
    public void generateOrder() {
        if (!isRunning) return;
        List<Merchant> merchants = merchantRepository.findAll();
        if (merchants.isEmpty()) return;

        // 轮盘赌算法根据商家评分选择取餐点
        double totalScore = merchants.stream().mapToDouble(Merchant::getRating).sum();
        double randomVal = random.nextDouble() * totalScore;
        double currentSum = 0;
        Merchant selectedMerchant = merchants.get(0);
        for (Merchant m : merchants) {
            currentSum += m.getRating();
            if (currentSum >= randomVal) {
                selectedMerchant = m;
                break;
            }
        }

        Coordinate pickup = selectedMerchant.getLocation();
        // 送餐点：从住宅区格子中随机选取
        Coordinate delivery;
        if (!residentialCells.isEmpty()) {
            int[] cell = residentialCells.get(random.nextInt(residentialCells.size()));
            delivery = new Coordinate(cell[0], cell[1]);
        } else {
            delivery = new Coordinate(random.nextInt(201) - 100, random.nextInt(201) - 100);
        }
        
        Order order = new Order(pickup, delivery);
        activeOrders.add(order);
        
        broadcastState("ORDER_CREATED", order);
    }

    // 2. 每秒执行一次自动派单 & 超时订单清理
    @Scheduled(fixedRate = 1000)
    public void assignOrders() {
        if (!isRunning) return;

        // ★ 检查待处理订单是否超时（超过 1 分钟 / 60,000ms 未被接单则置为失效并移除）
        long now = System.currentTimeMillis();
        Iterator<Order> iter = activeOrders.iterator();
        while (iter.hasNext()) {
            Order o = iter.next();
            if (o.getStatus() == 0 && (now - o.getCreateTime() > 60000)) {
                iter.remove();
                int expCount = expiredOrderCount.incrementAndGet();
                Map<String, Object> expPayload = new HashMap<>();
                expPayload.put("id", o.getId());
                expPayload.put("expiredCount", expCount);
                broadcastState("ORDER_EXPIRED", expPayload);
                System.out.println(">>> 订单 " + o.getId().substring(0, 8) + " 超过1分钟未接单，已标记为失效移除 (累计失效: " + expCount + ")");
            }
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
        if (!isRunning) return;
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
        // ... (此处留作扩展：根据时间计算评价，并存入数据库)
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
