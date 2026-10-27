// 规划建造与建筑详情综合面板 (完整对齐 Vue3 BuildingDetailModal.vue)
import { gameStore } from '../../network/gameStore.js';
import { fmtMoney, fmtInt, fmtPct } from '../../utils/format.js';

export default class BuildingModal {
    constructor(overlayContainer, onClose) {
        this.container = overlayContainer;
        this.onClose = onClose;
        this.element = null;
        this.activeDetailTab = 'ATTRIBUTES'; // 'ATTRIBUTES' | 'EVOLVE' | 'MERGE' | 'INTERIORS'
    }

    open(mode = 'AUTO') {
        this.close();

        const plotId = gameStore.currentPlotId || gameStore.determineHighestUnlockedPlot();
        const plot = gameStore.config?.plotsById?.[plotId] || (gameStore.config?.plots || [])[0];
        const cell = gameStore.selectedCell || { x: 0, y: 0 };
        const bld = gameStore.selectedBuildingId 
            ? (gameStore.state?.buildings || []).find(b => b.id === gameStore.selectedBuildingId)
            : null;

        const isBuildMode = mode === 'BUILD' || (!bld && mode === 'AUTO');

        this.element = document.createElement('div');
        this.element.className = 'modal-mask interactive';

        if (isBuildMode) {
            this.renderBuildView(plot, cell);
        } else {
            this.renderDetailView(bld, plot);
        }

        this.container.appendChild(this.element);
    }

    close() {
        if (this.element && this.element.parentNode) {
            this.element.parentNode.removeChild(this.element);
            this.element = null;
        }
        if (this.onClose) this.onClose();
    }

    // 1. 规划建造视图
    renderBuildView(plot, cell) {
        const mothers = (gameStore.config?.mothers || []).filter(m => m.isOpen ?? true);
        const plotAllowed = plot?.allowedMothers || [];
        const filteredMothers = mothers.filter(m => plotAllowed.length === 0 || plotAllowed.includes(m.id));
        const currentFunds = gameStore.state?.funds ?? gameStore.tick?.funds ?? 0;

        const cardsHtml = filteredMothers.map(m => {
            const isAcc = m.category === 'ACCOMMODATION';
            const cost = m.buildCost || 0;
            const canAfford = currentFunds >= cost;

            return `
                <div class="game-card">
                    <div class="card-top">
                        <span class="card-title">${m.displayName || m.id}</span>
                        <span class="chip chip-category">${isAcc ? '🏠 住宿建筑' : '🎡 配套体验'}</span>
                    </div>
                    <div class="card-meta-chips">
                        <span class="chip chip-value">基础造价: ${fmtMoney(cost)}</span>
                        <span class="chip chip-value">施工工期: ${m.buildDays || 3}天</span>
                    </div>
                    <button class="action-btn ${canAfford ? '' : 'disabled'}" 
                            data-build-mother="${m.id}" 
                            ${canAfford ? '' : 'disabled'}
                            style="${canAfford ? '' : 'opacity:0.55; cursor:not-allowed; background:#334155;'}">
                        ${canAfford ? `在此落格建造 (${cell.x}, ${cell.y})` : `资金不足 (需 ${fmtMoney(cost)})`}
                    </button>
                </div>
            `;
        }).join('');

        const plotName = plot?.displayName || '度假地块';

        this.element.innerHTML = `
            <div class="modal-window">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">🏗️</span>
                        <span class="modal-title">规划施工：建造建筑 @ ${plotName} (格 ${cell.x}, ${cell.y})</span>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>
                <div class="modal-body">
                    <div class="card-grid">
                        ${cardsHtml || '<div style="color:var(--text-faint); grid-column:1/-1;">当前地块暂无可用母本建筑。</div>'}
                    </div>
                </div>
            </div>
        `;

        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());
        this.element.querySelectorAll('[data-build-mother]').forEach(btn => {
            btn.addEventListener('click', () => {
                if (btn.disabled) return;
                const motherId = btn.dataset.buildMother;
                const targetPlotId = plot?.id || gameStore.currentPlotId;
                gameStore.buildBuilding(motherId, targetPlotId, cell.x, cell.y);
                this.close();
            });
        });
    }

    // 2. 深度建筑详情视图 (100% 对齐 Vue3)
    renderDetailView(bld, plot) {
        if (!bld) return this.close();
        const form = bld.form || {};
        const isUnderConstruction = bld.status === 'UNDER_CONSTRUCTION' || bld.underConstruction;
        const level = bld.level || bld.formLevel || 1;
        const isSupport = form.category === 'SUPPORT';

        // 舒适度与人力保障
        const comfortData = gameStore.state?.buildingComfort?.[bld.id] || {};
        const totalComfort = comfortData.totalComfort ?? bld.comfortScore ?? form.baseComfort ?? 50;
        const interiorComfort = comfortData.interiorComfort ?? 0;
        const staffFactor = comfortData.staffFactor ?? 1.0;
        const shortages = comfortData.shortages || [];

        // 容量与收益
        const capacityPerRoom = form.capacityPerRoom ?? 2;
        const serviceCapacity = form.serviceCapacity ?? 50;
        const baseCap = isSupport ? serviceCapacity : capacityPerRoom;
        const interiorCapBonus = comfortData.interiorCapacity ?? 0;
        const totalCap = baseCap + interiorCapBonus;

        const pricePerRoom = form.pricePerRoomByLevel?.[level - 1] ?? 280;
        const unitPrice = form.unitPriceByLevel?.[level - 1] ?? 50;

        // 同形态升星
        const maxLevel = gameStore.config?.formMaxLevel || 3;
        const canLevelUp = level < maxLevel && !isUnderConstruction;
        const nextLevelCost = form.formLevelUpCost?.[level - 1] ?? 2000;
        const nextLevelDays = form.formLevelUpDays?.[level - 1] ?? 2;

        // 客群相性列表
        const formAffinityMap = form.affinities || form.segmentAffinity || {};
        const affinitiesList = (gameStore.config?.segments || []).map(seg => {
            const baseAff = Number(formAffinityMap[seg.id] ?? 0.5);
            let grade = 'C';
            if (baseAff >= 1.2) grade = 'S';
            else if (baseAff >= 0.8) grade = 'A';
            else if (baseAff >= 0.4) grade = 'B';
            else if (baseAff <= 0) grade = '—';
            return {
                id: seg.id,
                name: seg.displayName || seg.id,
                color: seg.color || '#fff',
                baseAff,
                grade
            };
        });

        // 分支进化路线 (按源形态匹配)
        const evoLines = (gameStore.config?.evolutionLines || []).filter(l => l.sourceFormId === form.id);
        const evoCardsHtml = evoLines.map(line => {
            const targetForm = gameStore.config?.buildingFormsById?.[line.targetFormId] || {};
            const tierMet = (targetForm.tier || 1) <= (plot?.evolveTierCap ?? 3);
            const levelMet = level >= (line.requiredLevel || 1);
            const canEvolve = levelMet && tierMet && !isUnderConstruction;

            return `
                <div class="game-card" style="border-color: rgba(56, 189, 248, 0.3);">
                    <div class="card-top">
                        <span class="card-title">➡️ 目标形态: ${targetForm.displayName || line.targetFormId}</span>
                        <span class="chip chip-category">Tier ${targetForm.tier || 1}</span>
                    </div>
                    <div class="card-meta-chips">
                        <span class="chip chip-value">升级造价: ${fmtMoney(line.cost)}</span>
                        <span class="chip chip-value">施工工期: ${line.days}天</span>
                        <span class="chip ${levelMet ? 'chip-value' : 'chip-category'}">要求星级: Lv${line.requiredLevel}</span>
                    </div>
                    ${!tierMet ? `<div style="font-size:11px; color:var(--red);">⚠️ 超出当前地块 Tier 上限 (地块限制 Tier ${plot?.evolveTierCap})</div>` : ''}
                    <button class="action-btn gold" data-branch-target="${line.targetFormId}" ${canEvolve ? '' : 'disabled'}>
                        ${!levelMet ? `需升星至 Lv${line.requiredLevel}` : (!tierMet ? '超出地块阶位' : (isUnderConstruction ? '工程施工中' : '启动形态进化'))}
                    </button>
                </div>
            `;
        }).join('') || '<div style="color:var(--text-faint); grid-column:1/-1;">当前形态暂无可选分支进化路线。</div>';

        // 相邻合并进化配方检测
        const mergeRecipes = gameStore.config?.mergeRecipes || [];
        const allBuildings = gameStore.state?.buildings || [];
        // 查找与当前建筑相邻的建筑 (曼哈顿距离为 1)
        const adjacentBuildings = allBuildings.filter(other => 
            other.id !== bld.id && other.plotId === bld.plotId && 
            Math.abs(other.cellX - bld.cellX) + Math.abs(other.cellY - bld.cellY) === 1
        );

        const mergeCardsHtml = mergeRecipes.map(recipe => {
            const targetForm = gameStore.config?.buildingFormsById?.[recipe.resultForm] || {};
            const sources = recipe.sources || [];
            // 检查当前建筑是否符合其中一个原料
            const matchCurrent = sources.find(s => s.formId === form.id && level >= (s.minLevel || 1));
            if (!matchCurrent) return '';

            // 寻找满足另一个原料的相邻建筑
            const otherReq = sources.find(s => s !== matchCurrent) || sources[0];
            const candidateOthers = adjacentBuildings.filter(o => 
                o.form?.id === otherReq.formId && (o.level || 1) >= (otherReq.minLevel || 1) && !o.underConstruction
            );

            return `
                <div class="game-card" style="border-color: rgba(251, 191, 36, 0.4);">
                    <div class="card-top">
                        <span class="card-title">✨ 合并融合成: ${recipe.displayName || targetForm.displayName}</span>
                        <span class="chip chip-value">造价 ${fmtMoney(recipe.mergeCost)}</span>
                    </div>
                    <div style="font-size:12px; color:var(--text-dim); line-height:1.4;">
                        配方要求: ${sources.map(s => `${gameStore.config?.buildingFormsById?.[s.formId]?.displayName || s.formId} (Lv${s.minLevel}+)`).join(' + ')}
                    </div>
                    ${candidateOthers.length > 0 ? `
                        <div style="margin-top:6px;">
                            <label style="font-size:11px; color:var(--text-faint);">选择相邻待合并建筑:</label>
                            <select id="merge-partner-select" style="width:100%; padding:6px; background:#1e293b; color:#fff; border:1px solid #475569; border-radius:4px; margin:4px 0 8px;">
                                ${candidateOthers.map(o => `<option value="${o.id}">${o.form?.displayName} (格位: ${o.cellX}, ${o.cellY})</option>`).join('')}
                            </select>
                            <button class="action-btn gold" style="width:100%;" id="btn-merge-evolve">
                                启动双建筑融合作业 (${recipe.mergeDays || 3}天)
                            </button>
                        </div>
                    ` : `
                        <div style="font-size:11px; color:#fca5a5; margin-top:6px;">
                            ⚠️ 附近 1 格范围内暂无满足星级条件的相邻原料建筑。
                        </div>
                    `}
                </div>
            `;
        }).filter(Boolean).join('') || '<div style="color:var(--text-faint); grid-column:1/-1;">当前建筑暂无可触发的合院融合配方。</div>';

        // 内装设施挂载与升级列表
        const availableInteriors = form.availableInteriors || [];
        const interiorCategoryMap = { CARRY: '承载设施', NORMAL: '普通设施', COMFORT: '舒适设施', ENTERTAINMENT: '娱乐设施' };

        const interiorsHtml = availableInteriors.map(intId => {
            const intCfg = gameStore.config?.interiorsById?.[intId] || {};
            const curLvl = bld.interiorLevels?.[intId] || 0;
            // 最大上限受形态 maxInteriorLevel 约束
            const maxCap = form.maxInteriorLevel?.[level - 1] ?? (gameStore.config?.interiorMaxLevelFallback || 3);
            const nextCost = intCfg.upgradeCostByLevel?.[curLvl] ?? 800;
            const nextDays = intCfg.upgradeDaysByLevel?.[curLvl] ?? 1;
            const canUpgrade = curLvl < maxCap && !isUnderConstruction;
            const isCarry = intCfg.category === 'CARRY' || form.capacityInterior === intId;

            return `
                <div class="game-card">
                    <div class="card-top">
                        <span class="card-title">🛋️ ${intCfg.displayName || intId}</span>
                        <span class="chip chip-value">Lv${curLvl} / ${maxCap}</span>
                    </div>
                    <div class="card-meta-chips">
                        <span class="chip chip-category">${interiorCategoryMap[intCfg.category] || '设施'}</span>
                        <span class="chip chip-value">舒适度: +${intCfg.comfortPerLevel}/级</span>
                        ${isCarry ? `<span class="chip chip-category" style="color:var(--gold);">容量贡献: +${intCfg.capacityPerLevel || 1}人/级</span>` : ''}
                        ${intCfg.revenueBonusPerLevel ? `<span class="chip chip-value">营收加成: +${(intCfg.revenueBonusPerLevel * 100).toFixed(0)}%/级</span>` : ''}
                    </div>
                    <div style="font-size:11px; color:var(--text-dim); margin-top:4px;">
                        升级所需: ${fmtMoney(nextCost)} | 工期: ${nextDays}天
                    </div>
                    <button class="action-btn" data-upgrade-interior="${intId}" ${canUpgrade ? '' : 'disabled'}>
                        ${curLvl >= maxCap ? `已达星级上限 (Lv${maxCap})` : (isUnderConstruction ? '施工进行中' : '升级此项设施')}
                    </button>
                </div>
            `;
        }).join('') || '<div style="color:var(--text-faint); grid-column:1/-1;">当前形态暂无可用内装配置。</div>';

        this.element.innerHTML = `
            <div class="modal-window" style="max-width:920px;">
                <div class="modal-header">
                    <div class="modal-title-box">
                        <span class="modal-icon">🏠</span>
                        <div>
                            <div class="modal-title">${form.displayName || '度假建筑'} (Lv${level} ★${'★'.repeat(level)})</div>
                            <div style="font-size:11px; color:var(--text-faint); margin-top:2px;">
                                归属地块: ${plot?.displayName || bld.plotId} | 网格坐标: (${bld.cellX}, ${bld.cellY}) | ID: ${bld.id}
                            </div>
                        </div>
                    </div>
                    <button class="modal-close-btn" id="modal-close-btn">×</button>
                </div>

                <!-- 建筑详情子导航 Tab -->
                <div style="display:flex; gap:10px; padding:12px 24px; border-bottom:1px solid rgba(255,255,255,0.06);">
                    <button class="hud-btn ${this.activeDetailTab === 'ATTRIBUTES' ? 'active' : ''}" id="tab-det-attrs">📊 综合参数与客群</button>
                    <button class="hud-btn ${this.activeDetailTab === 'EVOLVE' ? 'active' : ''}" id="tab-det-evolve">✨ 升星与分支进化</button>
                    <button class="hud-btn ${this.activeDetailTab === 'MERGE' ? 'active' : ''}" id="tab-det-merge">🔗 合并融合成套</button>
                    <button class="hud-btn ${this.activeDetailTab === 'INTERIORS' ? 'active' : ''}" id="tab-det-interiors">🛋️ 内装部件配置</button>
                </div>

                <div class="modal-body">
                    ${isUnderConstruction ? `
                        <div style="background:rgba(245,158,11,0.15); border:1px solid #f59e0b; padding:12px 16px; border-radius:8px; color:#fbbf24; margin-bottom:16px;">
                            🚧 当前有工程正在施工中，工期剩余: <strong>${bld.constructionDaysRemaining || 1} 天</strong>。工程期间建筑保持原等级正常营业，竣工次日 0 点生效。
                        </div>
                    ` : ''}

                    ${this.activeDetailTab === 'ATTRIBUTES' ? `
                        <div style="display:flex; flex-direction:column; gap:16px;">
                            <!-- 核心数据看板 -->
                            <div class="card-grid" style="grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));">
                                <div class="game-card">
                                    <span style="font-size:11px; color:var(--text-faint);">综合舒适度评分</span>
                                    <strong style="font-size:22px; color:var(--green);">${totalComfort} 分</strong>
                                    <span style="font-size:11px; color:var(--text-dim);">
                                        基础 ${form.baseComfort || 50} + 内装 ${interiorComfort} (人力保障: ${(staffFactor * 100).toFixed(0)}%)
                                    </span>
                                </div>
                                <div class="game-card">
                                    <span style="font-size:11px; color:var(--text-faint);">${isSupport ? '服务接待容量' : '客房承载人数'}</span>
                                    <strong style="font-size:22px; color:var(--accent);">${totalCap} 人</strong>
                                    <span style="font-size:11px; color:var(--text-dim);">
                                        基础 ${baseCap} + 内装加成 ${interiorCapBonus}
                                    </span>
                                </div>
                                <div class="game-card">
                                    <span style="font-size:11px; color:var(--text-faint);">${isSupport ? '配套消费客单价' : '间夜基准房价'}</span>
                                    <strong style="font-size:22px; color:var(--gold);">${fmtMoney(isSupport ? unitPrice : pricePerRoom)}</strong>
                                    <span style="font-size:11px; color:var(--text-dim);">升星可大幅提高客单与溢价</span>
                                </div>
                            </div>

                            ${shortages.length > 0 ? `
                                <div style="background:rgba(239,68,68,0.12); border:1px solid rgba(239,68,68,0.3); border-radius:8px; padding:10px 14px; font-size:12px; color:#fca5a5;">
                                    ⚠️ 缺员警报：由于 ${shortages.join('、')} 供给不足，当前建筑舒适度被打打折 ${(staffFactor * 100).toFixed(0)}%！
                                </div>
                            ` : ''}

                            <!-- 各客群相性匹配 -->
                            <div>
                                <h4 style="margin-bottom:10px; color:#fff;">🎯 客群吸引相性评分</h4>
                                <div style="display:grid; grid-template-columns:repeat(auto-fill, minmax(130px, 1fr)); gap:10px;">
                                    ${affinitiesList.map(a => `
                                        <div class="game-card" style="padding:10px; text-align:center;">
                                            <div style="font-size:13px; font-weight:700; color:${a.color};">${a.name}</div>
                                            <div style="font-size:20px; font-weight:800; color:var(--gold); margin:4px 0;">${a.grade}</div>
                                            <div style="font-size:11px; color:var(--text-faint);">相性系数: ${a.baseAff.toFixed(2)}</div>
                                        </div>
                                    `).join('')}
                                </div>
                            </div>

                            <!-- 岗位能力需求清单 -->
                            <div>
                                <h4 style="margin-bottom:8px; color:#fff;">💼 每日运营人力需求</h4>
                                <div style="display:flex; flex-wrap:wrap; gap:8px;">
                                    ${Object.entries(form.abilityDemands || {}).map(([abId, demand]) => `
                                        <span class="chip chip-value" style="font-size:12px; padding:4px 10px;">
                                            ${gameStore.config?.abilitiesById?.[abId]?.displayName || abId}: <strong>${demand}点/天</strong>
                                        </span>
                                    `).join('') || '<span style="color:var(--text-faint); font-size:12px;">此建筑日常运营无需额外专业岗位支持。</span>'}
                                </div>
                            </div>
                        </div>
                    ` : ''}

                    ${this.activeDetailTab === 'EVOLVE' ? `
                        <div style="display:flex; flex-direction:column; gap:20px;">
                            <!-- 同形态升星 -->
                            <div>
                                <h4 style="margin-bottom:10px; color:#fff;">⭐ 同形态升星 (提升基础舒适度与房价)</h4>
                                <div class="game-card" style="flex-direction:row; justify-content:space-between; align-items:center;">
                                    <div>
                                        <div style="font-size:15px; font-weight:800; color:#fff;">晋升至 Lv${level + 1}</div>
                                        <div style="font-size:12px; color:var(--text-dim); margin-top:2px;">
                                            升级造价: <strong style="color:var(--gold);">${fmtMoney(nextLevelCost)}</strong> | 施工工期: <strong>${nextLevelDays}天</strong>
                                        </div>
                                    </div>
                                    <button class="action-btn" id="btn-level-up" ${canLevelUp ? '' : 'disabled'}>
                                        ${level >= maxLevel ? '已达最高星级 (Lv3)' : '提升星级'}
                                    </button>
                                </div>
                            </div>

                            <!-- 分支形态进化 -->
                            <div>
                                <h4 style="margin-bottom:10px; color:#fff;">✨ 跨形态分支演进路线</h4>
                                <div class="card-grid">
                                    ${evoCardsHtml}
                                </div>
                            </div>
                        </div>
                    ` : ''}

                    ${this.activeDetailTab === 'MERGE' ? `
                        <div>
                            <h4 style="margin-bottom:10px; color:#fff;">🔗 相邻建筑融合配方 (两座满星建筑融合成高级合院)</h4>
                            <div class="card-grid">
                                ${mergeCardsHtml}
                            </div>
                        </div>
                    ` : ''}

                    ${this.activeDetailTab === 'INTERIORS' ? `
                        <div>
                            <h4 style="margin-bottom:10px; color:#fff;">🛋️ 内部设施安装与升级</h4>
                            <div class="card-grid">
                                ${interiorsHtml}
                            </div>
                        </div>
                    ` : ''}
                </div>
            </div>
        `;

        this.attachListeners(bld);
    }

    attachListeners(bld) {
        this.element.querySelector('#modal-close-btn').addEventListener('click', () => this.close());

        const bindTab = (id, tabKey) => {
            const btn = this.element.querySelector(id);
            if (btn) btn.addEventListener('click', () => { this.activeDetailTab = tabKey; this.renderDetailView(bld, gameStore.config?.plotsById?.[bld.plotId]); });
        };
        bindTab('#tab-det-attrs', 'ATTRIBUTES');
        bindTab('#tab-det-evolve', 'EVOLVE');
        bindTab('#tab-det-merge', 'MERGE');
        bindTab('#tab-det-interiors', 'INTERIORS');

        const lvlBtn = this.element.querySelector('#btn-level-up');
        if (lvlBtn) {
            lvlBtn.addEventListener('click', () => {
                gameStore.formLevelUp(bld.id);
                this.close();
            });
        }

        this.element.querySelectorAll('[data-branch-target]').forEach(btn => {
            btn.addEventListener('click', () => {
                const targetFormId = btn.dataset.branchTarget;
                gameStore.branchEvolve(bld.id, targetFormId);
                this.close();
            });
        });

        const mergeBtn = this.element.querySelector('#btn-merge-evolve');
        if (mergeBtn) {
            mergeBtn.addEventListener('click', () => {
                const selectEl = this.element.querySelector('#merge-partner-select');
                if (selectEl && selectEl.value) {
                    gameStore.mergeEvolve(bld.id, selectEl.value);
                    this.close();
                }
            });
        }

        this.element.querySelectorAll('[data-upgrade-interior]').forEach(btn => {
            btn.addEventListener('click', () => {
                const intId = btn.dataset.upgradeInterior;
                gameStore.interiorUpgrade(bld.id, intId);
                this.close();
            });
        });
    }
}
