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
    var currentAbortController = null;
    var handshakeRetryTimer = null;
    var handshakeAttempts = 0;
    var MAX_HANDSHAKE_ATTEMPTS = 20; // ~4 seconds at 200ms interval

    // ============ DOM 元素 ============
    var $loading = document.getElementById('loading');
    var $error = document.getElementById('error');
    var $empty = document.getElementById('empty');
    var $content = document.getElementById('content');
    var $patientInfo = document.getElementById('patientInfo');
    var $patientName = document.getElementById('patientName');
    var $patientMrn = document.getElementById('patientMrn');
    var $patientBed = document.getElementById('patientBed');
    var $patientGender = document.getElementById('patientGender');
    var $patientAge = document.getElementById('patientAge');
    var $tableBody = document.getElementById('tableBody');
    var $btnExport = document.getElementById('btnExport');

    // ============ 初始化 ============
    function init() {
        // 绑定导出按钮
        $btnExport.addEventListener('click', exportExcel);

        // 监听 SmartCare postMessage
        window.addEventListener('message', onMessage);

        // 开始握手
        startHandshake();
    }

    // ============ postMessage 握手 ============
    function startHandshake() {
        handshakeAttempts = 0;
        sendReadyMessage();
    }

    function sendReadyMessage() {
        if (handshakeRetryTimer) {
            clearTimeout(handshakeRetryTimer);
        }

        window.parent.postMessage({
            type: 'SmartCare-form-ready',
            form: 'bloodSugar'
        }, '*');

        handshakeAttempts++;
        if (handshakeAttempts < MAX_HANDSHAKE_ATTEMPTS) {
            handshakeRetryTimer = setTimeout(sendReadyMessage, 200);
        }
    }

    function onMessage(event) {
        // 安全检查：验证来源
        if (!isAllowedOrigin(event.origin)) {
            return;
        }

        var data = event.data;
        if (!data || typeof data !== 'object') return;

        // 处理 SmartCare 发送的患者信息
        if (data.type === 'SmartCare-patient-info' || data.type === 'SmartCare-set-patient') {
            var patientId = extractPatientId(data);
            if (patientId) {
                // 停止握手重试
                if (handshakeRetryTimer) {
                    clearTimeout(handshakeRetryTimer);
                    handshakeRetryTimer = null;
                }
                onPatientChange(patientId);
            }
        }
    }

    function isAllowedOrigin(origin) {
        if (!origin) return true; // 同源可能没有 origin
        for (var i = 0; i < SMARTCARE_ALLOWED_ORIGINS.length; i++) {
            if (SMARTCARE_ALLOWED_ORIGINS[i] === origin) return true;
        }
        // 允许同源
        if (origin === window.location.origin) return true;
        return false;
    }

    function extractPatientId(data) {
        var patient = data.patient || data;
        if (!patient) return null;

        // 优先级：id > _id > pid
        var id = patient.id || patient._id || patient.pid;
        if (id === undefined || id === null) return null;

        // 转为字符串并 trim
        return String(id).trim();
    }

    // ============ 患者切换 ============
    function onPatientChange(pid) {
        if (!pid || pid === currentPatientId) return;

        // 取消上一个请求
        if (currentAbortController) {
            currentAbortController.abort();
        }

        currentPatientId = pid;
        fetchPatientData(pid);
    }

    // ============ 数据请求 ============
    function fetchPatientData(pid) {
        currentAbortController = new AbortController();

        showLoading();
        hideError();
        hideEmpty();
        hideContent();

        var url = API_BASE + '/' + encodeURIComponent(pid);

        fetch(url, { signal: currentAbortController.signal })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('请求失败: ' + response.status);
                }
                return response.json();
            })
            .then(function (result) {
                if (currentPatientId !== pid) return; // 患者已切换，忽略
                hideLoading();

                var data = result.data;
                if (!data || !data.patient) {
                    showError('未找到患者信息');
                    return;
                }

                renderPatientInfo(data.patient);

                if (!data.rows || data.rows.length === 0) {
                    showEmpty();
                } else {
                    renderTable(data.rows);
                    showContent();
                }
            })
            .catch(function (err) {
                if (err.name === 'AbortError') return; // 请求被取消
                if (currentPatientId !== pid) return;
                hideLoading();
                showError('加载失败: ' + err.message);
            });
    }

    // ============ 渲染 ============
    function renderPatientInfo(patient) {
        setTextContent($patientName, '姓名: ' + (patient.name || '--'));
        setTextContent($patientMrn, '病案号: ' + (patient.mrn || '--'));
        setTextContent($patientBed, '床号: ' + (patient.bedNo || '--'));
        setTextContent($patientGender, '性别: ' + (patient.gender || '--'));
        setTextContent($patientAge, '年龄: ' + (patient.age || '--'));
        $patientInfo.style.display = 'flex';
    }

    function renderTable(rows) {
        // 清空表格
        while ($tableBody.firstChild) {
            $tableBody.removeChild($tableBody.firstChild);
        }

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var tr = document.createElement('tr');

            // 时间
            appendCell(tr, row.time || '--');
            // 血糖
            appendCell(tr, row.resultDisplay || (row.result != null ? String(row.result) : '--'));
            // 胰岛素
            appendCell(tr, row.insulin != null ? String(row.insulin) : '--');
            // 激素当量
            appendCell(tr, row.steroidFactor != null ? String(row.steroidFactor) : '--');
            // 校正因子
            appendCell(tr, row.correctionFactor != null ? String(row.correctionFactor) : '--');
            // IRI
            var iriTd = document.createElement('td');
            var iriSpan = document.createElement('span');
            iriSpan.className = 'iri-value';
            setTextContent(iriSpan, row.iri != null ? String(row.iri) : '--');
            iriTd.appendChild(iriSpan);
            tr.appendChild(iriTd);
            // 激素详情
            appendDrugDetailsCell(tr, row.drugDetails);

            $tableBody.appendChild(tr);
        }
    }

    function appendCell(tr, text) {
        var td = document.createElement('td');
        setTextContent(td, text);
        tr.appendChild(td);
    }

    function appendDrugDetailsCell(tr, drugDetails) {
        var td = document.createElement('td');
        if (drugDetails && drugDetails.length > 0) {
            var container = document.createElement('div');
            container.className = 'drug-detail';
            for (var i = 0; i < drugDetails.length; i++) {
                var d = drugDetails[i];
                var item = document.createElement('div');
                item.className = 'drug-detail-item';
                var text = d.time + ' ' + d.name + ' ' + (d.dose != null ? d.dose : '') + (d.unit || '');
                if (d.hydrocortisoneEquivalent != null) {
                    text += ' (等效: ' + d.hydrocortisoneEquivalent + 'mg)';
                }
                setTextContent(item, text);
                container.appendChild(item);
            }
            td.appendChild(container);
        } else {
            setTextContent(td, '--');
        }
        tr.appendChild(td);
    }

    // ============ 导出 Excel ============
    function exportExcel() {
        if (typeof XLSX === 'undefined') {
            alert('Excel 导出组件未加载');
            return;
        }

        var table = document.getElementById('dataTable');
        if (!table) return;

        var wb = XLSX.utils.table_to_book(table, { sheet: '血糖IRI统计' });
        var fileName = '血糖IRI统计_' + (currentPatientId || 'unknown') + '.xlsx';
        XLSX.writeFile(wb, fileName);
    }

    // ============ UI 辅助 ============
    function setTextContent(el, text) {
        // 安全：使用 textContent 防止 XSS
        el.textContent = text != null ? String(text) : '';
    }

    function showLoading() { $loading.style.display = 'block'; }
    function hideLoading() { $loading.style.display = 'none'; }
    function showError(msg) {
        setTextContent($error, msg || '发生错误');
        $error.style.display = 'block';
    }
    function hideError() { $error.style.display = 'none'; }
    function showEmpty() { $empty.style.display = 'block'; }
    function hideEmpty() { $empty.style.display = 'none'; }
    function showContent() { $content.style.display = 'block'; }
    function hideContent() { $content.style.display = 'none'; }

    // ============ 启动 ============
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
