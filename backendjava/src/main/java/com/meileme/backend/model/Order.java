package com.meileme.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String id;
    private String merchantId;
    private Coordinate pickupLocation;   // 商家取餐点
    private Coordinate deliveryLocation; // 客户送餐点
    
    /**
     * 订单状态枚举：
     * 0: CREATED (等待调度派单 / 商家备餐中)
     * 1: DISPATCHED (骑手已接单前往商家)
     * 2: WAITING_MEAL (骑手已到达商家，等待餐品出餐)
     * 3: DELIVERING (骑手已取餐，正在前往送餐点配送中)
     * 4: COMPLETED (已送达完成)
     * 5: EXPIRED (等餐超时或派单超时失效)
     */
    private int status = 0;
    
    // === 订单金额与成本 ===
    private double orderPrice = 20.0;    // 订单定价 (元)
    private double materialCost = 10.0;  // 耗材成本 (元)
    private double platformTake = 5.0;   // 平台抽成金额 (元)

    // === 时间戳与时效追踪 (毫秒) ===
    private long createTime;             // 订单创建时刻
    private long prepFinishTime;         // 预计商家备餐完成时刻 (T_ready)
    private long riderArriveMerchantTime = 0L; // 骑手到达商家时刻
    private long pickupTime = 0L;        // 骑手成功取餐时刻
    private long deliverTime = 0L;       // 订单送达时刻

    // === 订单时效与质量评分结果 ===
    private double prepDurationSec = 0.0;     // 实际出餐用时 (秒)
    private double pickupDurationSec = 0.0;   // 骑手到店用时 (秒)
    private double waitDurationSec = 0.0;     // 骑手在店等餐用时 (秒)
    private double deliveryDurationSec = 0.0; // 骑手送达路程用时 (秒)
    private double totalDurationSec = 0.0;    // 订单全流程总耗时 (秒)
    
    private double qualityScore = 5.0;        // 菜品耗材质量分 (1.0 ~ 5.0)
    private double timeScore = 5.0;           // 配送履约时效分 (1.0 ~ 5.0)
    private double finalScore = 5.0;          // 订单综合体验分 (1.0 ~ 5.0)

    public Order(Coordinate pickup, Coordinate delivery) {
        this(null, pickup, delivery);
    }

    public Order(String merchantId, Coordinate pickup, Coordinate delivery) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.pickupLocation = pickup;
        this.deliveryLocation = delivery;
        this.status = 0;
        this.createTime = System.currentTimeMillis();
    }
}

