// 底部 Dock 菜单导航栏 (100% 对齐 Vue3 核心玩家功能模块)
export default class BottomNav {
    constructor(container, onNavClick) {
        this.container = container;
        this.onNavClick = onNavClick;
        this.activeTab = null;
        this.element = null;
        this.render();
    }

    render() {
        this.element = document.createElement('div');
        this.element.className = 'bottom-dock interactive';
        this.container.appendChild(this.element);

        // 8 大核心玩家功能模块，与 Vue3 完全对齐
        const navItems = [
            { id: 'OVERVIEW', icon: '📈', label: '总览' },
            { id: 'REGION', icon: '🗺️', label: '区块' },
            { id: 'BUILD', icon: '🏗️', label: '建造' },
            { id: 'STAFF', icon: '🧑‍💼', label: '员工' },
            { id: 'SKILL', icon: '🌳', label: '技能' },
            { id: 'ACHIEVEMENT', icon: '🏆', label: '成就' },
            { id: 'CODEX', icon: '📖', label: '图鉴' },
            { id: 'LOG', icon: '📊', label: '日志' },
        ];

        this.element.innerHTML = navItems.map(item => `
            <button class="dock-item" data-nav-id="${item.id}">
                <span class="dock-icon">${item.icon}</span>
                <span class="dock-label">${item.label}</span>
            </button>
        `).join('');

        this.element.querySelectorAll('.dock-item').forEach(btn => {
            btn.addEventListener('click', () => {
                const navId = btn.dataset.navId;
                this.setActive(navId);
                if (this.onNavClick) this.onNavClick(navId);
            });
        });
    }

    setActive(navId) {
        this.activeTab = navId;
        this.element.querySelectorAll('.dock-item').forEach(btn => {
            if (btn.dataset.navId === navId) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    clearActive() {
        this.activeTab = null;
        this.element.querySelectorAll('.dock-item').forEach(btn => btn.classList.remove('active'));
    }
}
