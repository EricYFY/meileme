package com.meileme.backend.model;

import com.meileme.backend.config.SalaryMode;
import com.meileme.backend.config.StaffType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 员工实体（底薪/效率/负面概率由 StaffType × SalaryMode 计算得出） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Staff {

    private String id;
    private StaffType type;
    private SalaryMode salaryMode;
    private double dailySalary;            // 结算用日薪（已按档位计算）
    private double efficiencyMultiplier;   // 效率倍率 1.20 / 1.00 / 0.80
    private double negativeEventProb;      // 每日负面事件概率 0~1
    private Coordinate position;           // 岗位可视化锚点（不占用建筑格）

    /** 供前端展示的中文岗位名 */
    public String getTypeName() {
        return type == null ? null : type.getLabel();
    }
}
