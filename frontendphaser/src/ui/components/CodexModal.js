// 图鉴典藏系统弹窗 (完全遵循最新配置驱动规范与文学卷轴设计)
import { gameStore } from '../../network/gameStore.js';

export default class CodexModal {
    constructor(overlayContainer, onClose) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.element = null;
        this.currentTab = 'FORM'; // 'FORM' | 'INTERIOR' | 'PLOT' | 'POSITION'
        this.selectedDetailItem = null;
    }

    open() {
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
        if (this.selectedDetailItem) {
            this.renderStoryDetail(this.selectedDetailItem);
            return;
        }

        const codex = gameStore.state?.codex || {};
        const unlockedForms = new Set(codex.unlockedForms || []);
        const unlockedInteriors = new Set(codex.unlockedInteriors || []);
        const unlockedPlots = new Set(codex.unlockedPlots || []);
        const unlockedPositions = new Set(codex.unlockedPositions || []);

        const interiorCatMap = {
            CARRY: '承载设施',
            NORMAL: '普通设施',
            COMFORT: '舒适设施',
            ENTERTAINMENT: '娱乐设施',
        };

        let items = [];

        if (this.currentTab === 'FORM') {
            const list = gameStore.config?.buildingForms || [];
            items = list.map((f, idx) => {
                const isUnlocked = unlockedForms.has(f.id);
                const catName = f.category === 'ACCOMMODATION' ? '住宿类' : (f.category === 'SUPPORT' ? '配套类' : (f.category || '建筑形态'));
                return {
                    id: f.id,
                    name: f.displayName || f.name || f.id,
                    isUnlocked,
                    code: `F-${String(idx + 1).padStart(2, '0')}`,
                    icon: '🏰',
                    propCategory: catName,
                    propDetail: `基础舒适度 ${f.baseComfort ?? 0}`,
                    story: f.story || '',
                    tip: '首次建造或进化完成该建筑形态时解锁。'
                };
            });
        } else if (this.currentTab === 'INTERIOR') {
            const list = gameStore.config?.interiors || [];
            items = list.map((it, idx) => {
                const isUnlocked = unlockedInteriors.has(it.id);
                const catName = it.categoryDisplayName || interiorCatMap[it.category] || it.category || '内装设施';
                const revenuePct = it.revenueBonusPerLevel != null ? Number((it.revenueBonusPerLevel * 100).toFixed(1)) : 0;
                return {
                    id: it.id,
                    name: it.displayName || it.id,
                    isUnlocked,
                    code: `I-${String(idx + 1).padStart(2, '0')}`,
                    icon: '🛋️',
                    propCategory: catName,
                    propDetail: `每级收益 +${revenuePct}%`,
                    story: it.story || '',
                    tip: '首次在建筑中安装或升级该内装时解锁。'
                };
            });
        } else if (this.currentTab === 'PLOT') {
            const list = gameStore.config?.plots || [];
            items = list.map((p, idx) => {
                const isUnlocked = unlockedPlots.has(p.id);
                const regionName = gameStore.config?.regionsById?.[p.region]?.displayName || p.region || '度假区';
                const width = p.width ?? p.gridWidth ?? 0;
                const height = p.height ?? p.gridHeight ?? 0;
                return {
                    id: p.id,
                    name: p.displayName || p.id,
                    isUnlocked,
                    code: `P-${String(idx + 1).padStart(2, '0')}`,
                    icon: '🗺️',
                    propCategory: regionName,
                    propDetail: `长宽 ${width} × ${height}`,
                    story: p.story || '',
                    tip: '首次购买并解锁该地块时收录。'
                };
            });
        } else if (this.currentTab === 'POSITION') {
            const list = gameStore.config?.positions || [];
            items = list.map((pos, idx) => {
                const isUnlocked = unlockedPositions.has(pos.id);
                const hireCostText = pos.hireCost != null ? `招聘费 ¥${pos.hireCost}` : '招聘费 --';
                const upgradeDays = Array.isArray(pos.upgradeDaysByLevel) && pos.upgradeDaysByLevel.length
                    ? pos.upgradeDaysByLevel.join('/')
                    : (pos.upgradeDaysByLevel ?? '--');
                return {
                    id: pos.id,
                    name: pos.displayName || pos.id,
                    isUnlocked,
                    code: `S-${String(idx + 1).padStart(2, '0')}`,
                    icon: '👔',
                    propCategory: hireCostText,
                    propDetail: `升级工作 ${upgradeDays} 天`,
                    story: pos.story || '',
                    tip: '首次招聘签约该岗位员工时解锁。'
                };
            });
        }

        const cardsHtml = items.map(item => `
            <div class="game-card ${item.isUnlocked ? '' : 'locked'}" data-codex-id="${item.id}" style="${item.isUnlocked ? 'cursor:pointer;' : 'opacity:0.65;'}">
                <div class="card-top">
                    <span style="font-size:11px; font-weight:700; color:var(--text-faint);">${item.code}</span>
                    <span class="chip ${item.isUnlocked ? 'chip-value' : 'chip-category'}">
                        ${item.isUnlocked ? '✓ 已收录' : '🔒 未收录'}
                    </span>
                </div>
                <div style="display:flex; align-items:center; gap:8px;">
                    <span style="font-size:24px;">${item.isUnlocked ? item.icon : '❓'}</span>
                    <span class="card-title">${item.isUnlocked ? item.name : '？？？？'}</span>
                </div>
                ${item.isUnlocked ? `
                    <div class="card-meta-chips">
                        <span class="chip chip-category">${item.propCategory}</span>
                        <span class="chip chip-value">${item.propDetail}</span>
                    </div>
                    <div style="font-size:11px; color:var(--accent); text-align:right; margin-top:4px;">
                        点击查看典藏故事 ➔
                    </div>
                ` : `
                    <div style="font-size:11px; color:var(--text-faint); line-height:1.4;">
                        💡 ${item.tip}
                    </div>
                `}
            </div>
        `).join('');

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">📖</span>
                        <span class="modal-title">度假村全岛典藏图鉴系统</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>

                <!-- 分类 Tabs -->
                <div style="display:flex; gap:10px; padding:12px 24px; border-bottom:1px solid rgba(255,255,255,0.06);">
                    <button class="hud-btn ${this.currentTab === 'FORM' ? 'active' : ''}" id="tab-form">🏰 建筑形态</button>
                    <button class="hud-btn ${this.currentTab === 'INTERIOR' ? 'active' : ''}" id="tab-interior">🛋️ 内装设施</button>
                    <button class="hud-btn ${this.currentTab === 'PLOT' ? 'active' : ''}" id="tab-plot">🗺️ 度假地块</button>
                    <button class="hud-btn ${this.currentTab === 'POSITION' ? 'active' : ''}" id="tab-position">👔 员工岗位</button>
                </div>

                <div class="modal-body">
                    <div class="card-grid">
                        ${cardsHtml}
                    </div>
                </div>
            </div>
        `;

        this.attachListeners(items);
    }

    // 典藏详情弹窗：严格仅展示【名称】与【故事】两字段
    renderStoryDetail(item) {
        this.element.innerHTML = `
            <div class="modal-window" style="max-width:560px;">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">${item.icon}</span>
                        <span class="modal-title">典藏卷轴档案</span>
                    </div>
                    <button class="modal-close-btn" id="modal-back-btn">← 返回</button>
                </div>
                <div class="modal-body">
                    <div class="codex-archive-box">
                        <div>
                            <span class="archive-field-tag">名称</span>
                            <h2 class="archive-name-val">${item.name}</h2>
                        </div>
                        <div style="height:1px; background:rgba(229,193,125,0.25); margin:6px 0;"></div>
                        <div>
                            <span class="archive-field-tag">故事</span>
                            <div class="archive-quote-content">
                                ${item.story || '暂无更多典藏历史记载。'}
                            </div>
                        </div>
                        <div style="display:flex; justify-content:flex-end; margin-top:8px;">
                            <span style="border:2px solid #10b981; color:#10b981; font-weight:800; font-size:12px; padding:4px 12px; border-radius:6px; transform:rotate(-5deg);">
                                ✓ 已收录典藏
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        `;

        this.element.querySelector('#modal-back-btn').addEventListener('click', () => {
            this.selectedDetailItem = null;
            this.render();
        });
    }

    attachListeners(items) {
        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());

        const bindTab = (id, tabKey) => {
            const btn = this.element.querySelector(id);
            if (btn) btn.addEventListener('click', () => { this.currentTab = tabKey; this.render(); });
        };
        bindTab('#tab-form', 'FORM');
        bindTab('#tab-interior', 'INTERIOR');
        bindTab('#tab-plot', 'PLOT');
        bindTab('#tab-position', 'POSITION');

        this.element.querySelectorAll('[data-codex-id]').forEach(card => {
            card.addEventListener('click', () => {
                const id = card.dataset.codexId;
                const target = items.find(i => i.id === id);
                if (target && target.isUnlocked) {
                    this.selectedDetailItem = target;
                    this.render();
                }
            });
        });
    }
}
