<script setup>
import { ref, computed } from 'vue';
import { store } from '../../services/store.js';
import ProgressBar from '../common/ProgressBar.vue';
import { fmtMoney, fmtInt } from '../../utils/format.js';

// 当前选中的大类 Tab
const currentTab = ref('ALL'); // 'ALL' | 'FORM' | 'INTERIOR' | 'PLOT' | 'POSITION'
// 过滤状态
const statusFilter = ref('ALL'); // 'ALL' | 'UNLOCKED' | 'LOCKED'
// 选中的详情项
const selectedItem = ref(null);

const codex = computed(() => store.state?.codex || {
    unlockedForms: [],
    unlockedInteriors: [],
    unlockedPlots: [],
    unlockedPositions: [],
});

const unlockedFormsSet = computed(() => new Set(codex.value.unlockedForms || []));
const unlockedInteriorsSet = computed(() => new Set(codex.value.unlockedInteriors || []));
const unlockedPlotsSet = computed(() => new Set(codex.value.unlockedPlots || []));
const unlockedPositionsSet = computed(() => new Set(codex.value.unlockedPositions || []));

// 1. 建筑形态图鉴
const formItems = computed(() => {
    const list = store.config?.buildingForms || [];
    return list.map((f, idx) => {
        const isUnlocked = unlockedFormsSet.value.has(f.id);
        return {
            category: 'FORM',
            categoryName: '建筑形态',
            categoryIcon: '🏰',
            code: `F-${String(idx + 1).padStart(2, '0')}`,
            id: f.id,
            name: f.displayName || f.name || f.id,
            isUnlocked,
            tier: f.tier,
            buildingCategory: f.category === 'ACCOMMODATION' ? '住宿类' : '配套类',
            capacityDesc: f.category === 'ACCOMMODATION' 
                ? `容纳 ${f.capacityPerRoom} 人/间` 
                : `服务容量 ${f.serviceCapacity} 人`,
            priceDesc: f.category === 'ACCOMMODATION'
                ? `房价: ${f.pricePerRoomByLevel ? f.pricePerRoomByLevel.join(' / ') : '--'}`
                : `单价: ${f.unitPriceByLevel ? f.unitPriceByLevel.join(' / ') : '--'}`,
            unlockTip: '首次完成该建筑形态的建造或分支/合并进化时解锁。',
            detail: f,
        };
    });
});

// 2. 内装设施图鉴
const interiorItems = computed(() => {
    const list = store.config?.interiors || [];
    return list.map((it, idx) => {
        const isUnlocked = unlockedInteriorsSet.value.has(it.id);
        return {
            category: 'INTERIOR',
            categoryName: '内装设施',
            categoryIcon: '🛋️',
            code: `I-${String(idx + 1).padStart(2, '0')}`,
            id: it.id,
            name: it.displayName || it.id,
            isUnlocked,
            targetCategory: it.targetCategory === 'ACCOMMODATION' ? '适用于住宿类' : '适用于配套类',
            comfortBonus: it.comfortBonusByLevel ? it.comfortBonusByLevel.join(' / ') : '--',
            revenueBonus: it.revenueBonusByLevel ? it.revenueBonusByLevel.join(' / ') : '--',
            unlockTip: '首次在建筑中完成该内装设施的新建或升级时解锁。',
            detail: it,
        };
    });
});

// 3. 度假村地块图鉴
const plotItems = computed(() => {
    const list = store.config?.plots || [];
    return list.map((p, idx) => {
        const isUnlocked = unlockedPlotsSet.value.has(p.id);
        const regionCfg = store.config?.regionsById?.[p.region];
        return {
            category: 'PLOT',
            categoryName: '度假村地块',
            categoryIcon: '🗺️',
            code: `P-${String(idx + 1).padStart(2, '0')}`,
            id: p.id,
            name: p.displayName || p.id,
            isUnlocked,
            regionName: regionCfg?.displayName || p.region || '度假区',
            gridSize: `${p.gridWidth || 4} × ${p.gridHeight || 4} 单元格`,
            costDesc: p.isUnlockedByDefault ? '初始领地' : `解锁资金: ${fmtMoney(p.unlockCost)}`,
            unlockTip: '首次购买并解锁该地块时解锁。',
            detail: p,
        };
    });
});

// 4. 员工岗位图鉴
const positionItems = computed(() => {
    const list = store.config?.positions || [];
    return list.map((pos, idx) => {
        const isUnlocked = unlockedPositionsSet.value.has(pos.id);
        const abilityCfg = store.config?.abilitiesById?.[pos.abilityType];
        return {
            category: 'POSITION',
            categoryName: '员工岗位',
            categoryIcon: '👔',
            code: `S-${String(idx + 1).padStart(2, '0')}`,
            id: pos.id,
            name: pos.displayName || pos.id,
            isUnlocked,
            abilityName: abilityCfg?.displayName || pos.abilityType || '专业服务',
            abilityValues: pos.abilityValueByLevel ? pos.abilityValueByLevel.join(' / ') : '--',
            salaries: pos.salaryByLevel ? pos.salaryByLevel.join(' / ') : '--',
            unlockTip: '首次在度假村中成功招聘该岗位的员工时解锁。',
            detail: pos,
        };
    });
});

// 统计总数与分类数
const stats = computed(() => {
    const fTotal = formItems.value.length;
    const fCount = formItems.value.filter(i => i.isUnlocked).length;
    const iTotal = interiorItems.value.length;
    const iCount = interiorItems.value.filter(i => i.isUnlocked).length;
    const pTotal = plotItems.value.length;
    const pCount = plotItems.value.filter(i => i.isUnlocked).length;
    const sTotal = positionItems.value.length;
    const sCount = positionItems.value.filter(i => i.isUnlocked).length;

    const total = fTotal + iTotal + pTotal + sTotal;
    const unlocked = fCount + iCount + pCount + sCount;
    const ratio = total > 0 ? unlocked / total : 0;
    const percent = (ratio * 100).toFixed(1);

    let title = '初出茅庐';
    if (ratio >= 1.0) title = '👑 全知全能度假王国典藏家';
    else if (ratio >= 0.75) title = '💎 度假王国传奇大师';
    else if (ratio >= 0.5) title = '🌟 卓越庄园开拓者';
    else if (ratio >= 0.25) title = '🌿 度假村筑梦新秀';

    return {
        total,
        unlocked,
        ratio,
        percent,
        title,
        form: { total: fTotal, unlocked: fCount, ratio: fTotal ? fCount / fTotal : 0 },
        interior: { total: iTotal, unlocked: iCount, ratio: iTotal ? iCount / iTotal : 0 },
        plot: { total: pTotal, unlocked: pCount, ratio: pTotal ? pCount / pTotal : 0 },
        position: { total: sTotal, unlocked: sCount, ratio: sTotal ? sCount / sTotal : 0 },
    };
});

// 过滤后的条目列表
const filteredItems = computed(() => {
    let items = [];
    if (currentTab.value === 'ALL') {
        items = [
            ...formItems.value,
            ...interiorItems.value,
            ...plotItems.value,
            ...positionItems.value,
        ];
    } else if (currentTab.value === 'FORM') {
        items = formItems.value;
    } else if (currentTab.value === 'INTERIOR') {
        items = interiorItems.value;
    } else if (currentTab.value === 'PLOT') {
        items = plotItems.value;
    } else if (currentTab.value === 'POSITION') {
        items = positionItems.value;
    }

    if (statusFilter.value === 'UNLOCKED') {
        return items.filter(i => i.isUnlocked);
    }
    if (statusFilter.value === 'LOCKED') {
        return items.filter(i => !i.isUnlocked);
    }
    return items;
});
</script>

<template>
    <div class="codex-container">
        <!-- 顶部总览大卡片 -->
        <div class="codex-header-card">
            <div class="master-banner">
                <div class="banner-left">
                    <div class="badge-tag">📖 度假村典藏图鉴系统</div>
                    <div class="master-title">{{ stats.title }}</div>
                    <div class="master-subtitle faint">
                        收集全岛建筑形态、精选内装、领地地块与精英岗位，记录度假村每一处辉煌印记。
                    </div>
                </div>
                <div class="banner-right">
                    <div class="overall-num-box">
                        <span class="unlocked-big num gold">{{ stats.unlocked }}</span>
                        <span class="total-faint num">/ {{ stats.total }}</span>
                    </div>
                    <div class="overall-pct faint">总达成率 <strong class="pos num">{{ stats.percent }}%</strong></div>
                    <div class="bar-wrap">
                        <ProgressBar :ratio="stats.ratio" :color="'gold'" />
                    </div>
                </div>
            </div>

            <!-- 四大类子进度卡片 -->
            <div class="sub-progress-grid">
                <div class="sub-prog-card" :class="{ active: currentTab === 'FORM' }" @click="currentTab = 'FORM'">
                    <div class="sp-icon">🏰</div>
                    <div class="sp-info">
                        <div class="sp-name">建筑形态</div>
                        <div class="sp-val num">{{ stats.form.unlocked }} / {{ stats.form.total }}</div>
                        <ProgressBar :ratio="stats.form.ratio" :color="'accent'" />
                    </div>
                </div>

                <div class="sub-prog-card" :class="{ active: currentTab === 'INTERIOR' }" @click="currentTab = 'INTERIOR'">
                    <div class="sp-icon">🛋️</div>
                    <div class="sp-info">
                        <div class="sp-name">内装设施</div>
                        <div class="sp-val num">{{ stats.interior.unlocked }} / {{ stats.interior.total }}</div>
                        <ProgressBar :ratio="stats.interior.ratio" :color="'pos'" />
                    </div>
                </div>

                <div class="sub-prog-card" :class="{ active: currentTab === 'PLOT' }" @click="currentTab = 'PLOT'">
                    <div class="sp-icon">🗺️</div>
                    <div class="sp-info">
                        <div class="sp-name">度假村地块</div>
                        <div class="sp-val num">{{ stats.plot.unlocked }} / {{ stats.plot.total }}</div>
                        <ProgressBar :ratio="stats.plot.ratio" :color="'gold'" />
                    </div>
                </div>

                <div class="sub-prog-card" :class="{ active: currentTab === 'POSITION' }" @click="currentTab = 'POSITION'">
                    <div class="sp-icon">👔</div>
                    <div class="sp-info">
                        <div class="sp-name">员工岗位</div>
                        <div class="sp-val num">{{ stats.position.unlocked }} / {{ stats.position.total }}</div>
                        <ProgressBar :ratio="stats.position.ratio" :color="'accent'" />
                    </div>
                </div>
            </div>
        </div>

        <!-- 过滤控制栏 -->
        <div class="codex-toolbar">
            <div class="tabs-group">
                <button class="filter-tab-btn" :class="{ active: currentTab === 'ALL' }" @click="currentTab = 'ALL'">
                    全部图鉴 ({{ stats.unlocked }}/{{ stats.total }})
                </button>
                <button class="filter-tab-btn" :class="{ active: currentTab === 'FORM' }" @click="currentTab = 'FORM'">
                    🏰 建筑形态 ({{ stats.form.unlocked }}/{{ stats.form.total }})
                </button>
                <button class="filter-tab-btn" :class="{ active: currentTab === 'INTERIOR' }" @click="currentTab = 'INTERIOR'">
                    🛋️ 内装设施 ({{ stats.interior.unlocked }}/{{ stats.interior.total }})
                </button>
                <button class="filter-tab-btn" :class="{ active: currentTab === 'PLOT' }" @click="currentTab = 'PLOT'">
                    🗺️ 度假村地块 ({{ stats.plot.unlocked }}/{{ stats.plot.total }})
                </button>
                <button class="filter-tab-btn" :class="{ active: currentTab === 'POSITION' }" @click="currentTab = 'POSITION'">
                    👔 员工岗位 ({{ stats.position.unlocked }}/{{ stats.position.total }})
                </button>
            </div>

            <div class="status-group">
                <button class="status-btn" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">
                    全部
                </button>
                <button class="status-btn" :class="{ active: statusFilter === 'UNLOCKED' }" @click="statusFilter = 'UNLOCKED'">
                    ✨ 仅已解锁
                </button>
                <button class="status-btn" :class="{ active: statusFilter === 'LOCKED' }" @click="statusFilter = 'LOCKED'">
                    🔒 仅未解锁
                </button>
            </div>
        </div>

        <!-- 图鉴卡片网格展示 -->
        <div class="codex-grid">
            <div 
                v-for="item in filteredItems" 
                :key="item.category + '_' + item.id"
                class="codex-card"
                :class="{ 'is-unlocked': item.isUnlocked, 'is-locked': !item.isUnlocked }"
                @click="selectedItem = item"
            >
                <!-- 卡片顶角编号与图标 -->
                <div class="card-head">
                    <span class="card-code num">{{ item.code }}</span>
                    <span class="card-cat-badge">{{ item.categoryIcon }} {{ item.categoryName }}</span>
                    <span class="status-indicator">
                        <span v-if="item.isUnlocked" class="unlocked-badge">已解锁</span>
                        <span v-else class="locked-badge">🔒 未收录</span>
                    </span>
                </div>

                <!-- 卡片内容主体 -->
                <div class="card-body">
                    <div class="card-avatar">
                        <div class="avatar-ring">
                            <span v-if="item.isUnlocked" class="avatar-icon">{{ item.categoryIcon }}</span>
                            <span v-else class="avatar-icon locked-icon">❓</span>
                        </div>
                    </div>

                    <div class="card-title">
                        <span v-if="item.isUnlocked" class="name-text">{{ item.name }}</span>
                        <span v-else class="locked-text">？？？？</span>
                    </div>

                    <!-- 属性指标摘要 -->
                    <div class="card-metrics">
                        <template v-if="item.isUnlocked">
                            <div v-if="item.category === 'FORM'" class="metric-row">
                                <span class="tag faint">{{ item.buildingCategory }}</span>
                                <span class="desc dim">{{ item.capacityDesc }}</span>
                            </div>
                            <div v-if="item.category === 'INTERIOR'" class="metric-row">
                                <span class="tag faint">{{ item.targetCategory }}</span>
                                <span class="desc dim">舒适度加成: {{ item.comfortBonus }}</span>
                            </div>
                            <div v-if="item.category === 'PLOT'" class="metric-row">
                                <span class="tag faint">{{ item.regionName }}</span>
                                <span class="desc dim">{{ item.gridSize }}</span>
                            </div>
                            <div v-if="item.category === 'POSITION'" class="metric-row">
                                <span class="tag faint">{{ item.abilityName }}</span>
                                <span class="desc dim">能力值: {{ item.abilityValues }}</span>
                            </div>
                        </template>

                        <template v-else>
                            <div class="lock-tip-box">
                                <span class="tip-icon">💡</span>
                                <span class="tip-text">{{ item.unlockTip }}</span>
                            </div>
                        </template>
                    </div>
                </div>

                <div class="card-footer">
                    <span v-if="item.isUnlocked" class="view-detail-hint">点击查看典藏详情 ➔</span>
                    <span v-else class="view-detail-hint faint">未收录，达成条件自动激活</span>
                </div>
            </div>
        </div>

        <!-- 详细信息弹窗 -->
        <div v-if="selectedItem" class="modal-backdrop" @click.self="selectedItem = null">
            <div class="modal-card codex-detail-modal">
                <div class="modal-head">
                    <div class="head-left">
                        <span class="m-icon">{{ selectedItem.categoryIcon }}</span>
                        <div>
                            <div class="m-title">
                                {{ selectedItem.isUnlocked ? selectedItem.name : '神秘未收录图鉴' }}
                            </div>
                            <div class="m-subtitle faint">
                                编号: {{ selectedItem.code }} · 类别: {{ selectedItem.categoryName }}
                            </div>
                        </div>
                    </div>
                    <button class="close-btn" @click="selectedItem = null">✕</button>
                </div>

                <div class="modal-body">
                    <div v-if="!selectedItem.isUnlocked" class="detail-locked-banner">
                        <div class="lock-big-icon">🔒</div>
                        <div class="lock-title">此图鉴尚未解锁</div>
                        <div class="lock-desc">{{ selectedItem.unlockTip }}</div>
                    </div>

                    <div v-else class="detail-content">
                        <!-- 建筑形态详情 -->
                        <div v-if="selectedItem.category === 'FORM'" class="detail-section">
                            <div class="section-title">🏰 建筑形态档案</div>
                            <div class="kv-grid">
                                <div class="kv-item"><span class="k">形态ID:</span><span class="v num">{{ selectedItem.id }}</span></div>
                                <div class="kv-item"><span class="k">大类分类:</span><span class="v">{{ selectedItem.buildingCategory }}</span></div>
                                <div class="kv-item"><span class="k">进化阶层:</span><span class="v">Tier {{ selectedItem.tier }}</span></div>
                                <div class="kv-item"><span class="k">容量规格:</span><span class="v">{{ selectedItem.capacityDesc }}</span></div>
                                <div class="kv-item full"><span class="k">收益配置:</span><span class="v">{{ selectedItem.priceDesc }}</span></div>
                            </div>
                        </div>

                        <!-- 内装设施详情 -->
                        <div v-if="selectedItem.category === 'INTERIOR'" class="detail-section">
                            <div class="section-title">🛋️ 内装设施档案</div>
                            <div class="kv-grid">
                                <div class="kv-item"><span class="k">内装ID:</span><span class="v num">{{ selectedItem.id }}</span></div>
                                <div class="kv-item"><span class="k">适用大类:</span><span class="v">{{ selectedItem.targetCategory }}</span></div>
                                <div class="kv-item full"><span class="k">各级舒适度:</span><span class="v num">{{ selectedItem.comfortBonus }}</span></div>
                                <div class="kv-item full"><span class="k">各级收益加成:</span><span class="v num">{{ selectedItem.revenueBonus }}</span></div>
                            </div>
                        </div>

                        <!-- 度假村地块详情 -->
                        <div v-if="selectedItem.category === 'PLOT'" class="detail-section">
                            <div class="section-title">🗺️ 度假村地块档案</div>
                            <div class="kv-grid">
                                <div class="kv-item"><span class="k">地块ID:</span><span class="v num">{{ selectedItem.id }}</span></div>
                                <div class="kv-item"><span class="k">所属区域:</span><span class="v">{{ selectedItem.regionName }}</span></div>
                                <div class="kv-item"><span class="k">领地网格:</span><span class="v">{{ selectedItem.gridSize }}</span></div>
                                <div class="kv-item full"><span class="k">开拓说明:</span><span class="v">{{ selectedItem.costDesc }}</span></div>
                            </div>
                        </div>

                        <!-- 员工岗位详情 -->
                        <div v-if="selectedItem.category === 'POSITION'" class="detail-section">
                            <div class="section-title">👔 员工岗位档案</div>
                            <div class="kv-grid">
                                <div class="kv-item"><span class="k">岗位ID:</span><span class="v num">{{ selectedItem.id }}</span></div>
                                <div class="kv-item"><span class="k">负责能力:</span><span class="v">{{ selectedItem.abilityName }}</span></div>
                                <div class="kv-item full"><span class="k">各级服务能力:</span><span class="v num">{{ selectedItem.abilityValues }}</span></div>
                                <div class="kv-item full"><span class="k">每日薪资结构:</span><span class="v num">{{ selectedItem.salaries }}</span></div>
                            </div>
                        </div>

                        <div class="stamp-box">
                            <div class="stamp-seal">✓ 已收录典藏</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.codex-container {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding-bottom: 30px;
}

/* 顶部总览大卡片 */
.codex-header-card {
    background: linear-gradient(135deg, #17233d 0%, #10182b 100%);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 20px 24px;
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.master-banner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
}

.badge-tag {
    display: inline-block;
    font-size: 11px;
    font-weight: 700;
    color: var(--accent);
    background: rgba(90, 140, 255, 0.12);
    border: 1px solid rgba(90, 140, 255, 0.3);
    padding: 3px 10px;
    border-radius: 20px;
    margin-bottom: 6px;
}

.master-title {
    font-size: 24px;
    font-weight: 800;
    color: #fff;
    letter-spacing: .5px;
    margin-bottom: 4px;
}

.master-subtitle {
    font-size: 13px;
    max-width: 520px;
}

.banner-right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    min-width: 200px;
}

.overall-num-box {
    display: flex;
    align-items: baseline;
    gap: 6px;
}

.unlocked-big {
    font-size: 32px;
    font-weight: 800;
}

.total-faint {
    font-size: 18px;
}

.overall-pct {
    font-size: 13px;
    margin-bottom: 6px;
}

.bar-wrap {
    width: 220px;
}

/* 子分类统计 */
.sub-progress-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    gap: 12px;
}

.sub-prog-card {
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid var(--border-soft);
    border-radius: var(--radius-sm);
    padding: 12px 14px;
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: all .2s;
}

.sub-prog-card:hover {
    background: rgba(90, 140, 255, 0.08);
    border-color: var(--accent);
    transform: translateY(-2px);
}

.sub-prog-card.active {
    background: rgba(90, 140, 255, 0.14);
    border-color: var(--accent);
}

.sp-icon {
    font-size: 26px;
}

.sp-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.sp-name {
    font-size: 12px;
    color: var(--text-dim);
}

.sp-val {
    font-size: 14px;
    font-weight: 700;
}

/* 过滤工具栏 */
.codex-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 8px 12px;
}

.tabs-group, .status-group {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
}

.filter-tab-btn, .status-btn {
    background: transparent;
    border: 1px solid transparent;
    color: var(--text-dim);
    font-size: 12px;
    padding: 6px 12px;
    border-radius: 6px;
    cursor: pointer;
    transition: all .15s;
}

.filter-tab-btn:hover, .status-btn:hover {
    color: var(--text);
    background: var(--panel-2);
}

.filter-tab-btn.active, .status-btn.active {
    background: var(--accent-soft);
    color: #fff;
    border-color: var(--accent);
    font-weight: 600;
}

/* 图鉴卡片网格 */
.codex-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 14px;
}

.codex-card {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 14px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-height: 200px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: transform .2s, box-shadow .2s, border-color .2s;
}

.codex-card:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.4);
}

/* 已解锁样式 */
.codex-card.is-unlocked {
    border-color: rgba(90, 140, 255, 0.4);
    background: linear-gradient(180deg, #18233a 0%, #131b2e 100%);
}

.codex-card.is-unlocked:hover {
    border-color: var(--accent);
}

/* 未解锁样式 */
.codex-card.is-locked {
    background: linear-gradient(180deg, #131926 0%, #0d121c 100%);
    opacity: 0.82;
    border-style: dashed;
}

.card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    margin-bottom: 12px;
}

.card-code {
    font-weight: 700;
    color: var(--gold);
    letter-spacing: .5px;
}

.card-cat-badge {
    background: var(--panel-3);
    padding: 2px 7px;
    border-radius: 4px;
    color: var(--text-dim);
}

.unlocked-badge {
    color: var(--pos);
    font-weight: 700;
}

.locked-badge {
    color: var(--text-faint);
}

.card-body {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 8px;
    flex: 1;
}

.card-avatar {
    margin-top: 4px;
}

.avatar-ring {
    width: 52px;
    height: 52px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--panel-2);
    border: 2px solid var(--border);
    transition: all .2s;
}

.is-unlocked .avatar-ring {
    border-color: rgba(90, 140, 255, 0.6);
    box-shadow: 0 0 16px rgba(90, 140, 255, 0.25);
}

.avatar-icon {
    font-size: 26px;
}

.locked-icon {
    color: var(--text-faint);
    font-size: 20px;
    font-weight: 800;
}

.card-title {
    font-size: 15px;
    font-weight: 700;
    margin-top: 2px;
}

.name-text {
    color: #fff;
}

.locked-text {
    color: var(--text-faint);
    letter-spacing: 2px;
}

.card-metrics {
    width: 100%;
    margin-top: 6px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.metric-row {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 8px;
    font-size: 12px;
}

.tag {
    background: rgba(255, 255, 255, 0.06);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 11px;
}

.lock-tip-box {
    display: flex;
    align-items: center;
    gap: 6px;
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.05);
    padding: 6px 10px;
    border-radius: 6px;
    font-size: 11px;
    color: var(--text-faint);
    text-align: left;
}

.tip-icon {
    font-size: 13px;
}

.tip-text {
    line-height: 1.4;
}

.card-footer {
    margin-top: 12px;
    padding-top: 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    font-size: 11px;
    text-align: center;
}

.view-detail-hint {
    color: var(--accent);
    font-weight: 500;
}

/* 模态框 */
.modal-backdrop {
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0, 0, 0, 0.65);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
}

.codex-detail-modal {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    width: 90%;
    max-width: 500px;
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.6);
    overflow: hidden;
}

.modal-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    background: var(--panel-2);
    border-bottom: 1px solid var(--border);
}

.head-left {
    display: flex;
    align-items: center;
    gap: 12px;
}

.m-icon {
    font-size: 28px;
}

.m-title {
    font-size: 17px;
    font-weight: 700;
    color: #fff;
}

.m-subtitle {
    font-size: 11px;
}

.close-btn {
    background: transparent;
    border: none;
    color: var(--text-dim);
    font-size: 18px;
    padding: 4px 8px;
}

.modal-body {
    padding: 20px;
    max-height: 75vh;
    overflow-y: auto;
}

.detail-locked-banner {
    text-align: center;
    padding: 30px 10px;
}

.lock-big-icon {
    font-size: 42px;
    margin-bottom: 10px;
}

.lock-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--text-dim);
    margin-bottom: 8px;
}

.lock-desc {
    font-size: 13px;
    color: var(--text-faint);
    max-width: 340px;
    margin: 0 auto;
    line-height: 1.5;
}

.detail-section {
    margin-bottom: 20px;
}

.section-title {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-dim);
    margin-bottom: 12px;
    border-bottom: 1px solid var(--border-soft);
    padding-bottom: 6px;
}

.kv-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}

.kv-item {
    display: flex;
    flex-direction: column;
    gap: 3px;
    background: var(--panel-2);
    padding: 8px 12px;
    border-radius: 6px;
}

.kv-item.full {
    grid-column: 1 / -1;
}

.kv-item .k {
    font-size: 11px;
    color: var(--text-faint);
}

.kv-item .v {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
}

.stamp-box {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
}

.stamp-seal {
    border: 2px solid var(--pos);
    color: var(--pos);
    font-weight: 800;
    font-size: 12px;
    padding: 4px 12px;
    border-radius: 6px;
    transform: rotate(-6deg);
    box-shadow: 0 0 10px rgba(67, 214, 154, 0.25);
    letter-spacing: 1px;
}
</style>
