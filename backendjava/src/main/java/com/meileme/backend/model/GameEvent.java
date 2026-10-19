package com.meileme.backend.model;

import com.meileme.backend.config.EventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/** 随机事件实体 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameEvent {

    private String id;
    private EventType type;
    private int day;
    private String description;
    private Map<String, Object> effects = new HashMap<>();
}
