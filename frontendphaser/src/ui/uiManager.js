// UI 综合管理器：组装 HUD、Dock 菜单与各功能弹窗 (全量对齐 Vue3)
import { gameStore } from '../network/gameStore.js';
import TopHud from './components/TopHud.js';
import BottomNav from './components/BottomNav.js';
import BuildingModal from './components/BuildingModal.js';
import StaffModal from './components/StaffModal.js';
import SkillModal from './components/SkillModal.js';
import AchievementModal from './components/AchievementModal.js';
import CodexModal from './components/CodexModal.js';
import DailyLogModal from './components/DailyLogModal.js';
import OverviewModal from './components/OverviewModal.js';
import RegionModal from './components/RegionModal.js';
import CelebrationBanner from './components/CelebrationBanner.js';

export default class UIManager {
    constructor(overlayContainer) {
        this.container = overlayContainer;
        this.topHud = null;
        this.bottomNav = null;
        this.celebrationBanner = null;

        // 弹窗实例
        this.buildingModal = null;
        this.staffModal = null;
        this.skillModal = null;
        this.achievementModal = null;
        this.codexModal = null;
        this.dailyLogModal = null;
        this.overviewModal = null;
        this.regionModal = null;

        this.init();
        this.bindStoreEvents();
    }

    init() {
        this.celebrationBanner = new CelebrationBanner(this.container);
        this.topHud = new TopHud(this.container);

        // 弹窗关闭回调
        const onModalClose = () => {
            if (this.bottomNav) this.bottomNav.clearActive();
        };

        // 打开建筑详情回调
        const onOpenBuilding = (bldId) => {
            gameStore.selectBuilding(bldId);
            this.buildingModal.open('DETAIL');
        };

        this.buildingModal = new BuildingModal(this.container, onModalClose);
        this.staffModal = new StaffModal(this.container, onModalClose);
        this.skillModal = new SkillModal(this.container, onModalClose);
        this.achievementModal = new AchievementModal(this.container, onModalClose);
        this.codexModal = new CodexModal(this.container, onModalClose);
        this.dailyLogModal = new DailyLogModal(this.container, onModalClose);
        this.overviewModal = new OverviewModal(this.container, onModalClose, onOpenBuilding);
        this.regionModal = new RegionModal(this.container, onModalClose);

        // 底部 Dock (8 大功能)
        this.bottomNav = new BottomNav(this.container, (navId) => {
            this.handleNavClick(navId);
        });
    }

    closeAllModals() {
        this.buildingModal.close();
        this.staffModal.close();
        this.skillModal.close();
        this.achievementModal.close();
        this.codexModal.close();
        this.dailyLogModal.close();
        this.overviewModal.close();
        this.regionModal.close();
    }

    handleNavClick(navId) {
        this.closeAllModals();

        switch (navId) {
            case 'OVERVIEW':
                this.overviewModal.open('PREVIEW');
                break;
            case 'REGION':
                this.regionModal.open();
                break;
            case 'BUILD':
                this.buildingModal.open('BUILD');
                break;
            case 'STAFF':
                this.staffModal.open();
                break;
            case 'SKILL':
                this.skillModal.open();
                break;
            case 'ACHIEVEMENT':
                this.achievementModal.open();
                break;
            case 'CODEX':
                this.codexModal.open();
                break;
            case 'LOG':
                this.dailyLogModal.open();
                break;
        }
    }

    bindStoreEvents() {
        // 场景中点击空地落格
        gameStore.on('CELL_SELECTED', (cell) => {
            if (!gameStore.selectedBuildingId) {
                this.closeAllModals();
                this.buildingModal.open('BUILD');
                this.bottomNav.setActive('BUILD');
            }
        });

        // 场景中点击建筑
        gameStore.on('BUILDING_SELECTED', (buildingId) => {
            if (buildingId) {
                this.closeAllModals();
                this.buildingModal.open('DETAIL');
            }
        });

        // 图鉴解锁横幅演出
        gameStore.on('CODEX_UNLOCKED', (msg) => {
            const catName = msg.category === 'FORM' ? '建筑形态' : (msg.category === 'INTERIOR' ? '内装设施' : (msg.category === 'PLOT' ? '度假地块' : '员工岗位'));
            this.celebrationBanner.showBanner('📖 图鉴典藏解锁！', `已收录【${catName}】: ${msg.name || msg.id}`, '✨');
            this.celebrationBanner.showToast(`典藏图鉴新收录：${msg.name || msg.id}`, 'gold');
        });

        // 建筑竣工/升星/进化成功横幅
        gameStore.on('PROJECT_COMPLETED', (msg) => {
            this.celebrationBanner.showBanner('🎉 工程竣工！', msg.description || '建筑施工与升级已圆满完成！', '🏰');
            this.celebrationBanner.showToast(msg.description || '工程施工已完成！', 'gold');
        });

        // 指令进入待生效队列
        gameStore.on('ACTION_QUEUED', (msg) => {
            this.celebrationBanner.showToast(`指令已排队：${msg.description || msg.action}`, 'info');
        });

        // 操作失败
        gameStore.on('ACTION_FAILED', (msg) => {
            const reason = msg.reason || '操作无法执行';
            this.celebrationBanner.showToast(`操作失败: ${reason}`, 'alert');
        });

        // 每日结算
        gameStore.on('DAY_SETTLED', (msg) => {
            const profit = msg.settle?.netProfit || 0;
            this.celebrationBanner.showToast(`第 ${msg.day} 天结算完成，营业净收入: ¥${profit.toLocaleString()}`, 'gold');
        });
    }
}
