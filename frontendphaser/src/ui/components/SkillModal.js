// 经营科技技能树弹窗
import { gameStore } from '../../network/gameStore.js';

export default class SkillModal {
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
        const skills = gameStore.config?.skills || [];
        const unlockedSkills = gameStore.state?.unlockedSkills || [];
        const skillPoints = gameStore.state?.skillPoints || 0;

        const cardsHtml = skills.map(sk => {
            const isUnlocked = unlockedSkills.includes(sk.id);
            // 依赖校验
            const prereqs = sk.prerequisites || [];
            const prereqMet = prereqs.every(id => unlockedSkills.includes(id));
            const canUnlock = !isUnlocked && prereqMet && skillPoints >= (sk.costPoints || 1);

            return `
                <div class="game-card" style="${isUnlocked ? 'border-color:#10b981; background:rgba(16,185,129,0.12);' : ''}">
                    <div class="card-top">
                        <span class="card-title">🌳 ${sk.displayName || sk.id}</span>
                        <span class="chip ${isUnlocked ? 'chip-value' : 'chip-category'}">
                            ${isUnlocked ? '✓ 已点亮' : `消耗: ${sk.costPoints || 1} 点`}
                        </span>
                    </div>
                    <div style="font-size:12px; color:var(--text-dim); line-height:1.5;">
                        ${sk.description || '为度假村经营提供专属科技被动增益。'}
                    </div>
                    ${prereqs.length > 0 ? `
                        <div style="font-size:11px; color:var(--text-faint);">
                            前置要求: ${prereqs.join(', ')}
                        </div>
                    ` : ''}
                    <button class="action-btn ${isUnlocked ? '' : 'gold'}" data-unlock-skill="${sk.id}" ${canUnlock ? '' : 'disabled'}>
                        ${isUnlocked ? '已完成研发' : (!prereqMet ? '前置科技未达成' : (skillPoints < (sk.costPoints || 1) ? '技能点不足' : '研发科技'))}
                    </button>
                </div>
            `;
        }).join('');

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">🌳</span>
                        <span class="modal-title">经营科技技能树 (可用科技点: <strong style="color:var(--gold);">${skillPoints}</strong>)</span>
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
        this.element.querySelectorAll('[data-unlock-skill]').forEach(btn => {
            btn.addEventListener('click', () => {
                const skillId = btn.dataset.unlockSkill;
                gameStore.unlockSkill(skillId);
                setTimeout(() => this.render(), 300);
            });
        });
    }
}
