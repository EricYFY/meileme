// 经营总览面板：今日预计、客群洞察、人力供需与建筑快速清册
import { gameStore } from '../../network/gameStore.js';
import { fmtMoney, fmtInt, fmtPct } from '../../utils/format.js';

export default class OverviewModal {
    constructor(overlayContainer, onClose, onOpenBuilding) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.onOpenBuilding = onOpenBuilding;
        this.element = null;
        this.activeSubTab = 'PREVIEW'; // 'PREVIEW' | 'GUESTS' | 'ABILITY' | 'BUILDINGS'
    }

    open(subTab = 'PREVIEW') {
        this.activeSubTab = subTab;
        this.close();
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
        const preview = gameStore.state?.todayPreview || {};
        const st = gameStore.state || {};
        const config = gameStore.config || {};

        // 1. 今日预计 HTML
        const previewHtml = `
            <div style="display:flex; flex-direction:column; gap:16px;">
                <div class="hud-pill" style="border-radius:10px; justify-content:space-between; padding:16px 20px;">
                    <div>
                        <div style="font-size:12px; color:var(--text-faint);">预计当日营业净利润 (次日 0 点结算生效)</div>
                        <div style="font-size:26px; font-weight:800; color:var(--gold); margin-top:4px;">
                            ${fmtMoney(preview.netProfit ?? 0)}
                        </div>
                    </div>
                    <div style="text-align:right;">
                        <div style="font-size:12px; color:var(--text-faint);">预计接待总人数</div>
                        <div style="font-size:22px; font-weight:800; color:var(--green); margin-top:4px;">
                            ${fmtInt(preview.expectedGuests ?? 0)} 人
                        </div>
                    </div>
                </div>

                <div class="card-grid" style="grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));">
                    <div class="game-card">
                        <span style="font-size:12px; color:var(--text-faint);">房屋客房收益</span>
                        <strong style="font-size:16px; color:var(--accent);">+${fmtMoney(preview.accommodationRevenue ?? 0)}</strong>
                        <span style="font-size:11px; color:var(--text-dim);">售出间数: ${preview.occupiedRooms ?? 0} / ${preview.totalRooms ?? 0}</span>
                    </div>
                    <div class="game-card">
                        <span style="font-size:12px; color:var(--text-faint);">配套体验收益</span>
                        <strong style="font-size:16px; color:var(--accent);">+${fmtMoney(preview.supportRevenue ?? 0)}</strong>
                        <span style="font-size:11px; color:var(--text-dim);">服务人次: ${fmtInt(preview.expectedGuests ?? 0)}</span>
                    </div>
                    <div class="game-card">
                        <span style="font-size:12px; color:var(--text-faint);">客房入住率</span>
                        <strong style="font-size:16px; color:var(--gold);">${fmtPct(preview.occupancyRate ?? 0)}</strong>
                        <span style="font-size:11px; color:var(--text-dim);">空置间数: ${Math.max(0, (preview.totalRooms ?? 0) - (preview.occupiedRooms ?? 0))}</span>
                    </div>
                </div>
            </div>
        `;

        // 2. 客群洞察与转化分析 HTML
        const segments = config.segments || [];
        const segmentRowsHtml = segments.map(s => {
            const potential = Number(preview.potentialBySegment?.[s.id] || 0);
            const intent = Number(preview.intentBySegment?.[s.id] || 0);
            const served = Number(preview.servedBySegment?.[s.id] || 0);
            const attraction = Number(preview.attractionBySegment?.[s.id] || 0);
            const conv = potential > 0 ? (intent / potential) : (preview.conversionBySegment?.[s.id] || 0);
            const overflow = Math.max(0, intent - served);

            return `
                <div class="game-card">
                    <div class="card-top">
                        <span class="card-title" style="color:${s.color || '#fff'};">👥 ${s.displayName || s.id}</span>
                        <span class="chip chip-value">综合吸引力 ${attraction.toFixed(1)}分</span>
                    </div>
                    <div class="card-meta-chips">
                        <span class="chip chip-category">转化率: ${fmtPct(conv)}</span>
                        <span class="chip chip-value">辐射潜量: ${fmtInt(potential)}人</span>
                    </div>
                    <div style="font-size:12px; color:var(--text-dim); display:flex; justify-content:space-between; margin-top:4px;">
                        <span>意向顾客: <strong style="color:#fff;">${fmtInt(intent)}</strong></span>
                        <span>实际接待: <strong style="color:var(--green);">${fmtInt(served)}</strong></span>
                        ${overflow > 0 ? `<span style="color:var(--red);">容量不足流失: ${fmtInt(overflow)}</span>` : '<span style="color:var(--green);">全部接待</span>'}
                    </div>
                </div>
            `;
        }).join('');

        // 3. 人力供需状况 HTML
        const abilities = config.abilities || [];
        const abilityRows = abilities.map(a => {
            const demand = (st.abilityDemand || {})[a.id] || 0;
            const supply = (st.abilitySupply || {})[a.id] || 0;
            const ratio = (st.abilityRatios || {})[a.id] ?? (demand > 0 ? supply / demand : 1.0);
            return {
                id: a.id,
                name: a.displayName || a.id,
                demand,
                supply,
                ratio,
                desc: a.description || ''
            };
        }).filter(r => r.demand > 0 || r.supply > 0);

        const abilityHtml = abilityRows.map(r => `
            <div class="game-card">
                <div class="card-top">
                    <span class="card-title">💼 ${r.name}</span>
                    <span class="chip ${r.ratio < 1.0 ? 'chip-value' : 'chip-category'}" style="${r.ratio < 1.0 ? 'color:var(--red); border-color:var(--red);' : ''}">
                        ${r.ratio < 1.0 ? '⚠️ 人力不足' : '✓ 满足保障'} (${fmtPct(r.ratio)})
                    </span>
                </div>
                <div style="font-size:13px; margin:4px 0;">
                    供给点数: <strong style="color:var(--accent);">${fmtInt(r.supply)}</strong> / 需求点数: <strong style="color:#fff;">${fmtInt(r.demand)}</strong>
                </div>
                ${r.ratio < 1.0 ? `
                    <div style="font-size:11px; color:var(--red); line-height:1.4;">
                        舒适度折损系数: ${(r.ratio * 100).toFixed(0)}%，将直接拉低顾客转化率！建议尽快招聘相关员工。
                    </div>
                ` : `
                    <div style="font-size:11px; color:var(--text-faint);">
                        ${r.desc || '专业能力充足，建筑舒适度 100% 满额运转。'}
                    </div>
                `}
            </div>
        `).join('') || '<div style="color:var(--text-faint); padding:20px; text-align:center;">暂无任何岗位能力需求。</div>';

        // 4. 建筑快速清册 HTML
        const buildings = st.buildings || [];
        const buildingsHtml = buildings.map(b => {
            const form = b.form || {};
            const isUnderConstruction = b.status === 'UNDER_CONSTRUCTION' || b.underConstruction;
            const plotName = config.plotsById?.[b.plotId]?.displayName || b.plotId;

            return `
                <div class="game-card" style="cursor:pointer;" data-open-bld-id="${b.id}">
                    <div class="card-top">
                        <span class="card-title">${form.displayName || b.id} (Lv${b.level || 1} ★)</span>
                        <span class="chip ${isUnderConstruction ? 'chip-value' : 'chip-category'}">
                            ${isUnderConstruction ? '🚧 施工中' : '🟢 正常营业'}
                        </span>
                    </div>
                    <div class="card-meta-chips">
                        <span class="chip chip-value">地块: ${plotName} (${b.cellX}, ${b.cellY})</span>
                        <span class="chip chip-category">舒适度: ${b.comfortScore || form.baseComfort || 50}</span>
                    </div>
                    <div style="font-size:11px; color:var(--accent); text-align:right; margin-top:4px;">
                        点击查看详情 / 升级演进 ➔
                    </div>
                </div>
            `;
        }).join('') || '<div style="color:var(--text-faint); padding:20px; text-align:center;">当前度假村尚未建造任何建筑。</div>';

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">📈</span>
                        <span class="modal-title">度假村经营宏观总览</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>

                <!-- 子导航 Tabs -->
                <div style="display:flex; gap:10px; padding:12px 24px; border-bottom:1px solid rgba(255,255,255,0.06);">
                    <button class="hud-btn ${this.activeSubTab === 'PREVIEW' ? 'active' : ''}" id="tab-sub-preview">📅 今日预计营收</button>
                    <button class="hud-btn ${this.activeSubTab === 'GUESTS' ? 'active' : ''}" id="tab-sub-guests">👥 客群转化画像</button>
                    <button class="hud-btn ${this.activeSubTab === 'ABILITY' ? 'active' : ''}" id="tab-sub-ability">💼 人力供需比对</button>
                    <button class="hud-btn ${this.activeSubTab === 'BUILDINGS' ? 'active' : ''}" id="tab-sub-bld">🏠 建筑快速清册 (${buildings.length})</button>
                </div>

                <div class="modal-body">
                    ${this.activeSubTab === 'PREVIEW' ? previewHtml : ''}
                    ${this.activeSubTab === 'GUESTS' ? `<div class="card-grid">${segmentRowsHtml}</div>` : ''}
                    ${this.activeSubTab === 'ABILITY' ? `<div class="card-grid">${abilityHtml}</div>` : ''}
                    ${this.activeSubTab === 'BUILDINGS' ? `<div class="card-grid">${buildingsHtml}</div>` : ''}
                </div>
            </div>
        `;

        this.attachListeners();
    }

    attachListeners() {
        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());

        const bindSubTab = (id, tabKey) => {
            const btn = this.element.querySelector(id);
            if (btn) btn.addEventListener('click', () => { this.activeSubTab = tabKey; this.render(); });
        };
        bindSubTab('#tab-sub-preview', 'PREVIEW');
        bindSubTab('#tab-sub-guests', 'GUESTS');
        bindSubTab('#tab-sub-ability', 'ABILITY');
        bindSubTab('#tab-sub-bld', 'BUILDINGS');

        this.element.querySelectorAll('[data-open-bld-id]').forEach(btn => {
            btn.addEventListener('click', () => {
                const bldId = btn.dataset.openBldId;
                this.close();
                if (this.onOpenBuilding) this.onOpenBuilding(bldId);
            });
        });
    }
}
