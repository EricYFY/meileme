// 成就挑战弹窗
import { gameStore } from '../../network/gameStore.js';

export default class AchievementModal {
    constructor(overlayContainer, onClose) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.element = null;
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
        const achievements = gameStore.config?.achievements || [];
        const unlockedIds = gameStore.state?.unlockedAchievements || [];

        const cardsHtml = achievements.map(ach => {
            const isUnlocked = unlockedIds.includes(ach.id);
            return `
                <div class="game-card" style="${isUnlocked ? 'border-color:#fbbf24; background:rgba(251,191,36,0.12);' : ''}">
                    <div class="card-top">
                        <span class="card-title">🏆 ${ach.displayName || ach.id}</span>
                        <span class="chip ${isUnlocked ? 'chip-category' : 'chip-value'}">
                            ${isUnlocked ? '✓ 已达成' : `奖励: ${ach.rewardSkillPoints || 1}科技点`}
                        </span>
                    </div>
                    <div style="font-size:12px; color:var(--text-dim); line-height:1.5;">
                        ${ach.description || '达成经营目标自动派发奖励，并作为高阶地块准入凭据。'}
                    </div>
                </div>
            `;
        }).join('');

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">🏆</span>
                        <span class="modal-title">成就荣耀挑战 (${unlockedIds.length} / ${achievements.length})</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>
                <div class="modal-body">
                    <div class="card-grid">
                        ${cardsHtml}
                    </div>
                </div>
            </div>
        `;

        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());
    }
}
