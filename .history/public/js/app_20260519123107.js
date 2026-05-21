const API_BASE = '/api/stats';

const els = {
  btnTabQuality: document.getElementById('btnTabQuality'),
  btnTabDrg: document.getElementById('btnTabDrg'),
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
};

const statsTable = document.getElementById('statsTable');

let activeView = 'quality';
let lastDrgResult = null;
let lastDrgQuery = null;
let lastDrgStatus = { text: '请选择条件后查询', isError: false };
let lastQualityResult = null;
let lastQualityQuery = null;
let lastQualityStatus = { text: '请选择条件后查询', isError: false };
let lastDetail = null;
let lastDetailMeta = null;

els.btnTabQuality.addEventListener('click', () => switchView('quality'));
els.btnTabDrg.addEventListener('click', () => switchView('drg'));
els.btnYearQuery.addEventListener('click', () => handleQuery('year'));
els.btnRangeQuery.addEventListener('click', () => handleQuery('range'));
els.btnExport.addEventListener('click', exportCurrentTable);

[els.btnCloseDetail, els.btnOkDetail, els.btnCancelDetail].forEach(btn => {
  btn.addEventListener('click', closeDetailModal);
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
      <td class="quality-name-cell">${escapeHtml(row.name)}</td>
      ${renderQualityValueCell(row, 'ratio', row.ratio)}
      ${renderQualityValueCell(row, 'numerator', row.numerator)}
      ${renderQualityValueCell(row, 'denominator', row.denominator)}
      ${months.map(month => renderQualityMonthCell(row, month)).join('')}
    </tr>
  `).join('');

  els.tableBody.querySelectorAll('.quality-detail-trigger').forEach(cell => {
    cell.addEventListener('click', () => handleQualityCellClick(cell.dataset.key, cell.dataset.field));
  });
}

function renderQualityValueCell(row, field, value) {
  void row;
  void field;
  return `<td>${escapeHtml(value ?? '')}</td>`;
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

async function openQualityDetail(indicatorKey, startMonth, endMonth, itemOrder = '') {
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
    },
    onLoaded: detail => ({
      title: `${detail.indicator?.name || '指标详情'} - 统计详情`,
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
    els.detailTitle.textContent = ui.title;
    els.detailStatus.textContent = ui.status;
    els.btnExportDetail.disabled = !detail.rows.length || Boolean(lastDetailMeta?.disableExport);
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
      if (!target) return;
      await openQualityDetail(target, startMonth, endMonth, itemOrder);
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

function closeDetailModal() {
  els.detailModal.classList.add('hidden');
  document.body.classList.remove('modal-open');
}

function exportCurrentTable() {
  if (activeView === 'quality') {
    if (!lastQualityResult?.indicators?.length) return;
    const csv = toQualityCsv(lastQualityResult.months || [], lastQualityResult.indicators);
    downloadBlob(`\ufeff${csv}`, `quality-stats-${Date.now()}.csv`, 'text/csv;charset=utf-8;');
    return;
  }

  if (!lastDrgResult?.data?.length) return;
  const csv = toDrgCsv(lastDrgResult.months, lastDrgResult.data);
  downloadBlob(`\ufeff${csv}`, `icu-stats-${Date.now()}.csv`, 'text/csv;charset=utf-8;');
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
  const header = exportColumns.map(col => col.title);
  const body = exportRows.map(row => exportColumns.map(col => row[col.key] ?? ''));
  const metaRows = [
    [title],
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
    Title: title,
    Subject: rangeText,
    Author: 'ICU重症医学科指标统计',
    CreatedDate: new Date(),
  };
  XLSX.utils.book_append_sheet(wb, ws, sanitizeSheetName(indicatorName));
  XLSX.writeFile(wb, `${sanitizeFileName(indicatorName)}-${meta.startMonth}_${meta.endMonth}.xlsx`);
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

function formatMonthLabel(monthValue) {
  const [year, month] = String(monthValue).split('-');
  if (!year || !month) return monthValue;
  return `${year}年${Number(month)}月`;
}

function initializeDefaultFilters() {
  const now = new Date();
  const endMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const startMonth = new Date(now.getFullYear(), now.getMonth() - 2, 1);

  els.year.value = String(now.getFullYear());
  els.startMonth.value = formatInputMonth(startMonth);
  els.endMonth.value = formatInputMonth(endMonth);
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
