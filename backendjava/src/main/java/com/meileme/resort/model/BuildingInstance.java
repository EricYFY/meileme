package com.meileme.resort.model;

import com.meileme.resort.config.BuildingCategory;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.CustomerSegment;
import com.meileme.resort.config.EvolutionLine;
import com.meileme.resort.config.MotherBuildingType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑实例。
 *
 * <p>住宿类建筑实例语义为<b>一间房</b>：容量 = 每间可接待人数（+ 承载型内装附加容量），
 * 分配人数 > 0 即视为该间售出，收入 = 售出间数 × 该形态固定房价。</p>
 */
@Data
public class BuildingInstance {

    /** 建筑唯一 ID */
    private String id;

    /** 所属母本 */
    private MotherBuildingType mother;

    /** 当前形态 */
    private BuildingForm form;

    /** 当前形态等级（Lv1~Lv3） */
    private int formLevel = 1;

    /** 所属地块 ID */
    private String plotId;

    /** 落格坐标（列，从左 0 起；-1 表示未落格） */
    private int cellX = -1;

    /** 落格坐标（行，从上 0 起；-1 表示未落格） */
    private int cellY = -1;

    /** 是否存活（合并进化的源建筑会被逻辑删除，保留墓碑记录） */
    private boolean alive = true;

    /** 内装列表 */
    private List<InteriorInstance> interiors = new ArrayList<>();

    /** 合并进化来源建筑 ID（非合并产物则为空列表） */
    private List<String> mergeSourceIds = new ArrayList<>();

    /** 建成 / 形态变更生效的游戏日 */
    private int builtDay;

    /** 是否住宿类（优先由形态决定；形态未定则回退至母本） */
    public boolean isAccommodation() {
        if (form != null) {
            return form.isAccommodation();
        }
        return mother != null && mother.getCategory() == BuildingCategory.ACCOMMODATION;
    }

    /** 是否配套类（优先由形态决定；形态未定则回退至母本） */
    public boolean isSupport() {
        if (form != null) {
            return form.isSupport();
        }
        return mother != null && mother.getCategory() == BuildingCategory.SUPPORT;
    }

    /** 进化线（由形态推导） */
    public EvolutionLine line() {
        return form == null ? EvolutionLine.BASE : form.getLine();
    }

    /** 与另一建筑是否在地图上相邻（同地块且曼哈顿距离 = 1，即上下左右） */
    public boolean isAdjacentTo(BuildingInstance o) {
        if (o == null || plotId == null || !plotId.equals(o.plotId)) {
            return false;
        }
        if (cellX < 0 || cellY < 0 || o.cellX < 0 || o.cellY < 0) {
            return false;
        }
        return Math.abs(cellX - o.cellX) + Math.abs(cellY - o.cellY) == 1;
    }

    /** 内装提供的指定客群吸引力加成（动态配置驱动） */
    public double interiorAffinityBonusOf(String segmentId, com.meileme.resort.service.ConfigRegistry registry) {
        if (segmentId == null) return 0;
        double sum = 0;
        for (InteriorInstance it : interiors) {
            double aff = 0;
            if (registry != null) {
                com.meileme.resort.config.InteriorConfig ic = registry.getInterior(it.getInteriorType().name());
                if (ic != null) {
                    aff = ic.segmentAffinityOf(segmentId);
                } else {
                    aff = it.getInteriorType().segmentAffinityOf(segmentId);
                }
            } else {
                aff = it.getInteriorType().segmentAffinityOf(segmentId);
            }
            sum += aff * it.getLevel();
        }
        return sum;
    }

    /** 内装提供的指定客群吸引力加成（按各内装等级累加） */
    public double interiorAffinityBonusOf(String segmentId) {
        return interiorAffinityBonusOf(segmentId, null);
    }

    /** 内装提供的指定客群吸引力加成（枚举兼容） */
    public double interiorAffinityBonusOf(CustomerSegment segment) {
        return segment == null ? 0 : interiorAffinityBonusOf(segment.name(), null);
    }

    /** 内装提供的房屋收益加成比例（按各内装等级累加） */
    public double interiorRevenueBonus() {
        double sum = 0;
        for (InteriorInstance it : interiors) {
            sum += it.getInteriorType().getRevenueBonusPerLevel() * it.getLevel();
        }
        return sum;
    }

    /** 查找指定内装类型的实例 */
    public InteriorInstance findInterior(com.meileme.resort.config.InteriorType type) {
        for (InteriorInstance it : interiors) {
            if (it.getInteriorType() == type) {
                return it;
            }
        }
        return null;
    }
}
