// 金色横幅庆贺演出与 Toast 提示组件
export default class CelebrationBanner {
    constructor(overlayContainer) {
        this.container = overlayContainer;
        this.toastContainer = null;
        this.init();
    }

    init() {
        this.toastContainer = document.createElement('div');
        this.toastContainer.className = 'toast-container interactive';
        this.container.appendChild(this.toastContainer);
    }

    /**
     * 弹出金色庆贺横幅
     * @param {string} title 主标题
     * @param {string} desc 副标题/说明
     * @param {string} icon 图标
     */
    showBanner(title, desc = '', icon = '🎉') {
        const wrap = document.createElement('div');
        wrap.className = 'celebration-banner-wrap interactive';
        wrap.innerHTML = `
            <div class="celebration-banner">
                <span style="font-size:26px;">${icon}</span>
                <div>
                    <div>${title}</div>
                    ${desc ? `<div style="font-size:12px; font-weight:600; opacity:0.85; margin-top:2px;">${desc}</div>` : ''}
                </div>
            </div>
        `;
        this.container.appendChild(wrap);

        // 3.5秒后渐隐移除
        setTimeout(() => {
            wrap.style.transition = 'all 0.4s ease-out';
            wrap.style.opacity = '0';
            wrap.style.transform = 'translate(-50%, -40px)';
            setTimeout(() => {
                if (wrap.parentNode) wrap.parentNode.removeChild(wrap);
            }, 450);
        }, 3500);
    }

    /**
     * 弹出普通 Toast 消息
     */
    showToast(text, kind = 'info') {
        const toast = document.createElement('div');
        toast.className = 'toast-item';
        const icon = kind === 'alert' ? '⚠️' : (kind === 'gold' ? '✨' : '💬');
        toast.innerHTML = `<span>${icon} ${text}</span>`;
        this.toastContainer.appendChild(toast);

        setTimeout(() => {
            toast.style.transition = 'all 0.3s ease-out';
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(40px)';
            setTimeout(() => {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 350);
        }, 3200);
    }
}
