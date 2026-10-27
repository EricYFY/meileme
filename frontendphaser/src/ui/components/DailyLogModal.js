// 结算日志与经营报表弹窗 (支持每日明细与区间趋势分析)
import { gameStore } from '../../network/gameStore.js';
import { fmtMoney, fmtInt } from '../../utils/format.js';

export default class DailyLogModal {
    constructor(overlayContainer, onClose) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.element = null;
        this.activeView = 'DAILY'; // 'DAILY' | 'RANGE'
    }

    open() {
        this.close();
        this.element = document.createElement('div');
        this.element.className = 'modal-mask interactive';
        this.render();
        this.container.appendChild(this.element);

        // 请求最新日报历史
        gameStore.requestDailyLog();
    }

    close() {
        if (this.element && this.element.parentNode) {
            this.element.parentNode.removeChild(this.element);
            this.element = null;
        }
        if (this.onClose) this.onClose();
    }

    render() {
        const settle = gameStore.lastSettle || {};
        const logs = gameStore.dailyLog || [];

        // 每日明细行
        const logRowsHtml = logs.map(l => `
            <div class="game-card" style="padding:14px 18px; margin-bottom:10px;">
                <div class="card-top">
                    <span style="font-weight:800; font-size:15px; color:var(--accent);">
                        📅 第 ${l.day} 天 (${l.date}) 经营结单
                    </span>
                    <strong style="font-size:16px; color:var(--gold);">${fmtMoney(l.netProfit || l.totalRevenue)}</strong>
                </div>
                <div class="card-meta-chips" style="margin-top:6px;">
                    <span class="chip chip-value">🏠 房屋营收: ${fmtMoney(l.accommodationRevenue)}</span>
                    <span class="chip chip-value">🎡 配套营收: ${fmtMoney(l.supportRevenue)}</span>
                    <span class="chip chip-category">实际接待: ${fmtInt(l.actualVisitors)}人</span>
                    ${l.lostVisitors > 0 ? `<span class="chip chip-value" style="color:var(--red);">流失客数: ${fmtInt(l.lostVisitors)}人</span>` : '<span class="chip chip-category" style="color:var(--green);">0流失</span>'}
                </div>
            </div>
        `).join('') || '<div style="color:var(--text-faint); padding:30px; text-align:center;">暂无结算历史，进入对局后次日 0 点自动生成首份结单。</div>';

        // 区间统计 (总营业额、总接待人次、平均日收入)
        const totalProfit = logs.reduce((sum, l) => sum + (l.netProfit || l.totalRevenue || 0), 0);
        const totalGuests = logs.reduce((sum, l) => sum + (l.actualVisitors || 0), 0);
        const avgDaily = logs.length > 0 ? totalProfit / logs.length : 0;

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">📊</span>
                        <span class="modal-title">度假村经营结单与报表审计</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>

                <!-- 顶部宏观统计卡 -->
                <div style="padding:16px 24px 0;">
                    <div class="hud-pill" style="border-radius:10px; justify-content:space-around;">
                        <div class="stat-item">
                            <span>累计历史净收益:</span>
                            <strong style="color:var(--gold); font-size:16px;">${fmtMoney(totalProfit)}</strong>
                        </div>
                        <div class="stat-item">
                            <span>累计接待顾客:</span>
                            <strong style="color:var(--green); font-size:16px;">${fmtInt(totalGuests)} 人</strong>
                        </div>
                        <div class="stat-item">
                            <span>平均每日收益:</span>
                            <strong style="color:var(--accent); font-size:16px;">${fmtMoney(avgDaily)}/天</strong>
                        </div>
                    </div>
                </div>

                <div class="modal-body">
                    ${settle.day ? `
                        <div style="background:rgba(56,189,248,0.12); border:1px solid rgba(56,189,248,0.3); border-radius:10px; padding:14px 18px; margin-bottom:16px;">
                            <div style="font-size:14px; font-weight:800; color:#fff; margin-bottom:4px;">
                                🔔 最近结算快报 (第 ${settle.day} 天)
                            </div>
                            <div style="display:flex; justify-content:space-between; font-size:13px; color:var(--text-dim);">
                                <span>当日利润: <strong style="color:var(--gold);">${fmtMoney(settle.netProfit)}</strong></span>
                                <span>住宿: ${fmtMoney(settle.accommodationRevenue)} | 配套: ${fmtMoney(settle.supportRevenue)}</span>
                                <span>接待客数: <strong style="color:#fff;">${fmtInt(settle.actualVisitors)}</strong></span>
                            </div>
                        </div>
                    ` : ''}

                    <h4 style="margin-bottom:12px; color:#fff;">📜 逐日结算报表清单</h4>
                    <div>
                        ${logRowsHtml}
                    </div>
                </div>
            </div>
        `;

        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());
    }
}
