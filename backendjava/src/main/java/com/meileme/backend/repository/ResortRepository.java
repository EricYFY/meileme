package com.meileme.backend.repository;

import com.meileme.backend.config.GameConfig;
import com.meileme.backend.model.DailyReport;
import com.meileme.backend.model.Resort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存度假村仓储：单例持有当前局的 Resort，并保留每日汇总历史供区间报告聚合。
 */
@Repository
public class ResortRepository {

    private volatile Resort resort;

    /** 每日汇总历史（按时间正序，最早在前），最多保留 REPORT_HISTORY_LIMIT 条 */
    private final List<DailyReport> history = new CopyOnWriteArrayList<>();

    public void save(Resort resort) {
        this.resort = resort;
    }

    public Resort get() {
        return resort;
    }

    public boolean exists() {
        return resort != null;
    }

    /** 追加一条每日汇总；超出上限时裁剪最旧的一条 */
    public void appendReport(DailyReport report) {
        if (report == null) return;
        history.add(report);
        while (history.size() > GameConfig.REPORT_HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    /** 取最近 n 天汇总（按时间正序返回，最多 n 条；不足则返回全部） */
    public List<DailyReport> recent(int n) {
        int size = history.size();
        if (n <= 0 || n >= size) return new ArrayList<>(history);
        return new ArrayList<>(history.subList(size - n, size));
    }

    /** 清空当前局与历史 */
    public void clear() {
        this.resort = null;
        this.history.clear();
    }
}
