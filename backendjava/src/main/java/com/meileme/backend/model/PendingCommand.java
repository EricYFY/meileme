package com.meileme.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待生效指令：玩家在游戏运行期间下达的操作，统一在下一个游戏日 0 点生效。
 * 建造费 / 地块解锁费在点击时即刻扣除；其余指令只入队，次日生效。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingCommand {

    public enum Type {
        BUILD_BUILDING,      // 建造
        HIRE_STAFF,          // 招聘
        FIRE_STAFF,          // 解雇
        SET_ROOM_PRICE,      // 调整房价
        SET_AD_BUDGET,       // 调整广告预算
        ACCEPT_TEAM_ORDER,   // 接受团建单
        REJECT_TEAM_ORDER,   // 拒绝团建单
        UNLOCK_PLOT          // 解锁地块
    }

    private String id;
    private Type type;
    private String summary;   // 前端展示用中文描述
    private int queuedDay;    // 入队时所在游戏日

    // 参数按 type 取用：
    //   BUILD_BUILDING          -> text1=建筑枚举名, text2=地块ID
    //   HIRE_STAFF              -> text1=岗位枚举名, text2=薪资档位
    //   FIRE_STAFF              -> text1=员工ID
    //   ACCEPT/REJECT_TEAM_ORDER-> text1=订单ID, text2=规模名(仅展示参考)
    //   UNLOCK_PLOT             -> text1=地块ID
    //   SET_ROOM_PRICE          -> num1=普通房价, num2=豪华房价
    //   SET_AD_BUDGET           -> num1=广告预算
    private String text1;
    private String text2;
    private double num1;
    private double num2;
}
