package com.meileme.backend.model;

import com.meileme.backend.config.TeamOrderSize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 游客订单：散客 / 团建大单 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestOrder {

    public enum OrderType { LEISURE, TEAM }

    /** PENDING 待处理 / ACCEPTED 已接单 / REJECTED 已拒绝 / CHECK_IN 入住 / CONSUME 消费 / CHECK_OUT 退房 / CANCELLED 取消 / DEFAULTED 违约 */
    public enum Status { PENDING, ACCEPTED, REJECTED, CHECK_IN, CONSUME, CHECK_OUT, CANCELLED, DEFAULTED }

    private String id;
    private OrderType orderType;
    private Status status = Status.PENDING;
    private TeamOrderSize size;          // 仅团建单非空
    private int guestCount;
    private int requiredRooms;
    private double revenue;
    private double penalty;
    private boolean requiresRestaurant;
    private boolean requiresSpa;
    private boolean requiresViewpoint;
    private int createdDay;
    private int effectiveDay;            // 团建单生效游戏日（接受后 = 接受次日）

    /** 供前端展示的规模中文名 */
    public String getSizeName() {
        return size == null ? null : size.getLabel();
    }
}
