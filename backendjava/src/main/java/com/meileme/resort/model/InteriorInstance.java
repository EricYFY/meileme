package com.meileme.resort.model;

import com.meileme.resort.config.InteriorType;
import lombok.Data;

/** 建筑内装实例（影响舒适度、承载容量、客群吸引力加成、房屋收益加成）。 */
@Data
public class InteriorInstance {

    /** 内装实例 ID */
    private String id;

    /** 内装类型 */
    private InteriorType interiorType;

    /** 当前等级 */
    private int level = 1;

    public InteriorInstance() {
    }

    public InteriorInstance(String id, InteriorType interiorType, int level) {
        this.id = id;
        this.interiorType = interiorType;
        this.level = level;
    }
}
