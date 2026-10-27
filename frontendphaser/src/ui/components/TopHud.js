// 顶部 HUD：资金、天数、日期、倍速控制、对局控制与地块切换器
import { gameStore } from '../../network/gameStore.js';
import { fmtMoney, fmtInt } from '../../utils/format.js';

export default class TopHud {
    constructor(container) {
        this.container = container;
        this.element = null;
        this.render();
        this.bindEvents();
    }

    render() {
        this.element = document.createElement('div');
        this.element.className = 'top-hud interactive';
        this.container.appendChild(this.element);
        this.update();
    }

    update() {
        if (!this.element) return;
        const funds = gameStore.tick.funds || 0;
        const day = gameStore.tick.day || 1;
        const date = gameStore.tick.date || '2026-05-01';
        const speed = gameStore.tick.speed || 1;
        const paused = gameStore.tick.paused || false;
        const running = gameStore.tick.running || false;
        const connected = gameStore.connected;

        // 地块列表
        const plots = gameStore.config?.plots || [];
        const currentPlotId = gameStore.currentPlotId;

        const plotButtonsHtml = plots.map(p => {
            const isUnlocked = gameStore.state?.unlockedPlots?.includes(p.id) ?? p.unlockedByDefault;
            const isActive = p.id === currentPlotId;
            const lockIcon = isUnlocked ? '' : '🔒 ';
            return `
                <button class="plot-tab-btn ${isActive ? 'active' : ''}" data-plot-id="${p.id}">
                    ${lockIcon}${p.displayName || p.id}
                </button>
            `;
        }).join('');

        this.element.innerHTML = `
            <!-- 左侧：资金与天数 HUD 药丸 -->
            <div class="hud-pill">
                <div class="brand-badge">
                    <span style="display:inline-block; width:8px; height:8px; border-radius:50%; background:${connected ? '#10b981' : '#ef4444'}; box-shadow:0 0 6px ${connected ? '#10b981' : '#ef4444'};"></span>
                    🏞️ 美了么·等轴测度假村
                </div>
                ${running ? `
                    <div class="stat-item">
                        <span class="stat-icon">💰</span>
                        <span class="funds-val">${fmtMoney(funds)}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-icon">📅</span>
                        <span class="day-val">第 ${day} 天</span>
                        <span style="font-size: 11px; color: var(--text-faint); margin-left: 2px;">(${date})</span>
                    </div>
                ` : `
                    <div style="font-size:12px; color:var(--text-faint);">对局未启动</div>
                `}
            </div>

            <!-- 中间：地块地图切换器与状态提醒 -->
            <div style="display:flex; flex-direction:column; align-items:center; gap:6px;">
                <div class="plot-selector-bar">
                    ${plotButtonsHtml}
                </div>
                ${!running ? `
                    <div style="font-size:11px; color:#fbbf24; background:rgba(0,0,0,0.5); padding:2px 10px; border-radius:12px;">
                        💡 尚未开启对局，请点击右上角「▶ 开始经营」启动度假村
                    </div>
                ` : ''}
            </div>

            <!-- 右侧：倍速与游戏控制 -->
            <div class="hud-pill">
                <div class="controls-group">
                    <button class="hud-btn ${speed === 1 ? 'active' : ''}" data-speed="1">1x</button>
                    <button class="hud-btn ${speed === 2 ? 'active' : ''}" data-speed="2">2x</button>
                    <button class="hud-btn ${speed === 5 ? 'active' : ''}" data-speed="5">5x</button>
                    
                    ${running ? `
                        <button class="hud-btn ${paused ? 'play' : 'pause'}" id="hud-pause-btn">
                            ${paused ? '▶ 继续' : '⏸ 暂停'}
                        </button>
                        <button class="hud-btn danger" id="hud-stop-btn">⏹ 停止</button>
                    ` : `
                        <button class="hud-btn play" id="hud-start-btn">▶ 开始经营</button>
                    `}
                </div>
            </div>
        `;

        this.attachListeners();
    }

    attachListeners() {
        // 地块切换点击
        this.element.querySelectorAll('.plot-tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const plotId = btn.dataset.plotId;
                const isUnlocked = gameStore.state?.unlockedPlots?.includes(plotId);
                if (isUnlocked) {
                    gameStore.selectPlot(plotId);
                    this.update();
                } else {
                    const p = gameStore.config?.plotsById?.[plotId];
                    if (p && confirm(`地块【${p.displayName}】尚未解锁，解锁需要资金 ${fmtMoney(p.unlockCost)}，是否前往【区块】面板查看解锁条件？`)) {
                        // 触发打开区块面板
                        const regionBtn = document.querySelector('[data-nav-id="REGION"]');
                        if (regionBtn) regionBtn.click();
                    }
                }
            });
        });

        // 倍速点击
        this.element.querySelectorAll('[data-speed]').forEach(btn => {
            btn.addEventListener('click', () => {
                const s = Number(btn.dataset.speed);
                gameStore.setSpeed(s);
            });
        });

        // 暂停/继续
        const pauseBtn = this.element.querySelector('#hud-pause-btn');
        if (pauseBtn) {
            pauseBtn.addEventListener('click', () => {
                if (gameStore.tick.paused) {
                    gameStore.resumeGame();
                } else {
                    gameStore.pauseGame();
                }
            });
        }

        // 开始对局
        const startBtn = this.element.querySelector('#hud-start-btn');
        if (startBtn) {
            startBtn.addEventListener('click', () => {
                gameStore.startGame();
            });
        }

        // 停止对局
        const stopBtn = this.element.querySelector('#hud-stop-btn');
        if (stopBtn) {
            stopBtn.addEventListener('click', () => {
                if (confirm('确认结束当前度假村对局吗？')) {
                    gameStore.stopGame();
                }
            });
        }
    }

    bindEvents() {
        gameStore.on('TIME_TICK', () => this.update());
        gameStore.on('STATE_UPDATED', () => this.update());
        gameStore.on('CONFIG_UPDATED', () => this.update());
        gameStore.on('PLOT_CHANGED', () => this.update());
        gameStore.on('CONNECTED_CHANGE', () => this.update());
    }
}
