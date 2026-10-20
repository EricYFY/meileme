package com.meileme.resort.model;

import com.meileme.resort.config.PendingCommandType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待生效指令。
 *
 * <p>所有玩家操作统一入队，于<b>次日 0 点</b>统一生效并二次校验；失败会广播 ACTION_FAILED。</p>
 */
@Data
public class PendingCommand {

    /** 指令唯一 ID */
    private String id;

    /** 指令类型 */
    private PendingCommandType type;

    /** 创建时的游戏日 */
    private int createdDay;

    /** 生效的游戏日（= 创建日 + 1） */
    private int effectiveDay;

    /** 指令载荷（不同指令类型键不同） */
    private Map<String, Object> payload = new LinkedHashMap<>();

    /** 人类可读描述（用于前端展示队列） */
    private String description;

    public PendingCommand() {
    }

    public PendingCommand(String id, PendingCommandType type, int createdDay, int effectiveDay,
                          Map<String, Object> payload, String description) {
        this.id = id;
        this.type = type;
        this.createdDay = createdDay;
        this.effectiveDay = effectiveDay;
        this.payload = payload == null ? new LinkedHashMap<>() : payload;
        this.description = description;
    }

    public String str(String key) {
        Object v = payload.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public double num(String key, double def) {
        Object v = payload.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int intVal(String key, int def) {
        return (int) Math.round(num(key, def));
    }
}
