package com.meileme.backend.model;

import lombok.Data;
import java.util.UUID;

@Data
public class Merchant {
    private String id;
    private Coordinate location;
    private float rating; // 1.0 to 5.0
    private int totalOrders;

    public Merchant(Coordinate location) {
        this.id = UUID.randomUUID().toString();
        this.location = location;
        this.rating = 5.0f; // 初始满分
        this.totalOrders = 0;
    }
}
