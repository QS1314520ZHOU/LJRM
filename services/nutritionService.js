const DrugExe = require('../models/DrugExe');
const TubeExe = require('../models/TubeExe');
const Patient = require('../models/Patient');
const moment = require('moment');

// ── 常量 ──────────────────────────────────────────────

const EAST_8_OFFSET_MINUTES = 8 * 60;

const NUTRITION_INDICATORS = [
  { id: 1, name: '肠内营养统计', key: 'enteral', unit: '人' },
  { id: 2, name: '肠外营养统计', key: 'parenteral', unit: '人' },
  { id: 3, name: '胃肠管留置例数', key: 'gastricTube', unit: '人' },
  { id: 4, name: '肠内实施例数', key: 'enteralExec', unit: '人' },
];

const ENTERAL_KEYWORDS = ['肠内营养粉剂', '肠内营养混悬液'];
const PARENTERAL_KEYWORDS = ['脂肪乳氨基酸'];
const GASTRIC_TUBE_TYPES = ['鼻肠管', '胃肠管', '胃管'];

const NUTRITION_DETAIL_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'department', title: '科室' },
  { key: 'bedNo', title: '床号' },
  { key: 'name', title: '姓名' },
  { key: 'age', title: '年龄' },
  { key: 'hospitalNo', title: '住院号' },
  { key: 'icuAdmissionTime', title: '入科时间' },
  { key: 'icuDischargeTime', title: '出科时间' },
  { key: 'icuDays', title: '在科天数' },
  { key: 'admissionDoctor', title: '收治医生' },
  { key: 'attendingDoctor', title: '管床医生' },
  { key: 'admissionSource', title: '入科来源' },
  { key: 'dischargeType', title: '出科类型' },
  { key: 'transferDept', title: '转出科室' },
  { key: 'diagnosis', title: '临床诊断' },
];

const GASTRIC_TUBE_DETAIL_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'department', title: '科室' },
  { key: 'bedNo', title: '床号' },
  { key: 'name', title: '姓名' },
  { key: 'age', title: '年龄' },
  { key: 'hospitalNo', title: '住院号' },
  { key: 'icuAdmissionTime', title: '入科时间' },
  { key: 'icuDischargeTime', title: '出科时间' },
  { key: 'icuDays', title: '在科天数' },
  { key: 'tubeType', title: '置管类型' },
  { key: 'tubeEndTime', title: '置管结束时间' },
  { key: 'admissionDoctor', title: '收治医生' },
  { key: 'attendingDoctor', title: '管床医生' },
  { key: 'admissionSource', title: '入科来源' },
  { key: 'dischargeType', title: '出科类型' },
  { key: 'transferDept', title: '转出科室' },
  { key: 'diagnosis', title: '临床诊断' },
];

const DAILY_DETAIL_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'department', title: '科室' },
  { key: 'bedNo', title: '床号' },
  { key: 'name', title: '姓名' },
  { key: 'age', title: '年龄' },
  { key: 'hospitalNo', title: '住院号' },
  { key: 'icuAdmissionTime', title: '入科时间' },
  { key: 'icuDischargeTime', title: '出科时间' },
  { key: 'icuDays', title: '在科天数' },
  { key: 'drugNames', title: '药名' },
  { key: 'medicationTime', title: '用药时间' },
  { key: 'liquidAmount', title: '剂量(mL)' },
  { key: 'liquidAmountUnit', title: '单位' },
  { key: 'admissionDoctor', title: '收治医生' },
  { key: 'attendingDoctor', title: '管床医生' },
  { key: 'admissionSource', title: '入科来源' },
  { key: 'dischargeType', title: '出科类型' },
  { key: 'transferDept', title: '转出科室' },
  { key: 'diagnosis', title: '临床诊断' },
];

const SUPPORTED_NUTRITION_KEYS = new Set(NUTRITION_INDICATORS.map(item => item.key));
const DEPARTMENT_FIELDS = ['department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName'];
const PATIENT_SELECT = [
  '_id', 'hisPid', 'mrn', 'name', 'birthday', 'age', 'gender', 'hisBed', 'bedNo', 'bedCode', 'bedName', 'bedNumber',
  'hospitalNo', 'hospitalNumber', 'zyh', 'zyhm', 'hospitalTime', 'icuAdmissionTime', 'icuDischargeTime',
  'department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName', 'admissionDoctor',
  'admissionDoctorName', 'attendingDoctor', 'attendingDoctorName', 'chargeDoctorName', 'tubeDoctorName',
  'bedDoctor', 'admissionSource', 'inSource', 'source', 'dischargedType', 'dischargeType', 'outType',
  'dischargedDepartment', 'transferDept', 'outDeptName',
  'admissionDiagnosis', 'diagnosis', 'clinicalDiagnosis', 'primaryDiagnosis',
  'status',
].join(' ');

// ── 工具函数（复用 DRG 统计同款东八区逻辑）───────────

function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function validateYear(year) {
  const n = Number(year);
  if (!Number.isInteger(n) || n < 2000 || n > 2099) throw new Error('年份格式不正确');
  return n;
}

function validateMonth(month, fieldName) {
  if (!moment(month, 'YYYY-MM', true).isValid()) throw new Error(`${fieldName}格式不正确，应为 YYYY-MM`);
  return month;
}

function validateDate(date, fieldName) {
  if (!moment(date, 'YYYY-MM-DD', true).isValid()) throw new Error(`${fieldName}格式不正确，应为 YYYY-MM-DD`);
  return date;
}

function buildMonths(startMonth, endMonth) {
  validateMonth(startMonth, '开始月份');
  validateMonth(endMonth, '结束月份');
  const cur = moment(startMonth, 'YYYY-MM');
  const end = moment(endMonth, 'YYYY-MM');
  if (cur.isAfter(end)) throw new Error('开始月份不能晚于结束月份');
  if (end.diff(cur, 'months') > 36) throw new Error('查询范围不能超过 36 个月');

  const months = [];
  while (cur.isSameOrBefore(end)) {
    months.push(cur.format('YYYY-MM'));
    cur.add(1, 'month');
  }
  return months;
}

function getMonthRange(monthKey) {
  return {
    startDate: moment.parseZone(`${monthKey}-01T00:00:00+08:00`).startOf('month').toDate(),
    endDate: moment.parseZone(`${monthKey}-01T00:00:00+08:00`).endOf('month').toDate(),
  };
}

function getFullRange(startMonth, endMonth) {
  const months = buildMonths(startMonth, endMonth);
  const start = getMonthRange(months[0]).startDate;
  const end = getMonthRange(months[months.length - 1]).endDate;
  return { startDate: start, endDate: end };
}

function getDayRange(dateStr) {
  validateDate(dateStr, '日期');
  return {
    startDate: moment.parseZone(`${dateStr}T00:00:00+08:00`).toDate(),
    endDate: moment.parseZone(`${dateStr}T23:59:59.999+08:00`).toDate(),
  };
}

function normalizeText(value) {
  return String(value ?? '').trim();
}

function asDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateTime(value) {
  const date = asDate(value);
  return date ? moment(date).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM-DD HH:mm') : '';
}

function firstValue(doc, fields) {
  for (const field of fields) {
    const value = doc[field];
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return '';
}

function calcAge(patient) {
  const explicitAge = firstValue(patient, ['age']);
  if (explicitAge !== '') return String(explicitAge).includes('岁') ? String(explicitAge) : `${explicitAge}岁`;
  const birthday = asDate(patient.birthday);
  if (!birthday) return '';
  return `${moment().utcOffset(EAST_8_OFFSET_MINUTES).diff(moment(birthday).utcOffset(EAST_8_OFFSET_MINUTES), 'years')}岁`;
}

function calcIcuDays(patient) {
  const start = asDate(patient.icuAdmissionTime);
  if (!start) return '';
  const end = asDate(patient.icuDischargeTime) || new Date();
  return `${Math.max(1, moment(end).utcOffset(EAST_8_OFFSET_MINUTES).diff(moment(start).utcOffset(EAST_8_OFFSET_MINUTES), 'days') + 1)}天`;
}

function buildDepartmentOr(department) {
  if (process.env.ENABLE_DEPT_FILTER !== 'true' || !department) return [];
  const regex = new RegExp(escapeRegExp(department), 'i');
  return DEPARTMENT_FIELDS.map(field => ({ [field]: regex }));
}

function buildPatientFilter(extra = {}, department) {
  const and = [{ status: { $ne: 'invalid' } }, extra];
  const deptOr = buildDepartmentOr(department);
  if (deptOr.length) and.push({ $or: deptOr });
  return { $and: and };
}

function toDetailRow(patient, index, extra = {}) {
  return {
    index,
    department: firstValue(patient, ['department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName']) || '重症医学科',
    bedNo: firstValue(patient, ['hisBed']),
    name: firstValue(patient, ['name']),
    age: calcAge(patient),
    hospitalNo: firstValue(patient, ['mrn']),
    icuAdmissionTime: formatDateTime(patient.icuAdmissionTime),
    icuDischargeTime: formatDateTime(patient.icuDischargeTime),
    icuDays: calcIcuDays(patient),
    admissionDoctor: firstValue(patient, ['bedDoctor']),
    attendingDoctor: firstValue(patient, ['bedDoctor']),
    admissionSource: firstValue(patient, ['admissionSource', 'inSource', 'source']),
    dischargeType: firstValue(patient, ['dischargedType']),
    transferDept: firstValue(patient, ['dischargedDepartment']),
    diagnosis: firstValue(patient, ['clinicalDiagnosis', 'diagnosis', 'admissionDiagnosis', 'primaryDiagnosis']),
    ...extra,
  };
}

// ── 关键词正则构建 ────────────────────────────────────

function getKeywordsByIndicator(indicatorKey) {
  switch (indicatorKey) {
    case 'enteral': return ENTERAL_KEYWORDS;
    case 'parenteral': return PARENTERAL_KEYWORDS;
    default: throw new Error(`不支持的营养指标: ${indicatorKey}`);
  }
}

function buildKeywordRegexOr(keywords) {
  // 精确匹配（大小写敏感），不转义——中文关键词不含正则特殊字符
  return keywords.map(kw => ({ 'drugList.name': { $regex: escapeRegExp(kw) } }));
}

// ── 科室过滤辅助 ──────────────────────────────────────

async function getDepartmentPatientIds(department) {
  if (process.env.ENABLE_DEPT_FILTER !== 'true' || !department) return null;
  const patients = await Patient.find(buildPatientFilter({}, department))
    .select('_id')
    .lean();
  return patients.map(p => String(p._id));
}

// ── 按月统计（aggregation）─────────────────────────────

async function getMonthlyCounts(startMonth, endMonth, indicatorKey, department) {
  const months = buildMonths(startMonth, endMonth);
  const { startDate, endDate } = getFullRange(startMonth, endMonth);
  const keywords = getKeywordsByIndicator(indicatorKey);
  const keywordOr = buildKeywordRegexOr(keywords);

  const match = {
    status: { $ne: 'invalid' },
    startTime: { $gte: startDate, $lte: endDate },
    $or: keywordOr,
  };

  // 科室过滤
  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      // 该科室无患者，返回全零
      const monthMap = {};
      months.forEach(m => { monthMap[m] = 0; });
      return monthMap;
    }
    match.pid = { $in: pids };
  }

  const result = await DrugExe.aggregate([
    { $match: match },
    {
      $project: {
        pid: 1,
        month: { $dateToString: { format: '%Y-%m', date: '$startTime', timezone: '+08:00' } },
      },
    },
    { $match: { month: { $gte: startMonth, $lte: endMonth } } },
    { $group: { _id: { month: '$month', pid: '$pid' } } },
    { $group: { _id: '$_id.month', count: { $sum: 1 } } },
  ]);

  const countMap = {};
  result.forEach(r => { countMap[r._id] = r.count; });
  months.forEach(m => { if (!countMap[m]) countMap[m] = 0; });
  return countMap;
}

// ── 胃肠管留置例数：接续合并工具 ───────────────────────
// 同一患者+同一类型下，startTime == endTime 的相邻记录合并为一个留置段(episode)
// 3 个类型独立建链，不跨类型合并；每个 episode 最终归 1 例

function buildGastricTubeEpisodes(tubeRecords) {
  // 1. 按 `${pid}::${type}` 分组
  const groups = new Map();
  for (const record of tubeRecords) {
    const pid = normalizeText(record.pid);
    const type = normalizeText(record.type);
    if (!pid || !type) continue;
    const groupKey = `${pid}::${type}`;
    if (!groups.has(groupKey)) groups.set(groupKey, []);
    groups.get(groupKey).push(record);
  }

  const episodes = [];

  for (const [, records] of groups) {
    // 2. 组内按 startTime 升序（缺失时用 endTime 兜底）
    records.sort((a, b) => {
      const aTime = asDate(a.startTime) || asDate(a.endTime);
      const bTime = asDate(b.startTime) || asDate(b.endTime);
      if (!aTime && !bTime) return 0;
      if (!aTime) return 1;
      if (!bTime) return -1;
      return aTime - bTime;
    });

    // 3. 线性扫描合并接续记录
    let currentEp = null;
    for (const record of records) {
      const recStart = asDate(record.startTime);
      const recEnd = asDate(record.endTime);
      if (!recEnd) continue; // endTime 为空的不参与 episode（过滤条件已保证，防御性保留）

      if (currentEp) {
        // 精确相等判定：startTime.getTime() === episode 末端 endTime.getTime()
        const epEndTime = currentEp.finalEndTime.getTime();
        const recStartTime = recStart ? recStart.getTime() : null;
        if (recStartTime !== null && recStartTime === epEndTime) {
          // 接续 → 合并，更新末端 endTime
          currentEp.finalEndTime = recEnd;
          continue;
        }
        // 不接续 → 结束当前 episode
        episodes.push({
          pid: currentEp.pid,
          type: currentEp.type,
          finalEndTime: currentEp.finalEndTime,
        });
      }

      // 另起新 episode
      currentEp = {
        pid: normalizeText(record.pid),
        type: normalizeText(record.type),
        finalEndTime: recEnd,
      };
    }
    // 收尾最后一个 episode
    if (currentEp) {
      episodes.push({
        pid: currentEp.pid,
        type: currentEp.type,
        finalEndTime: currentEp.finalEndTime,
      });
    }
  }

  return episodes;
}

// ── 胃肠管留置例数（TubeExe，endTime 归月，接续合并后计数）──

async function getGastricTubeMonthlyCounts(startMonth, endMonth, department) {
  const months = buildMonths(startMonth, endMonth);
  const { startDate, endDate } = getFullRange(startMonth, endMonth);

  const match = {
    type: { $in: GASTRIC_TUBE_TYPES },
    valid: { $ne: null },
    endTime: { $gte: startDate, $lte: endDate },
  };

  // 科室过滤
  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      const monthMap = {};
      months.forEach(m => { monthMap[m] = 0; });
      return monthMap;
    }
    match.pid = { $in: pids };
  }

  // 拉取记录（需 startTime 用于接续判断）
  const tubeRecords = await TubeExe.find(match)
    .select('pid type startTime endTime')
    .lean();

  if (!tubeRecords.length) {
    const monthMap = {};
    months.forEach(m => { monthMap[m] = 0; });
    return monthMap;
  }

  // 接续合并为留置段(episode)，每段 1 例
  const episodes = buildGastricTubeEpisodes(tubeRecords);

  // 按 finalEndTime +08:00 归月
  const countMap = {};
  months.forEach(m => { countMap[m] = 0; });
  for (const episode of episodes) {
    const month = moment(episode.finalEndTime).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM');
    if (countMap.hasOwnProperty(month)) {
      countMap[month]++;
    }
  }
  return countMap;
}

// ── 肠内实施例数（DrugExe 同关键词，startTime 归月，逐条计数）─

async function getEnteralExecMonthlyCounts(startMonth, endMonth, department) {
  const months = buildMonths(startMonth, endMonth);
  const { startDate, endDate } = getFullRange(startMonth, endMonth);
  const keywordOr = buildKeywordRegexOr(ENTERAL_KEYWORDS);

  const match = {
    status: { $ne: 'invalid' },
    startTime: { $gte: startDate, $lte: endDate },
    $or: keywordOr,
  };

  // 科室过滤
  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      const monthMap = {};
      months.forEach(m => { monthMap[m] = 0; });
      return monthMap;
    }
    match.pid = { $in: pids };
  }

  const result = await DrugExe.aggregate([
    { $match: match },
    {
      $project: {
        month: { $dateToString: { format: '%Y-%m', date: '$startTime', timezone: '+08:00' } },
      },
    },
    { $match: { month: { $gte: startMonth, $lte: endMonth } } },
    { $group: { _id: '$month', count: { $sum: 1 } } },   // 逐条计一例，不去重
  ]);

  const countMap = {};
  result.forEach(r => { countMap[r._id] = r.count; });
  months.forEach(m => { if (!countMap[m]) countMap[m] = 0; });
  return countMap;
}

// ── 年度 / 范围统计 ───────────────────────────────────

// ── 统一分派入口：杜绝参数串位（indicatorKey 始终在第一参数位）─

async function getIndicatorMonthlyCounts(indicatorKey, startMonth, endMonth, department) {
  switch (indicatorKey) {
    case 'enteral':
    case 'parenteral':
      // 保持原4参顺序 getMonthlyCounts(startMonth, endMonth, indicatorKey, department)
      return getMonthlyCounts(startMonth, endMonth, indicatorKey, department);
    case 'gastricTube':
      return getGastricTubeMonthlyCounts(startMonth, endMonth, department);
    case 'enteralExec':
      return getEnteralExecMonthlyCounts(startMonth, endMonth, department);
    default:
      throw new Error(`不支持的营养指标: ${indicatorKey}`);
  }
}

async function getYearStats(year, department = '') {
  const y = validateYear(year);
  const months = Array.from({ length: 12 }, (_, i) => `${y}-${String(i + 1).padStart(2, '0')}`);
  const data = await Promise.all(NUTRITION_INDICATORS.map(async (indicator) => {
    const monthMap = await getIndicatorMonthlyCounts(indicator.key, `${y}-01`, `${y}-12`, department);
    const total = Object.values(monthMap).reduce((sum, v) => sum + v, 0);
    return {
      id: indicator.id,
      name: indicator.name,
      key: indicator.key,
      unit: indicator.unit,
      total,
      months: monthMap,
    };
  }));
  return { months, data, startMonth: `${y}-01`, endMonth: `${y}-12` };
}

async function getRangeStats(startMonth, endMonth, department = '') {
  const months = buildMonths(startMonth, endMonth);
  const data = await Promise.all(NUTRITION_INDICATORS.map(async (indicator) => {
    const monthMap = await getIndicatorMonthlyCounts(indicator.key, startMonth, endMonth, department);
    const total = Object.values(monthMap).reduce((sum, v) => sum + v, 0);
    return {
      id: indicator.id,
      name: indicator.name,
      key: indicator.key,
      unit: indicator.unit,
      total,
      months: monthMap,
    };
  }));
  return { months, data, startMonth, endMonth };
}

// ── 月度详情下钻 ──────────────────────────────────────

async function getDetail(indicatorKey, startMonth, endMonth, department = '') {
  if (!SUPPORTED_NUTRITION_KEYS.has(indicatorKey)) throw new Error('营养指标不支持');

  const months = buildMonths(startMonth, endMonth);
  const { startDate, endDate } = getFullRange(startMonth, endMonth);
  const indicator = NUTRITION_INDICATORS.find(item => item.key === indicatorKey);

  // ── 胃肠管留置例数：TubeExe 逐条列出 ──
  if (indicatorKey === 'gastricTube') {
    const match = {
      type: { $in: GASTRIC_TUBE_TYPES },
      valid: { $ne: null },
      endTime: { $gte: startDate, $lte: endDate },
    };

    if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
      const pids = await getDepartmentPatientIds(department);
      if (!pids.length) {
        return { indicator, columns: GASTRIC_TUBE_DETAIL_COLUMNS, rows: [] };
      }
      match.pid = { $in: pids };
    }

    const tubeRecords = await TubeExe.find(match)
      .select('pid type startTime endTime')
      .lean();

    if (!tubeRecords.length) {
      return { indicator, columns: GASTRIC_TUBE_DETAIL_COLUMNS, rows: [] };
    }

    // 接续合并为留置段(episode)，每个 episode 1 行
    const episodes = buildGastricTubeEpisodes(tubeRecords);
    if (!episodes.length) {
      return { indicator, columns: GASTRIC_TUBE_DETAIL_COLUMNS, rows: [] };
    }

    const epPids = [...new Set(episodes.map(ep => ep.pid))];
    const patients = await Patient.find(buildPatientFilter({ _id: { $in: epPids } }, department))
      .select(PATIENT_SELECT)
      .lean();
    const patientMap = new Map(patients.map(p => [String(p._id), p]));

    const rows = episodes
      .map((episode) => {
        const patient = patientMap.get(episode.pid);
        const base = patient ? toDetailRow(patient, 0) : null;
        return {
          index: 0,
          department: base?.department || '',
          bedNo: base?.bedNo || '',
          name: base?.name || '',
          age: base?.age || '',
          hospitalNo: base?.hospitalNo || '',
          icuAdmissionTime: base?.icuAdmissionTime || '',
          icuDischargeTime: base?.icuDischargeTime || '',
          icuDays: base?.icuDays || '',
          admissionDoctor: base?.admissionDoctor || '',
          attendingDoctor: base?.attendingDoctor || '',
          admissionSource: base?.admissionSource || '',
          dischargeType: base?.dischargeType || '',
          transferDept: base?.transferDept || '',
          diagnosis: base?.diagnosis || '',
          tubeType: episode.type || '',
          tubeEndTime: formatDateTime(episode.finalEndTime),
          _sortTime: patient ? asDate(patient.icuAdmissionTime) : null,
        };
      })
      .sort((a, b) => {
        if (!a._sortTime && !b._sortTime) return 0;
        if (!a._sortTime) return 1;
        if (!b._sortTime) return -1;
        return a._sortTime - b._sortTime;
      })
      .map((row, idx) => ({ ...row, index: idx + 1 }));

    return { indicator, columns: GASTRIC_TUBE_DETAIL_COLUMNS, rows };
  }

  // ── 肠内实施例数：DrugExe 逐条列出 ──
  if (indicatorKey === 'enteralExec') {
    const keywordOr = buildKeywordRegexOr(ENTERAL_KEYWORDS);

    const match = {
      status: { $ne: 'invalid' },
      startTime: { $gte: startDate, $lte: endDate },
      $or: keywordOr,
    };

    if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
      const pids = await getDepartmentPatientIds(department);
      if (!pids.length) {
        return { indicator, columns: DAILY_DETAIL_COLUMNS, rows: [] };
      }
      match.pid = { $in: pids };
    }

    const drugRecords = await DrugExe.find(match)
      .select('pid liquidAmount liquidAmountUnit startTime drugList')
      .lean();

    // 归月过滤（startTime 在东八区）
    const filtered = drugRecords.filter(r => {
      const m = moment(r.startTime).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM');
      return m >= months[0] && m <= months[months.length - 1];
    });

    if (!filtered.length) {
      return { indicator, columns: DAILY_DETAIL_COLUMNS, rows: [] };
    }

    const pids = [...new Set(filtered.map(r => normalizeText(r.pid)))];
    const patients = await Patient.find(buildPatientFilter({ _id: { $in: pids } }, department))
      .select(PATIENT_SELECT)
      .lean();
    const patientMap = new Map(patients.map(p => [String(p._id), p]));

    const rows = filtered
      .map((record) => {
        const patient = patientMap.get(normalizeText(record.pid));
        const base = patient ? toDetailRow(patient, 0) : null;
        const drugNames = (record.drugList || [])
          .map(d => d.name || '')
          .filter(Boolean)
          .join('、');
        return {
          index: 0,
          department: base?.department || '',
          bedNo: base?.bedNo || '',
          name: base?.name || '',
          age: base?.age || '',
          hospitalNo: base?.hospitalNo || '',
          icuAdmissionTime: base?.icuAdmissionTime || '',
          icuDischargeTime: base?.icuDischargeTime || '',
          icuDays: base?.icuDays || '',
          drugNames: drugNames || '',
          medicationTime: formatDateTime(record.startTime),
          liquidAmount: record.liquidAmount ?? '',
          liquidAmountUnit: record.liquidAmountUnit ?? '',
          admissionDoctor: base?.admissionDoctor || '',
          attendingDoctor: base?.attendingDoctor || '',
          admissionSource: base?.admissionSource || '',
          dischargeType: base?.dischargeType || '',
          transferDept: base?.transferDept || '',
          diagnosis: base?.diagnosis || '',
          _sortTime: patient ? asDate(patient.icuAdmissionTime) : null,
        };
      })
      .sort((a, b) => {
        if (!a._sortTime && !b._sortTime) return 0;
        if (!a._sortTime) return 1;
        if (!b._sortTime) return -1;
        return a._sortTime - b._sortTime;
      })
      .map((row, idx) => ({ ...row, index: idx + 1 }));

    return { indicator, columns: DAILY_DETAIL_COLUMNS, rows };
  }

  // ── enteral / parenteral：原有逻辑（DrugExe + pid 去重）─

  const keywords = getKeywordsByIndicator(indicatorKey);
  const keywordOr = buildKeywordRegexOr(keywords);

  const match = {
    status: { $ne: 'invalid' },
    startTime: { $gte: startDate, $lte: endDate },
    $or: keywordOr,
  };

  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      return { indicator, columns: NUTRITION_DETAIL_COLUMNS, rows: [] };
    }
    match.pid = { $in: pids };
  }

  // 查询 drugExe 记录，按 {pid, month} 去重取第一条
  const rawRecords = await DrugExe.find(match)
    .select('pid startTime')
    .lean();

  // 东八区归月 + pid 去重
  const seen = new Set();
  const dedupedPids = [];
  for (const record of rawRecords) {
    const pid = normalizeText(record.pid);
    const month = moment(record.startTime).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM');
    if (month < months[0] || month > months[months.length - 1]) continue;
    const key = `${pid}::${month}`;
    if (seen.has(key)) continue;
    seen.add(key);
    dedupedPids.push(pid);
  }

  const uniquePids = [...new Set(dedupedPids)];
  if (!uniquePids.length) {
    return { indicator, columns: NUTRITION_DETAIL_COLUMNS, rows: [] };
  }

  const patients = await Patient.find(buildPatientFilter({ _id: { $in: uniquePids } }, department))
    .select(PATIENT_SELECT)
    .lean();

  // 按入科时间升序排列
  const sortedPatients = patients
    .map(p => ({ patient: p, sortTime: asDate(p.icuAdmissionTime) }))
    .sort((a, b) => {
      if (!a.sortTime && !b.sortTime) return 0;
      if (!a.sortTime) return 1;
      if (!b.sortTime) return -1;
      return a.sortTime - b.sortTime;
    });

  const rows = sortedPatients
    .map((item, idx) => toDetailRow(item.patient, idx + 1));

  return { indicator, columns: NUTRITION_DETAIL_COLUMNS, rows };
}

// ── 每日肠内营养 ──────────────────────────────────────

function buildDateList(startDate, endDate) {
  const dates = [];
  const cur = moment(startDate, 'YYYY-MM-DD');
  const end = moment(endDate, 'YYYY-MM-DD');
  if (cur.isAfter(end)) throw new Error('开始日期不能晚于结束日期');
  const diffDays = end.diff(cur, 'days');
  if (diffDays > 366) throw new Error('查询范围不能超过 366 天');

  while (cur.isSameOrBefore(end)) {
    dates.push(cur.format('YYYY-MM-DD'));
    cur.add(1, 'day');
  }
  return dates;
}

async function getDailyEnteral(startDateStr, endDateStr, department = '') {
  validateDate(startDateStr, '开始日期');
  validateDate(endDateStr, '结束日期');
  const days = buildDateList(startDateStr, endDateStr);

  const overallStart = moment.parseZone(`${days[0]}T00:00:00+08:00`).toDate();
  const overallEnd = moment.parseZone(`${days[days.length - 1]}T23:59:59.999+08:00`).toDate();

  const keywordOr = buildKeywordRegexOr(ENTERAL_KEYWORDS);
  const match = {
    status: { $ne: 'invalid' },
    startTime: { $gte: overallStart, $lte: overallEnd },
    $or: keywordOr,
  };

  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      return { days, data: days.map(d => ({ date: d, count: 0 })), startDate: startDateStr, endDate: endDateStr };
    }
    match.pid = { $in: pids };
  }

  const result = await DrugExe.aggregate([
    { $match: match },
    {
      $project: {
        pid: 1,
        date: { $dateToString: { format: '%Y-%m-%d', date: '$startTime', timezone: '+08:00' } },
      },
    },
    { $match: { date: { $gte: days[0], $lte: days[days.length - 1] } } },
    { $group: { _id: { date: '$date', pid: '$pid' } } },
    { $group: { _id: '$_id.date', count: { $sum: 1 } } },
  ]);

  const countMap = {};
  result.forEach(r => { countMap[r._id] = r.count; });
  const data = days.map(date => ({ date, count: countMap[date] || 0 }));

  return { days, data, startDate: startDateStr, endDate: endDateStr };
}

async function getDailyEnteralDetail(dateStr, department = '') {
  validateDate(dateStr, '日期');
  const { startDate, endDate } = getDayRange(dateStr);
  const keywordOr = buildKeywordRegexOr(ENTERAL_KEYWORDS);

  const match = {
    status: { $ne: 'invalid' },
    startTime: { $gte: startDate, $lte: endDate },
    $or: keywordOr,
  };

  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      return {
        indicator: { key: 'dailyEnteral', name: `每日肠内营养使用人数(${dateStr})` },
        columns: DAILY_DETAIL_COLUMNS,
        rows: [],
      };
    }
    match.pid = { $in: pids };
  }

  const drugRecords = await DrugExe.find(match)
    .select('pid liquidAmount liquidAmountUnit startTime drugList')
    .lean();

  if (!drugRecords.length) {
    return {
      indicator: { key: 'dailyEnteral', name: `每日肠内营养使用人数(${dateStr})` },
      columns: DAILY_DETAIL_COLUMNS,
      rows: [],
    };
  }

  const pids = [...new Set(drugRecords.map(r => normalizeText(r.pid)))];
  const patients = await Patient.find(buildPatientFilter({ _id: { $in: pids } }, department))
    .select(PATIENT_SELECT)
    .lean();
  const patientMap = new Map(patients.map(p => [String(p._id), p]));

  // 分条列出，每条给药记录独立一行，按入科时间升序
  const rows = drugRecords
    .map((record, idx) => {
      const patient = patientMap.get(normalizeText(record.pid));
      const base = patient ? toDetailRow(patient, idx + 1) : null;
      const drugNames = (record.drugList || [])
        .map(d => d.name || '')
        .filter(Boolean)
        .join('、');
      return {
        index: idx + 1,
        department: base?.department || '',
        bedNo: base?.bedNo || '',
        name: base?.name || '',
        age: base?.age || '',
        hospitalNo: base?.hospitalNo || '',
        icuAdmissionTime: base?.icuAdmissionTime || '',
        icuDischargeTime: base?.icuDischargeTime || '',
        icuDays: base?.icuDays || '',
        admissionDoctor: base?.admissionDoctor || '',
        attendingDoctor: base?.attendingDoctor || '',
        admissionSource: base?.admissionSource || '',
        dischargeType: base?.dischargeType || '',
        transferDept: base?.transferDept || '',
        diagnosis: base?.diagnosis || '',
        drugNames: drugNames || '',
        medicationTime: formatDateTime(record.startTime),
        liquidAmount: record.liquidAmount ?? '',
        liquidAmountUnit: record.liquidAmountUnit ?? '',
        _sortTime: patient ? asDate(patient.icuAdmissionTime) : null,
      };
    })
    .sort((a, b) => {
      if (!a._sortTime && !b._sortTime) return 0;
      if (!a._sortTime) return 1;
      if (!b._sortTime) return -1;
      return a._sortTime - b._sortTime;
    })
    .map((row, idx) => ({ ...row, index: idx + 1 }));

  return {
    indicator: { key: 'dailyEnteral', name: `每日肠内营养使用人数(${dateStr})` },
    columns: DAILY_DETAIL_COLUMNS,
    rows,
  };
}

// ── 日期范围批量明细（一键导出用）────────────────────

async function getDailyEnteralRangeDetail(startDateStr, endDateStr, department = '') {
  validateDate(startDateStr, '开始日期');
  validateDate(endDateStr, '结束日期');
  const days = buildDateList(startDateStr, endDateStr);

  const overallStart = moment.parseZone(`${days[0]}T00:00:00+08:00`).toDate();
  const overallEnd = moment.parseZone(`${days[days.length - 1]}T23:59:59.999+08:00`).toDate();
  const keywordOr = buildKeywordRegexOr(ENTERAL_KEYWORDS);

  const match = {
    status: { $ne: 'invalid' },
    startTime: { $gte: overallStart, $lte: overallEnd },
    $or: keywordOr,
  };

  if (department && process.env.ENABLE_DEPT_FILTER === 'true') {
    const pids = await getDepartmentPatientIds(department);
    if (!pids.length) {
      return { columns: DAILY_DETAIL_COLUMNS, rows: [] };
    }
    match.pid = { $in: pids };
  }

  const drugRecords = await DrugExe.find(match)
    .select('pid liquidAmount liquidAmountUnit startTime drugList')
    .lean();

  if (!drugRecords.length) {
    return { columns: DAILY_DETAIL_COLUMNS, rows: [] };
  }

  const pids = [...new Set(drugRecords.map(r => normalizeText(r.pid)))];
  const patients = await Patient.find(buildPatientFilter({ _id: { $in: pids } }, department))
    .select(PATIENT_SELECT)
    .lean();
  const patientMap = new Map(patients.map(p => [String(p._id), p]));

  const rows = drugRecords
    .map((record) => {
      const patient = patientMap.get(normalizeText(record.pid));
      const base = patient ? toDetailRow(patient, 0) : null;
      const drugNames = (record.drugList || [])
        .map(d => d.name || '')
        .filter(Boolean)
        .join('、');
      const statDate = moment(record.startTime).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM-DD');
      return {
        statDate,
        department: base?.department || '',
        bedNo: base?.bedNo || '',
        name: base?.name || '',
        age: base?.age || '',
        hospitalNo: base?.hospitalNo || '',
        icuAdmissionTime: base?.icuAdmissionTime || '',
        icuDischargeTime: base?.icuDischargeTime || '',
        icuDays: base?.icuDays || '',
        drugNames: drugNames || '',
        liquidAmount: record.liquidAmount ?? '',
        liquidAmountUnit: record.liquidAmountUnit ?? '',
        admissionDoctor: base?.admissionDoctor || '',
        attendingDoctor: base?.attendingDoctor || '',
        admissionSource: base?.admissionSource || '',
        dischargeType: base?.dischargeType || '',
        transferDept: base?.transferDept || '',
        diagnosis: base?.diagnosis || '',
        _sortTime: patient ? asDate(patient.icuAdmissionTime) : null,
      };
    })
    .sort((a, b) => {
      if (!a._sortTime && !b._sortTime) return 0;
      if (!a._sortTime) return 1;
      if (!b._sortTime) return -1;
      return a._sortTime - b._sortTime;
    })
    .map((row, idx) => ({ ...row, index: idx + 1 }));

  return {
    columns: [{ key: 'index', title: '序号' }, { key: 'statDate', title: '统计日期' }, ...DAILY_DETAIL_COLUMNS.slice(1)],
    rows,
  };
}

module.exports = {
  NUTRITION_INDICATORS,
  ENTERAL_KEYWORDS,
  PARENTERAL_KEYWORDS,
  GASTRIC_TUBE_TYPES,
  getYearStats,
  getRangeStats,
  getDetail,
  getDailyEnteral,
  getDailyEnteralDetail,
  getDailyEnteralRangeDetail,
  getGastricTubeMonthlyCounts,
  getEnteralExecMonthlyCounts,
};
