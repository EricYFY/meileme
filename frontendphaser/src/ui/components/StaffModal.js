// 员工管理与人才市场弹窗
import { gameStore } from '../../network/gameStore.js';
import { fmtMoney } from '../../utils/format.js';

export default class StaffModal {
    constructor(overlayContainer, onClose) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.element = null;
        this.currentTab = 'HIRE'; // 'HIRE' | 'ROSTER' | 'SUPPLY'
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
        const positions = gameStore.config?.positions || [];
        const staffs = gameStore.state?.staffs || [];

        // 1. 招聘卡片
        const hireCardsHtml = positions.map(p => `
            <div class="game-card">
                <div class="card-top">
                    <span class="card-title">👔 ${p.displayName || p.id}</span>
                    <span class="chip chip-category">中介费 ¥${p.hireCost}</span>
                </div>
                <div style="font-size:12px; color:var(--text-dim); line-height:1.5;">
                    晋升要求在岗天数: ${p.upgradeDaysByLevel?.join('/') || '10/20'} 天
                </div>
                <button class="action-btn" data-hire-pos="${p.id}">
                    立即签约招聘 (¥${p.hireCost})
                </button>
            </div>
        `).join('');

        // 2. 在册员工卡片
        const rosterCardsHtml = staffs.map(st => {
            const posCfg = gameStore.config?.positionsById?.[st.position] || {};
            const lvl = st.level || 1;
            const days = st.daysEmployed || 0;
            const reqDays = posCfg.upgradeDaysByLevel?.[lvl - 1] ?? 10;
            const canTrain = days >= reqDays && lvl < 3 && !st.isTraining;
            const trainCost = posCfg.trainCostByLevel?.[lvl - 1] ?? 240;

            return `
                <div class="game-card">
                    <div class="card-top">
                        <span class="card-title">${st.name || '在册员工'} (${posCfg.displayName || st.position})</span>
                        <span class="chip chip-value">Lv${lvl}</span>
                    </div>
                    <div class="card-meta-chips">
                        <span class="chip chip-category">累计在岗: ${days} 天</span>
                        <span class="chip chip-value">晋升门槛: ${reqDays} 天</span>
                    </div>
                    ${st.isTraining ? `
                        <div style="font-size:12px; color:#fbbf24;">⏳ 晋升培训中 (剩余工期: ${st.trainingDaysRemaining}天)</div>
                    ` : `
                        <div style="display:flex; gap:8px;">
                            <button class="action-btn gold" style="flex:1;" data-train-staff="${st.id}" ${canTrain ? '' : 'disabled'}>
                                ${canTrain ? `培训晋升 (¥${trainCost})` : `需在岗 ${reqDays} 天`}
                            </button>
                            <button class="action-btn danger" data-fire-staff="${st.id}">解雇</button>
                        </div>
                    `}
                </div>
            `;
        }).join('');

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">🧑‍💼</span>
                        <span class="modal-title">人力资源管理中心 (在册员工: ${staffs.length}人)</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>

                <!-- 切换 Tab -->
                <div style="display:flex; gap:10px; padding:12px 24px; border-bottom:1px solid rgba(255,255,255,0.06);">
                    <button class="hud-btn ${this.currentTab === 'HIRE' ? 'active' : ''}" id="tab-hire">💼 人才招聘市场</button>
                    <button class="hud-btn ${this.currentTab === 'ROSTER' ? 'active' : ''}" id="tab-roster">📋 在册员工列表 (${staffs.length})</button>
                </div>

                <div class="modal-body">
                    ${this.currentTab === 'HIRE' ? `
                        <div class="card-grid">${hireCardsHtml}</div>
                    ` : `
                        <div class="card-grid">
                            ${rosterCardsHtml || '<div style="color:var(--text-faint); grid-column:1/-1;">暂未招聘任何员工，请前往招聘市场引进人才。</div>'}
                        </div>
                    `}
                </div>
            </div>
        `;

        this.attachListeners();
    }

    attachListeners() {
        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());

        const tabHire = this.element.querySelector('#tab-hire');
        const tabRoster = this.element.querySelector('#tab-roster');
        if (tabHire) tabHire.addEventListener('click', () => { this.currentTab = 'HIRE'; this.render(); });
        if (tabRoster) tabRoster.addEventListener('click', () => { this.currentTab = 'ROSTER'; this.render(); });

        this.element.querySelectorAll('[data-hire-pos]').forEach(btn => {
            btn.addEventListener('click', () => {
                const posId = btn.dataset.hirePos;
                gameStore.hireStaff(posId);
                setTimeout(() => this.render(), 300);
            });
        });

        this.element.querySelectorAll('[data-train-staff]').forEach(btn => {
            btn.addEventListener('click', () => {
                const staffId = btn.dataset.trainStaff;
                gameStore.trainStaff(staffId);
                setTimeout(() => this.render(), 300);
            });
        });

        this.element.querySelectorAll('[data-fire-staff]').forEach(btn => {
            btn.addEventListener('click', () => {
                const staffId = btn.dataset.fireStaff;
                if (confirm('确认解雇该员工吗？将依照工龄与底薪支付解约补偿金。')) {
                    gameStore.fireStaff(staffId);
                    setTimeout(() => this.render(), 300);
                }
            });
        });
    }
}
