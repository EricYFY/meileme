package com.meileme.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {
    private String id;
    private Coordinate location;
    private float rating; // 1.0 to 5.0
    private int completedOrders;
    private int ongoingOrders;

    // === 财务收支字段 ===
    private double commission = 0.0;   // 累计支付给平台的入驻佣金
    private double orderRevenue = 0.0; // 累计菜品订单净收入 (抽成后)
    private double totalIncome = 0.0;  // 累计总收益 (订单收入 - 佣金)

    public Merchant(Coordinate location) {
        this.id = UUID.randomUUID().toString();
        this.location = location;
        this.rating = 5.0f; // 初始满分
        this.completedOrders = 0;
        this.ongoingOrders = 0;
        this.commission = 0.0;
        this.orderRevenue = 0.0;
        this.totalIncome = 0.0;
    }
}
