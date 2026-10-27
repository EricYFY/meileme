<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { store, api } from '../../services/store.js';

const emit = defineEmits(['close']);

// 13 个大类导航
const TABS = [
    { id: 'global', name: '全局参数', icon: '🌐' },
    { id: 'segments', name: '客群配置', icon: '👥' },
    { id: 'regions', name: '区域客流', icon: '🗺️' },
    { id: 'mothers', name: '母本建筑', icon: '🏛️' },
    { id: 'buildingForms', name: '建筑形态', icon: '🏢' },
    { id: 'evolutionLines', name: '进化路线', icon: '🌿' },
    { id: 'interiors', name: '内装配置', icon: '🛋️' },
    { id: 'mergeRecipes', name: '合并配方', icon: '🧬' },
    { id: 'plots', name: '地块地图', icon: '📍' },
    { id: 'abilities', name: '能力体系', icon: '⚡' },
    { id: 'positions', name: '岗位能力', icon: '🧑‍💼' },
    { id: 'skills', name: '技能树', icon: '🌳' },
    { id: 'achievements', name: '成就系统', icon: '🏆' },
];

// 用户明确指定的 10 大技能分类及对应效果映射
const SKILL_CATEGORIES = [
    { name: '减少内装升级费用', effectType: 'COST_DISCOUNT', effectKey: 'INTERIOR_UPGRADE_COST_DISCOUNT', hint: '如 0.10 代表减免 10%' },
    { name: '减少招聘费用', effectType: 'COST_DISCOUNT', effectKey: 'HIRE_COST_DISCOUNT', hint: '如 0.10 代表减免 10%' },
    { name: '减少解雇费用', effectType: 'COST_DISCOUNT', effectKey: 'FIRE_COMPENSATION_DISCOUNT', hint: '如 0.10 代表减免 10%' },
    { name: '减少建筑进化费用', effectType: 'COST_DISCOUNT', effectKey: 'EVOLVE_COST_DISCOUNT', hint: '如 0.10 代表减免 10%' },
    { name: '减少建筑升级费用', effectType: 'COST_DISCOUNT', effectKey: 'EVOLVE_COST_DISCOUNT', hint: '如 0.10 代表减免 10%' },
    { name: '提升内装舒适度', effectType: 'MULTIPLIER', effectKey: 'INTERIOR_COMFORT_MULT', hint: '如 0.15 代表提升 15%' },
    { name: '提升岗位能力值贡献', effectType: 'MULTIPLIER', effectKey: 'STAFF_ABILITY_MULT', hint: '如 0.10 代表提升 10%' },
    { name: '减少建筑建造费用', effectType: 'COST_DISCOUNT', effectKey: 'BUILD_COST_DISCOUNT', hint: '如 0.10 代表减免 10%' },
    { name: '提升住宿收益', effectType: 'MULTIPLIER', effectKey: 'ROOM_PRICE_MULT', hint: '如 0.05 代表提升 5%' },
    { name: '提升配套收益', effectType: 'MULTIPLIER', effectKey: 'SUPPORT_PRICE_MULT', hint: '如 0.05 代表提升 5%' },
];

// 用户明确指定的 6 大成就分类及对应类型映射
const ACHIEVEMENT_CATEGORIES = [
    { id: 'BUILDING_COUNT', name: '已建N个建筑', unit: '个建筑', hasEntity: false },
    { id: 'CUMULATIVE_GUESTS', name: '累计接待N名顾客', unit: '名顾客', hasEntity: false },
    { id: 'TOTAL_ASSETS', name: '总资产达到N万', unit: '万元', hasEntity: false },
    { id: 'STAFF_COUNT', name: '员工总数达到N个', unit: '名员工', hasEntity: false },
    { id: 'POSITION_LEVEL_COUNT', name: '拥有N名A等级的B岗位', unit: '名员工', hasEntity: true, entityType: 'position' },
    { id: 'FORM_LEVEL_COUNT', name: '建造N个A等级B形态建筑', unit: '个建筑', hasEntity: true, entityType: 'form' },
];

const currentTab = ref('global');

// 本地可编辑的副本（深度克隆自 store.adminConfig）
const form = ref(null);
const fileInput = ref(null);

onMounted(() => {
    api.getAdminConfig();
});

watch(
    () => store.adminConfig,
    (cfg) => {
        if (cfg) {
            form.value = JSON.parse(JSON.stringify(cfg));
            normalizeConfig();
        }
    },
    { immediate: true }
);

// 规范化配置缺失字段与反向绑定
function normalizeConfig() {
    if (!form.value) return;
    const f = form.value;
    if (!f.segments) f.segments = [];
    if (!f.regions) f.regions = [];
    if (!f.mothers) f.mothers = [];
    if (!f.buildingForms) f.buildingForms = [];
    if (!f.evolutionLines) f.evolutionLines = [];
    if (!f.interiors) f.interiors = [];
    if (!f.mergeRecipes) f.mergeRecipes = [];
    if (!f.plots) f.plots = [];
    if (!f.abilities) f.abilities = [];
    if (!f.positions) f.positions = [];
    if (!f.achievements) f.achievements = [];

    // 0. 规范化全局参数
    if (!f.global) f.global = {};
    if (f.global.minConversionRate === undefined) f.global.minConversionRate = 0.05;
    if (f.global.defaultSegmentAffinity === undefined) f.global.defaultSegmentAffinity = 0.5;
    if (f.global.formMinLevel === undefined) f.global.formMinLevel = 1;
    if (f.global.formMaxLevel === undefined) f.global.formMaxLevel = 3;
    if (f.global.interiorMaxLevelFallback === undefined) f.global.interiorMaxLevelFallback = 3;
    if (f.global.staffMinLevel === undefined) f.global.staffMinLevel = 1;
    if (f.global.staffMaxLevel === undefined) f.global.staffMaxLevel = 3;
    if (f.global.fireCompensationPerDayRatio === undefined) f.global.fireCompensationPerDayRatio = 0.1;
    if (f.global.mapSeed === undefined) f.global.mapSeed = 12345;

    // 1. 规范化母体：彻底移除废弃字段
    f.mothers.forEach((m) => {
        if (m.isOpen === undefined) m.isOpen = true;
        delete m.baseFormId;
        delete m.initialBuildDays;
        delete m.phase1Buildable;
        delete m.capacityPerRoom;
        delete m.serviceCapacity;
    });

    // 2. 规范化形态升级费用、工期与 availableInteriors、大类，彻底移除 line/buildCost/evolveFrom
    f.buildingForms.forEach((bf) => {
        if (!bf.availableInteriors) bf.availableInteriors = [];
        if (!bf.category) {
            bf.category = 'ACCOMMODATION';
        }
        delete bf.line;
        delete bf.buildCost;
        delete bf.evolveFrom;
        delete bf.mother;
        if (!bf.formLevelUpCost || bf.formLevelUpCost.length < 2) {
            bf.formLevelUpCost = [bf.formLevelUpCost?.[0] || 2000, bf.formLevelUpCost?.[1] || 4000];
        }
        if (!bf.formLevelUpDays || bf.formLevelUpDays.length < 2) {
            bf.formLevelUpDays = [bf.formLevelUpDays?.[0] || 2, bf.formLevelUpDays?.[1] || 3];
        }
        if (!Array.isArray(bf.maxInteriorLevel)) {
            bf.maxInteriorLevel = [1, 2, 3];
        }
        if (!bf.abilityDemands || typeof bf.abilityDemands !== 'object') {
            bf.abilityDemands = {};
        }
    });

    // 3. 规范化配方 sources，彻底移除 sourceFormA/B
    f.mergeRecipes.forEach((r) => {
        if (!r.sources || r.sources.length === 0) {
            r.sources = [
                { formId: r.sourceFormA || '', minLevel: r.sourceLevelA || 3 },
                { formId: r.sourceFormB || '', minLevel: r.sourceLevelB || 3 },
            ];
        }
        delete r.sourceFormA;
        delete r.sourceLevelA;
        delete r.sourceFormB;
        delete r.sourceLevelB;
    });

    // 4. 规范化地块 allowedMothers，彻底移除 buildLimit/accommodationWhitelist/supportWhitelist
    f.plots.forEach((p) => {
        if (!p.allowedMothers) {
            p.allowedMothers = Array.from(new Set([...(p.accommodationWhitelist || []), ...(p.supportWhitelist || [])]));
        }
        delete p.buildLimit;
        delete p.accommodationWhitelist;
        delete p.supportWhitelist;
    });

    // 4.5 规范化建筑形态多等级数组与客群相性
    f.buildingForms.forEach((bf) => {
        if (!Array.isArray(bf.formLevelUpCost)) bf.formLevelUpCost = [2000, 4000];
        if (!Array.isArray(bf.formLevelUpDays)) bf.formLevelUpDays = [2, 3];
        if (!Array.isArray(bf.pricePerRoom)) bf.pricePerRoom = [200, 280, 380];
        if (!Array.isArray(bf.unitPrice)) bf.unitPrice = [30, 45, 60];
        if (!Array.isArray(bf.maxInteriorLevel)) bf.maxInteriorLevel = [1, 2, 3];
        if (!bf.affinities || typeof bf.affinities !== 'object') {
            bf.affinities = {};
        }
        (f.segments || []).forEach((seg) => {
            if (bf.affinities[seg.id] === undefined || bf.affinities[seg.id] === null) {
                bf.affinities[seg.id] = 0.5;
            }
        });
        if (bf.capacityInterior === undefined) {
            if (bf.category === 'ACCOMMODATION') {
                bf.capacityInterior = 'BED';
            } else if (bf.id && bf.id.toUpperCase().includes('RESTAURANT')) {
                bf.capacityInterior = 'DINING_TABLE';
            } else if (bf.id && bf.id.toUpperCase().includes('HOT_SPRING')) {
                bf.capacityInterior = 'HOT_SPRING_TUB';
            } else {
                bf.capacityInterior = null;
            }
        }
    });

    // 4.6 规范化内装升级工期、升级费用与多客群相性
    f.interiors.forEach((it) => {
        if (!it.category) {
            it.category = it.id === 'BED' ? 'CARRY' : 'NORMAL';
        }
        delete it.applicableMothers;
        delete it.affinitySegment;
        delete it.affinityPerLevel;
        if (!Array.isArray(it.upgradeDaysByLevel) || it.upgradeDaysByLevel.length === 0) {
            it.upgradeDaysByLevel = [1, 2, 3];
        }
        if (!Array.isArray(it.upgradeCostByLevel) || it.upgradeCostByLevel.length === 0) {
            it.upgradeCostByLevel = [800, 1600, 3200];
        }
        while (it.upgradeCostByLevel.length < it.upgradeDaysByLevel.length) {
            const lastCost = it.upgradeCostByLevel[it.upgradeCostByLevel.length - 1] || 1000;
            it.upgradeCostByLevel.push(lastCost * 2);
        }
        if (it.revenueBonusPerLevel === undefined || it.revenueBonusPerLevel === null) {
            it.revenueBonusPerLevel = 0.02;
        }
        if (it.capacityPerLevel === undefined || it.capacityPerLevel === null) {
            it.capacityPerLevel = it.id === 'BED' ? 1 : (it.category === 'CARRY' ? 1 : 0);
        }
        if (!it.segmentAffinityPerLevel) {
            it.segmentAffinityPerLevel = {};
        }
        (f.segments || []).forEach((seg) => {
            if (it.segmentAffinityPerLevel[seg.id] === undefined) {
                it.segmentAffinityPerLevel[seg.id] = 0;
            }
        });
    });

    // 5. 规范化能力体系（去除冗余字段）
    f.abilities.forEach((ab) => {
        delete ab.category;
        delete ab.baseSupply;
        delete ab.shortageSeverity;
    });

    // 6. 规范化岗位多能力输出（细化到每个能力各个等级的具体数值）
    f.positions.forEach((p) => {
        if (!Array.isArray(p.upgradeDaysByLevel) || p.upgradeDaysByLevel.length === 0) {
            p.upgradeDaysByLevel = [10, 20];
        }
        delete p.abilities;
        delete p.abilityValues;
        delete p.abilityValueByLevel;
        delete p.upgradeDaysLv1ToLv2;
        delete p.upgradeDaysLv2ToLv3;
        if (!Array.isArray(p.trainDaysByLevel) || p.trainDaysByLevel.length === 0) {
            p.trainDaysByLevel = [1, 2];
        }
        while (p.trainDaysByLevel.length < p.upgradeDaysByLevel.length) {
            p.trainDaysByLevel.push(p.trainDaysByLevel[p.trainDaysByLevel.length - 1] + 1);
        }
        if (!Array.isArray(p.trainCostByLevel) || p.trainCostByLevel.length === 0) {
            p.trainCostByLevel = [Math.round((p.hireCost || 400) * 0.6), Math.round((p.hireCost || 400) * 1.2)];
        }
        while (p.trainCostByLevel.length < p.upgradeDaysByLevel.length) {
            const lastCost = p.trainCostByLevel[p.trainCostByLevel.length - 1] || 400;
            p.trainCostByLevel.push(Math.round(lastCost * 1.5));
        }
        if (!p.abilityOutputs) {
            p.abilityOutputs = {};
            if (f.abilities && f.abilities.length > 0) {
                p.abilityOutputs[f.abilities[0].id] = [30, 55, 85];
            }
        }
        Object.keys(p.abilityOutputs).forEach((abKey) => {
            const vals = p.abilityOutputs[abKey];
            if (!Array.isArray(vals) || vals.length === 0) {
                p.abilityOutputs[abKey] = [30, 55, 85];
            }
        });
        p.hireCost = p.hireCost || 400;
        p.compensationBase = p.compensationBase || 600;
        // 彻底移除薪资
        delete p.baseSalary;
        delete p.salaryIncreasePerLevel;
        delete p.dailyTrainingCost;
    });

    // 7. 规范化技能分类映射
    f.skills.forEach((sk) => {
        if (!sk.category || !SKILL_CATEGORIES.some((c) => c.name === sk.category)) {
            const matched = SKILL_CATEGORIES.find((c) => c.effectKey === sk.effectKey) || SKILL_CATEGORIES[0];
            sk.category = matched.name;
            sk.effectType = matched.effectType;
            sk.effectKey = matched.effectKey;
        }
        if (!sk.prerequisites) sk.prerequisites = [];
        delete sk.skillPointsCost;
    });

    // 8. 规范化成就分类与参数映射
    f.achievements.forEach((ach) => {
        const matched = ACHIEVEMENT_CATEGORIES.find((c) => c.id === ach.type || c.name === ach.category) || ACHIEVEMENT_CATEGORIES[0];
        ach.category = matched.name;
        ach.type = matched.id;
        ach.targetValue = ach.targetValue !== undefined ? ach.targetValue : (ach.target || 10);
        ach.rewardSkillPoints = ach.rewardSkillPoints !== undefined ? ach.rewardSkillPoints : 1;
        delete ach.isMilestone;

        if (ach.param && ach.param.includes('_')) {
            const idx = ach.param.lastIndexOf('_');
            ach.targetEntity = ach.param.substring(0, idx);
            ach.targetLevel = parseInt(ach.param.substring(idx + 1)) || 1;
        } else {
            ach.targetEntity = matched.entityType === 'position' ? (f.positions[0]?.id || '') : (f.buildingForms[0]?.id || '');
            ach.targetLevel = 1;
        }
    });
}

async function onSave() {
    if (!form.value) return;

    form.value.mothers.forEach((m) => {
        delete m.baseFormId;
        delete m.initialBuildDays;
        delete m.phase1Buildable;
        delete m.capacityPerRoom;
        delete m.serviceCapacity;
    });

    form.value.buildingForms.forEach((bf) => {
        if (!bf.category) {
            bf.category = 'ACCOMMODATION';
        }
        delete bf.line;
        delete bf.buildCost;
        delete bf.evolveFrom;
        delete bf.mother;
    });

    form.value.mergeRecipes.forEach((r) => {
        delete r.sourceFormA;
        delete r.sourceLevelA;
        delete r.sourceFormB;
        delete r.sourceLevelB;
    });

    form.value.plots.forEach((p) => {
        delete p.buildLimit;
        delete p.accommodationWhitelist;
        delete p.supportWhitelist;
    });

    form.value.positions.forEach((p) => {
        delete p.abilities;
        delete p.abilityValues;
        delete p.abilityValueByLevel;
        delete p.upgradeDaysLv1ToLv2;
        delete p.upgradeDaysLv2ToLv3;
    });

    form.value.interiors.forEach((it) => {
        delete it.applicableMothers;
        delete it.affinitySegment;
        delete it.affinityPerLevel;
    });

    form.value.skills.forEach((sk) => {
        const cat = SKILL_CATEGORIES.find((c) => c.name === sk.category);
        if (cat) {
            sk.effectType = cat.effectType;
            sk.effectKey = cat.effectKey;
        }
        sk.costPoints = Math.max(1, sk.costPoints || 1);
        delete sk.skillPointsCost;
    });

    // 成就同步
    form.value.achievements.forEach((ach) => {
        const cat = ACHIEVEMENT_CATEGORIES.find((c) => c.name === ach.category);
        if (cat) {
            ach.type = cat.id;
            ach.target = ach.targetValue;
            if (cat.hasEntity) {
                ach.param = `${ach.targetEntity || ''}_${ach.targetLevel || 1}`;
            } else {
                ach.param = '';
            }
        }
        ach.rewardSkillPoints = Math.max(0, ach.rewardSkillPoints !== undefined ? ach.rewardSkillPoints : 1);
        delete ach.isMilestone;
    });

    if (confirm('确认保存当前所有参数至 admin-config.json 并在开局时生效？')) {
        const res = await api.saveAdminConfig(form.value);
        if (res && res.success) {
            alert(`✅ 保存成功！已写入 admin-config.json 并热重载生效！\n\n文件路径: ${res.filePath || 'admin-config.json'}`);
        } else {
            alert(`❌ 保存失败：${res?.message || '网络连接异常，请检查后端是否启动'}`);
        }
    }
}

async function onResetDefault() {
    if (confirm('警告：此操作将删除 admin-config.json 并重置回 default-config.json 出厂默认值，确定继续？')) {
        const res = await api.resetAdminConfig();
        if (res && res.success) {
            alert('✅ 已成功删除 admin-config.json 并重置为出厂默认配置！');
        } else {
            alert(`❌ 重置失败：${res?.message || '网络连接异常'}`);
        }
    }
}

function onExport() {
    if (!form.value) return;
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(form.value, null, 2));
    const a = document.createElement('a');
    a.setAttribute('href', dataStr);
    a.setAttribute('download', 'resort-config.json');
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function onImportClick() {
    if (fileInput.value) fileInput.value.click();
}

function onFileSelected(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
        try {
            const parsed = JSON.parse(e.target.result);
            form.value = parsed;
            normalizeConfig();
            alert('本地 JSON 配置导入成功，请核对无误后点击「保存配置」！');
        } catch (err) {
            alert('JSON 文件解析失败，请检查格式！');
        }
    };
    reader.readAsText(file);
    event.target.value = '';
}

// ===================== CRUD 联动方法 =====================

// 1. 客群
function addSegment() {
    const newId = 'SEG_' + Date.now().toString().slice(-4);
    form.value.segments.push({
        id: newId,
        displayName: '新客群',
        description: '客群偏好描述',
        color: '#38bdf8',
    });
    form.value.regions.forEach((r) => {
        if (!r.segmentRatios) r.segmentRatios = {};
        r.segmentRatios[newId] = 0.2;
    });
}
function removeSegment(idx) {
    const id = form.value.segments[idx]?.id;
    if (confirm(`确定删除客群 [${id}]？将同时清理区域与内装中该客群的关联！`)) {
        form.value.segments.splice(idx, 1);
        form.value.regions.forEach((r) => {
            if (r.segmentRatios) delete r.segmentRatios[id];
        });
    }
}

// 2. 区域
function addRegion() {
    const newId = 'REGION_' + Date.now().toString().slice(-4);
    const ratios = {};
    form.value.segments.forEach((s) => { ratios[s.id] = 0.25; });
    form.value.regions.push({
        id: newId,
        displayName: '新区域',
        description: '风景优美的度假区域',
        baseDailyFlow: 100,
        segmentRatios: ratios,
    });
}
function removeRegion(idx) {
    const r = form.value.regions[idx];
    if (confirm(`确定删除区域 [${r.displayName}] 吗？`)) {
        form.value.regions.splice(idx, 1);
    }
}

// 3. 母体建筑
function addMother() {
    const newId = 'MOTHER_' + Date.now().toString().slice(-4);
    form.value.mothers.push({
        id: newId,
        displayName: '新母体建筑',
        category: 'ACCOMMODATION',
        isOpen: true,
        buildCost: 5000,
        buildDays: 3,
    });
}
function removeMother(idx) {
    const m = form.value.mothers[idx];
    if (confirm(`确定删除母体 [${m.displayName}]？请确认无正在依赖此母体的形态！`)) {
        form.value.mothers.splice(idx, 1);
    }
}

// 4. 建筑形态
function addBuildingForm() {
    const newId = 'FORM_' + Date.now().toString().slice(-4);
    const initialAffinities = {};
    (form.value.segments || []).forEach((s) => {
        initialAffinities[s.id] = 0.5;
    });
    const initialDemands = {};
    (form.value.abilities || []).forEach((ab) => {
        initialDemands[ab.id] = 0;
    });
    form.value.buildingForms.push({
        id: newId,
        displayName: '新建筑形态',
        category: 'ACCOMMODATION',
        tier: 1,
        capacityPerRoom: 2,
        serviceCapacity: 0,
        pricePerRoom: [300, 350, 400],
        unitPrice: [0, 0, 0],
        evolveCost: 10000,
        evolveDays: 3,
        formLevelUpCost: [2000, 4000],
        formLevelUpDays: [2, 3],
        baseComfort: 50,
        capacityInterior: 'BED',
        affinities: initialAffinities,
        maxInteriorLevel: [3, 3, 3],
        abilityDemands: initialDemands,
        availableInteriors: form.value.interiors?.slice(0, 3).map((it) => it.id) || [],
        story: '',
    });
}
function removeBuildingForm(idx) {
    const f = form.value.buildingForms[idx];
    if (confirm(`确定删除建筑形态 [${f.displayName}] (${f.id}) 吗？`)) {
        form.value.buildingForms.splice(idx, 1);
    }
}
function toggleFormInterior(formItem, interiorId) {
    if (!formItem.availableInteriors) formItem.availableInteriors = [];
    const idx = formItem.availableInteriors.indexOf(interiorId);
    if (idx >= 0) formItem.availableInteriors.splice(idx, 1);
    else formItem.availableInteriors.push(interiorId);
}

function addFormLevel(f) {
    if (!Array.isArray(f.formLevelUpCost)) f.formLevelUpCost = [2000, 4000];
    if (!Array.isArray(f.formLevelUpDays)) f.formLevelUpDays = [2, 3];
    if (!Array.isArray(f.pricePerRoom)) f.pricePerRoom = [200, 280, 380];
    if (!Array.isArray(f.unitPrice)) f.unitPrice = [30, 45, 60];
    if (!Array.isArray(f.maxInteriorLevel)) f.maxInteriorLevel = [1, 2, 3];

    const len = f.formLevelUpCost.length;
    const lastCost = f.formLevelUpCost[len - 1] || 3000;
    const lastDays = f.formLevelUpDays[len - 1] || 3;
    f.formLevelUpCost.push(Math.round(lastCost * 1.5));
    f.formLevelUpDays.push(lastDays + 1);

    const lastPrice = f.pricePerRoom[f.pricePerRoom.length - 1] || 250;
    f.pricePerRoom.push(Math.round(lastPrice * 1.25));

    const lastUnit = f.unitPrice[f.unitPrice.length - 1] || 40;
    f.unitPrice.push(Math.round(lastUnit * 1.25));

    const lastInt = f.maxInteriorLevel[f.maxInteriorLevel.length - 1] || 3;
    f.maxInteriorLevel.push(lastInt + 1);
}

function removeFormLevel(f, lvIdx) {
    if (!f.formLevelUpCost || f.formLevelUpCost.length <= 0) return;
    const targetLv = f.formLevelUpCost.length + 1;
    if (confirm(`确定移除建筑形态最高等级阶段 [Lv${targetLv - 1} ➔ Lv${targetLv}] 吗？`)) {
        f.formLevelUpCost.splice(lvIdx, 1);
        if (f.formLevelUpDays.length > f.formLevelUpCost.length) f.formLevelUpDays.splice(lvIdx, 1);
        if (f.pricePerRoom.length > f.formLevelUpCost.length + 1) f.pricePerRoom.pop();
        if (f.unitPrice.length > f.formLevelUpCost.length + 1) f.unitPrice.pop();
        if (f.maxInteriorLevel.length > f.formLevelUpCost.length + 1) f.maxInteriorLevel.pop();
    }
}

// 5. 进化路线
function addEvolutionLine() {
    const forms = form.value.buildingForms || [];
    const newId = 'EVO_' + Date.now().toString().slice(-4);
    form.value.evolutionLines.push({
        id: newId,
        sourceFormId: forms[0]?.id || '',
        requiredLevel: 3,
        targetFormId: forms[1]?.id || forms[0]?.id || '',
        cost: 10000,
        days: 3,
    });
}
function removeEvolutionLine(idx) {
    if (confirm('确定删除该条进化路线吗？')) {
        form.value.evolutionLines.splice(idx, 1);
    }
}

function addInterior() {
    const newId = 'IT_' + Date.now().toString().slice(-4);
    const segAffinities = {};
    (form.value.segments || []).forEach((seg) => {
        segAffinities[seg.id] = 0;
    });
    if (form.value.segments && form.value.segments[0]) {
        segAffinities[form.value.segments[0].id] = 0.1;
    }
    form.value.interiors.push({
        id: newId,
        displayName: '新内装设施',
        category: 'NORMAL',
        comfortPerLevel: 3,
        capacityPerLevel: 0,
        revenueBonusPerLevel: 0.02,
        segmentAffinityPerLevel: segAffinities,
        upgradeCostByLevel: [1000, 2000, 4000],
        upgradeDaysByLevel: [1, 2, 3],
        story: '',
    });
}
function removeInterior(idx) {
    const it = form.value.interiors[idx];
    if (confirm(`确定删除内装 [${it.displayName}] 吗？`)) {
        form.value.interiors.splice(idx, 1);
    }
}
function addInteriorLevel(it) {
    if (!Array.isArray(it.upgradeDaysByLevel)) {
        it.upgradeDaysByLevel = [1, 2, 3];
    }
    const len = it.upgradeDaysByLevel.length;
    const lastDays = it.upgradeDaysByLevel[len - 1] || 2;
    it.upgradeDaysByLevel.push(lastDays + 1);

    if (Array.isArray(it.upgradeCostByLevel)) {
        const lastCost = it.upgradeCostByLevel[it.upgradeCostByLevel.length - 1] || 2000;
        it.upgradeCostByLevel.push(lastCost * 2);
    }
}
function removeInteriorLevel(it, idx) {
    if (!it.upgradeDaysByLevel || it.upgradeDaysByLevel.length <= 1) {
        alert('至少保留一级内装！');
        return;
    }
    if (confirm('确定移除最高级内装吗？')) {
        it.upgradeDaysByLevel.splice(idx, 1);
        if (Array.isArray(it.upgradeCostByLevel) && it.upgradeCostByLevel.length > it.upgradeDaysByLevel.length) {
            it.upgradeCostByLevel.pop();
        }
    }
}

// 7. 合并配方
function addMergeRecipe() {
    const forms = form.value.buildingForms || [];
    const newId = 'MERGE_' + Date.now().toString().slice(-4);
    form.value.mergeRecipes.push({
        id: newId,
        displayName: '新复合合并建筑',
        sources: [
            { formId: forms[0]?.id || '', minLevel: 3 },
            { formId: forms[1]?.id || forms[0]?.id || '', minLevel: 3 },
        ],
        resultForm: forms[0]?.id || '',
        resultLevel: 1,
        mergeCost: 35000,
        mergeDays: 5,
    });
}
function removeMergeRecipe(idx) {
    if (confirm('确定删除该合并配方吗？')) {
        form.value.mergeRecipes.splice(idx, 1);
    }
}
function addRecipeSource(recipe) {
    const forms = form.value.buildingForms || [];
    if (!recipe.sources) recipe.sources = [];
    recipe.sources.push({ formId: forms[0]?.id || '', minLevel: 3 });
}
function removeRecipeSource(recipe, srcIdx) {
    if (recipe.sources.length <= 2) {
        alert('合并配方至少需要 2 个原料建筑！');
        return;
    }
    recipe.sources.splice(srcIdx, 1);
}

// 8. 地块地图
function addPlot() {
    const newId = 'PLOT_' + Date.now().toString().slice(-4);
    const regId = form.value.regions?.[0]?.id || 'LIMGRAVE';
    form.value.plots.push({
        id: newId,
        displayName: '新地块',
        region: regId,
        orderInRegion: (form.value.plots?.length || 0) + 1,
        width: 7,
        height: 7,
        buildableCellCount: 15,
        unlockCost: 50000,
        allowedMothers: form.value.mothers?.filter((m) => m.isOpen)?.map((m) => m.id) || [],
        evolveTierCap: 2,
        unlockedByDefault: false,
        story: '',
    });
}
function removePlot(idx) {
    const p = form.value.plots[idx];
    if (confirm(`确定删除地块 [${p.displayName}] 吗？`)) {
        form.value.plots.splice(idx, 1);
    }
}
function togglePlotMother(plot, motherId) {
    if (!plot.allowedMothers) plot.allowedMothers = [];
    const idx = plot.allowedMothers.indexOf(motherId);
    if (idx >= 0) plot.allowedMothers.splice(idx, 1);
    else plot.allowedMothers.push(motherId);
}

// 9. 能力体系（去除冗余的大类与基础保底供给量）
function addAbility() {
    const newId = 'ABILITY_' + Date.now().toString().slice(-4);
    form.value.abilities.push({
        id: newId,
        displayName: '新能力服务',
        description: '保持设施正常运营所需要的能力支撑',
    });
}
function removeAbility(idx) {
    const a = form.value.abilities[idx];
    if (confirm(`确定删除能力 [${a.displayName}] (${a.id}) 吗？`)) {
        form.value.abilities.splice(idx, 1);
    }
}

// 10. 岗位能力：无薪资，为各个能力分别配置各等级提供能力值
function addPosition() {
    const newId = 'POS_' + Date.now().toString().slice(-4);
    const abId = form.value.abilities?.[0]?.id || 'ROOM_CLEANING';
    const outputs = {};
    outputs[abId] = [30, 55, 85];
    form.value.positions.push({
        id: newId,
        displayName: '新岗位',
        abilities: [abId],
        abilityOutputs: outputs,
        hireCost: 500,
        upgradeDaysByLevel: [10, 20],
        trainDaysByLevel: [1, 2],
        trainCostByLevel: [300, 600],
        compensationBase: 700,
        story: '',
    });
}
function removePosition(idx) {
    const p = form.value.positions[idx];
    if (confirm(`确定删除岗位 [${p.displayName}] 吗？`)) {
        form.value.positions.splice(idx, 1);
    }
}
function addPositionAbilityOutput(pos) {
    const existing = Object.keys(pos.abilityOutputs || {});
    const candidate = form.value.abilities?.find((a) => !existing.includes(a.id)) || form.value.abilities?.[0];
    if (!candidate) {
        alert('当前没有更多可用能力，请先在「能力体系」中添加能力！');
        return;
    }
    if (!pos.abilityOutputs) pos.abilityOutputs = {};
    pos.abilityOutputs[candidate.id] = [30, 55, 85];
    pos.abilities = Object.keys(pos.abilityOutputs);
}
function removePositionAbilityOutput(pos, abKey) {
    if (Object.keys(pos.abilityOutputs || {}).length <= 1) {
        alert('每个岗位至少需要提供一项能力！');
        return;
    }
    delete pos.abilityOutputs[abKey];
    pos.abilities = Object.keys(pos.abilityOutputs);
}
function getAbilityName(abId) {
    return form.value?.abilities?.find((a) => a.id === abId)?.displayName || abId;
}

function addPositionUpgradeTier(pos) {
    if (!Array.isArray(pos.upgradeDaysByLevel)) {
        pos.upgradeDaysByLevel = [10, 20];
    }
    const len = pos.upgradeDaysByLevel.length;
    const lastDays = pos.upgradeDaysByLevel[len - 1] || 20;
    pos.upgradeDaysByLevel.push(lastDays + 10);

    if (!Array.isArray(pos.trainDaysByLevel)) {
        pos.trainDaysByLevel = [1, 2];
    }
    const lastTrainDays = pos.trainDaysByLevel[pos.trainDaysByLevel.length - 1] || 2;
    pos.trainDaysByLevel.push(lastTrainDays + 1);

    if (!Array.isArray(pos.trainCostByLevel)) {
        pos.trainCostByLevel = [Math.round((pos.hireCost || 400) * 0.6), Math.round((pos.hireCost || 400) * 1.2)];
    }
    const lastCost = pos.trainCostByLevel[pos.trainCostByLevel.length - 1] || 600;
    pos.trainCostByLevel.push(Math.round(lastCost * 1.5));

    // 联动为该岗位的所有能力追加下一级能力值
    Object.keys(pos.abilityOutputs || {}).forEach((k) => {
        const vals = pos.abilityOutputs[k];
        if (Array.isArray(vals)) {
            const lastVal = vals[vals.length - 1] || 50;
            vals.push(Math.round(lastVal * 1.4));
        }
    });
}

function removePositionUpgradeTier(pos, idx) {
    if (!pos.upgradeDaysByLevel || pos.upgradeDaysByLevel.length <= 1) {
        alert('每个岗位至少保留一个等级晋升阶段！');
        return;
    }
    const targetLv = pos.upgradeDaysByLevel.length + 1;
    if (confirm(`确定移除岗位最高等级阶段 [Lv${targetLv - 1} ➔ Lv${targetLv}] 吗？`)) {
        pos.upgradeDaysByLevel.splice(idx, 1);
        if (pos.trainDaysByLevel && pos.trainDaysByLevel.length > pos.upgradeDaysByLevel.length) {
            pos.trainDaysByLevel.pop();
        }
        if (pos.trainCostByLevel && pos.trainCostByLevel.length > pos.upgradeDaysByLevel.length) {
            pos.trainCostByLevel.pop();
        }
        Object.keys(pos.abilityOutputs || {}).forEach((k) => {
            const vals = pos.abilityOutputs[k];
            if (Array.isArray(vals) && vals.length > pos.upgradeDaysByLevel.length + 1) {
                vals.pop();
            }
        });
    }
}

function addAbilityOutputLevel(pos, abKey) {
    const vals = pos.abilityOutputs[abKey];
    if (Array.isArray(vals)) {
        const lastVal = vals[vals.length - 1] || 30;
        vals.push(Math.round(lastVal * 1.4));
    }
}

function removeAbilityOutputLevel(pos, abKey) {
    const vals = pos.abilityOutputs[abKey];
    if (Array.isArray(vals) && vals.length > 1) {
        vals.pop();
    }
}

// 11. 技能树
function addSkill() {
    const newId = 'SK_' + Date.now().toString().slice(-4);
    const defaultCat = SKILL_CATEGORIES[0];
    form.value.skills.push({
        id: newId,
        displayName: '新技能',
        category: defaultCat.name,
        effectType: defaultCat.effectType,
        effectKey: defaultCat.effectKey,
        value: 0.10,
        costPoints: 2,
        prerequisites: [],
    });
}
function onSkillCategoryChange(sk) {
    const cat = SKILL_CATEGORIES.find((c) => c.name === sk.category);
    if (cat) {
        sk.effectType = cat.effectType;
        sk.effectKey = cat.effectKey;
    }
}
function removeSkill(idx) {
    const s = form.value.skills[idx];
    if (confirm(`确定删除技能 [${s.displayName}] 吗？`)) {
        form.value.skills.splice(idx, 1);
    }
}
function toggleSkillPrereq(skill, prereqId) {
    if (!skill.prerequisites) skill.prerequisites = [];
    const idx = skill.prerequisites.indexOf(prereqId);
    if (idx >= 0) skill.prerequisites.splice(idx, 1);
    else skill.prerequisites.push(prereqId);
}

// 技能树可视化拓扑计算与定位
const selectedSkillId = ref(null);

const SKILL_CATEGORY_COLORS = {
    '减少内装升级费用': '#7ad7e0',
    '减少招聘费用': '#ffd37a',
    '减少解雇费用': '#f2b53c',
    '减少建筑进化费用': '#a78bfa',
    '减少建筑升级费用': '#5a8cff',
    '提升内装舒适度': '#7ee0a3',
    '提升岗位能力值贡献': '#38bdf8',
    '减少建筑建造费用': '#f472b6',
    '提升住宿收益': '#f59e0b',
    '提升配套收益': '#10b981',
};

function getSkillColor(cat) {
    return SKILL_CATEGORY_COLORS[cat] || '#38bdf8';
}

const skillTreeLayout = computed(() => {
    const list = form.value?.skills || [];
    if (!list || list.length === 0) {
        return { nodes: [], edges: [], width: 400, height: 200, maxDepth: 0 };
    }

    const byId = {};
    list.forEach((s) => { byId[s.id] = s; });

    // 计算每个技能节点的拓扑深度
    const depth = {};
    const ids = list.map((s) => s.id);
    let changed = true;
    let guard = 0;
    while (changed && guard++ < ids.length + 3) {
        changed = false;
        for (const id of ids) {
            const s = byId[id];
            const prereqs = (s.prerequisites || []).filter((p) => byId[p]);
            let d = 0;
            if (prereqs.length > 0) {
                d = Math.max(...prereqs.map((p) => (depth[p] ?? -1) + 1));
            }
            if (depth[id] === undefined || d > depth[id]) {
                depth[id] = d;
                changed = true;
            }
        }
    }

    // 分层统计
    const layers = {};
    for (const s of list) {
        const d = depth[s.id] || 0;
        (layers[d] = layers[d] || []).push(s);
    }
    const maxDepth = Math.max(0, ...Object.keys(layers).map(Number));

    const NODE_W = 160;
    const NODE_H = 64;
    const HGAP = 68;
    const VGAP = 28;

    const nodes = [];
    const pos = {};
    let maxY = 0;

    for (let d = 0; d <= maxDepth; d++) {
        const layerList = layers[d] || [];
        layerList.forEach((s, idx) => {
            const x = 30 + d * (NODE_W + HGAP);
            const y = 30 + idx * (NODE_H + VGAP);
            pos[s.id] = { x, y };
            nodes.push({
                ...s,
                x,
                y,
                depth: d,
            });
            maxY = Math.max(maxY, y + NODE_H + 30);
        });
    }

    // 生成平滑贝塞尔曲线连线
    const edges = [];
    for (const s of list) {
        for (const p of (s.prerequisites || [])) {
            if (pos[p] && pos[s.id]) {
                const x1 = pos[p].x + NODE_W;
                const y1 = pos[p].y + NODE_H / 2;
                const x2 = pos[s.id].x;
                const y2 = pos[s.id].y + NODE_H / 2;
                const midX = (x1 + x2) / 2;
                const path = `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`;
                edges.push({
                    id: `${p}->${s.id}`,
                    source: p,
                    target: s.id,
                    path,
                });
            }
        }
    }

    const width = Math.max((maxDepth + 1) * (NODE_W + HGAP) + 60, 640);
    const height = Math.max(maxY, 200);
    return { nodes, edges, width, height, maxDepth };
});

function scrollToSkill(id) {
    selectedSkillId.value = id;
    const el = document.getElementById('skill-card-' + id);
    if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

// 12. 成就
function addAchievement() {
    const newId = 'ACH_' + Date.now().toString().slice(-4);
    const defaultCat = ACHIEVEMENT_CATEGORIES[0];
    form.value.achievements.push({
        id: newId,
        displayName: '新成就',
        description: '达成此项经营指标',
        category: defaultCat.name,
        type: defaultCat.id,
        targetValue: 10,
        rewardSkillPoints: 1,
        targetEntity: '',
        targetLevel: 1,
    });
}
function onAchievementCategoryChange(ach) {
    const cat = ACHIEVEMENT_CATEGORIES.find((c) => c.name === ach.category);
    if (cat) {
        ach.type = cat.id;
        if (cat.entityType === 'position') {
            ach.targetEntity = form.value.positions[0]?.id || '';
            ach.targetLevel = 1;
        } else if (cat.entityType === 'form') {
            ach.targetEntity = form.value.buildingForms[0]?.id || '';
            ach.targetLevel = 1;
        } else {
            ach.targetEntity = '';
            ach.targetLevel = 1;
        }
    }
}
function removeAchievement(idx) {
    const a = form.value.achievements[idx];
    if (confirm(`确定删除成就 [${a.displayName}] 吗？`)) {
        form.value.achievements.splice(idx, 1);
    }
}
</script>

<template>
    <div class="admin-modal-mask">
        <div class="admin-modal-container">
            <!-- 顶栏工具条 -->
            <div class="admin-header">
                <div class="header-left">
                    <span class="adm-title">⚙️ 游戏数值与参数管理后台</span>
                    <span class="config-status" :class="store.isCustomConfig ? 'custom' : 'default'">
                        {{ store.isCustomConfig ? '已应用自定义配置 (admin-config.json)' : '当前为出厂默认配置 (default-config.json)' }}
                    </span>
                </div>
                <div class="header-right">
                    <input type="file" ref="fileInput" accept=".json" style="display:none" @change="onFileSelected" />
                    <button class="ghost small-btn" @click="onImportClick">📤 导入本地 JSON</button>
                    <button class="ghost small-btn" @click="onExport">📥 导出配置 JSON</button>
                    <button class="danger small-btn" @click="onResetDefault">🔄 恢复出厂默认值</button>
                    <button class="primary small-btn" @click="onSave">💾 保存配置并应用</button>
                    <button class="close-btn" @click="emit('close')">✕</button>
                </div>
            </div>

            <!-- 主体布局 -->
            <div class="admin-body" v-if="form">
                <!-- 左侧导航 -->
                <div class="admin-sidebar">
                    <div
                        v-for="t in TABS"
                        :key="t.id"
                        class="tab-item"
                        :class="{ active: currentTab === t.id }"
                        @click="currentTab = t.id"
                    >
                        <span class="tab-icon">{{ t.icon }}</span>
                        <span class="tab-name">{{ t.name }}</span>
                    </div>
                </div>

                <!-- 右侧主内容区 -->
                <div class="admin-content">
                    <!-- 1. 全局参数 -->
                    <div v-if="currentTab === 'global'" class="tab-pane">
                        <div class="pane-header">
                            <h3>🌐 全局运营与引擎参数</h3>
                            <span class="sub-tip">控制对局初始资金、时间流速与基础数学模型</span>
                        </div>
                        <div class="form-grid">
                            <div class="form-group">
                                <label>开局初始资金 (元)</label>
                                <input type="number" v-model.number="form.global.startingFunds" step="1000" />
                            </div>
                            <div class="form-group">
                                <label>单日真实时长 (秒/天)</label>
                                <input type="number" v-model.number="form.global.realSecondsPerGameDay" step="5" />
                            </div>
                            <div class="form-group">
                                <label>引擎心跳间隔 (毫秒)</label>
                                <input type="number" v-model.number="form.global.tickIntervalMs" step="50" />
                            </div>
                            <div class="form-group">
                                <label>默认开局倍速</label>
                                <input type="number" v-model.number="form.global.defaultSpeed" step="0.5" />
                            </div>
                            <div class="form-group">
                                <label>客流转化分母 (转化灵敏度)</label>
                                <input type="number" v-model.number="form.global.conversionDenominator" step="10" />
                            </div>
                            <div class="form-group">
                                <label>最大客流转化率 (0~1.0)</label>
                                <input type="number" v-model.number="form.global.maxConversionRate" step="0.05" min="0" max="1" />
                            </div>
                            <div class="form-group">
                                <label>人力短缺舒适度保底系数 (0~1.0)</label>
                                <input type="number" v-model.number="form.global.staffShortageMinComfortRatio" step="0.05" min="0" max="1" />
                            </div>
                            <div class="form-group">
                                <label>最小客流转化率 (0~1.0)</label>
                                <input type="number" v-model.number="form.global.minConversionRate" step="0.01" min="0" max="1" />
                            </div>
                            <div class="form-group">
                                <label>默认客群契合度 (相性保底)</label>
                                <input type="number" v-model.number="form.global.defaultSegmentAffinity" step="0.05" min="0" max="1" />
                            </div>
                            <div class="form-group">
                                <label>建筑形态最低初始等级</label>
                                <input type="number" v-model.number="form.global.formMinLevel" min="1" />
                            </div>
                            <div class="form-group">
                                <label>建筑形态最高等级上限</label>
                                <input type="number" v-model.number="form.global.formMaxLevel" min="1" />
                            </div>
                            <div class="form-group">
                                <label>内装回退最高等级 (Fallback)</label>
                                <input type="number" v-model.number="form.global.interiorMaxLevelFallback" min="1" />
                            </div>
                            <div class="form-group">
                                <label>员工最低初始等级</label>
                                <input type="number" v-model.number="form.global.staffMinLevel" min="1" />
                            </div>
                            <div class="form-group">
                                <label>员工最高等级上限</label>
                                <input type="number" v-model.number="form.global.staffMaxLevel" min="1" />
                            </div>
                            <div class="form-group">
                                <label>解雇补偿每日折算比例</label>
                                <input type="number" v-model.number="form.global.fireCompensationPerDayRatio" step="0.01" min="0" />
                            </div>
                            <div class="form-group">
                                <label>地图生成随机种子 (mapSeed)</label>
                                <input type="number" v-model.number="form.global.mapSeed" step="1" />
                            </div>
                        </div>
                    </div>

                    <!-- 2. 客群配置 -->
                    <div v-else-if="currentTab === 'segments'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>👥 客群体系配置</h3>
                                <button class="primary small-btn" @click="addSegment">➕ 新增客群</button>
                            </div>
                            <span class="sub-tip">可修改客群 ID、名称、说明及代表色；客群变更自动联动区域客流分布与内装偏好</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(seg, sIdx) in form.segments" :key="seg.id" class="card-item">
                                <div class="card-item-title">
                                    <span class="color-dot" :style="{ background: seg.color || '#fff' }"></span>
                                    <span>{{ seg.displayName }} ({{ seg.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeSegment(sIdx)">🗑️ 删除</button>
                                </div>
                                <div class="form-grid four">
                                    <div class="form-group">
                                        <label>客群 ID</label>
                                        <input type="text" v-model="seg.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>客群名称</label>
                                        <input type="text" v-model="seg.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>代表色 (HEX)</label>
                                        <input type="color" v-model="seg.color" />
                                    </div>
                                    <div class="form-group">
                                        <label>偏好描述</label>
                                        <input type="text" v-model="seg.description" />
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 3. 区域配置 -->
                    <div v-else-if="currentTab === 'regions'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🗺️ 区域客流与客群分布</h3>
                                <button class="primary small-btn" @click="addRegion">➕ 新增区域</button>
                            </div>
                            <span class="sub-tip">每个区域的基础客流以及各客群的意向客流权重占比</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(reg, rIdx) in form.regions" :key="reg.id" class="card-item">
                                <div class="card-item-title">
                                    <span>区域：{{ reg.displayName }} ({{ reg.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeRegion(rIdx)">🗑️ 删除</button>
                                </div>
                                <div class="form-grid three">
                                    <div class="form-group">
                                        <label>区域 ID</label>
                                        <input type="text" v-model="reg.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>区域名称</label>
                                        <input type="text" v-model="reg.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>每日基础客流 (人/天)</label>
                                        <input type="number" v-model.number="reg.baseDailyFlow" />
                                    </div>
                                    <div class="form-group full-row">
                                        <label>区域特色与背景描述</label>
                                        <input type="text" v-model="reg.description" placeholder="请输入区域特色与背景故事" />
                                    </div>
                                </div>
                                <div class="sub-section">
                                    <div class="sub-label">各客群权重占比 (自动联动当前客群列表)：</div>
                                    <div class="form-grid four">
                                        <div v-for="seg in form.segments" :key="seg.id" class="form-group">
                                            <label>{{ seg.displayName }} ({{ seg.id }})</label>
                                            <input type="number" v-model.number="reg.segmentRatios[seg.id]" step="0.05" min="0" max="1" />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 4. 母本建筑 -->
                    <div v-else-if="currentTab === 'mothers'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🏛️ 母本建筑清单</h3>
                                <button class="primary small-btn" @click="addMother">➕ 新增母体</button>
                            </div>
                            <span class="sub-tip">勾选「开放」的母体将在形态中默认作为基础形态呈现，其建造成本与工期在此维护</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(m, mIdx) in form.mothers" :key="m.id" class="card-item">
                                <div class="card-item-title">
                                    <span class="tag-badge">{{ m.category === 'ACCOMMODATION' ? '住宿' : '配套' }}</span>
                                    <span>{{ m.displayName }} ({{ m.id }})</span>
                                    <label class="checkbox-label" style="margin-left: 12px">
                                        <input type="checkbox" v-model="m.isOpen" />
                                        <span :style="{ color: m.isOpen ? '#7ee0a3' : '#888' }">{{ m.isOpen ? '✅ 开放建造' : '⏸ 未开放' }}</span>
                                    </label>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeMother(mIdx)">🗑️ 删除</button>
                                </div>
                                <div class="form-grid five">
                                    <div class="form-group">
                                        <label>母体 ID</label>
                                        <input type="text" v-model="m.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>母体名称</label>
                                        <input type="text" v-model="m.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>建筑类别</label>
                                        <select v-model="m.category">
                                            <option value="ACCOMMODATION">住宿类 (ACCOMMODATION)</option>
                                            <option value="SUPPORT">配套类 (SUPPORT)</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>建造成本 (元)</label>
                                        <input type="number" v-model.number="m.buildCost" step="500" />
                                    </div>
                                    <div class="form-group">
                                        <label>建造工期 (天)</label>
                                        <input type="number" v-model.number="m.buildDays" min="1" />
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 5. 建筑形态 -->
                    <div v-else-if="currentTab === 'buildingForms'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🏢 建筑形态管理</h3>
                                <button class="primary small-btn" @click="addBuildingForm">➕ 新增衍生形态</button>
                            </div>
                            <span class="sub-tip">形态与母体完全解耦，自主拥有大类（住宿/配套）；此处可配置同形态各等级升级费用与工期、内装挂载</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(f, fIdx) in form.buildingForms" :key="f.id" class="card-item">
                                <div class="card-item-title">
                                    <span class="tag-badge" :style="{ background: f.category === 'SUPPORT' ? '#e8f5e9' : '#e3f2fd', color: f.category === 'SUPPORT' ? '#2e7d32' : '#1565c0' }">
                                        {{ f.category === 'SUPPORT' ? '配套类' : '住宿类' }}
                                    </span>
                                    <span class="tag-badge">{{ f.tier === 0 ? 'Tier 0 基础' : `Tier ${f.tier} 衍生形态` }}</span>
                                    <span>{{ f.displayName }} ({{ f.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeBuildingForm(fIdx)">🗑️ 删除形态</button>
                                </div>
                                <div class="form-grid four">
                                    <div class="form-group">
                                        <label>形态 ID</label>
                                        <input type="text" v-model="f.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>形态展示名</label>
                                        <input type="text" v-model="f.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>建筑大类 (核心属性)</label>
                                        <select v-model="f.category">
                                            <option value="ACCOMMODATION">住宿类 (ACCOMMODATION)</option>
                                            <option value="SUPPORT">配套类 (SUPPORT)</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>形态阶数 (Tier)</label>
                                        <input type="number" v-model.number="f.tier" min="0" max="4" />
                                    </div>
                                    <div class="form-group">
                                        <label>基础舒适度</label>
                                        <input type="number" v-model.number="f.baseComfort" step="5" />
                                    </div>
                                    <div class="form-group" v-if="f.category === 'ACCOMMODATION'">
                                        <label>每间客房容量 (住宿)</label>
                                        <input type="number" v-model.number="f.capacityPerRoom" />
                                    </div>
                                    <div class="form-group" v-else>
                                        <label>服务容量 (配套)</label>
                                        <input type="number" v-model.number="f.serviceCapacity" />
                                    </div>
                                    <div class="form-group">
                                        <label>★ 承载能力对应内装 (单选)</label>
                                        <select v-model="f.capacityInterior">
                                            <option :value="null">无对应内装 (不扩充容量)</option>
                                            <option v-for="it in form.interiors" :key="it.id" :value="it.id">
                                                {{ it.displayName }} ({{ it.id }})
                                            </option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>Lv1 允许内装上限 (级)</label>
                                        <input type="number" v-model.number="f.maxInteriorLevel[0]" min="1" />
                                    </div>
                                </div>

                                <!-- ★ 核心：同形态等级升级消耗、工期与各级收益配置 (动态支持任意多级) -->
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>★ 同形态各等级升级消耗、工期与收益配置 (动态支持自由增减等级)：</span>
                                        <button class="ghost micro-btn" @click="addFormLevel(f)">➕ 增加升级等级 (Lv{{ (f.formLevelUpCost || []).length + 2 }})</button>
                                    </div>
                                    <div class="form-levels-table">
                                        <div class="form-level-row head">
                                            <span class="col-tier">升级阶段</span>
                                            <span class="col-cost">升级费用 (元)</span>
                                            <span class="col-days">升级工期 (天)</span>
                                            <span class="col-rev">{{ f.category === 'ACCOMMODATION' ? '升至该级客房收益 (元/间)' : '升至该级配套收益 (元/人)' }}</span>
                                            <span class="col-int" style="width: 100px; text-align: center;">内装上限 (级)</span>
                                            <span class="col-act">操作</span>
                                        </div>
                                        <div v-for="(cost, lvIdx) in (f.formLevelUpCost || [])" :key="lvIdx" class="form-level-row">
                                            <span class="col-tier tag-badge">Lv{{ lvIdx + 1 }} ➔ Lv{{ lvIdx + 2 }}</span>
                                            <div class="col-cost">
                                                <input type="number" v-model.number="f.formLevelUpCost[lvIdx]" step="500" placeholder="升级费用" />
                                            </div>
                                            <div class="col-days">
                                                <input type="number" v-model.number="f.formLevelUpDays[lvIdx]" min="1" placeholder="工期天数" />
                                            </div>
                                            <div class="col-rev">
                                                <input
                                                    v-if="f.category === 'ACCOMMODATION'"
                                                    type="number"
                                                    v-model.number="f.pricePerRoom[lvIdx + 1]"
                                                    step="20"
                                                    placeholder="房费收益"
                                                />
                                                <input
                                                    v-else
                                                    type="number"
                                                    v-model.number="f.unitPrice[lvIdx + 1]"
                                                    step="5"
                                                    placeholder="服务单价"
                                                />
                                            </div>
                                            <div class="col-int" style="width: 100px; text-align: center;">
                                                <input
                                                    type="number"
                                                    v-model.number="f.maxInteriorLevel[lvIdx + 1]"
                                                    min="1"
                                                    placeholder="内装上限"
                                                    style="width: 80px"
                                                />
                                            </div>
                                            <div class="col-act">
                                                <button
                                                    v-if="lvIdx === f.formLevelUpCost.length - 1"
                                                    class="danger micro-btn"
                                                    @click="removeFormLevel(f, lvIdx)"
                                                    title="移除最高等级阶段"
                                                >✕ 删除此级</button>
                                                <span v-else class="text-faint">-</span>
                                            </div>
                                        </div>
                                        <div v-if="!f.formLevelUpCost || f.formLevelUpCost.length === 0" class="empty-tip">
                                            当前形态固定为 Lv1 形态（无同形态升级）。点击上方「➕ 增加升级等级」即可开放升星！
                                        </div>
                                    </div>
                                    <div class="tip-text">
                                        💡 提示：该形态当前最高等级为 <strong>Lv{{ (f.formLevelUpCost || []).length + 1 }}</strong>。点击上方按钮可任意扩充升级到 Lv4、Lv5...；若需配置跨形态变迁（如标准客房 ➔ 亲子套房），请前往左侧「🌿 进化路线」。
                                    </div>
                                </div>

                                <!-- ★ 岗位能力需求配置 (abilityDemands) -->
                                <div class="sub-section">
                                    <div class="sub-label">★ 岗位能力需求配置 (abilityDemands - 运转该建筑所需各项能力基准值)：</div>
                                    <div class="segment-affinities-grid">
                                        <div v-for="ab in form.abilities" :key="ab.id" class="seg-affinity-item">
                                            <span class="seg-name">{{ ab.displayName }} ({{ ab.id }})</span>
                                            <div class="seg-input-wrap">
                                                <input
                                                    type="number"
                                                    v-model.number="f.abilityDemands[ab.id]"
                                                    min="0"
                                                    step="5"
                                                    placeholder="0"
                                                />
                                                <span class="unit-text">点</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="tip-text">
                                        💡 能力短缺影响：当在册员工提供的总能力低于所有建筑需求总和时，该建筑舒适度会按比例折损。
                                    </div>
                                </div>

                                <!-- ★ 客群基础相性配置 (形态对不同客群的基础契合度) -->
                                <div class="sub-section">
                                    <div class="sub-label">★ 客群基础相性配置 (形态对不同客群的基础契合度，默认 0.5，影响整体吸引力)：</div>
                                    <div class="segment-affinities-grid">
                                        <div v-for="seg in form.segments" :key="seg.id" class="seg-affinity-item">
                                            <span class="seg-name">{{ seg.displayName }} ({{ seg.id }})</span>
                                            <div class="seg-input-wrap">
                                                <input
                                                    type="number"
                                                    v-model.number="f.affinities[seg.id]"
                                                    step="0.05"
                                                    placeholder="0.5"
                                                />
                                            </div>
                                        </div>
                                    </div>
                                    <div class="tip-text">
                                        💡 吸引力计算：单建筑对该客群吸引力 = (形态基础相性 + ∑内装相性) × 建筑综合舒适度。若未单独配置则默认取 0.5。
                                    </div>
                                </div>

                                <!-- 内装挂载配置 -->
                                <div class="sub-section">
                                    <div class="sub-label">该形态允许包含的可用内装设施 (勾选即生效于对局建筑详情弹窗)：</div>
                                    <div class="checkbox-grid">
                                        <label
                                            v-for="it in form.interiors"
                                            :key="it.id"
                                            class="check-pill"
                                            :class="{ active: (f.availableInteriors || []).includes(it.id) }"
                                        >
                                            <input
                                                type="checkbox"
                                                :checked="(f.availableInteriors || []).includes(it.id)"
                                                @change="toggleFormInterior(f, it.id)"
                                            />
                                            {{ it.displayName }}
                                        </label>
                                    </div>
                                </div>

                                <div class="sub-section">
                                    <div class="sub-label">📖 典藏故事 (图鉴介绍)：</div>
                                    <textarea
                                        v-model="f.story"
                                        rows="2"
                                        placeholder="填写该形态在图鉴中展示的背景故事..."
                                        style="width: 100%; padding: 6px 10px; border-radius: 6px; background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.12); color: #fff; font-size: 13px;"
                                    ></textarea>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 6. 进化路线 -->
                    <div v-else-if="currentTab === 'evolutionLines'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🌿 进化路线配置</h3>
                                <button class="primary small-btn" @click="addEvolutionLine">➕ 新增进化路线</button>
                            </div>
                            <span class="sub-tip">公式化配置跨形态进化：源形态到达几级后，花费多少资金与工期进化为目标形态</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(evo, eIdx) in form.evolutionLines" :key="evo.id" class="card-item formula-card">
                                <div class="card-item-title">
                                    <span class="tag-badge">路线 #{{ eIdx + 1 }}</span>
                                    <span>{{ evo.id }}</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeEvolutionLine(eIdx)">🗑️ 删除路线</button>
                                </div>
                                <div class="formula-row">
                                    <div class="formula-node">
                                        <label>源建筑形态</label>
                                        <select v-model="evo.sourceFormId">
                                            <option v-for="f in form.buildingForms" :key="f.id" :value="f.id">
                                                {{ f.displayName }} ({{ f.id }})
                                            </option>
                                        </select>
                                    </div>
                                    <div class="formula-node">
                                        <label>要求源形态等级</label>
                                        <input type="number" v-model.number="evo.requiredLevel" min="1" />
                                    </div>
                                    <div class="formula-arrow">➔</div>
                                    <div class="formula-node">
                                        <label>进化目标形态</label>
                                        <select v-model="evo.targetFormId">
                                            <option v-for="f in form.buildingForms" :key="f.id" :value="f.id">
                                                {{ f.displayName }} ({{ f.id }})
                                            </option>
                                        </select>
                                    </div>
                                    <div class="formula-node">
                                        <label>进化花费 (元)</label>
                                        <input type="number" v-model.number="evo.cost" step="1000" />
                                    </div>
                                    <div class="formula-node">
                                        <label>进化工期 (天)</label>
                                        <input type="number" v-model.number="evo.days" min="1" />
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 7. 内装配置 -->
                    <div v-else-if="currentTab === 'interiors'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🛋️ 内装配置体系</h3>
                                <button class="primary small-btn" @click="addInterior">➕ 新增内装</button>
                            </div>
                            <span class="sub-tip">配置各内装属性、等级舒适度产出、升级工期及客群相性加成</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(it, iIdx) in form.interiors" :key="it.id" class="card-item">
                                <div class="card-item-title">
                                    <span>{{ it.displayName }} ({{ it.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeInterior(iIdx)">🗑️ 删除</button>
                                </div>
                                <div class="form-grid five">
                                    <div class="form-group">
                                        <label>内装 ID</label>
                                        <input type="text" v-model="it.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>内装名称</label>
                                        <input type="text" v-model="it.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>内装类别 (Category)</label>
                                        <select v-model="it.category">
                                            <option value="NORMAL">普通设施 (NORMAL)</option>
                                            <option value="CARRY">承载设施 (CARRY)</option>
                                            <option value="COMFORT">舒适设施 (COMFORT)</option>
                                            <option value="ENTERTAINMENT">娱乐设施 (ENTERTAINMENT)</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>每级舒适度加成</label>
                                        <input type="number" v-model.number="it.comfortPerLevel" step="0.5" />
                                    </div>
                                    <div class="form-group" v-if="it.category === 'CARRY'">
                                        <label>★ 每级提供承载能力</label>
                                        <input type="number" v-model.number="it.capacityPerLevel" step="1" placeholder="如床位设1" />
                                    </div>
                                    <div class="form-group">
                                        <label>★ 每级房屋收益加成比例</label>
                                        <input type="number" v-model.number="it.revenueBonusPerLevel" step="0.005" placeholder="例如 0.02 表示 +2%" />
                                    </div>
                                </div>

                                <!-- ★ 客群偏好相性加成 (多客群吸引力修正) -->
                                <div class="sub-section">
                                    <div class="sub-label">★ 客群偏好相性加成 (每升一级对不同客群吸引力的修正系数，正数提升、负数排斥)：</div>
                                    <div class="segment-affinities-grid">
                                        <div v-for="seg in form.segments" :key="seg.id" class="seg-affinity-item">
                                            <span class="seg-name">{{ seg.displayName }} ({{ seg.id }})</span>
                                            <div class="seg-input-wrap">
                                                <input
                                                    type="number"
                                                    v-model.number="it.segmentAffinityPerLevel[seg.id]"
                                                    step="0.02"
                                                    placeholder="0"
                                                />
                                                <span class="unit-text">/级</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="tip-text">
                                        💡 吸引力分配运作原理：客人选择建筑时，总吸引力 = 形态基础偏好 + ∑(内装该客群加成 × 内装等级)。不同客群在此独立配置加成，如亲子设施对家庭为 +0.15、对商务可设为 -0.05。
                                    </div>
                                </div>

                                <!-- ★ 动态内装各等级升级费用与工期阶梯配置 -->
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>★ 各等级内装升级费用与工期配置 (支持自由扩展更多等级)：</span>
                                        <button class="ghost micro-btn" @click="addInteriorLevel(it)">➕ 增加内装等级 (Lv{{ (it.upgradeDaysByLevel || []).length + 1 }})</button>
                                    </div>
                                    <div class="form-levels-table mini">
                                        <div class="form-level-row head mini-cols">
                                            <span class="col-tier">等级阶段</span>
                                            <span class="col-cost">升级费用 (元)</span>
                                            <span class="col-days">升级工期 (天)</span>
                                            <span class="col-act">操作</span>
                                        </div>
                                        <div v-for="(days, dIdx) in (it.upgradeDaysByLevel || [])" :key="dIdx" class="form-level-row mini-cols">
                                            <span class="col-tier tag-badge">{{ dIdx === 0 ? 'Lv1 首次安装' : `升至 Lv${dIdx + 1}` }}</span>
                                            <div class="col-cost">
                                                <input type="number" v-model.number="it.upgradeCostByLevel[dIdx]" step="200" placeholder="费用 (元)" />
                                            </div>
                                            <div class="col-days">
                                                <input type="number" v-model.number="it.upgradeDaysByLevel[dIdx]" min="1" placeholder="工期 (天)" />
                                            </div>
                                            <div class="col-act">
                                                <button
                                                    v-if="dIdx === it.upgradeDaysByLevel.length - 1 && it.upgradeDaysByLevel.length > 1"
                                                    class="danger micro-btn"
                                                    @click="removeInteriorLevel(it, dIdx)"
                                                    title="移除最高等级阶段"
                                                >✕ 删除此级</button>
                                                <span v-else class="text-faint">-</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div class="sub-section">
                                    <div class="sub-label">📖 典藏故事 (图鉴介绍)：</div>
                                    <textarea
                                        v-model="it.story"
                                        rows="2"
                                        placeholder="填写该内装设施在图鉴中展示的背景故事..."
                                        style="width: 100%; padding: 6px 10px; border-radius: 6px; background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.12); color: #fff; font-size: 13px;"
                                    ></textarea>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 8. 合并配方 -->
                    <div v-else-if="currentTab === 'mergeRecipes'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🧬 合并进化配方 (支持 N 合一)</h3>
                                <button class="primary small-btn" @click="addMergeRecipe">➕ 新增合并配方</button>
                            </div>
                            <span class="sub-tip">可自由添加 N 栋建筑原料，合成终极复合建筑</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(rec, rIdx) in form.mergeRecipes" :key="rec.id" class="card-item">
                                <div class="card-item-title">
                                    <span class="tag-badge">配方 #{{ rIdx + 1 }}</span>
                                    <span>{{ rec.displayName }} ({{ rec.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeMergeRecipe(rIdx)">🗑️ 删除配方</button>
                                </div>
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>原料建筑清单 (N 个建筑)：</span>
                                        <button class="ghost micro-btn" @click="addRecipeSource(rec)">+ 添加原料建筑</button>
                                    </div>
                                    <div class="source-list">
                                        <div v-for="(src, sIdx) in rec.sources" :key="sIdx" class="source-row">
                                            <span class="source-tag">建筑 {{ sIdx + 1 }}</span>
                                            <select v-model="src.formId">
                                                <option v-for="f in form.buildingForms" :key="f.id" :value="f.id">
                                                    {{ f.displayName }} ({{ f.id }})
                                                </option>
                                            </select>
                                            <span>要求形态等级 ≥</span>
                                            <input type="number" v-model.number="src.minLevel" min="1" style="width: 60px" />
                                            <button class="ghost micro-btn" @click="removeRecipeSource(rec, sIdx)">✕ 移除</button>
                                        </div>
                                    </div>
                                </div>
                                <div class="sub-section">
                                    <div class="sub-label">产物与合成花费：</div>
                                    <div class="form-grid four">
                                        <div class="form-group">
                                            <label>合成产物形态</label>
                                            <select v-model="rec.resultForm">
                                                <option v-for="f in form.buildingForms" :key="f.id" :value="f.id">
                                                    {{ f.displayName }} ({{ f.id }})
                                                </option>
                                            </select>
                                        </div>
                                        <div class="form-group">
                                            <label>初始形态等级</label>
                                            <input type="number" v-model.number="rec.resultLevel" min="1" />
                                        </div>
                                        <div class="form-group">
                                            <label>合并费用 (元)</label>
                                            <input type="number" v-model.number="rec.mergeCost" step="5000" />
                                        </div>
                                        <div class="form-group">
                                            <label>合并不动产工期 (天)</label>
                                            <input type="number" v-model.number="rec.mergeDays" min="1" />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 9. 地块地图 -->
                    <div v-else-if="currentTab === 'plots'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>📍 地块地图配置</h3>
                                <button class="primary small-btn" @click="addPlot">➕ 新增地块</button>
                            </div>
                            <span class="sub-tip">所属区域支持下拉选择；每个地块可多选允许建造的母体建筑列表</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(p, pIdx) in form.plots" :key="p.id" class="card-item">
                                <div class="card-item-title">
                                    <span>{{ p.displayName }} ({{ p.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removePlot(pIdx)">🗑️ 删除地块</button>
                                </div>
                                <div class="form-grid five">
                                    <div class="form-group">
                                        <label>地块 ID</label>
                                        <input type="text" v-model="p.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>地块名称</label>
                                        <input type="text" v-model="p.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>所属大区域 (下拉)</label>
                                        <select v-model="p.region">
                                            <option v-for="r in form.regions" :key="r.id" :value="r.id">
                                                {{ r.displayName }} ({{ r.id }})
                                            </option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>解锁花费 (元)</label>
                                        <input type="number" v-model.number="p.unlockCost" step="5000" />
                                    </div>
                                    <div class="form-group">
                                        <label>形态阶数上限 (Tier Cap)</label>
                                        <input type="number" v-model.number="p.evolveTierCap" min="1" max="5" />
                                    </div>
                                </div>
                                <div class="sub-section">
                                    <div class="sub-label">该地块允许建造的母体建筑列表 (勾选多选)：</div>
                                    <div class="checkbox-grid">
                                        <label
                                            v-for="m in form.mothers"
                                            :key="m.id"
                                            class="check-pill"
                                            :class="{ active: (p.allowedMothers || []).includes(m.id) }"
                                        >
                                            <input
                                                type="checkbox"
                                                :checked="(p.allowedMothers || []).includes(m.id)"
                                                @change="togglePlotMother(p, m.id)"
                                            />
                                            {{ m.displayName }}
                                        </label>
                                    </div>
                                </div>

                                <div class="sub-section">
                                    <div class="sub-label">📖 典藏故事 (图鉴介绍)：</div>
                                    <textarea
                                        v-model="p.story"
                                        rows="2"
                                        placeholder="填写该地块在图鉴中展示的地理风貌与背景故事..."
                                        style="width: 100%; padding: 6px 10px; border-radius: 6px; background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.12); color: #fff; font-size: 13px;"
                                    ></textarea>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 10. 能力体系 (纯净简化：仅保留 ID 与名称，去除大类与基础保底供给量) -->
                    <div v-else-if="currentTab === 'abilities'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>⚡ 度假村能力服务体系</h3>
                                <button class="primary small-btn" @click="addAbility">➕ 新增能力</button>
                            </div>
                            <span class="sub-tip">定义度假村中各项专业服务能力（如保洁、维修、烹饪、温泉运营等），供岗位与建筑需求直接引用</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(ab, aIdx) in form.abilities" :key="ab.id" class="card-item">
                                <div class="card-item-title">
                                    <span class="tag-badge">能力</span>
                                    <span>{{ ab.displayName }} ({{ ab.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeAbility(aIdx)">🗑️ 删除</button>
                                </div>
                                <div class="form-grid three">
                                    <div class="form-group">
                                        <label>能力唯一 ID</label>
                                        <input type="text" v-model="ab.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>能力展示名称</label>
                                        <input type="text" v-model="ab.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>描述说明</label>
                                        <input type="text" v-model="ab.description" placeholder="能力用途简要说明" />
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 11. 岗位能力 (无固定薪资，针对各个能力配置不同等级的具体能力值) -->
                    <div v-else-if="currentTab === 'positions'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🧑‍💼 岗位能力配置</h3>
                                <button class="primary small-btn" @click="addPosition">➕ 新增岗位</button>
                            </div>
                            <span class="sub-tip">配置岗位各等级对于各个能力分别贡献的能力值、招聘花费与升级天数（岗位日常无固定薪资）</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(pos, pIdx) in form.positions" :key="pos.id" class="card-item">
                                <div class="card-item-title">
                                    <span>岗位：{{ pos.displayName }} ({{ pos.id }})</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removePosition(pIdx)">🗑️ 删除岗位</button>
                                </div>
                                <div class="form-grid four">
                                    <div class="form-group">
                                        <label>岗位 ID</label>
                                        <input type="text" v-model="pos.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>岗位名称</label>
                                        <input type="text" v-model="pos.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>招聘一次性花费 (元)</label>
                                        <input type="number" v-model.number="pos.hireCost" step="100" />
                                    </div>
                                    <div class="form-group">
                                        <label>解雇补偿基准值 (元)</label>
                                        <input type="number" v-model.number="pos.compensationBase" step="100" />
                                    </div>
                                </div>

                                <!-- ★ 动态岗位各等级晋升在岗天数 -->
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>★ 岗位各等级晋升所需在岗累计天数：</span>
                                        <button class="ghost micro-btn" @click="addPositionUpgradeTier(pos)">➕ 增加晋升等级 (Lv{{ (pos.upgradeDaysByLevel || []).length + 2 }})</button>
                                    </div>
                                    <div class="tier-tags-row">
                                        <div v-for="(days, tIdx) in (pos.upgradeDaysByLevel || [])" :key="tIdx" class="tier-tag-item">
                                            <span class="tier-name">Lv{{ tIdx + 1 }} ➔ Lv{{ tIdx + 2 }} 在岗：</span>
                                            <input type="number" v-model.number="pos.upgradeDaysByLevel[tIdx]" min="1" style="width: 70px" />
                                            <span class="unit-text">天</span>
                                            <button
                                                v-if="tIdx === pos.upgradeDaysByLevel.length - 1 && pos.upgradeDaysByLevel.length > 1"
                                                class="ghost micro-btn remove-btn"
                                                @click="removePositionUpgradeTier(pos, tIdx)"
                                                title="移除最高晋升阶段"
                                            >✕</button>
                                        </div>
                                    </div>
                                </div>

                                <!-- ★ 动态岗位各等级升级培训工期天数 -->
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>★ 岗位各等级升级培训工期天数（培训期间原等级能力正常提供）：</span>
                                    </div>
                                    <div class="tier-tags-row">
                                        <div v-for="(days, tIdx) in (pos.trainDaysByLevel || [])" :key="tIdx" class="tier-tag-item">
                                            <span class="tier-name">Lv{{ tIdx + 1 }} ➔ Lv{{ tIdx + 2 }} 培训工期：</span>
                                            <input type="number" v-model.number="pos.trainDaysByLevel[tIdx]" min="0" style="width: 70px" />
                                            <span class="unit-text">天</span>
                                        </div>
                                    </div>
                                </div>

                                <!-- ★ 动态岗位各等级升级培训费用 -->
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>★ 岗位各等级升级培训费用 (元)：</span>
                                    </div>
                                    <div class="tier-tags-row">
                                        <div v-for="(cost, tIdx) in (pos.trainCostByLevel || [])" :key="tIdx" class="tier-tag-item">
                                            <span class="tier-name">Lv{{ tIdx + 1 }} ➔ Lv{{ tIdx + 2 }} 培训费：</span>
                                            <input type="number" v-model.number="pos.trainCostByLevel[tIdx]" min="0" step="50" style="width: 80px" />
                                            <span class="unit-text">元</span>
                                        </div>
                                    </div>
                                </div>

                                <!-- ★ 核心：针对各个能力分别配置各等级提供能力值 (动态任意多级) -->
                                <div class="sub-section">
                                    <div class="sub-label-with-btn">
                                        <span>★ 该岗位针对各能力的阶梯能力值产出 (支持自由增减各级能力值)：</span>
                                        <button class="ghost micro-btn" @click="addPositionAbilityOutput(pos)">+ 为该岗位关联能力</button>
                                    </div>
                                    <div class="ability-output-list">
                                        <div
                                            v-for="(vals, abKey) in pos.abilityOutputs"
                                            :key="abKey"
                                            class="ability-output-card"
                                        >
                                            <div class="ab-card-header">
                                                <div class="ab-select-box">
                                                    <label>对应能力：</label>
                                                    <select :value="abKey" @change="changePositionAbilityKey(pos, abKey, $event.target.value)">
                                                        <option v-for="ab in form.abilities" :key="ab.id" :value="ab.id">
                                                            {{ ab.displayName }} ({{ ab.id }})
                                                        </option>
                                                    </select>
                                                </div>
                                                <div class="spacer"></div>
                                                <button class="ghost micro-btn" @click="addAbilityOutputLevel(pos, abKey)">➕ 增加等级 (Lv{{ vals.length + 1 }})</button>
                                                <button
                                                    v-if="vals.length > 1"
                                                    class="ghost micro-btn"
                                                    @click="removeAbilityOutputLevel(pos, abKey)"
                                                >➖ 减少最高级</button>
                                                <button class="danger micro-btn" @click="removePositionAbilityOutput(pos, abKey)">🗑️ 移除该能力</button>
                                            </div>
                                            <div class="ab-levels-grid">
                                                <div v-for="(v, vIdx) in vals" :key="vIdx" class="ab-level-cell">
                                                    <span class="cell-label">Lv{{ vIdx + 1 }}</span>
                                                    <input type="number" v-model.number="vals[vIdx]" step="5" />
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div class="sub-section">
                                    <div class="sub-label">📖 典藏故事 (图鉴介绍)：</div>
                                    <textarea
                                        v-model="pos.story"
                                        rows="2"
                                        placeholder="填写该岗位员工在图鉴中展示的背景事迹与职业故事..."
                                        style="width: 100%; padding: 6px 10px; border-radius: 6px; background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.12); color: #fff; font-size: 13px;"
                                    ></textarea>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 12. 技能树 (10个明确分类) -->
                    <div v-else-if="currentTab === 'skills'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🌳 技能树配置</h3>
                                <button class="primary small-btn" @click="addSkill">➕ 新增技能</button>
                            </div>
                            <span class="sub-tip">技能分类已明确限制为 10 大专用方向，前置技能通过多选器关联</span>
                        </div>

                        <!-- ★ 最上方：当前配置的技能树可视化全景图谱 -->
                        <div class="skill-tree-preview-wrapper">
                            <div class="preview-header">
                                <div class="preview-title">
                                    <span class="icon">🕸️</span>
                                    <span>当前配置技能树全景拓扑图 (实时响应下方配置修改，点击节点可定位至编辑卡片)</span>
                                </div>
                                <div class="tree-stats">
                                    <span class="stat-pill">总技能数：<strong>{{ form.skills.length }}</strong></span>
                                    <span class="stat-pill">拓扑深度：<strong>{{ skillTreeLayout.maxDepth + 1 }} 阶</strong></span>
                                    <span class="stat-pill">依赖连线：<strong>{{ skillTreeLayout.edges.length }} 条</strong></span>
                                </div>
                            </div>
                            <div class="skill-tree-canvas-scroll">
                                <svg
                                    :width="skillTreeLayout.width"
                                    :height="skillTreeLayout.height"
                                    class="skill-tree-svg"
                                >
                                    <defs>
                                        <marker
                                            id="admin-skill-arrow"
                                            viewBox="0 0 10 10"
                                            refX="8"
                                            refY="5"
                                            markerWidth="6"
                                            markerHeight="6"
                                            orient="auto-start-reverse"
                                        >
                                            <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#38bdf8" opacity="0.8" />
                                        </marker>
                                        <pattern id="admin-grid-pattern" width="20" height="20" patternUnits="userSpaceOnUse">
                                            <circle cx="1" cy="1" r="1" fill="rgba(255,255,255,0.08)" />
                                        </pattern>
                                    </defs>
                                    <rect width="100%" height="100%" fill="url(#admin-grid-pattern)" />

                                    <!-- 依赖连线 -->
                                    <path
                                        v-for="e in skillTreeLayout.edges"
                                        :key="e.id"
                                        :d="e.path"
                                        class="skill-tree-edge"
                                        marker-end="url(#admin-skill-arrow)"
                                    />

                                    <!-- 技能节点 -->
                                    <g
                                        v-for="node in skillTreeLayout.nodes"
                                        :key="node.id"
                                        :transform="`translate(${node.x}, ${node.y})`"
                                        class="skill-tree-node-group"
                                        :class="{ active: selectedSkillId === node.id }"
                                        @click="scrollToSkill(node.id)"
                                    >
                                        <rect
                                            width="160"
                                            height="64"
                                            rx="8"
                                            class="node-rect"
                                            :style="{ borderLeftColor: getSkillColor(node.category) }"
                                        />
                                        <!-- 分类指示彩条 -->
                                        <rect
                                            x="0"
                                            y="0"
                                            width="4"
                                            height="64"
                                            rx="2"
                                            :fill="getSkillColor(node.category)"
                                        />
                                        <!-- 技能名称 -->
                                        <text x="12" y="22" class="node-title">{{ node.displayName || node.id }}</text>
                                        <!-- 技能分类 -->
                                        <text x="12" y="40" class="node-cat" :fill="getSkillColor(node.category)">{{ node.category }}</text>
                                        <!-- 效果与消耗 -->
                                        <text x="12" y="54" class="node-meta">
                                            消耗 {{ node.costPoints || 1 }} 点 · 效果 +{{ (node.value * 100).toFixed(0) }}%
                                        </text>
                                    </g>
                                </svg>
                            </div>
                        </div>

                        <div class="card-list">
                            <div
                                v-for="(sk, sIdx) in form.skills"
                                :key="sk.id"
                                :id="'skill-card-' + sk.id"
                                class="card-item skill-card-item"
                                :class="{ highlighted: selectedSkillId === sk.id }"
                            >
                                <div class="card-item-title">
                                    <span>{{ sk.displayName }} ({{ sk.id }})</span>
                                    <span class="tag-badge" :style="{ borderColor: getSkillColor(sk.category), color: getSkillColor(sk.category) }">{{ sk.category }}</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeSkill(sIdx)">🗑️ 删除技能</button>
                                </div>
                                <div class="form-grid four">
                                    <div class="form-group">
                                        <label>技能 ID</label>
                                        <input type="text" v-model="sk.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>技能名称</label>
                                        <input type="text" v-model="sk.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>★ 技能分类 (明确固定分类)</label>
                                        <select v-model="sk.category" @change="onSkillCategoryChange(sk)">
                                            <option v-for="cat in SKILL_CATEGORIES" :key="cat.name" :value="cat.name">
                                                {{ cat.name }}
                                            </option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>加成数值 (如 0.10 代表 10%)</label>
                                        <input type="number" v-model.number="sk.value" step="0.05" />
                                    </div>
                                    <div class="form-group">
                                        <label>点亮消耗技能点</label>
                                        <input type="number" v-model.number="sk.costPoints" min="1" max="5" />
                                    </div>
                                    <div class="form-group full-row">
                                        <label>技能说明 / 描述</label>
                                        <input type="text" v-model="sk.description" />
                                    </div>
                                </div>
                                <div class="sub-section">
                                    <div class="sub-label">前置技能需求 (多选标签勾选，必须先点亮方可解锁)：</div>
                                    <div class="checkbox-grid">
                                        <label
                                            v-for="cand in form.skills.filter((x) => x.id !== sk.id)"
                                            :key="cand.id"
                                            class="check-pill"
                                            :class="{ active: (sk.prerequisites || []).includes(cand.id) }"
                                        >
                                            <input
                                                type="checkbox"
                                                :checked="(sk.prerequisites || []).includes(cand.id)"
                                                @change="toggleSkillPrereq(sk, cand.id)"
                                            />
                                            {{ cand.displayName }}
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 13. 成就系统 (6个明确分类) -->
                    <div v-else-if="currentTab === 'achievements'" class="tab-pane">
                        <div class="pane-header">
                            <div class="title-with-btn">
                                <h3>🏆 成就系统管理</h3>
                                <button class="primary small-btn" @click="addAchievement">➕ 新增成就</button>
                            </div>
                            <span class="sub-tip">每达成一个成就即奖励配置的技能点数；地块解锁所需的成就条件在地块地图模块中配置</span>
                        </div>
                        <div class="card-list">
                            <div v-for="(ach, aIdx) in form.achievements" :key="ach.id" class="card-item">
                                <div class="card-item-title">
                                    <span>{{ ach.displayName }} ({{ ach.id }})</span>
                                    <span class="tag-badge">{{ ach.category }}</span>
                                    <span class="tag-badge" style="background: #fef3c7; color: #b45309">⭐ +{{ ach.rewardSkillPoints || 1 }} 技能点</span>
                                    <div class="spacer"></div>
                                    <button class="danger micro-btn" @click="removeAchievement(aIdx)">🗑️ 删除</button>
                                </div>
                                <div class="form-grid four">
                                    <div class="form-group">
                                        <label>成就 ID</label>
                                        <input type="text" v-model="ach.id" />
                                    </div>
                                    <div class="form-group">
                                        <label>成就名称</label>
                                        <input type="text" v-model="ach.displayName" />
                                    </div>
                                    <div class="form-group">
                                        <label>★ 成就分类 (明确固定分类)</label>
                                        <select v-model="ach.category" @change="onAchievementCategoryChange(ach)">
                                            <option v-for="c in ACHIEVEMENT_CATEGORIES" :key="c.id" :value="c.name">
                                                {{ c.name }}
                                            </option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label>★ 达成奖励技能点数</label>
                                        <input type="number" v-model.number="ach.rewardSkillPoints" min="0" max="10" />
                                    </div>
                                </div>

                                <!-- 根据分类渲染专属参数输入行 -->
                                <div class="sub-section">
                                    <div class="sub-label">达成判定条件参数设置：</div>
                                    <div class="form-grid three">
                                        <!-- 1. 已建N个建筑 / 2. 累计接待N名 / 3. 总资产达到N万 / 4. 员工总数达到N个 -->
                                        <div class="form-group">
                                            <label>目标数量 / 阈值 N</label>
                                            <input type="number" v-model.number="ach.targetValue" min="1" />
                                        </div>

                                        <!-- 5. 拥有N名A等级的B岗位 -->
                                        <template v-if="ach.category === '拥有N名A等级的B岗位'">
                                            <div class="form-group">
                                                <label>目标岗位 B (下拉选择)</label>
                                                <select v-model="ach.targetEntity">
                                                    <option v-for="p in form.positions" :key="p.id" :value="p.id">
                                                        {{ p.displayName }} ({{ p.id }})
                                                    </option>
                                                </select>
                                            </div>
                                            <div class="form-group">
                                                <label>要求员工等级 A</label>
                                                <input type="number" v-model.number="ach.targetLevel" min="1" placeholder="目标等级 (如 1, 2, 3, 4...)" />
                                            </div>
                                        </template>

                                        <!-- 6. 建造N个A等级B形态建筑 -->
                                        <template v-if="ach.category === '建造N个A等级B形态建筑'">
                                            <div class="form-group">
                                                <label>目标建筑形态 B (下拉选择)</label>
                                                <select v-model="ach.targetEntity">
                                                    <option v-for="f in form.buildingForms" :key="f.id" :value="f.id">
                                                        {{ f.displayName }} ({{ f.id }})
                                                    </option>
                                                </select>
                                            </div>
                                            <div class="form-group">
                                                <label>要求形态等级 A</label>
                                                <input type="number" v-model.number="ach.targetLevel" min="1" placeholder="目标等级 (如 1, 2, 3, 4...)" />
                                            </div>
                                        </template>
                                    </div>
                                    <div class="form-group full-row" style="margin-top: 10px">
                                        <label>成就达成描述</label>
                                        <input type="text" v-model="ach.description" />
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div v-else class="admin-loading">
                <span>⏳ 正在拉取游戏配置数据…</span>
            </div>
        </div>
    </div>
</template>

<style scoped>
.admin-modal-mask {
    position: fixed;
    inset: 0;
    background: rgba(4, 7, 13, 0.85);
    backdrop-filter: blur(8px);
    z-index: 9999;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
}

.admin-modal-container {
    background: #0f141f;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 12px;
    width: 95vw;
    max-width: 1400px;
    height: 90vh;
    display: flex;
    flex-direction: column;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.7);
    color: #e2e8f0;
}

.admin-header {
    height: 58px;
    padding: 0 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #141b2a;
    border-radius: 12px 12px 0 0;
}

.header-left {
    display: flex;
    align-items: center;
    gap: 14px;
}

.adm-title {
    font-size: 16px;
    font-weight: 700;
    color: #fff;
}

.config-status {
    font-size: 11px;
    padding: 3px 8px;
    border-radius: 4px;
}
.config-status.default {
    background: rgba(148, 163, 184, 0.15);
    color: #94a3b8;
}
.config-status.custom {
    background: rgba(234, 179, 8, 0.15);
    color: #facc15;
    border: 1px solid rgba(234, 179, 8, 0.3);
}

.header-right {
    display: flex;
    align-items: center;
    gap: 8px;
}

.small-btn {
    padding: 6px 12px;
    font-size: 12px;
    border-radius: 6px;
}

.close-btn {
    background: transparent;
    border: none;
    color: #94a3b8;
    font-size: 18px;
    cursor: pointer;
    padding: 4px 8px;
}
.close-btn:hover {
    color: #fff;
}

.admin-body {
    flex: 1;
    display: flex;
    overflow: hidden;
}

.admin-sidebar {
    width: 180px;
    background: #0d121c;
    border-right: 1px solid rgba(255, 255, 255, 0.06);
    overflow-y: auto;
    padding: 8px 0;
}

.tab-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    font-size: 13px;
    color: #8b9bc0;
    cursor: pointer;
    transition: all 0.15s ease;
}
.tab-item:hover {
    background: rgba(255, 255, 255, 0.04);
    color: #fff;
}
.tab-item.active {
    background: #1e293b;
    color: #38bdf8;
    border-left: 3px solid #38bdf8;
    font-weight: 600;
}

.admin-content {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: #0a0e17;
}

.pane-header {
    margin-bottom: 20px;
    padding-bottom: 12px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.title-with-btn {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.pane-header h3 {
    margin: 0;
    font-size: 16px;
    color: #f1f5f9;
}
.sub-tip {
    font-size: 12px;
    color: #64748b;
    margin-top: 4px;
    display: inline-block;
}

.tip-text {
    margin-top: 8px;
    font-size: 11px;
    color: #94a3b8;
    line-height: 1.5;
}

.form-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 16px;
}
.form-grid.three {
    grid-template-columns: repeat(3, 1fr);
}
.form-grid.four {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
}

.form-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
}
.form-group.full-row {
    grid-column: 1 / -1;
}

.form-group label {
    font-size: 11px;
    color: #94a3b8;
    font-weight: 500;
}

.form-group input,
.form-group select {
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #f8fafc;
    padding: 7px 10px;
    border-radius: 6px;
    font-size: 12px;
    outline: none;
}
.form-group input:focus,
.form-group select:focus {
    border-color: #38bdf8;
}

.card-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.card-item {
    background: #111726;
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    padding: 16px;
}

.card-item-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 14px;
}

.color-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
}

.tag-badge {
    background: rgba(56, 189, 248, 0.15);
    color: #38bdf8;
    font-size: 11px;
    padding: 2px 6px;
    border-radius: 4px;
}

.micro-btn {
    padding: 3px 8px;
    font-size: 11px;
    border-radius: 4px;
}

.sub-section {
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px dashed rgba(255, 255, 255, 0.08);
}

.sub-label {
    font-size: 11px;
    color: #94a3b8;
    margin-bottom: 8px;
    font-weight: 500;
}
.sub-label-with-btn {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    font-size: 11px;
    color: #94a3b8;
}

.checkbox-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.check-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid rgba(255, 255, 255, 0.1);
    padding: 4px 10px;
    border-radius: 6px;
    font-size: 11px;
    cursor: pointer;
    user-select: none;
    color: #94a3b8;
    transition: all 0.15s ease;
}
.check-pill.active {
    background: rgba(56, 189, 248, 0.18);
    border-color: #38bdf8;
    color: #f0f9ff;
}

.formula-card {
    background: #111726;
}

.formula-row {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.formula-node {
    display: flex;
    flex-direction: column;
    gap: 4px;
}
.formula-node label {
    font-size: 10px;
    color: #64748b;
}
.formula-node select,
.formula-node input {
    background: #182236;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #fff;
    padding: 6px 10px;
    border-radius: 6px;
    font-size: 12px;
}

.formula-arrow {
    font-size: 16px;
    color: #38bdf8;
    font-weight: 800;
    margin-top: 14px;
}

.source-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.source-row {
    display: flex;
    align-items: center;
    gap: 10px;
    background: rgba(0, 0, 0, 0.2);
    padding: 6px 12px;
    border-radius: 6px;
    font-size: 12px;
}
.source-tag {
    font-weight: 600;
    color: #94a3b8;
}
.source-row select {
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #fff;
    padding: 4px 8px;
    border-radius: 4px;
}

/* 动态形态等级表格 */
.form-levels-table {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    padding: 10px;
    margin-top: 6px;
}
.form-level-row {
    display: grid;
    grid-template-columns: 140px 1fr 1fr 1.3fr 90px;
    gap: 10px;
    align-items: center;
}
.form-level-row.head {
    font-size: 11px;
    color: #94a3b8;
    font-weight: 600;
    padding-bottom: 4px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.form-level-row input {
    width: 100%;
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #fff;
    padding: 5px 8px;
    border-radius: 4px;
    font-size: 12px;
}
.empty-tip {
    font-size: 12px;
    color: #94a3b8;
    text-align: center;
    padding: 12px;
}

/* 动态等级梯级标签条 (在岗天数、内装工期) */
.tier-tags-row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 8px;
}
.tier-tag-item {
    display: flex;
    align-items: center;
    gap: 6px;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid rgba(255, 255, 255, 0.08);
    padding: 4px 8px;
    border-radius: 6px;
    font-size: 12px;
}
.tier-tag-item input {
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #fff;
    padding: 3px 6px;
    border-radius: 4px;
    font-size: 12px;
    text-align: center;
}
.unit-text {
    font-size: 11px;
    color: #64748b;
}

/* 岗位各项能力明细卡片 */
.ability-output-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin-top: 8px;
}
.ability-output-card {
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    padding: 10px 14px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.ab-card-header {
    display: flex;
    align-items: center;
    gap: 10px;
}
.ab-select-box {
    display: flex;
    align-items: center;
    gap: 6px;
}
.ab-select-box label {
    font-size: 12px;
    color: #94a3b8;
}
.ab-select-box select {
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.15);
    color: #fff;
    padding: 4px 10px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
}
.ab-levels-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    background: rgba(0, 0, 0, 0.15);
    padding: 8px 10px;
    border-radius: 6px;
}
.ab-level-cell {
    display: flex;
    align-items: center;
    gap: 6px;
}
.ab-level-cell .cell-label {
    font-size: 11px;
    color: #38bdf8;
    font-weight: 700;
}
.ab-level-cell input {
    width: 70px;
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #fff;
    padding: 4px 6px;
    border-radius: 4px;
    font-size: 12px;
    text-align: center;
}

/* 技能树拓扑可视化全景预览 */
.skill-tree-preview-wrapper {
    background: #0d1527;
    border: 1px solid rgba(56, 189, 248, 0.25);
    border-radius: 10px;
    margin-bottom: 20px;
    overflow: hidden;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}
.preview-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    background: rgba(15, 23, 42, 0.7);
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.preview-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 700;
    color: #e2e8f0;
}
.tree-stats {
    display: flex;
    gap: 10px;
}
.stat-pill {
    font-size: 11px;
    color: #94a3b8;
    background: rgba(255, 255, 255, 0.06);
    padding: 3px 8px;
    border-radius: 12px;
}
.stat-pill strong {
    color: #38bdf8;
}
.skill-tree-canvas-scroll {
    overflow: auto;
    max-height: 380px;
    background: #090f1d;
}
.skill-tree-svg {
    display: block;
}
.skill-tree-edge {
    stroke: rgba(56, 189, 248, 0.55);
    stroke-width: 2;
    fill: none;
}
.skill-tree-node-group {
    cursor: pointer;
    transition: transform 0.15s ease;
}
.skill-tree-node-group:hover {
    filter: drop-shadow(0 0 8px rgba(56, 189, 248, 0.5));
}
.skill-tree-node-group .node-rect {
    fill: #151e33;
    stroke: rgba(255, 255, 255, 0.12);
    stroke-width: 1.5;
    transition: all 0.2s ease;
}
.skill-tree-node-group:hover .node-rect {
    fill: #1e294b;
    stroke: #38bdf8;
}
.skill-tree-node-group.active .node-rect {
    fill: #1e294b;
    stroke: #facc15;
    stroke-width: 2.5;
    filter: drop-shadow(0 0 10px rgba(250, 204, 21, 0.6));
}
.skill-tree-node-group .node-title {
    font-size: 12px;
    font-weight: 700;
    fill: #ffffff;
}
.skill-tree-node-group .node-cat {
    font-size: 10px;
    font-weight: 600;
}
.skill-tree-node-group .node-meta {
    font-size: 9px;
    fill: #94a3b8;
}

/* 技能卡片高亮联动 */
.skill-card-item {
    transition: all 0.3s ease;
}
.skill-card-item.highlighted {
    border-color: #facc15 !important;
    box-shadow: 0 0 16px rgba(250, 204, 21, 0.4);
    background: rgba(30, 41, 69, 0.9) !important;
}

/* 客群相性网格 */
.segment-affinities-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
    gap: 8px;
    margin-top: 6px;
}
.seg-affinity-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.08);
    padding: 6px 10px;
    border-radius: 6px;
    font-size: 12px;
}
.seg-name {
    color: #cbd5e1;
    font-weight: 500;
}
.seg-input-wrap {
    display: flex;
    align-items: center;
    gap: 4px;
}
.seg-input-wrap input {
    width: 65px;
    background: #141b2a;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: #fff;
    padding: 3px 6px;
    border-radius: 4px;
    font-size: 12px;
    text-align: center;
}

/* mini 版等级表格 (内装使用) */
.form-levels-table.mini {
    margin-top: 6px;
}
.form-level-row.mini-cols {
    grid-template-columns: 140px 1.5fr 1fr 90px;
}

.admin-loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: #8b9bc0;
}
</style>
