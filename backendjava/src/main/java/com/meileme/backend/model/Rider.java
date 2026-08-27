package com.meileme.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rider {
    private String id;
    private Coordinate currentPosition; // 当前坐标
    private Coordinate targetPosition;  // 目标坐标 (寻路终点)
    private float speed;                // 移动速度 (单位/秒)
    private int status;                 // 0: 空闲, 1: 前往取餐, 2: 配送中
    private String currentOrderId;      // 正在处理的订单ID
    
    public Rider(Coordinate start) {
        this.id = UUID.randomUUID().toString();
        this.currentPosition = start;
        this.speed = 2.0f; 
        this.status = 0;
    }
}
