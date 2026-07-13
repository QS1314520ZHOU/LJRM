const API_BASE = '/api/stats';

const els = {
  btnTabQuality: document.getElementById('btnTabQuality'),
  btnTabDrg: document.getElementById('btnTabDrg'),
  btnTabNutrition: document.getElementById('btnTabNutrition'),
  year: document.getElementById('year'),
  department: document.getElementById('department'),
  startMonth: document.getElementById('startMonth'),
  endMonth: document.getElementById('endMonth'),
  btnYearQuery: document.getElementById('btnYearQuery'),
  btnRangeQuery: document.getElementById('btnRangeQuery'),
  btnExport: document.getElementById('btnExport'),
  status: document.getElementById('status'),
  tableHead: document.getElementById('tableHead'),
  tableBody: document.getElementById('tableBody'),
  detailModal: document.getElementById('detailModal'),
  detailTitle: document.getElementById('detailTitle'),
  detailStatus: document.getElementById('detailStatus'),
  detailHead: document.getElementById('detailHead'),
  detailBody: document.getElementById('detailBody'),
  detailTable: document.querySelector('.detail-table'),
  btnCloseDetail: document.getElementById('btnCloseDetail'),
  btnOkDetail: document.getElementById('btnOkDetail'),
  btnCancelDetail: document.getElementById('btnCancelDetail'),
  btnExportDetail: document.getElementById('btnExportDetail'),
  dailySection: document.getElementById('dailySection'),
  dailyStartDate: document.getElementById('dailyStartDate'),
  dailyEndDate: document.getElementById('dailyEndDate'),
  btnDailyQuery: document.getElementById('btnDailyQuery'),
  dailyStatus: document.getElementById('dailyStatus'),
  dailyHead: document.getElementById('dailyHead'),
  dailyBody: document.getElementById('dailyBody'),
};

const statsTable = document.getElementById('statsTable');

let activeView = 'quality';
let lastDrgResult = null;
let lastDrgQuery = null;
let lastDrgStatus = { text: '请选择条件后查询', isError: false };
let lastQualityResult = null;
let lastQualityQuery = null;
let lastQualityStatus = { text: '请选择条件后查询', isError: false };
let lastNutritionResult = null;
let lastNutritionQuery = null;
let lastNutritionStatus = { text: '请选择条件后查询', isError: false };
let lastDailyResult = null;
let lastDetail = null;
let lastDetailMeta = null;
let detailHistory = [];   // 详情弹窗的层级栈，[{ openArgs, detail, meta, title, status }, ...]


const QUALITY_INDICATOR_TOOLTIP_MAP = {
  shockBundleRate: [
    '通过医嘱提取',
    '分子医嘱：感染性休克患者集束化治疗',
    '分母医嘱：感染性休克护理常规',
  ].join('\n'),
  dvtRate: [
    '通过医嘱提取',
    '分子医嘱：肢体气压治疗 / 梯度压力弹力袜 / 腔静脉滤器 / 低分子肝素钠 / 低分子肝素钙 / 那曲肝素 / 依诺肝素 / 达肝素钠注射液 / 利伐沙班',
    '分母逻辑：同期ICU患者总数',
  ].join('\n'),
  shockUltrasoundRate: [
    '通过医嘱提取',
    '分子医嘱：重症超声筛查评估',
    '分母医嘱：休克护理常规',
  ].join('\n'),
  shockHemodynamicRate: [
    '通过医嘱提取',
    '分子医嘱：CVP',
    '分母医嘱：休克护理常规',
  ].join('\n'),
  ardsRate: [
    '通过医嘱提取',
    '分子医嘱：俯卧位通气',
    '分母医嘱：中重度ARDS护理常规',
  ].join('\n'),
  en48hRate: [
    '通过医嘱提取',
    '分子医嘱：流质饮食',
    '分母逻辑：入科超过48h的同期ICU患者',
  ].join('\n'),
  painRate: [
    '通过医嘱提取',
    '分子医嘱：镇痛评估',
    '分母逻辑：同期ICU患者总数',
  ].join('\n'),
  sedationRate: [
    '通过医嘱提取',
    '分子医嘱：镇静评估',
    '分母逻辑：同期ICU患者总数',
  ].join('\n'),
  acuteBrainInjuryRate: [
    '通过医嘱提取',
    '分子医嘱：格拉斯哥昏迷评分',
    '分母医嘱：急性脑损伤护理常规',
  ].join('\n'),
};

const qualityTooltipEl = document.createElement('div');
qualityTooltipEl.className = 'quality-tooltip-popup hidden';
document.body.appendChild(qualityTooltipEl);

els.btnTabQuality.addEventListener('click', () => switchView('quality'));
els.btnTabDrg.addEventListener('click', () => switchView('drg'));
els.btnTabNutrition?.addEventListener('click', () => switchView('nutrition'));
els.btnYearQuery.addEventListener('click', () => handleQuery('year'));
els.btnRangeQuery.addEventListener('click', () => handleQuery('range'));
els.btnExport.addEventListener('click', exportCurrentTable);
els.btnDailyQuery?.addEventListener('click', () => queryDaily());

// × 和 确定：直接全部关闭
[els.btnCloseDetail, els.btnOkDetail].forEach(btn => {
  btn.addEventListener('click', closeDetailModal);
});

// 取消：有上一级就回退，没有上一级才关闭
els.btnCancelDetail.addEventListener('click', () => {
  if (detailHistory.length > 1) {
    detailHistory.pop();                          // 弹掉当前层
    restoreDetail(detailHistory[detailHistory.length - 1]); // 恢复上一层
  } else {
    closeDetailModal();
  }
});


els.detailModal.addEventListener('dblclick', event => {
  if (event.target === els.detailModal) closeDetailModal();
});

els.btnExportDetail.addEventListener('click', () => {
  if (!lastDetail || lastDetailMeta?.disableExport) return;
  exportDetailXlsx(lastDetail, lastDetailMeta);
});

async function handleQuery(mode) {
  const department = els.department.value;
  if (mode === 'year') {
    const year = els.year.value;
    if (!year) return alert('请输入年份');

    if (activeView === 'quality') {
      await queryQuality({ mode, year, department });
      return;
    }

    if (activeView === 'nutrition') {
      await queryNutrition({
        url: `${API_BASE}/nutrition/year?year=${encodeURIComponent(year)}&department=${encodeURIComponent(department)}`,
        title: `年度统计：${year}年`,
        query: { mode, year, department, startMonth: `${year}-01`, endMonth: `${year}-12` },
      });
      return;
    }

    await queryDrg({
      url: `${API_BASE}/year?year=${encodeURIComponent(year)}&department=${encodeURIComponent(department)}`,
      title: `年度统计：${year}年`,
      query: {
        mode,
        year,
        department,
        startMonth: `${year}-01`,
        endMonth: `${year}-12`,
      },
    });
    return;
  }

  const startMonth = els.startMonth.value;
  const endMonth = els.endMonth.value;
  if (!startMonth || !endMonth) return alert('请选择月份范围');
  if (startMonth > endMonth) return alert('开始月份不能晚于结束月份');

  if (activeView === 'quality') {
    await queryQuality({ mode, startMonth, endMonth, department });
    return;
  }

  if (activeView === 'nutrition') {
    await queryNutrition({
      url: `${API_BASE}/nutrition/range?startMonth=${encodeURIComponent(startMonth)}&endMonth=${encodeURIComponent(endMonth)}&department=${encodeURIComponent(department)}`,
      title: `月份统计：${startMonth} 至 ${endMonth}`,
      query: { mode, startMonth, endMonth, department },
    });
    return;
  }

  await queryDrg({
    url: `${API_BASE}/range?startMonth=${encodeURIComponent(startMonth)}&endMonth=${encodeURIComponent(endMonth)}&department=${encodeURIComponent(department)}`,
    title: `月份统计：${startMonth} 至 ${endMonth}`,
    query: { mode, startMonth, endMonth, department },
  });
}

async function queryDrg({ url, title, query }) {
  setLoading(true);
  setStatus('正在查询DRG统计，请稍候...');

  try {
    const resp = await fetch(url);
    const json = await resp.json();
    if (!resp.ok || json.code !== 200) throw new Error(json.msg || '查询失败');

    lastDrgResult = json.data;
    lastDrgQuery = {
      ...query,
      startMonth: json.data.startMonth || query.startMonth,
      endMonth: json.data.endMonth || query.endMonth,
    };
    lastDrgStatus = {
      text: `${title}，共 ${json.data.months.length} 个月、${json.data.data.length} 个指标。点击总计或月份数据可查看详情。`,
      isError: false,
    };

    if (activeView === 'drg') {
      renderDrgTable(json.data.months, json.data.data);
      els.btnExport.disabled = !json.data.data?.length;
      setStatus(lastDrgStatus.text);
    }
  } catch (err) {
    lastDrgResult = null;
    lastDrgQuery = null;
    lastDrgStatus = { text: `DRG统计查询失败：${err.message}`, isError: true };

    if (activeView === 'drg') {
      renderDrgTable([], []);
      els.btnExport.disabled = true;
      setStatus(lastDrgStatus.text, true);
    }
  } finally {
    setLoading(false);
  }
}

async function queryQuality(query) {
  setLoading(true);
  setStatus('正在查询质控统计，请稍候...');

  try {
    const params = new URLSearchParams({ department: query.department || '' });
    if (query.mode === 'year') {
      params.set('year', query.year);
    } else {
      params.set('startMonth', query.startMonth);
      params.set('endMonth', query.endMonth);
    }

    const resp = await fetch(`${API_BASE}/quality?${params.toString()}`);
    const json = await resp.json();
    if (!resp.ok || json.code !== 200) throw new Error(json.msg || '查询失败');

    lastQualityQuery = { ...query };
    lastQualityResult = json.data;
    lastQualityStatus = {
      text: `质控统计已更新，当前展示 ${getQualityPeriodLabel()} 的按月数据。点击月份单元格可查看详情。`,
      isError: false,
    };

    if (activeView === 'quality') {
      renderQualityTable(json.data.indicators || []);
      els.btnExport.disabled = !(json.data.indicators || []).length;
      setStatus(lastQualityStatus.text);
    }
  } catch (err) {
    lastQualityResult = null;
    lastQualityStatus = { text: `质控统计查询失败：${err.message}`, isError: true };

    if (activeView === 'quality') {
      renderQualityTable([]);
      els.btnExport.disabled = true;
      setStatus(lastQualityStatus.text, true);
    }
  } finally {
    setLoading(false);
  }
}

function renderDrgTable(months, data) {
  statsTable.classList.remove('is-quality');
  statsTable.classList.add('is-drg');

  els.tableHead.innerHTML = `
    <th>序号</th>
    <th>指标名称</th>
    <th>单位</th>
    <th>总计</th>
    ${months.map(month => `<th>${escapeHtml(formatMonthLabel(month))}</th>`).join('')}
  `;

  if (!data.length) {
    els.tableBody.innerHTML = `<tr><td colspan="${4 + months.length}" class="empty">暂无数据</td></tr>`;
    return;
  }

  els.tableBody.innerHTML = data.map(row => `
    <tr>
      <td>${row.id}</td>
      <td class="name-cell">${escapeHtml(row.name)}</td>
      <td>${escapeHtml(row.unit || '')}</td>
      <td class="total-cell total-detail-trigger" data-key="${escapeHtml(row.key)}" title="点击查看总计详情">${formatNumber(row.total)}</td>
      ${months.map(month => `
        <td class="month-detail-trigger" data-key="${escapeHtml(row.key)}" data-month="${escapeHtml(month)}" title="点击查看 ${escapeHtml(month)} 详情">
          ${formatNumber(row.months?.[month] || 0)}
        </td>
      `).join('')}
    </tr>
  `).join('');

  els.tableBody.querySelectorAll('.month-detail-trigger').forEach(cell => {
    cell.addEventListener('click', () => openDrgMonthDetail(cell.dataset.key, cell.dataset.month));
  });

  els.tableBody.querySelectorAll('.total-detail-trigger').forEach(cell => {
    cell.addEventListener('click', () => openDrgTotalDetail(cell.dataset.key));
  });
}

function renderQualityTable(indicators) {
  statsTable.classList.remove('is-drg');
  statsTable.classList.add('is-quality');
  const months = lastQualityResult?.months || [];

  els.tableHead.innerHTML = `
    <th>指标名称</th>
    <th>比率</th>
    <th>分子</th>
    <th>分母</th>
    ${months.map(month => `<th>${escapeHtml(formatMonthLabel(month))}</th>`).join('')}
  `;

  if (!indicators.length) {
    els.tableBody.innerHTML = `<tr><td colspan="${4 + months.length}" class="empty">暂无数据</td></tr>`;
    return;
  }

  els.tableBody.innerHTML = indicators.map(row => `
    <tr>
      <td class="quality-name-cell${getQualityIndicatorTooltip(row.key) ? ' quality-name-tooltip' : ''}" data-tooltip="${escapeHtml(getQualityIndicatorTooltip(row.key) || '').replace(/\n/g, '&#10;')}">${escapeHtml(row.name)}</td>
      ${renderQualityValueCell(row, 'ratio', row.ratio)}
      ${renderQualityValueCell(row, 'numerator', row.numerator)}
      ${renderQualityValueCell(row, 'denominator', row.denominator)}
      ${months.map(month => renderQualityMonthCell(row, month)).join('')}
    </tr>
  `).join('');

  els.tableBody.querySelectorAll('.quality-detail-trigger').forEach(cell => {
    cell.addEventListener('click', () => handleQualityCellClick(cell.dataset.key, cell.dataset.field));
  });
  bindQualityNameTooltips();
}

function renderQualityValueCell(row, field, value) {
  void row;
  void field;
  return `<td>${escapeHtml(value ?? '')}</td>`;
}

function getQualityIndicatorTooltip(indicatorKey) {
  return QUALITY_INDICATOR_TOOLTIP_MAP[indicatorKey] || '';
}

function bindQualityNameTooltips() {
  els.tableBody.querySelectorAll('.quality-name-tooltip').forEach(cell => {
    cell.addEventListener('mouseenter', event => showQualityTooltip(event.currentTarget));
    cell.addEventListener('mousemove', event => moveQualityTooltip(event));
    cell.addEventListener('mouseleave', hideQualityTooltip);
  });
}

function showQualityTooltip(target) {
  const text = target?.dataset?.tooltip || '';
  if (!text) return;
  qualityTooltipEl.textContent = text;
  qualityTooltipEl.classList.remove('hidden');
}

function moveQualityTooltip(event) {
  if (qualityTooltipEl.classList.contains('hidden')) return;
  const offsetX = 18;
  const offsetY = 18;
  const maxLeft = window.innerWidth - qualityTooltipEl.offsetWidth - 12;
  const maxTop = window.innerHeight - qualityTooltipEl.offsetHeight - 12;
  const left = Math.min(event.clientX + offsetX, Math.max(12, maxLeft));
  const top = Math.min(event.clientY + offsetY, Math.max(12, maxTop));
  qualityTooltipEl.style.left = `${left}px`;
  qualityTooltipEl.style.top = `${top}px`;
}

function hideQualityTooltip() {
  qualityTooltipEl.classList.add('hidden');
}

function renderQualityMonthCell(row, month) {
  const monthValue = row.months?.[month]?.display ?? '';
  const field = `month:${month}`;
  const clickable = isQualityCellClickable(row.key, field, monthValue);
  const className = clickable ? 'quality-detail-trigger quality-clickable' : '';
  const title = clickable ? `点击查看 ${formatMonthLabel(month)} 详情` : '';
  return `<td class="${className}" data-key="${escapeHtml(row.key)}" data-field="${escapeHtml(field)}" title="${escapeHtml(title)}">${escapeHtml(monthValue)}</td>`;
}

function isQualityCellClickable(indicatorKey, field, value) {
  void indicatorKey;
  if (value === '/' || value === '' || value == null) return false;
  return field.startsWith('month:');
}

async function handleQualityCellClick(indicatorKey, field) {
  if (!lastQualityResult) return alert('请先查询质控统计数据');
  const month = field.startsWith('month:') ? field.slice(6) : '';

  if (month) {
    await openQualityDetail(indicatorKey, month, month);
    return;
  }

  void field;
}

async function openDrgMonthDetail(indicatorKey, month) {
  if (!lastDrgQuery) return alert('请先查询DRG统计数据');

  await openRemoteDetail({
    title: '统计详情',
    statusText: '正在加载详情...',
    fetcher: () => fetchDetail(`${API_BASE}/detail`, {
      indicatorKey,
      startMonth: month,
      endMonth: month,
      department: lastDrgQuery.department || '',
    }),
    meta: {
      view: 'drg',
      startMonth: month,
      endMonth: month,
      department: lastDrgQuery.department || '',
    },
    onLoaded: detail => {
      const indicatorName = detail.indicator?.name || '统计详情';
      return {
        title: `${indicatorName} - 统计详情`,
        status: `${month}，共 ${detail.rows.length} 条记录`,
      };
    },
  });
}

async function openDrgTotalDetail(indicatorKey) {
  if (!lastDrgQuery || !lastDrgResult) return alert('请先查询DRG统计数据');
  const months = (lastDrgResult.months || []).filter(month => month >= lastDrgQuery.startMonth && month <= lastDrgQuery.endMonth);
  if (!months.length) return;

  await openRemoteDetail({
    title: '总计详情',
    statusText: '正在加载总计详情...',
    fetcher: async () => {
      const details = await Promise.all(months.map(async month => ({
        month,
        detail: await fetchDetail(`${API_BASE}/detail`, {
          indicatorKey,
          startMonth: month,
          endMonth: month,
          department: lastDrgQuery.department || '',
        }),
      })));

      const firstDetail = details.find(item => item.detail)?.detail || {};
      const columns = [{ key: 'statMonth', title: '统计月份' }, ...(firstDetail.columns || [])];
      let index = 1;
      const rows = details.flatMap(({ month, detail }) => (detail.rows || []).map(row => ({
        ...row,
        statMonth: month,
        index: index++,
      })));

      return {
        indicator: firstDetail.indicator,
        columns,
        rows,
      };
    },
    meta: {
      view: 'drg',
      startMonth: lastDrgQuery.startMonth,
      endMonth: lastDrgQuery.endMonth,
      department: lastDrgQuery.department || '',
    },
    onLoaded: detail => {
      const indicatorName = detail.indicator?.name || '统计详情';
      return {
        title: `${indicatorName} - 总计详情`,
        status: `${lastDrgQuery.startMonth} 至 ${lastDrgQuery.endMonth}，共 ${detail.rows.length} 条记录`,
      };
    },
  });
}

async function openQualityDetail(indicatorKey, startMonth, endMonth, itemOrder = '', itemLabel = '') {
  if (!lastQualityResult) return alert('请先查询质控统计数据');

  await openRemoteDetail({
    title: '指标详情',
    statusText: '正在加载详情...',
    fetcher: () => {
      const params = new URLSearchParams({
        indicatorKey,
        startMonth,
        endMonth,
        department: lastQualityResult.department || '',
      });
      if (itemOrder !== '') params.set('itemOrder', itemOrder);
      return fetchDetail(`${API_BASE}/quality/detail`, Object.fromEntries(params.entries()));
    },
    meta: {
      view: 'quality',
      startMonth,
      endMonth,
      department: lastQualityResult.department || '',
      itemLabel,
    },
    onLoaded: detail => ({
      title: `${detail.indicator?.name || '指标详情'} - ${formatRangeLabel(startMonth, endMonth)}统计详情`,
      status: `${formatRangeLabel(startMonth, endMonth)}，共 ${detail.rows.length} 条记录`,
    }),
  });
}

async function openRemoteDetail({ title, statusText, fetcher, meta, onLoaded }) {
  lastDetail = null;
  lastDetailMeta = meta;
  openDetailModal();
  els.detailTitle.textContent = title;
  els.detailStatus.textContent = statusText;
  els.btnExportDetail.disabled = true;
  els.detailHead.innerHTML = '';
  els.detailBody.innerHTML = '<tr><td class="empty">正在加载...</td></tr>';

  try {
    const detail = await fetcher();
    lastDetail = detail;
    renderDetail(detail);
    const ui = onLoaded(detail);
    if (meta?.view === 'quality') {
      ui.title = buildQualityDetailTitle(detail.indicator?.name || '鎸囨爣璇︽儏', meta.itemLabel || '', meta.startMonth, meta.endMonth);
    }
    els.detailTitle.textContent = ui.title;
    els.detailStatus.textContent = ui.status;
    els.btnExportDetail.disabled = !detail.rows.length || Boolean(lastDetailMeta?.disableExport);

    // 压栈：保存当前层的完整状态，供"取消"回退使用
    detailHistory.push({
      detail,
      meta,
      title: ui.title,
      status: ui.status,
      exportDisabled: els.btnExportDetail.disabled,
    });
  } catch (err) {
    els.detailStatus.textContent = `加载失败：${err.message}`;
    els.detailBody.innerHTML = '<tr><td class="empty">详情加载失败</td></tr>';
  }
}


async function fetchDetail(url, params) {
  const query = new URLSearchParams(params);
  const resp = await fetch(`${url}?${query.toString()}`);
  const json = await resp.json();
  if (!resp.ok || json.code !== 200) throw new Error(json.msg || '详情查询失败');
  return json.data;
}

function renderDetail(detail) {
  const columns = detail.columns || [];
  const rows = detail.rows || [];
  els.detailTable.classList.toggle('is-summary', columns.some(col => col.type === 'action'));
  els.detailHead.innerHTML = columns.map(col => `<th>${escapeHtml(col.title)}</th>`).join('');

  if (!rows.length) {
    els.detailBody.innerHTML = `<tr><td colspan="${columns.length || 1}" class="empty">暂无明细数据</td></tr>`;
    return;
  }

  els.detailBody.innerHTML = rows.map(row => `
    <tr>
      ${columns.map(col => renderDetailCell(col, row)).join('')}
    </tr>
  `).join('');

  els.detailBody.querySelectorAll('.detail-action-btn').forEach(button => {
    button.addEventListener('click', async () => {
      const target = button.dataset.target;
      const startMonth = button.dataset.startMonth;
      const endMonth = button.dataset.endMonth;
      const itemOrder = button.dataset.itemOrder || '';
      const itemLabel = button.dataset.itemLabel || '';
      if (!target) return;
      await openQualityDetail(target, startMonth, endMonth, itemOrder, itemLabel);
    });
  });
}

function renderDetailCell(column, row) {
  if (column.type === 'action') {
    if (!row.action) return '<td></td>';
    return `
      <td>
        <button
          class="btn-primary detail-action-btn"
          type="button"
          data-target="${escapeHtml(row.action.target)}"
          data-start-month="${escapeHtml(row.action.startMonth || '')}"
          data-end-month="${escapeHtml(row.action.endMonth || '')}"
          data-item-order="${escapeHtml(row.action.itemOrder || '')}"
          data-item-label="${escapeHtml(row.action.itemLabel || row.item || '')}"
        >
          ${escapeHtml(row.action.label)}
        </button>
      </td>
    `;
  }
  return `<td>${escapeHtml(row[column.key] ?? '')}</td>`;
}

function openDetailModal() {
  els.detailModal.classList.remove('hidden');
  document.body.classList.add('modal-open');
}
function restoreDetail(snapshot) {
  if (!snapshot) return;
  lastDetail = snapshot.detail;
  lastDetailMeta = snapshot.meta;
  els.detailTitle.textContent = snapshot.title;
  els.detailStatus.textContent = snapshot.status;
  els.btnExportDetail.disabled = snapshot.exportDisabled;
  renderDetail(snapshot.detail);   // 重新渲染表格内容
}


function closeDetailModal() {
  els.detailModal.classList.add('hidden');
  document.body.classList.remove('modal-open');
  detailHistory = [];   // 关闭时清空层级栈
}

// ── 营养统计 ──────────────────────────────────────────

async function queryNutrition({ url, title, query }) {
  setLoading(true);
  setStatus('正在查询营养统计，请稍候...');

  try {
    const resp = await fetch(url);
    const json = await resp.json();
    if (!resp.ok || json.code !== 200) throw new Error(json.msg || '查询失败');

    lastNutritionResult = json.data;
    lastNutritionQuery = {
      ...query,
      startMonth: json.data.startMonth || query.startMonth,
      endMonth: json.data.endMonth || query.endMonth,
    };
    lastNutritionStatus = {
      text: `${title}，共 ${json.data.months.length} 个月、${json.data.data.length} 个指标。点击总计或月份数据可查看详情。`,
      isError: false,
    };

    if (activeView === 'nutrition') {
      renderNutritionTable(json.data.months, json.data.data);
      els.btnExport.disabled = !json.data.data?.length;
      setStatus(lastNutritionStatus.text);
    }
  } catch (err) {
    lastNutritionResult = null;
    lastNutritionQuery = null;
    lastNutritionStatus = { text: `营养统计查询失败：${err.message}`, isError: true };

    if (activeView === 'nutrition') {
      renderNutritionTable([], []);
      els.btnExport.disabled = true;
      setStatus(lastNutritionStatus.text, true);
    }
  } finally {
    setLoading(false);
  }
}

function renderNutritionTable(months, data) {
  statsTable.classList.remove('is-quality');
  statsTable.classList.add('is-drg');

  els.tableHead.innerHTML = `
    <th>序号</th>
    <th>指标名称</th>
    <th>单位</th>
    <th>总计</th>
    ${months.map(month => `<th>${escapeHtml(formatMonthLabel(month))}</th>`).join('')}
  `;

  if (!data.length) {
    els.tableBody.innerHTML = `<tr><td colspan="${4 + months.length}" class="empty">暂无数据</td></tr>`;
    return;
  }

  els.tableBody.innerHTML = data.map(row => `
    <tr>
      <td>${row.id}</td>
      <td class="name-cell">${escapeHtml(row.name)}</td>
      <td>${escapeHtml(row.unit || '')}</td>
      <td class="total-cell total-detail-trigger" data-key="${escapeHtml(row.key)}" title="点击查看总计详情">${formatNumber(row.total)}</td>
      ${months.map(month => `
        <td class="month-detail-trigger" data-key="${escapeHtml(row.key)}" data-month="${escapeHtml(month)}" title="点击查看 ${escapeHtml(month)} 详情">
          ${formatNumber(row.months?.[month] || 0)}
        </td>
      `).join('')}
    </tr>
  `).join('');

  els.tableBody.querySelectorAll('.month-detail-trigger').forEach(cell => {
    cell.addEventListener('click', () => openNutritionMonthDetail(cell.dataset.key, cell.dataset.month));
  });

  els.tableBody.querySelectorAll('.total-detail-trigger').forEach(cell => {
    cell.addEventListener('click', () => openNutritionTotalDetail(cell.dataset.key));
  });
}

async function openNutritionMonthDetail(indicatorKey, month) {
  if (!lastNutritionQuery) return alert('请先查询营养统计数据');

  await openRemoteDetail({
    title: '统计详情',
    statusText: '正在加载详情...',
    fetcher: () => fetchDetail(`${API_BASE}/nutrition/detail`, {
      indicatorKey,
      startMonth: month,
      endMonth: month,
      department: lastNutritionQuery.department || '',
    }),
    meta: {
      view: 'nutrition',
      startMonth: month,
      endMonth: month,
      department: lastNutritionQuery.department || '',
    },
    onLoaded: detail => {
      const indicatorName = detail.indicator?.name || '统计详情';
      return {
        title: `${indicatorName} - 统计详情`,
        status: `${month}，共 ${detail.rows.length} 条记录`,
      };
    },
  });
}

async function openNutritionTotalDetail(indicatorKey) {
  if (!lastNutritionQuery || !lastNutritionResult) return alert('请先查询营养统计数据');
  const months = (lastNutritionResult.months || []).filter(month => month >= lastNutritionQuery.startMonth && month <= lastNutritionQuery.endMonth);
  if (!months.length) return;

  await openRemoteDetail({
    title: '总计详情',
    statusText: '正在加载总计详情...',
    fetcher: async () => {
      const details = await Promise.all(months.map(async month => ({
        month,
        detail: await fetchDetail(`${API_BASE}/nutrition/detail`, {
          indicatorKey,
          startMonth: month,
          endMonth: month,
          department: lastNutritionQuery.department || '',
        }),
      })));

      const firstDetail = details.find(item => item.detail)?.detail || {};
      const columns = [{ key: 'statMonth', title: '统计月份' }, ...(firstDetail.columns || [])];
      let index = 1;
      const rows = details.flatMap(({ month, detail }) => (detail.rows || []).map(row => ({
        ...row,
        statMonth: month,
        index: index++,
      })));

      return {
        indicator: firstDetail.indicator,
        columns,
        rows,
      };
    },
    meta: {
      view: 'nutrition',
      startMonth: lastNutritionQuery.startMonth,
      endMonth: lastNutritionQuery.endMonth,
      department: lastNutritionQuery.department || '',
    },
    onLoaded: detail => {
      const indicatorName = detail.indicator?.name || '统计详情';
      return {
        title: `${indicatorName} - 总计详情`,
        status: `${lastNutritionQuery.startMonth} 至 ${lastNutritionQuery.endMonth}，共 ${detail.rows.length} 条记录`,
      };
    },
  });
}

// ── 每日肠内营养 ──────────────────────────────────────

async function queryDaily() {
  const startDate = els.dailyStartDate.value;
  const endDate = els.dailyEndDate.value;
  if (!startDate || !endDate) return alert('请选择日期范围');
  if (startDate > endDate) return alert('开始日期不能晚于结束日期');

  const department = els.department.value;
  els.dailyStatus.textContent = '正在查询每日肠内营养数据...';
  els.dailyStatus.classList.remove('error');

  try {
    const url = `${API_BASE}/nutrition/daily?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}&department=${encodeURIComponent(department)}`;
    const resp = await fetch(url);
    const json = await resp.json();
    if (!resp.ok || json.code !== 200) throw new Error(json.msg || '查询失败');

    lastDailyResult = json.data;
    renderDailyTable(json.data.days, json.data.data);
    els.dailyStatus.textContent = `${startDate} 至 ${endDate}，共 ${json.data.days.length} 天。点击日期行可查看当天详情。`;
    els.dailyStatus.classList.remove('error');
  } catch (err) {
    els.dailyStatus.textContent = `每日统计查询失败：${err.message}`;
    els.dailyStatus.classList.add('error');
    renderDailyTable([], []);
  }
}

function renderDailyTable(days, data) {
  const countMap = {};
  data.forEach(item => { countMap[item.date] = item.count; });

  if (!days.length) {
    els.dailyHead.innerHTML = '<th>日期</th><th>肠内营养使用人数</th>';
    els.dailyBody.innerHTML = '<tr><td colspan="2" class="empty">暂无数据</td></tr>';
    return;
  }

  els.dailyHead.innerHTML = '<th>日期</th><th>肠内营养使用人数</th>';
  els.dailyBody.innerHTML = days.map(date => `
    <tr class="daily-detail-trigger" data-date="${escapeHtml(date)}" title="点击查看 ${escapeHtml(date)} 详情" style="cursor:pointer;">
      <td>${escapeHtml(date)}</td>
      <td>${formatNumber(countMap[date] || 0)}</td>
    </tr>
  `).join('');

  els.dailyBody.querySelectorAll('.daily-detail-trigger').forEach(row => {
    row.addEventListener('click', () => openDailyDetail(row.dataset.date));
  });
}

async function openDailyDetail(date) {
  const department = els.department.value;

  await openRemoteDetail({
    title: '每日详情',
    statusText: '正在加载每日详情...',
    fetcher: () => fetchDetail(`${API_BASE}/nutrition/daily/detail`, {
      date,
      department,
    }),
    meta: {
      view: 'nutrition-daily',
      date,
      startMonth: date,
      endMonth: date,
      department,
    },
    onLoaded: detail => ({
      title: `每日肠内营养使用人数(${date}) - 统计详情`,
      status: `${date}，共 ${detail.rows.length} 条记录`,
    }),
  });
}


function exportCurrentTable() {
  if (!window.XLSX) {
    alert('XLSX 导出库未加载，请确认依赖已正确安装。');
    return;
  }

  if (activeView === 'quality') {
    if (!lastQualityResult?.indicators?.length) return;
    exportSummaryXlsx({
      title: '质控统计',
      department: lastQualityResult.department || '',
      startMonth: lastQualityResult.startMonth,
      endMonth: lastQualityResult.endMonth,
      columns: ['指标名称', '比率', '分子', '分母', ...(lastQualityResult.months || []).map(formatMonthLabel)],
      rows: (lastQualityResult.indicators || []).map(row => [
        row.name,
        row.ratio,
        row.numerator,
        row.denominator,
        ...(lastQualityResult.months || []).map(month => row.months?.[month]?.display || ''),
      ]),
      filenamePrefix: '质控统计',
    });
    return;
  }

  if (activeView === 'nutrition') {
    if (!lastNutritionResult?.data?.length) return;
    exportSummaryXlsx({
      title: '营养统计',
      department: lastNutritionQuery?.department || '',
      startMonth: lastNutritionQuery?.startMonth || lastNutritionResult.startMonth,
      endMonth: lastNutritionQuery?.endMonth || lastNutritionResult.endMonth,
      columns: ['序号', '指标名称', '单位', '总计', ...(lastNutritionResult.months || []).map(formatMonthLabel)],
      rows: (lastNutritionResult.data || []).map(row => [
        row.id,
        row.name,
        row.unit || '',
        row.total,
        ...(lastNutritionResult.months || []).map(month => row.months?.[month] || 0),
      ]),
      filenamePrefix: '营养统计',
    });
    return;
  }

  if (!lastDrgResult?.data?.length) return;
  exportSummaryXlsx({
    title: 'DRG统计',
    department: lastDrgQuery?.department || '',
    startMonth: lastDrgQuery?.startMonth || lastDrgResult.startMonth,
    endMonth: lastDrgQuery?.endMonth || lastDrgResult.endMonth,
    columns: ['序号', '指标名称', '单位', '总计', ...(lastDrgResult.months || []).map(formatMonthLabel)],
    rows: (lastDrgResult.data || []).map(row => [
      row.id,
      row.name,
      row.unit || '',
      row.total,
      ...(lastDrgResult.months || []).map(month => row.months?.[month] || 0),
    ]),
    filenamePrefix: 'DRG统计',
  });
}

function exportDetailXlsx(detail, meta) {
  if (!window.XLSX) {
    alert('XLSX 导出库未加载，请确认依赖已正确安装。');
    return;
  }

  const exportColumns = (detail.columns || []).filter(col => col.type !== 'action');
  const exportRows = (detail.rows || []).map(row => {
    const flatRow = {};
    exportColumns.forEach(col => {
      flatRow[col.key] = row[col.key] ?? '';
    });
    return flatRow;
  });
  const indicatorName = detail.indicator?.name || '统计详情';
  const rangeText = meta.startMonth === meta.endMonth ? meta.startMonth : `${meta.startMonth} 至 ${meta.endMonth}`;
  const generatedAt = new Date().toLocaleString('zh-CN', { hour12: false });
  const title = `${indicatorName}统计详情`;
  const itemLabel = meta.itemLabel || '';
  const exportTitle = itemLabel ? `${indicatorName}-${itemLabel}统计详情` : `${indicatorName}统计详情`;
  const exportSheetName = itemLabel ? `${indicatorName}-${itemLabel}` : indicatorName;
  const header = exportColumns.map(col => col.title);
  const body = exportRows.map(row => exportColumns.map(col => row[col.key] ?? ''));
  const metaRows = [
    [exportTitle],
    [`统计范围：${rangeText}`],
    [`科室：${meta.department || '全部科室'}    记录数：${exportRows.length}    导出时间：${generatedAt}`],
    [],
  ];
  const aoa = [...metaRows, header, ...body];
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const headerRowIndex = metaRows.length;
  const lastColumnIndex = Math.max(exportColumns.length - 1, 0);

  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: lastColumnIndex } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: lastColumnIndex } },
    { s: { r: 2, c: 0 }, e: { r: 2, c: lastColumnIndex } },
  ];
  ws['!cols'] = buildDetailColumnWidths(exportColumns, exportRows);
  ws['!rows'] = aoa.map((_, index) => ({
    hpt: index === 0 ? 28 : index === headerRowIndex ? 24 : 20,
  }));

  if (exportRows.length && exportColumns.length) {
    ws['!autofilter'] = {
      ref: XLSX.utils.encode_range({
        s: { r: headerRowIndex, c: 0 },
        e: { r: headerRowIndex + exportRows.length, c: lastColumnIndex },
      }),
    };
  }

  ws['!freeze'] = { xSplit: 0, ySplit: headerRowIndex + 1 };
  applyDetailSheetStyles(ws, aoa, headerRowIndex, lastColumnIndex);

  const wb = XLSX.utils.book_new();
  wb.Props = {
    Title: exportTitle,
    Subject: rangeText,
    Author: 'ICU重症医学科指标统计',
    CreatedDate: new Date(),
  };
  XLSX.utils.book_append_sheet(wb, ws, sanitizeSheetName(exportSheetName));
  XLSX.writeFile(wb, `${sanitizeFileName(exportSheetName)}-${meta.startMonth}_${meta.endMonth}.xlsx`);
}

function exportSummaryXlsx({ title, department, startMonth, endMonth, columns, rows, filenamePrefix }) {
  const generatedAt = new Date().toLocaleString('zh-CN', { hour12: false });
  const rangeText = startMonth && endMonth ? (startMonth === endMonth ? startMonth : `${startMonth} 至 ${endMonth}`) : '';
  const metaRows = [
    [title],
    [`统计范围：${rangeText}`],
    [`科室：${department || '全部科室'}    记录数：${rows.length}    导出时间：${generatedAt}`],
    [],
  ];
  const aoa = [...metaRows, columns, ...rows];
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const headerRowIndex = metaRows.length;
  const lastColumnIndex = Math.max(columns.length - 1, 0);

  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: lastColumnIndex } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: lastColumnIndex } },
    { s: { r: 2, c: 0 }, e: { r: 2, c: lastColumnIndex } },
  ];
  ws['!cols'] = columns.map((column, columnIndex) => ({
    wch: Math.min(36, Math.max(
      getDisplayWidth(column) + 4,
      ...rows.slice(0, 200).map(row => getDisplayWidth(row[columnIndex]) + 2),
    )),
  }));
  ws['!rows'] = aoa.map((_, index) => ({
    hpt: index === 0 ? 28 : index === headerRowIndex ? 24 : 20,
  }));

  if (rows.length && columns.length) {
    ws['!autofilter'] = {
      ref: XLSX.utils.encode_range({
        s: { r: headerRowIndex, c: 0 },
        e: { r: headerRowIndex + rows.length, c: lastColumnIndex },
      }),
    };
  }

  ws['!freeze'] = { xSplit: 0, ySplit: headerRowIndex + 1 };
  applyDetailSheetStyles(ws, aoa, headerRowIndex, lastColumnIndex);

  const wb = XLSX.utils.book_new();
  wb.Props = {
    Title: title,
    Subject: rangeText,
    Author: 'ICU统计',
    CreatedDate: new Date(),
  };
  XLSX.utils.book_append_sheet(wb, ws, sanitizeSheetName(title));
  XLSX.writeFile(wb, `${sanitizeFileName(filenamePrefix)}-${startMonth || 'all'}_${endMonth || 'all'}.xlsx`);
}

function buildDetailColumnWidths(columns, rows) {
  const widthRules = {
    index: { min: 6, max: 8 },
    statMonth: { min: 12, max: 14 },
    statDate: { min: 12, max: 14 },
    department: { min: 14, max: 22 },
    bedNo: { min: 8, max: 12 },
    name: { min: 10, max: 14 },
    age: { min: 8, max: 10 },
    hospitalNo: { min: 14, max: 20 },
    icuAdmissionTime: { min: 18, max: 20 },
    icuDischargeTime: { min: 18, max: 20 },
    occupiedBedDays: { min: 10, max: 12 },
    icuDays: { min: 10, max: 12 },
    admissionDoctor: { min: 12, max: 16 },
    attendingDoctor: { min: 12, max: 16 },
    admissionSource: { min: 14, max: 20 },
    dischargeType: { min: 14, max: 20 },
    transferDept: { min: 14, max: 24 },
    diagnosis: { min: 28, max: 60 },
    bedNum: { min: 12, max: 14 },
    bedDays: { min: 10, max: 12 },
    recordTime: { min: 18, max: 22 },
  };

  return columns.map(col => {
    const rule = widthRules[col.key] || { min: 10, max: 30 };
    const titleWidth = getDisplayWidth(col.title) + 4;
    const sampleWidth = rows.slice(0, 200).reduce((max, row) => Math.max(max, getDisplayWidth(row[col.key])), 0) + 2;
    return { wch: Math.min(rule.max, Math.max(rule.min, titleWidth, sampleWidth)) };
  });
}

function applyDetailSheetStyles(ws, aoa, headerRowIndex, lastColumnIndex) {
  const range = XLSX.utils.decode_range(ws['!ref']);
  for (let r = range.s.r; r <= range.e.r; r += 1) {
    for (let c = range.s.c; c <= range.e.c; c += 1) {
      const cellRef = XLSX.utils.encode_cell({ r, c });
      if (!ws[cellRef]) continue;
      ws[cellRef].s = {
        font: {
          name: 'Microsoft YaHei',
          sz: r === 0 ? 16 : 11,
          bold: r === 0 || r === headerRowIndex,
          color: { rgb: r === headerRowIndex ? 'FFFFFF' : '111827' },
        },
        alignment: {
          horizontal: r === 0 || r === headerRowIndex ? 'center' : 'left',
          vertical: 'center',
          wrapText: true,
        },
        fill: r === headerRowIndex
          ? { fgColor: { rgb: '1D4ED8' } }
          : r < headerRowIndex
            ? { fgColor: { rgb: 'EFF6FF' } }
            : undefined,
        border: {
          top: { style: 'thin', color: { rgb: 'D1D5DB' } },
          bottom: { style: 'thin', color: { rgb: 'D1D5DB' } },
          left: { style: 'thin', color: { rgb: 'D1D5DB' } },
          right: { style: 'thin', color: { rgb: 'D1D5DB' } },
        },
      };
    }
  }

  for (let c = 0; c <= lastColumnIndex; c += 1) {
    const headerRef = XLSX.utils.encode_cell({ r: headerRowIndex, c });
    if (ws[headerRef]) {
      ws[headerRef].s.alignment = { horizontal: 'center', vertical: 'center', wrapText: true };
    }
  }

  void aoa;
}

function toDrgCsv(months, data) {
  const header = ['序号', '指标名称', '单位', '总计', ...months];
  const rows = data.map(row => [
    row.id,
    row.name,
    row.unit || '',
    row.total,
    ...months.map(month => row.months?.[month] || 0),
  ]);
  return [header, ...rows].map(cols => cols.map(csvCell).join(',')).join('\n');
}

function toQualityCsv(months, data) {
  const header = ['指标名称', '比率', '分子', '分母', ...months.map(formatMonthLabel)];
  const rows = data.map(row => [
    row.name,
    row.ratio,
    row.numerator,
    row.denominator,
    ...months.map(month => row.months?.[month]?.display || ''),
  ]);
  return [header, ...rows].map(cols => cols.map(csvCell).join(',')).join('\n');
}

function csvCell(value) {
  const text = String(value ?? '');
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function downloadBlob(content, filename, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function getDisplayWidth(value) {
  const text = String(value ?? '');
  return [...text].reduce((sum, char) => sum + (/[\u4e00-\u9fa5\uff00-\uffef]/.test(char) ? 2 : 1), 0);
}

function sanitizeSheetName(value) {
  return sanitizeFileName(value).slice(0, 31) || '统计详情';
}

function sanitizeFileName(value) {
  return String(value || '统计详情').replace(/[\\/:*?"<>|]/g, '_');
}

function buildQualityDetailTitle(indicatorName, itemLabel, startMonth, endMonth) {
  const parts = [indicatorName];
  if (itemLabel) parts.push(itemLabel);
  parts.push(`${formatDetailPeriodLabel(startMonth, endMonth)}统计详情`);
  return parts.join('-');
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN');
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function setStatus(text, isError = false) {
  els.status.textContent = text;
  els.status.classList.toggle('error', isError);
}

function setLoading(loading) {
  els.btnYearQuery.disabled = loading;
  els.btnRangeQuery.disabled = loading;
  els.btnYearQuery.textContent = loading ? '查询中...' : '按年查询';
  els.btnRangeQuery.textContent = loading ? '查询中...' : '按月份查询';
}

function switchView(view) {
  activeView = view;
  closeDetailModal();
  els.btnTabQuality.classList.toggle('active', view === 'quality');
  els.btnTabDrg.classList.toggle('active', view === 'drg');
  els.btnTabNutrition?.classList.toggle('active', view === 'nutrition');

  // 每日子模块仅在营养统计视图显示
  if (els.dailySection) els.dailySection.style.display = view === 'nutrition' ? '' : 'none';

  if (view === 'quality') {
    if (lastQualityResult?.indicators?.length) {
      renderQualityTable(lastQualityResult.indicators);
      els.btnExport.disabled = false;
      setStatus(lastQualityStatus.text, lastQualityStatus.isError);
      return;
    }

    void handleQuery('range');
    return;
  }

  if (view === 'nutrition') {
    if (lastNutritionResult?.data?.length) {
      renderNutritionTable(lastNutritionResult.months, lastNutritionResult.data);
      els.btnExport.disabled = false;
      setStatus(lastNutritionStatus.text, lastNutritionStatus.isError);
      return;
    }

    void handleQuery('range');
    return;
  }

  if (lastDrgResult?.data?.length) {
    renderDrgTable(lastDrgResult.months, lastDrgResult.data);
    els.btnExport.disabled = false;
    setStatus(lastDrgStatus.text, lastDrgStatus.isError);
    return;
  }

  void handleQuery('range');
}

function getQualityPeriodLabel() {
  const startMonth = lastQualityResult?.startMonth || lastQualityQuery?.startMonth || els.startMonth.value;
  const endMonth = lastQualityResult?.endMonth || lastQualityQuery?.endMonth || els.endMonth.value;
  return formatRangeLabel(startMonth, endMonth);
}

function formatRangeLabel(startMonth, endMonth) {
  if (!startMonth || !endMonth) return '统计值';
  if (startMonth === endMonth) return formatMonthLabel(startMonth);
  return `${formatMonthLabel(startMonth)}-${formatMonthLabel(endMonth)}`;
}

function formatDetailPeriodLabel(startMonth, endMonth) {
  if (!startMonth || !endMonth) return '统计';
  if (startMonth === endMonth) return `${formatMonthLabel(startMonth)}份`;
  return `${formatMonthLabel(startMonth)}-${formatMonthLabel(endMonth)}`;
}

function formatMonthLabel(monthValue) {
  const [year, month] = String(monthValue).split('-');
  if (!year || !month) return monthValue;
  return `${year}年${Number(month)}月`;
}

function initializeDefaultFilters() {
  const now = new Date();
  const endMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const startMonth = new Date(now.getFullYear(), now.getMonth() - 4, 1);

  els.year.value = String(now.getFullYear());
  els.startMonth.value = formatInputMonth(startMonth);
  els.endMonth.value = formatInputMonth(endMonth);

  // 每日肠内营养默认最近7天
  const today = new Date();
  const weekAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);
  if (els.dailyEndDate) els.dailyEndDate.value = formatInputDate(today);
  if (els.dailyStartDate) els.dailyStartDate.value = formatInputDate(weekAgo);
}

function formatInputDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatInputMonth(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

window.addEventListener('DOMContentLoaded', () => {
  initializeDefaultFilters();
  void handleQuery('range');
});

// ── 时间输入框 showPicker() 事件委托 ──────────────────
// 点击框内任意位置（而非仅日历图标）弹出原生选择器
document.addEventListener('click', (e) => {
  const input = e.target.closest(
    'input[type="date"], input[type="month"], input[type="time"], input[type="datetime-local"], input[type="week"]'
  );
  if (input && typeof input.showPicker === 'function') {
    try { input.showPicker(); } catch (err) { /* 已弹出或浏览器不支持时静默忽略 */ }
  }
});
