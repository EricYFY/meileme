package com.meileme.resort.service;

import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.StaffInstance;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** 全局累计统计刷新（建筑形态等级计数 / 员工岗位等级计数），供成就判定使用。 */
@Service
public class StatsService {

    /** 重建建筑与员工的「形态 + 等级」「岗位 + 等级」计数 */
    public void rebuild(GameState state) {
        Map<String, Integer> formCounts = new HashMap<>();
        for (BuildingInstance b : state.aliveBuildings()) {
            String key = b.getForm().getId() + "_" + b.getFormLevel();
            formCounts.merge(key, 1, Integer::sum);
        }
        Map<String, Integer> positionCounts = new HashMap<>();
        for (StaffInstance s : state.aliveStaffs()) {
            String key = s.getPosition().name() + "_" + s.getLevel();
            positionCounts.merge(key, 1, Integer::sum);
        }
        state.getStats().setFormLevelCounts(formCounts);
        state.getStats().setPositionLevelCounts(positionCounts);
    }
}
