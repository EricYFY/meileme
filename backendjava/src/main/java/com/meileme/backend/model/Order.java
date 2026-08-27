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
    private Coordinate pickupLocation;  // 商家取餐点
    private Coordinate deliveryLocation; // 客户送餐点
    private int status; // 0: 等待接单, 1: 骑手已接单前往取餐, 2: 骑手配送中, 3: 已送达
    private long createTime;
    
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
