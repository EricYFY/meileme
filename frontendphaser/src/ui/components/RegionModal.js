// 区块与地块解锁中心 (完全对齐 Vue3 RegionPanel.vue)
import { gameStore } from '../../network/gameStore.js';
import { fmtMoney, fmtInt } from '../../utils/format.js';

export default class RegionModal {
    constructor(overlayContainer, onClose) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.element = null;
        this.activeRegionId = 'LIMGRAVE';
    }

    open() {
        this.close();
        // 默认选当前地块所在的大区
        const curPlot = gameStore.config?.plotsById?.[gameStore.currentPlotId];
        if (curPlot?.region) this.activeRegionId = curPlot.region;

        this.element = document.createElement('div');
        this.element.className = 'modal-mask interactive';
        this.render();
        this.container.appendChild(this.element);
    }

    close() {
        if (this.element && this.element.parentNode) {
            this.element.parentNode.removeChild(this.element);
            this.element = null;
        }
        if (this.onClose) this.onClose();
    }

    render() {
        const regions = gameStore.config?.regions || [];
        const plots = gameStore.config?.plots || [];
        const currentRegion = regions.find(r => r.id === this.activeRegionId) || regions[0] || {};
        const regionPlots = plots.filter(p => p.region === currentRegion.id);

        const unlockedPlots = new Set(gameStore.state?.unlockedPlots || []);
        const unlockedAch = new Set(gameStore.state?.unlockedAchievements || []);
        const funds = gameStore.state?.funds || 0;

        // 全局地块顺序
        const plotOrder = plots.map(p => p.id);

        // 辅助判断
        const previousPlotId = (plot) => {
            const idx = plotOrder.indexOf(plot.id);
            return idx > 0 ? plotOrder[idx - 1] : null;
        };

        const getLockReasons = (plot) => {
            const reasons = [];
            const prevId = previousPlotId(plot);
            if (prevId && !unlockedPlots.has(prevId)) {
                const prev = gameStore.config?.plotsById?.[prevId];
                reasons.push(`需先解锁上一地块「${prev?.displayName || prevId}」`);
            }
            for (const aid of plot.unlockAchievementIds || []) {
                if (!unlockedAch.has(aid)) {
                    const a = gameStore.config?.achievementsById?.[aid];
                    reasons.push(`达成成就「${a?.displayName || aid}」`);
                }
            }
            return reasons;
        };

        const regionTabsHtml = regions.map(r => `
            <button class="hud-btn ${r.id === this.activeRegionId ? 'active' : ''}" data-region-tab="${r.id}">
                🗺️ ${r.displayName || r.id}
            </button>
        `).join('');

        const plotCardsHtml = regionPlots.map(plot => {
            const isUnlocked = unlockedPlots.has(plot.id);
            const reasons = isUnlocked ? [] : getLockReasons(plot);
            const isEligible = !isUnlocked && reasons.length === 0;
            const cost = plot.unlockCost || 0;
            const canAfford = funds >= cost;

            // 统计该地块已建建筑数
            const bldCount = (gameStore.state?.buildings || []).filter(b => b.plotId === plot.id).length;

            return `
                <div class="game-card" style="${isUnlocked ? 'border-color:#10b981;' : ''}">
                    <div class="card-top">
                        <span class="card-title">📍 ${plot.displayName || plot.id}</span>
                        <span class="chip ${isUnlocked ? 'chip-value' : 'chip-category'}">
                            ${isUnlocked ? '✓ 已解锁使用' : `解锁资金: ${fmtMoney(cost)}`}
                        </span>
                    </div>

                    <div class="card-meta-chips">
                        <span class="chip chip-value">网格尺寸: ${plot.width || 7} × ${plot.height || 7}</span>
                        <span class="chip chip-category">天然可建格数: ${plot.buildableCellCount || 15}</span>
                        <span class="chip chip-value">已建建筑: ${bldCount}</span>
                    </div>

                    <div style="font-size:12px; color:var(--text-dim); line-height:1.5;">
                        <div>母本建筑白名单: ${(plot.allowedMothers || []).map(m => gameStore.config?.mothersById?.[m]?.displayName || m).join(' / ') || '不限'}</div>
                        <div>建筑进化层级上限: Tier ${plot.evolveTierCap ?? 2}</div>
                    </div>

                    ${isUnlocked ? `
                        <button class="action-btn" data-switch-plot="${plot.id}">
                            切换查看该地块地图 ➔
                        </button>
                    ` : (isEligible ? `
                        <div style="margin-top:4px;">
                            <div style="font-size:11px; color:var(--green); margin-bottom:4px;">✓ 前置条件均已达成，仅剩支付解锁费用</div>
                            <button class="action-btn gold" style="width:100%;" data-unlock-plot="${plot.id}" ${canAfford ? '' : 'disabled'}>
                                ${canAfford ? `支付并解锁地块 (${fmtMoney(cost)})` : '资金不足无法解锁'}
                            </button>
                        </div>
                    ` : `
                        <div style="background:rgba(239,68,68,0.12); border:1px solid rgba(239,68,68,0.25); border-radius:6px; padding:8px 10px; margin-top:4px;">
                            <div style="font-size:11px; color:#fca5a5; font-weight:700;">🔒 解锁前置未满足:</div>
                            ${reasons.map(r => `<div style="font-size:11px; color:#fca5a5; margin-top:2px;">• ${r}</div>`).join('')}
                        </div>
                    `)}
                </div>
            `;
        }).join('');

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">🗺️</span>
                        <span class="modal-title">全岛大区地块拓荒与解锁中心</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>

                <!-- 大区切换器 -->
                <div style="display:flex; gap:10px; padding:12px 24px; border-bottom:1px solid rgba(255,255,255,0.06);">
                    ${regionTabsHtml}
                </div>

                <div class="modal-body">
                    <!-- 当前大区客流介绍 -->
                    <div style="background:rgba(56,189,248,0.1); border:1px solid rgba(56,189,248,0.25); border-radius:10px; padding:14px 18px; margin-bottom:16px;">
                        <div style="font-size:15px; font-weight:800; color:#fff;">【${currentRegion.displayName || currentRegion.id}】辐射客流池</div>
                        <div style="font-size:12px; color:var(--text-dim); margin-top:4px;">
                            ${currentRegion.description || '周边重要客源地大区。'}
                        </div>
                        <div style="font-size:12px; color:var(--accent); margin-top:6px;">
                            每日基础客流潜量: <strong>${fmtInt(currentRegion.baseDailyFlow || 100)} 人/天</strong>
                        </div>
                    </div>

                    <h4 style="margin-bottom:12px; color:#fff;">📍 该区域所属地块列表</h4>
                    <div class="card-grid">
                        ${plotCardsHtml}
                    </div>
                </div>
            </div>
        `;

        this.attachListeners();
    }

    attachListeners() {
        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());

        this.element.querySelectorAll('[data-region-tab]').forEach(btn => {
            btn.addEventListener('click', () => {
                this.activeRegionId = btn.dataset.regionTab;
                this.render();
            });
        });

        this.element.querySelectorAll('[data-switch-plot]').forEach(btn => {
            btn.addEventListener('click', () => {
                const plotId = btn.dataset.switchPlot;
                gameStore.selectPlot(plotId);
                this.close();
            });
        });

        this.element.querySelectorAll('[data-unlock-plot]').forEach(btn => {
            btn.addEventListener('click', () => {
                const plotId = btn.dataset.unlockPlot;
                gameStore.unlockPlot(plotId);
                setTimeout(() => this.render(), 300);
            });
        });
    }
}
