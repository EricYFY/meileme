package com.meileme.resort.model;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 图鉴收集系统运行时状态。
 *
 * <p>追踪玩家在当前对局中首次发现/解锁的各类元素：
 * <ul>
 *     <li>建筑形态 (首次建造/进化竣工)</li>
 *     <li>内装设施 (首次安装/升级竣工)</li>
 *     <li>地块地图 (首次解锁)</li>
 *     <li>员工岗位 (首次招聘入职)</li>
 * </ul>
 * </p>
 */
@Data
public class CodexState {

    /** 已解锁的建筑形态 ID 集合 */
    private Set<String> unlockedForms = new LinkedHashSet<>();

    /** 已解锁的内装设施 ID 集合 */
    private Set<String> unlockedInteriors = new LinkedHashSet<>();

    /** 已解锁的地块地图 ID 集合 */
    private Set<String> unlockedPlots = new LinkedHashSet<>();

    /** 已解锁的员工岗位 ID 集合 */
    private Set<String> unlockedPositions = new LinkedHashSet<>();

    /**
     * 解锁建筑形态图鉴
     * @return true 若为首次解锁
     */
    public boolean unlockForm(String formId) {
        if (formId != null && !formId.isBlank()) {
            return unlockedForms.add(formId);
        }
        return false;
    }

    /**
     * 解锁内装设施图鉴
     * @return true 若为首次解锁
     */
    public boolean unlockInterior(String interiorId) {
        if (interiorId != null && !interiorId.isBlank()) {
            return unlockedInteriors.add(interiorId);
        }
        return false;
    }

    /**
     * 解锁地块地图图鉴
     * @return true 若为首次解锁
     */
    public boolean unlockPlot(String plotId) {
        if (plotId != null && !plotId.isBlank()) {
            return unlockedPlots.add(plotId);
        }
        return false;
    }

    /**
     * 解锁员工岗位图鉴
     * @return true 若为首次解锁
     */
    public boolean unlockPosition(String positionId) {
        if (positionId != null && !positionId.isBlank()) {
            return unlockedPositions.add(positionId);
        }
        return false;
    }
}
