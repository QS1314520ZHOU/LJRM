(function () {
    'use strict';

    // ============ 配置 ============
    var API_BASE = '/api/blood-sugar/patient';
    var SMARTCARE_ALLOWED_ORIGINS = [
        window.location.origin,
        'http://localhost:3000',
        'http://127.0.0.1:3000'
    ];

    // ============ 状态 ============
    var currentPatientId = null;
    var currentPatient = null;
    var currentRange = null;
    var currentAbortController = null;
    var requestVersion = 0;
    var handshakeRetryTimer = null;
    var handshakeAttempts = 0;
    var MAX_HANDSHAKE_ATTEMPTS = 20;
    var debounceTimer = null;

    // ============ DOM 元素 ============
    var $loading = document.getElementById('loading');
    var $error = document.getElementById('error');
    var $empty = document.getElementById('empty');
    var $tableContainer = document.getElementById('tableContainer');
    var $patientInfo = document.getElementById('patientInfo');
    var $patientName = document.getElementById('patientName');
    var $patientMrn = document.getElementById('patientMrn');
    var $patientBed = document.getElementById('patientBed');
    var $patientGender = document.getElementById('patientGender');
    var $patientAge = document.getElementById('patientAge');
    var $patientAdmissionTime = document.getElementById('patientAdmissionTime');
    var $patientDischargeTime = document.getElementById('patientDischargeTime');
    var $patientStatus = document.getElementById('patientStatus');
    var $tableBody = document.getElementById('tableBody');
    var $btnExport = document.getElementById('btnExport');
    var $btnRefresh = document.getElementById('btnRefresh');
    var $btnPreviousRange = document.getElementById('btnPreviousRange');
    var $btnNextRange = document.getElementById('btnNextRange');
    var $startTime = document.getElementById('startTime');
    var $endTime = document.getElementById('endTime');
    var $rangeStatus = document.getElementById('rangeStatus');
    var $steroidDialog = document.getElementById('steroidDialog');
    var $btnCloseSteroidDialog = document.getElementById('btnCloseSteroidDialog');
    var $btnConfirmSteroidDialog = document.getElementById('btnConfirmSteroidDialog');
    var $steroidDetailBody = document.getElementById('steroidDetailBody');

    // ============ 初始化 ============
    function init() {
        $btnExport.addEventListener('click', exportExcel);
        $btnRefresh.addEventListener('click', onRefresh);
        $btnPreviousRange.addEventListener('click', function () { shiftRange(-1); });
        $btnNextRange.addEventListener('click', function () { shiftRange(1); });

        $startTime.addEventListener('change', onTimeInputChange);
        $endTime.addEventListener('change', onTimeInputChange);

        $btnCloseSteroidDialog.addEventListener('click', closeSteroidDialog);
        $btnConfirmSteroidDialog.addEventListener('click', closeSteroidDialog);
        $steroidDialog.addEventListener('click', function (e) {
            if (e.target === $steroidDialog) closeSteroidDialog();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !$steroidDialog.hidden) closeSteroidDialog();
        });

        window.addEventListener('message', onMessage);
        document.addEventListener('visibilitychange', onVisibilityChange);
        window.addEventListener('pageshow', onVisibilityChange);

        startHandshake();
    }

    // ============ postMessage 握手 ============
    function startHandshake() {
        handshakeAttempts = 0;
        sendReadyMessage();
    }

    function sendReadyMessage() {
        if (handshakeRetryTimer) clearTimeout(handshakeRetryTimer);

        window.parent.postMessage({
            type: 'SmartCare-form-ready',
            form: 'bloodSugar'
        }, '*');

        handshakeAttempts++;
        if (handshakeAttempts < MAX_HANDSHAKE_ATTEMPTS) {
            handshakeRetryTimer = setTimeout(sendReadyMessage, 200);
        }
    }

    function onVisibilityChange() {
        if (document.hidden) return;
        sendReadyMessage();
        if (currentPatientId) {
            fetchPatientData(currentPatientId, currentRange);
        }
    }

    function onMessage(event) {
        if (!isAllowedOrigin(event.origin)) return;

        var data = event.data;
        if (!data || typeof data !== 'object') return;

        var type = data.type;
        if (type === 'SmartCare' || type === 'SmartCare-patient-info' || type === 'SmartCare-set-patient') {
            var patient = data.patient || data;
            if (!patient) return;

            var pid = extractPatientId(patient);
            if (!pid) return;

            if (handshakeRetryTimer) {
                clearTimeout(handshakeRetryTimer);
                handshakeRetryTimer = null;
            }

            onPatientReceived(pid, patient);
        }
    }

    function isAllowedOrigin(origin) {
        if (!origin) return true;
        for (var i = 0; i < SMARTCARE_ALLOWED_ORIGINS.length; i++) {
            if (SMARTCARE_ALLOWED_ORIGINS[i] === origin) return true;
        }
        if (origin === window.location.origin) return true;
        return false;
    }

    function extractPatientId(data) {
        var patient = data.patient || data;
        if (!patient) return null;

        var id = patient.id || patient._id || patient.pid || patient.patientId || patient.patientID;
        if (!id && patient.patient) {
            id = patient.patient.id || patient.patient._id;
        }
        if (id === undefined || id === null) return null;

        var strId = String(id).trim();
        if (strId === '[object Object]') return null;
        return strId;
    }

    // ============ 患者切换 ============
    function onPatientReceived(pid, patientData) {
        if (currentPatientId === pid) {
            // Same patient - update context and refresh
            updatePatientContext(patientData);
            var newRange = resolveDefaultRange(currentPatient);
            if (rangeChanged(currentRange, newRange)) {
                currentRange = newRange;
                updateRangeInputs(newRange);
            }
            fetchPatientData(pid, currentRange);
            return;
        }

        // New patient
        if (currentAbortController) currentAbortController.abort();
        requestVersion++;
        currentPatientId = pid;
        currentPatient = buildPatientContext(patientData);
        currentRange = resolveDefaultRange(currentPatient);
        updateRangeInputs(currentRange);
        clearTable();
        disableExport();
        renderPatientInfo(currentPatient);
        fetchPatientData(pid, currentRange);
    }

    function buildPatientContext(data) {
        return {
            pid: currentPatientId,
            name: data.name || '',
            mrn: data.mrn || '',
            bedNo: data.bedNo || data.bed || '',
            gender: data.sex || data.gender || '',
            age: data.age || '',
            admissionTime: data.admissionTime || data.inTime || data.inIcuTime || data.icuInTime || data.enterTime || null,
            dischargeTime: data.dischargeTime || data.outTime || data.outIcuTime || data.icuOutTime || data.leaveTime || null,
            discharged: isDischarged(data)
        };
    }

    function isDischarged(data) {
        if (data.dischargeTime || data.outTime || data.outIcuTime || data.icuOutTime || data.leaveTime) return true;
        var status = data.status || data.patientStatus || '';
        var dischargedValues = ['discharged', '已出科', '出科', '转出', '已转出'];
        for (var i = 0; i < dischargedValues.length; i++) {
            if (String(status).toLowerCase() === dischargedValues[i]) return true;
        }
        return false;
    }

    function updatePatientContext(data) {
        if (!currentPatient) return;
        currentPatient.name = data.name || currentPatient.name;
        currentPatient.mrn = data.mrn || currentPatient.mrn;
        currentPatient.bedNo = data.bedNo || data.bed || currentPatient.bedNo;
        currentPatient.gender = data.sex || data.gender || currentPatient.gender;
        currentPatient.age = data.age || currentPatient.age;
        currentPatient.admissionTime = data.admissionTime || data.inTime || data.inIcuTime || data.icuInTime || data.enterTime || currentPatient.admissionTime;
        currentPatient.dischargeTime = data.dischargeTime || data.outTime || data.outIcuTime || data.icuOutTime || data.leaveTime || currentPatient.dischargeTime;
        currentPatient.discharged = isDischarged(data) || currentPatient.discharged;
        renderPatientInfo(currentPatient);
    }

    function rangeChanged(r1, r2) {
        if (!r1 || !r2) return true;
        return r1.startTime !== r2.startTime || r1.endTime !== r2.endTime;
    }

    // ============ 默认时间范围 ============
    function resolveDefaultRange(patient) {
        if (patient && patient.discharged && patient.admissionTime && patient.dischargeTime) {
            return {
                startTime: patient.admissionTime,
                endTime: patient.dischargeTime,
                defaultReason: 'DISCHARGED_STAY'
            };
        }
        return getCurrentNursingRange();
    }

    function getCurrentNursingRange() {
        var now = new Date();
        var shanghaiNow = getShanghaiDate(now);
        var hour = shanghaiNow.getHours();

        var start, end;
        if (hour >= 8) {
            start = makeShanghaiDate(shanghaiNow.getFullYear(), shanghaiNow.getMonth(), shanghaiNow.getDate(), 8, 0);
            end = makeShanghaiDate(shanghaiNow.getFullYear(), shanghaiNow.getMonth(), shanghaiNow.getDate() + 1, 8, 0);
        } else {
            start = makeShanghaiDate(shanghaiNow.getFullYear(), shanghaiNow.getMonth(), shanghaiNow.getDate() - 1, 8, 0);
            end = makeShanghaiDate(shanghaiNow.getFullYear(), shanghaiNow.getMonth(), shanghaiNow.getDate(), 8, 0);
        }

        return {
            startTime: toIsoString(start),
            endTime: toIsoString(end),
            defaultReason: 'CURRENT_NURSING_DAY'
        };
    }

    function getShanghaiDate(date) {
        var offset = 8 * 60; // Shanghai is UTC+8
        var utc = date.getTime() + date.getTimezoneOffset() * 60000;
        return new Date(utc + offset * 60000);
    }

    function makeShanghaiDate(year, month, day, hour, minute) {
        // Create date in Shanghai timezone
        var date = new Date(year, month, day, hour, minute, 0, 0);
        // Adjust to UTC
        var offset = 8 * 60;
        var utc = date.getTime() - offset * 60000;
        return new Date(utc - date.getTimezoneOffset() * 60000);
    }

    function toIsoString(date) {
        return date.toISOString();
    }

    function formatShanghaiDateTime(isoStr) {
        if (!isoStr) return '--';
        var date = new Date(isoStr);
        var shanghai = getShanghaiDate(date);
        var y = shanghai.getFullYear();
        var m = String(shanghai.getMonth() + 1).padStart(2, '0');
        var d = String(shanghai.getDate()).padStart(2, '0');
        var h = String(shanghai.getHours()).padStart(2, '0');
        var min = String(shanghai.getMinutes()).padStart(2, '0');
        return y + '-' + m + '-' + d + ' ' + h + ':' + min;
    }

    function isoToInputValue(isoStr) {
        if (!isoStr) return '';
        var date = new Date(isoStr);
        var shanghai = getShanghaiDate(date);
        var y = shanghai.getFullYear();
        var m = String(shanghai.getMonth() + 1).padStart(2, '0');
        var d = String(shanghai.getDate()).padStart(2, '0');
        var h = String(shanghai.getHours()).padStart(2, '0');
        var min = String(shanghai.getMinutes()).padStart(2, '0');
        return y + '-' + m + '-' + d + 'T' + h + ':' + min;
    }

    function inputValueToIso(value) {
        if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return null;
        var parsed = new Date(value + ':00+08:00');
        if (isNaN(parsed.getTime())) return null;
        return parsed.toISOString();
    }

    // ============ 时间范围操作 ============
    function updateRangeInputs(range) {
        if (!range) return;
        $startTime.value = isoToInputValue(range.startTime);
        $endTime.value = isoToInputValue(range.endTime);
        updateRangeStatus(range);
        updateArrowStates();
    }

    function updateRangeStatus(range) {
        if (!range) {
            $rangeStatus.textContent = '等待患者信息';
            return;
        }
        var text = formatShanghaiDateTime(range.startTime) + ' 至 ' + formatShanghaiDateTime(range.endTime);
        if (range.defaultReason === 'CURRENT_NURSING_DAY') {
            text += '（当前护理日）';
        }
        $rangeStatus.textContent = text;
    }

    function updateArrowStates() {
        if (!currentRange || !currentPatient) {
            $btnPreviousRange.disabled = true;
            $btnNextRange.disabled = true;
            return;
        }

        // Check if we can shift backward (don't go before admission time)
        var canBack = true;
        if (currentPatient.admissionTime) {
            var startShanghai = getShanghaiDate(new Date(currentRange.startTime));
            var admShanghai = getShanghaiDate(new Date(currentPatient.admissionTime));
            var backStart = new Date(startShanghai);
            backStart.setDate(backStart.getDate() - 1);
            canBack = backStart >= new Date(admShanghai.getFullYear(), admShanghai.getMonth(), admShanghai.getDate());
        }

        // Check if we can shift forward (don't go after discharge time)
        var canForward = true;
        if (currentPatient.dischargeTime) {
            var endShanghai = getShanghaiDate(new Date(currentRange.endTime));
            var disShanghai = getShanghaiDate(new Date(currentPatient.dischargeTime));
            var fwdEnd = new Date(endShanghai);
            fwdEnd.setDate(fwdEnd.getDate() + 1);
            canForward = fwdEnd <= new Date(disShanghai.getFullYear(), disShanghai.getMonth(), disShanghai.getDate() + 1);
        }

        $btnPreviousRange.disabled = !canBack;
        $btnNextRange.disabled = !canForward;
    }

    function shiftRange(days) {
        if (!currentRange) return;

        var startShanghai = getShanghaiDate(new Date(currentRange.startTime));
        var endShanghai = getShanghaiDate(new Date(currentRange.endTime));

        startShanghai.setDate(startShanghai.getDate() + days);
        endShanghai.setDate(endShanghai.getDate() + days);

        currentRange.startTime = toIsoString(startShanghai);
        currentRange.endTime = toIsoString(endShanghai);
        currentRange.defaultReason = 'REQUESTED_RANGE';

        updateRangeInputs(currentRange);
        fetchPatientData(currentPatientId, currentRange);
    }

    function onTimeInputChange() {
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function () {
            var startIso = inputValueToIso($startTime.value);
            var endIso = inputValueToIso($endTime.value);

            if (!startIso || !endIso) {
                showError('请输入有效的开始和结束时间');
                return;
            }

            if (new Date(startIso) >= new Date(endIso)) {
                showError('开始时间必须早于结束时间');
                return;
            }

            currentRange = {
                startTime: startIso,
                endTime: endIso,
                defaultReason: 'REQUESTED_RANGE'
            };
            updateRangeStatus(currentRange);
            updateArrowStates();
            fetchPatientData(currentPatientId, currentRange);
        }, 300);
    }

    // ============ 刷新 ============
    function onRefresh() {
        if (!currentPatientId || !currentRange) return;
        fetchPatientData(currentPatientId, currentRange);
    }

    // ============ 数据请求 ============
    function fetchPatientData(pid, range) {
        if (currentAbortController) currentAbortController.abort();
        var version = ++requestVersion;

        currentAbortController = new AbortController();

        showLoading();
        hideError();
        hideEmpty();
        hideTable();
        disableExport();

        var url = API_BASE + '/' + encodeURIComponent(pid);
        if (range && range.startTime && range.endTime) {
            url += '?startTime=' + encodeURIComponent(range.startTime) + '&endTime=' + encodeURIComponent(range.endTime);
        }

        fetch(url, { signal: currentAbortController.signal })
            .then(function (response) {
                if (!response.ok) {
                    return response.json().then(function (err) {
                        throw new Error(err.error || '请求失败: ' + response.status);
                    }).catch(function (e) {
                        if (e.message) throw e;
                        throw new Error('请求失败: ' + response.status);
                    });
                }
                return response.json();
            })
            .then(function (result) {
                if (requestVersion !== version) return;
                if (currentPatientId !== pid) return;

                hideLoading();

                var data = result.data;
                if (!data || !data.patient) {
                    showError('未找到患者信息');
                    return;
                }

                // Update range from response
                if (data.range) {
                    currentRange = data.range;
                    updateRangeInputs(currentRange);
                }

                renderPatientInfo(data.patient);

                if (!data.rows || data.rows.length === 0) {
                    showEmpty();
                } else {
                    renderTable(data.rows);
                    showTable();
                    enableExport();
                }
            })
            .catch(function (err) {
                if (err.name === 'AbortError') return;
                if (requestVersion !== version) return;
                if (currentPatientId !== pid) return;
                hideLoading();
                showError('血糖数据加载失败，请重试');
            });
    }

    // ============ 渲染 ============
    function renderPatientInfo(patient) {
        setTextContent($patientBed, patient.bedNo || '--');
        setTextContent($patientName, patient.name || '--');
        setTextContent($patientMrn, patient.mrn || '--');
        setTextContent($patientGender, patient.gender || '--');
        setTextContent($patientAge, patient.age || '--');
        setTextContent($patientAdmissionTime, formatShanghaiDateTime(patient.admissionTime));
        setTextContent($patientDischargeTime, formatShanghaiDateTime(patient.dischargeTime));
        setTextContent($patientStatus, patient.discharged ? '已出科' : '在科');
        $patientInfo.hidden = false;
    }

    function renderTable(rows) {
        while ($tableBody.firstChild) {
            $tableBody.removeChild($tableBody.firstChild);
        }

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var tr = document.createElement('tr');

            // 序号
            appendCell(tr, String(i + 1));
            // 血糖时间
            appendCell(tr, row.time || '--');
            // 血糖
            appendCell(tr, row.resultDisplay || (row.result != null ? String(row.result) : '--'));
            // 胰岛素
            appendCell(tr, row.insulin != null ? String(row.insulin) : '--');
            // 激素当量 (clickable)
            appendClickableSteroidCell(tr, row.steroidFactor, row.drugDetails);
            // 校正因子
            appendCell(tr, row.correctionFactor != null ? String(row.correctionFactor) : '--');
            // IRI
            appendIriCell(tr, row.iri);
            // 激素详情按钮
            appendDetailButtonCell(tr, row.drugDetails);

            $tableBody.appendChild(tr);
        }
    }

    function appendCell(tr, text) {
        var td = document.createElement('td');
        setTextContent(td, text);
        td.title = text;
        tr.appendChild(td);
    }

    function appendIriCell(tr, iri) {
        var td = document.createElement('td');
        var span = document.createElement('span');
        span.className = 'iri-value';
        var text = iri != null ? String(iri) : '--';
        setTextContent(span, text);
        td.appendChild(span);
        td.title = text;
        tr.appendChild(td);
    }

    function appendClickableSteroidCell(tr, steroidFactor, drugDetails) {
        var td = document.createElement('td');
        var text = steroidFactor != null ? String(steroidFactor) : '--';
        setTextContent(td, text);
        td.title = text;

        if (drugDetails && drugDetails.length > 0) {
            td.className = 'clickable-factor';
            td.tabIndex = 0;
            td.setAttribute('role', 'button');
            td.setAttribute('aria-label', '查看激素详情');
            td.addEventListener('click', function () {
                openSteroidDialog(drugDetails);
            });
            td.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    openSteroidDialog(drugDetails);
                }
            });
        }
        tr.appendChild(td);
    }

    function appendDetailButtonCell(tr, drugDetails) {
        var td = document.createElement('td');
        var btn = document.createElement('button');
        btn.className = 'detail-link';
        btn.textContent = '查看';
        btn.disabled = !drugDetails || drugDetails.length === 0;
        if (drugDetails && drugDetails.length > 0) {
            btn.addEventListener('click', function () {
                openSteroidDialog(drugDetails);
            });
        }
        td.appendChild(btn);
        tr.appendChild(td);
    }

    // ============ 激素详情弹窗 ============
    function openSteroidDialog(drugDetails) {
        while ($steroidDetailBody.firstChild) {
            $steroidDetailBody.removeChild($steroidDetailBody.firstChild);
        }

        if (!drugDetails || drugDetails.length === 0) {
            var tr = document.createElement('tr');
            var td = document.createElement('td');
            td.colSpan = 5;
            td.style.textAlign = 'center';
            td.style.color = '#64748b';
            setTextContent(td, '当前8-8窗口无目标激素记录');
            tr.appendChild(td);
            $steroidDetailBody.appendChild(tr);
        } else {
            for (var i = 0; i < drugDetails.length; i++) {
                var d = drugDetails[i];
                var tr = document.createElement('tr');
                appendCell(tr, d.time || '--');
                appendCell(tr, d.name || '--');
                appendCell(tr, d.dose != null ? String(d.dose) : '--');
                appendCell(tr, d.unit || '--');
                appendCell(tr, d.hydrocortisoneEquivalent != null ? String(d.hydrocortisoneEquivalent) : '--');
                $steroidDetailBody.appendChild(tr);
            }
        }

        $steroidDialog.hidden = false;
        $btnCloseSteroidDialog.focus();
    }

    function closeSteroidDialog() {
        $steroidDialog.hidden = true;
    }

    // ============ 导出 Excel ============
    function exportExcel() {
        if (typeof XLSX === 'undefined') {
            alert('Excel 导出组件未加载');
            return;
        }

        if (!currentPatient || !currentRange) return;

        // Build aoa data
        var aoa = [];

        // Header info
        aoa.push(['血糖 IRI 统计']);
        aoa.push([]);
        aoa.push(['床号', currentPatient.bedNo || '--', '姓名', currentPatient.name || '--']);
        aoa.push(['病案号', currentPatient.mrn || '--', '性别', currentPatient.gender || '--']);
        aoa.push(['年龄', currentPatient.age || '--', '患者状态', currentPatient.discharged ? '已出科' : '在科']);
        aoa.push(['入科时间', formatShanghaiDateTime(currentPatient.admissionTime), '出科时间', formatShanghaiDateTime(currentPatient.dischargeTime)]);
        aoa.push(['查询开始时间', formatShanghaiDateTime(currentRange.startTime), '查询结束时间', formatShanghaiDateTime(currentRange.endTime)]);
        aoa.push(['时区', 'Asia/Shanghai']);
        aoa.push([]);

        // Table header
        aoa.push(['序号', '血糖时间', '血糖（mmol/L）', '胰岛素（U）', '激素当量（mg）', '校正因子', 'IRI', '激素详情']);

        // Table data
        var rows = $tableBody.querySelectorAll('tr');
        for (var i = 0; i < rows.length; i++) {
            var cells = rows[i].querySelectorAll('td');
            var rowData = [];
            for (var j = 0; j < cells.length; j++) {
                rowData.push(cells[j].textContent || '');
            }
            aoa.push(rowData);
        }

        var ws = XLSX.utils.aoa_to_sheet(aoa);
        var wb = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(wb, ws, '血糖IRI统计');

        var fileName = '血糖IRI统计_' + (currentPatient.name || 'unknown') + '_' +
            formatShanghaiDateTime(currentRange.startTime).replace(/[:-]/g, '').replace(' ', '-') + '_' +
            formatShanghaiDateTime(currentRange.endTime).replace(/[:-]/g, '').replace(' ', '-') + '.xlsx';

        XLSX.writeFile(wb, fileName);
    }

    // ============ UI 辅助 ============
    function setTextContent(el, text) {
        el.textContent = text != null ? String(text) : '';
    }

    function showLoading() { $loading.hidden = false; }
    function hideLoading() { $loading.hidden = true; }
    function showError(msg) {
        setTextContent($error, msg || '发生错误');
        $error.hidden = false;
    }
    function hideError() { $error.hidden = true; }
    function showEmpty() { $empty.hidden = false; }
    function hideEmpty() { $empty.hidden = true; }
    function showTable() { $tableContainer.hidden = false; }
    function hideTable() { $tableContainer.hidden = true; }
    function clearTable() {
        while ($tableBody.firstChild) {
            $tableBody.removeChild($tableBody.firstChild);
        }
    }
    function enableExport() { $btnExport.disabled = false; }
    function disableExport() { $btnExport.disabled = true; }

    // ============ 启动 ============
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
