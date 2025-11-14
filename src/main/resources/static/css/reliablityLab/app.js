// 当前设备ID
let currentDeviceId = '';

// 命令执行状态标志
let isExecutingCommand = false;
let executingCommandStatus = null;
// 暴露到window对象
window.window.currentExecutingCommand = null;

// 命令执行检查配置
const COMMAND_CHECK_CONFIG = {
    checkInterval: 5000      // 查询间隔：5秒（无超时限制，一直等待）
};

// 当前设备最新数据（用于获取 run_status 等信息）
// 直接使用window对象，确保跨文件同步
window.window.currentDeviceLatestData = null;

// 设备状态
const deviceState = {
    isRunning: false,
    currentTemp: 25.5,
    targetTemp: 25.0,
    currentHumidity: 60.0,
    targetHumidity: 60.0,
    tempPower: 0.0,
    humidityPower: 0.0,
    startTime: null,
    remainingTime: null
};

// DOM 元素
const elements = {
    monitorDatetime: document.getElementById('monitorDatetime'),
    menuDatetime: document.getElementById('menuDatetime'),
    constantDatetime: document.getElementById('constantDatetime'),
    constantStatus: document.getElementById('constantStatus'),
    programDatetime: document.getElementById('programDatetime'),
    programStatus: document.getElementById('programStatus'),
    currentTemp: document.getElementById('currentTemp'),
    targetTempDisplay: document.getElementById('targetTempDisplay'),
    tempPower: document.getElementById('tempPower'),
    currentHumidity: document.getElementById('currentHumidity'),
    targetHumidityDisplay: document.getElementById('targetHumidityDisplay'),
    humidityPower: document.getElementById('humidityPower'),
    runtime: document.getElementById('runtime'),
    remainingTime: document.getElementById('remainingTime'),
    settingsBtn: document.getElementById('settingsBtn'),
    chartBtn: document.getElementById('chartBtn'),
    runBtn: document.getElementById('runBtn'),
    programCurrentTemp: document.getElementById('programCurrentTemp'),
    programCurrentHumidity: document.getElementById('programCurrentHumidity'),
    programNumberDisplay: document.getElementById('programNumberDisplay'),
    totalSegmentsDisplay: document.getElementById('totalSegmentsDisplay'),
    programRuntime: document.getElementById('programRuntime'),
    programRemainingTime: document.getElementById('programRemainingTime'),
    runConfirmModal: document.getElementById('runConfirmModal'),
    modalTitle: document.getElementById('modalTitle'),
    modalNo: document.getElementById('modalNo'),
    modalYes: document.getElementById('modalYes'),
    valueInputModal: document.getElementById('valueInputModal'),
    valueInputLabel: document.getElementById('valueInputLabel'),
    valueInputRange: document.getElementById('valueInputRange'),
    valueInput: document.getElementById('valueInput')
};

// 数值输入对话框状态
let valueInputState = {
    currentTarget: null,
    currentValue: '',
    minValue: -200,
    maxValue: 400,
    decimalPlaces: 1
};

// 初始化
function init() {
    updateDateTime();
    bindEvents();
    startRuntimeCounter();
    setupToggleButtons();
    
    // 初始化返回主页按钮状态（一级页面显示，二级页面隐藏）
    const backHomeBtn = document.getElementById('backHomeBtn');
    const refreshDataBtn = document.getElementById('refreshDataBtn');
    const oeeAnalysisBtn = document.getElementById('oeeAnalysisBtn');
    const monitorTitle = document.getElementById('monitorTitle');
    const userInfo = document.getElementById('userInfo');
    const deviceMonitorPage = document.getElementById('deviceMonitorPage');
    const monitorHeader = document.querySelector('.monitor-header');
    const monitorDatetime = document.getElementById('monitorDatetime');
    
    if (backHomeBtn && deviceMonitorPage && deviceMonitorPage.classList.contains('active')) {
        backHomeBtn.style.display = 'flex';
        console.log('[初始化] 一级页面，显示返回主页按钮');
    }
    
    // 初始化刷新数据和OEE分析按钮状态（一级页面显示，二级和三级页面隐藏）
    if (refreshDataBtn && oeeAnalysisBtn && deviceMonitorPage && deviceMonitorPage.classList.contains('active')) {
        refreshDataBtn.style.display = 'inline-block';
        oeeAnalysisBtn.style.display = 'inline-block';
        console.log('[初始化] 一级页面，显示刷新数据和OEE分析按钮');
    }
    
    // 初始化监控系统标题状态（一级页面显示，二级和三级页面隐藏）
    if (monitorTitle && deviceMonitorPage && deviceMonitorPage.classList.contains('active')) {
        monitorTitle.style.display = 'block';
        console.log('[初始化] 一级页面，显示监控系统标题');
    }
    
    // 初始化用户信息状态（一级页面显示，二级和三级页面隐藏）
    if (userInfo && deviceMonitorPage && deviceMonitorPage.classList.contains('active')) {
        // 一级页面时，用户信息已经通过其他逻辑设置了显示状态
        console.log('[初始化] 一级页面，用户信息保持原有状态');
    }
    
    // 初始化 monitor-header 状态（一级页面显示，二级和三级页面隐藏）
    if (monitorHeader && deviceMonitorPage && deviceMonitorPage.classList.contains('active')) {
        monitorHeader.style.display = 'block';
        console.log('[初始化] 一级页面，显示 monitor-header');
    }
    
    // 初始化全局时间状态（一级页面显示）
    if (monitorDatetime && deviceMonitorPage && deviceMonitorPage.classList.contains('active')) {
        monitorDatetime.style.display = 'block';
        console.log('[初始化] 一级页面，显示全局时间');
    }
    
    // 每秒更新时间
    setInterval(updateDateTime, 1000);
    
    // 每3秒拉取一次最新数据库数据
    setInterval(fetchLatestData, 3000);
}

// 绑定事件
function bindEvents() {
    // 定值试验页面的运行按钮
    if (elements.runBtn) {
        elements.runBtn.addEventListener('click', handleRunButtonClick);
    }
    
    // 程式试验页面的运行按钮
    const programRunBtn = document.getElementById('programRunBtn');
    if (programRunBtn) {
        programRunBtn.addEventListener('click', handleRunButtonClick);
    }
    
    // 确认窗口按钮
    if (elements.modalNo) {
        elements.modalNo.addEventListener('click', hideRunConfirm);
    }
    if (elements.modalYes) {
        elements.modalYes.addEventListener('click', confirmRun);
    }
    
    // 设置按钮
    if (elements.settingsBtn) {
        elements.settingsBtn.addEventListener('click', () => {
            navigateTo('valueSettings');
        });
    }
    
    // 暂停按钮（原曲线按钮）
    if (elements.chartBtn) {
        elements.chartBtn.addEventListener('click', () => {
            handlePauseButtonClick();
        });
    }
    
    // 温度设定值点击
    if (elements.targetTempDisplay) {
        elements.targetTempDisplay.addEventListener('click', () => {
            showValueInputDialog('温度设定值', elements.targetTempDisplay, 'temp', -200, 400, 1);
        });
    }
    
    // 湿度设定值点击
    if (elements.targetHumidityDisplay) {
        elements.targetHumidityDisplay.addEventListener('click', () => {
            showValueInputDialog('湿度设定值', elements.targetHumidityDisplay, 'humidity', 0, 100, 1);
        });
    }
    
    // 程式号点击
    if (elements.programNumberDisplay) {
        elements.programNumberDisplay.addEventListener('click', () => {
            showValueInputDialog('程式号', elements.programNumberDisplay, 'program', 1, 120, 0);
        });
    }
    
    // 编辑按钮
    const editBtn = document.getElementById('editBtn');
    if (editBtn) {
        editBtn.addEventListener('click', () => {
            navigateTo('programEdit');
        });
    }
    
    // 程式暂停按钮（原曲线按钮）
    const programChartBtn = document.getElementById('programChartBtn');
    if (programChartBtn) {
        programChartBtn.addEventListener('click', () => {
            handlePauseButtonClick();
        });
    }
    
    // RTU/TCP 切换
    const rtuBtn = document.getElementById('rtuBtn');
    const tcpBtn = document.getElementById('tcpBtn');
    const rtuSettings = document.getElementById('rtuSettings');
    const tcpSettings = document.getElementById('tcpSettings');
    
    if (rtuBtn && tcpBtn) {
        rtuBtn.addEventListener('click', () => {
            rtuBtn.classList.add('active');
            tcpBtn.classList.remove('active');
            if (rtuSettings) rtuSettings.style.display = 'block';
            if (tcpSettings) tcpSettings.style.display = 'none';
        });
        tcpBtn.addEventListener('click', () => {
            tcpBtn.classList.add('active');
            rtuBtn.classList.remove('active');
            if (rtuSettings) rtuSettings.style.display = 'none';
            if (tcpSettings) tcpSettings.style.display = 'block';
        });
    }
    
    // 连接按钮
    const connectBtn = document.getElementById('connectBtn');
    if (connectBtn) {
        connectBtn.addEventListener('click', () => {
            alert('RTU连接功能待开发');
        });
    }
    
    // TCP连接按钮
    const tcpConnectBtn = document.getElementById('tcpConnectBtn');
    if (tcpConnectBtn) {
        tcpConnectBtn.addEventListener('click', () => {
            alert('TCP连接功能待开发');
        });
    }
    
    // 上传按钮
    const uploadBtn = document.getElementById('uploadBtn');
    if (uploadBtn) {
        uploadBtn.addEventListener('click', () => {
            alert('上传功能待开发');
        });
    }
}

// 设置切换按钮
function setupToggleButtons() {
    // 语言选择切换
    const langZh = document.getElementById('langZh');
    const langEn = document.getElementById('langEn');
    if (langZh && langEn) {
        langZh.addEventListener('click', () => {
            langZh.classList.add('active');
            langEn.classList.remove('active');
        });
        langEn.addEventListener('click', () => {
            langEn.classList.add('active');
            langZh.classList.remove('active');
        });
    }
    
    // 启动方式切换
    const startStop = document.getElementById('startStop');
    const startCold = document.getElementById('startCold');
    const startHot = document.getElementById('startHot');
    if (startStop && startCold && startHot) {
        [startStop, startCold, startHot].forEach(btn => {
            btn.addEventListener('click', () => {
                [startStop, startCold, startHot].forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
            });
        });
    }
    
    // 多台连接切换
    const multiNo = document.getElementById('multiNo');
    const multiYes = document.getElementById('multiYes');
    if (multiNo && multiYes) {
        multiNo.addEventListener('click', () => {
            multiNo.classList.add('active');
            multiYes.classList.remove('active');
        });
        multiYes.addEventListener('click', () => {
            multiYes.classList.add('active');
            multiNo.classList.remove('active');
        });
    }
    
    // 预约设置切换
    const appointmentOff = document.getElementById('appointmentOff');
    const appointmentOn = document.getElementById('appointmentOn');
    if (appointmentOff && appointmentOn) {
        appointmentOff.addEventListener('click', () => {
            appointmentOff.classList.add('active');
            appointmentOn.classList.remove('active');
        });
        appointmentOn.addEventListener('click', () => {
            appointmentOn.classList.add('active');
            appointmentOff.classList.remove('active');
        });
    }
    
    // 定时运行切换
    const timerOff = document.getElementById('timerOff');
    const timerOn = document.getElementById('timerOn');
    if (timerOff && timerOn) {
        timerOff.addEventListener('click', () => {
            timerOff.classList.add('active');
            timerOn.classList.remove('active');
        });
        timerOn.addEventListener('click', () => {
            timerOn.classList.add('active');
            timerOff.classList.remove('active');
        });
    }
}

// 处理运行按钮点击
function handleRunButtonClick() {
    // 如果正在执行命令，显示命令详情
    if (isExecutingCommand && window.window.currentExecutingCommand) {
        showExecutingCommandInfo();
        return;
    }
    
    // 否则显示运行确认窗口
    showRunConfirm();
}

// 显示正在执行的命令信息
function showExecutingCommandInfo() {
    if (!window.currentExecutingCommand) {
        showAlert('当前没有正在执行的命令', '提示', 'info');
        return;
    }
    
    const cmd = window.currentExecutingCommand;
    
    // 解析命令操作类型
    let actionText = '未知操作';
    if (cmd.set_run_status === '0') {
        actionText = '停止试验';
    } else if (cmd.set_run_status === '1') {
        actionText = '启动试验';
    } else if (cmd.set_run_status === '2') {
        actionText = '暂停试验';
    }
    
    // 解析试验类型
    let modeText = cmd.valueorprogram === '0' ? '程式试验' : '定值试验';
    
    // 计算等待时间
    let waitingTime = '';
    if (cmd.create_at) {
        try {
            const createTime = new Date(cmd.create_at);
            const now = new Date();
            const diffSeconds = Math.floor((now - createTime) / 1000);
            const minutes = Math.floor(diffSeconds / 60);
            const seconds = diffSeconds % 60;
            waitingTime = `已等待：${minutes}分${seconds}秒\n`;
        } catch (e) {
            // 忽略错误
        }
    }
    
    // 构造详细信息
    let details = `━━━━━━━━━━━━━━━━━━━━\n`;
    details += `⏳ 正在执行命令\n`;
    details += `━━━━━━━━━━━━━━━━━━━━\n\n`;
    details += `📋 命令ID：${cmd.id || '未知'}\n`;
    details += `🎯 操作类型：${actionText}\n`;
    details += `🔧 试验模式：${modeText}\n`;
    
    if (cmd.valueorprogram === '0') {
        // 程式试验
        if (cmd.set_program_number) {
            details += `📝 程式号：${cmd.set_program_number}\n`;
        }
        if (cmd.set_program_no) {
            details += `📝 设置程式号：${cmd.set_program_no}\n`;
        }
    } else {
        // 定值试验
        if (cmd.fixed_temp_set) {
            details += `🌡️ 设定温度：${cmd.fixed_temp_set}℃\n`;
        }
        if (cmd.fixed_hum_set) {
            details += `💧 设定湿度：${cmd.fixed_hum_set}%\n`;
        }
    }
    
    if (cmd.create_by) {
        details += `👤 创建者：${cmd.create_by}\n`;
    }
    if (cmd.create_at) {
        details += `⏰ 创建时间：${formatDateTime(cmd.create_at)}\n`;
    }
    if (waitingTime) {
        details += `⌛ ${waitingTime}`;
    }
    
    details += `\n━━━━━━━━━━━━━━━━━━━━\n`;
    details += `📊 状态检查：\n`;
    details += `• 查询间隔：每${COMMAND_CHECK_CONFIG.checkInterval/1000}秒\n`;
    details += `• 当前状态：等待设备执行\n`;
    details += `• 持续等待：无超时限制\n`;
    details += `━━━━━━━━━━━━━━━━━━━━\n\n`;
    details += `💡 提示：\n`;
    details += `系统会持续检查命令执行状态\n`;
    details += `如长时间未完成，请检查设备连接`;
    
    // 使用confirm代替alert，允许用户选择手动刷新
    const shouldRefresh = confirm(details + '\n\n是否立即刷新并重新检查命令状态？');
    
    if (shouldRefresh) {
        // 手动刷新数据并检查命令
        console.log('[用户操作] 手动刷新命令状态');
        fetchLatestData();
        // 给数据一点时间更新
        setTimeout(() => {
            const latestStatus = getCurrentDeviceRunStatus();
            if (latestStatus === executingCommandStatus) {
                // 命令已完成
                isExecutingCommand = false;
                const wasExecutingPause = (executingCommandStatus === '2');
                executingCommandStatus = null;
                
                if (cmd.id) {
                    markCommandAsFinished(cmd.id);
                }
                window.currentExecutingCommand = null;
                
                // 根据命令类型恢复按钮
                if (wasExecutingPause) {
                    updatePauseButtonNormal();
                } else {
                    const statusDisplay = getStatusDisplay(latestStatus);
                    updateRunButtons(statusDisplay);
                }
                
                showAlert('命令已执行完成！', '成功', 'success');
            } else {
                showAlert('命令仍在执行中，请稍后再试', '提示', 'info');
            }
        }, 500);
    }
}

// 格式化日期时间
function formatDateTime(dateTime) {
    if (!dateTime) return '--';
    if (typeof dateTime === 'string') {
        return dateTime.replace('T', ' ').split('.')[0];
    }
    return String(dateTime);
}

// 显示运行确认窗口
function showRunConfirm() {
    if (elements.runConfirmModal) {
        elements.runConfirmModal.style.display = 'block';

        // 根据当前状态决定确认窗口的标题和消息
        const currentRunStatus = getCurrentDeviceRunStatus();
        let confirmTitle = '确认操作';
        let confirmMessage = '';

        if (currentRunStatus === '0') {
            confirmTitle = '启动试验';
            confirmMessage = '确定要启动试验吗？<br><small>设备将开始运行试验程序，命令会自动检查执行状态。</small>';
        } else if (currentRunStatus === '1') {
            confirmTitle = '停止试验';
            confirmMessage = '确定要停止试验吗？<br><small>设备将立即停止当前试验，命令会自动检查执行状态。</small>';
        } else if (currentRunStatus === '2') {
            confirmTitle = '运行试验';
            confirmMessage = '当前试验处于暂停状态，确定要运行试验吗？<br><small>设备将从暂停状态恢复运行，命令会自动检查执行状态。</small>';
        } else {
            confirmMessage = '确定要执行此操作吗？<br><small>命令将发送至设备，执行后会自动检查执行状态。</small>';
        }

        if (elements.modalTitle) {
            elements.modalTitle.textContent = confirmTitle;
        }

        const modalMessage = document.getElementById('modalMessage');
        if (modalMessage) {
            modalMessage.innerHTML = confirmMessage;
        }
    }
}

// 隐藏运行确认窗口
function hideRunConfirm() {
    if (elements.runConfirmModal) {
        elements.runConfirmModal.style.display = 'none';
    }
}

// 确认运行/停止/继续
function confirmRun() {
    hideRunConfirm();

    // 添加点击动画效果
    const runBtn = elements.runBtn;
    const programRunBtn = document.getElementById('programRunBtn');
    const activeBtn = runBtn || programRunBtn;

    if (activeBtn) {
        activeBtn.classList.add('clicked');
        setTimeout(() => {
            activeBtn.classList.remove('clicked');
        }, 300);
    }

    // 检查是哪个页面的运行按钮
    const currentPage = document.querySelector('.page.active');
    const currentRunStatus = getCurrentDeviceRunStatus();
    const runMode = getCurrentDeviceRunMode();

    // 根据当前状态决定发送什么命令和目标状态
    let commandRunStatus;
    let targetStatusDisplay;

    if (currentRunStatus === '0') {
        // 当前停止，发送运行命令，目标状态为运行
        commandRunStatus = '1';
        targetStatusDisplay = getStatusDisplay('1'); // 运行状态
        
        // 如果是程式试验模式且在程式试验页面，检查是否设置了程式号
        if (runMode === '0' && currentPage && currentPage.id === 'programPage') {
            // 检查是否有临时设置的程式号（从reliabilityIndex.html中的tempProgramNumber）
            const hasModifiedProgramNumber = typeof window.isProgramNumberModified !== 'undefined' && window.isProgramNumberModified;
            
            if (!hasModifiedProgramNumber) {
                showAlert('请先设置程式号后再运行试验！', '提示', 'warning');
                return;
            }
        }
    } else if (currentRunStatus === '1') {
        // 当前运行，发送停止命令，目标状态为停止
        commandRunStatus = '0';
        targetStatusDisplay = getStatusDisplay('0'); // 停止状态
    } else if (currentRunStatus === '2') {
        // 当前暂停，发送继续命令，目标状态为运行
        commandRunStatus = '1';
        targetStatusDisplay = getStatusDisplay('1'); // 运行状态
    } else {
        // 默认发送运行命令，目标状态为运行
        commandRunStatus = '1';
        targetStatusDisplay = getStatusDisplay('1');
    }

    // 立即更新UI，给用户即时反馈
    updateUIForStatus(targetStatusDisplay, runMode);

    // 发送命令
    sendRunCommand(commandRunStatus, runMode);
}

// 根据状态立即更新UI显示
function updateUIForStatus(statusDisplay, runMode) {
    // 根据运行模式调整状态文本
    let finalStatusText = statusDisplay.statusText;
    if (runMode === '0' && statusDisplay.statusText === '停止') {
        finalStatusText = '程式停止';
    }

    // 检查当前活跃页面，只更新对应页面的状态
    const currentPage = document.querySelector('.page.active');

    if (currentPage && currentPage.id === 'constantPage') {
        // 更新定值试验页面的状态
        if (elements.constantStatus) {
            elements.constantStatus.textContent = finalStatusText;
            elements.constantStatus.classList.remove('running', 'paused');
            elements.constantStatus.classList.toggle('running', statusDisplay.statusText === '运行');
            elements.constantStatus.classList.toggle('paused', statusDisplay.statusText === '暂停');
        }
    } else if (currentPage && currentPage.id === 'programPage') {
        // 更新程式试验页面的状态
        if (elements.programStatus) {
            elements.programStatus.textContent = finalStatusText;
            elements.programStatus.classList.remove('running', 'paused');
            elements.programStatus.classList.toggle('running', statusDisplay.statusText === '运行');
            elements.programStatus.classList.toggle('paused', statusDisplay.statusText === '暂停');
        }
    }

    // 更新运行按钮
    updateRunButtons(statusDisplay);
}

// 切换运行状态
function toggleRun() {
    deviceState.isRunning = !deviceState.isRunning;
    
    if (deviceState.isRunning) {
        deviceState.startTime = new Date();
        elements.runBtn.textContent = '停止';
        elements.runBtn.classList.add('stopped');
        
        // 计算剩余时间（示例：设置2小时）
        deviceState.remainingTime = 2 * 60 * 60 * 1000; // 2小时
        
        // 更新状态显示
        updateTestStatus('试验运行');
    } else {
        deviceState.startTime = null;
        deviceState.remainingTime = null;
        elements.runBtn.textContent = '运行';
        elements.runBtn.classList.remove('stopped');
        
        // 停止时功率归零
        deviceState.tempPower = 0;
        deviceState.humidityPower = 0;
        if (elements.tempPower) elements.tempPower.textContent = '0.0%';
        if (elements.humidityPower) elements.humidityPower.textContent = '0.0%';
        
        // 更新状态显示
        updateTestStatus('试验停止');
    }
}

// 更新试验状态显示
function updateTestStatus(status) {
    if (elements.constantStatus) {
        elements.constantStatus.textContent = status;
        elements.constantStatus.classList.toggle('running', status === '试验运行');
    }
    if (elements.programStatus) {
        elements.programStatus.textContent = status;
        elements.programStatus.classList.toggle('running', status === '试验运行');
    }
}

// 显示数值输入对话框
function showValueInputDialog(label, targetElement, type, minValue, maxValue, decimalPlaces) {
    valueInputState.currentTarget = targetElement;
    valueInputState.currentValue = targetElement.textContent;
    valueInputState.minValue = minValue;
    valueInputState.maxValue = maxValue;
    valueInputState.decimalPlaces = decimalPlaces;
    
    if (elements.valueInputLabel) elements.valueInputLabel.textContent = label;
    if (elements.valueInputRange) elements.valueInputRange.textContent = `[${minValue} - ${maxValue}]`;
    if (elements.valueInput) {
        elements.valueInput.value = valueInputState.currentValue;
        // 聚焦到输入框并选中所有文本
        elements.valueInput.focus();
        elements.valueInput.select();
    }
    
    if (elements.valueInputModal) {
        elements.valueInputModal.style.display = 'block';
        bindKeypadEvents();
        bindKeyboardEvents();
    }
}

// 隐藏数值输入对话框
function hideValueInputDialog() {
    if (elements.valueInputModal) {
        elements.valueInputModal.style.display = 'none';
    }
}

// 绑定键盘事件
function bindKeyboardEvents() {
    if (!elements.valueInput) return;
    
    // 输入事件 - 实时验证
    elements.valueInput.addEventListener('input', (e) => {
        const value = e.target.value;
        
        // 只允许数字、小数点和负号
        if (!/^-?[\d.]*$/.test(value)) {
            e.target.value = valueInputState.currentValue;
            return;
        }
        
        // 限制小数点只能有一个
        const dotCount = (value.match(/\./g) || []).length;
        if (dotCount > 1) {
            e.target.value = valueInputState.currentValue;
            return;
        }
        
        // 限制小数位数
        if (value.includes('.')) {
            const decimalPart = value.split('.')[1];
            if (decimalPart && decimalPart.length > valueInputState.decimalPlaces) {
                e.target.value = valueInputState.currentValue;
                return;
            }
        }
        
        valueInputState.currentValue = value;
    });
    
    // 键盘事件
    elements.valueInput.addEventListener('keydown', (e) => {
        // Enter键确认
        if (e.key === 'Enter') {
            e.preventDefault();
            confirmValueInput();
        }
        // Escape键退出
        else if (e.key === 'Escape') {
            e.preventDefault();
            hideValueInputDialog();
        }
        // 只允许数字、小数点、负号、退格、删除、方向键等
        else if (!/^[0-9.-]$/.test(e.key) && 
                 !['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'].includes(e.key)) {
            e.preventDefault();
        }
    });
    
    // 失去焦点时验证
    elements.valueInput.addEventListener('blur', () => {
        validateAndFormatInput();
    });
}

// 验证并格式化输入
function validateAndFormatInput() {
    if (!elements.valueInput) return;
    
    // 检查是否是程式号输入，如果是则跳过验证（由 reliabilityIndex.html 处理）
    const modal = document.getElementById('valueInputModal');
    if (modal && modal.dataset.inputType === 'programNumber') {
        return;
    }
    
    let value = elements.valueInput.value.trim();
    
    // 如果为空，保持原值
    if (value === '') {
        elements.valueInput.value = valueInputState.currentValue;
        return;
    }
    
    // 验证数值范围
    const numValue = parseFloat(value);
    if (isNaN(numValue)) {
        elements.valueInput.value = valueInputState.currentValue;
        return;
    }
    
    // 检查范围
    if (numValue < valueInputState.minValue || numValue > valueInputState.maxValue) {
        showAlert(`请输入${valueInputState.minValue}到${valueInputState.maxValue}之间的有效数值`, '输入错误', 'warning');
        elements.valueInput.value = valueInputState.currentValue;
        return;
    }
    
    // 格式化显示
    if (valueInputState.decimalPlaces === 0) {
        elements.valueInput.value = Math.round(numValue).toString();
    } else {
        elements.valueInput.value = numValue.toFixed(valueInputState.decimalPlaces);
    }
    
    valueInputState.currentValue = elements.valueInput.value;
}

// 绑定键盘事件
function bindKeypadEvents() {
    // 数字按钮
    document.querySelectorAll('.keypad-btn[data-value]').forEach(btn => {
        btn.onclick = () => {
            const value = btn.getAttribute('data-value');
            if (value === '.') {
                if (!valueInputState.currentValue.includes('.')) {
                    valueInputState.currentValue += '.';
                }
            } else {
                valueInputState.currentValue += value;
            }
            if (elements.valueInput) elements.valueInput.value = valueInputState.currentValue;
        };
    });
    
    // 功能按钮
    const signBtn = document.getElementById('keypadSign');
    const backspaceBtn = document.getElementById('keypadBackspace');
    const clearBtn = document.getElementById('keypadClear');
    const exitBtn = document.getElementById('keypadExit');
    const confirmBtn = document.getElementById('keypadConfirm');
    
    if (signBtn) {
        signBtn.onclick = () => {
            if (valueInputState.currentValue.startsWith('-')) {
                valueInputState.currentValue = valueInputState.currentValue.substring(1);
            } else if (valueInputState.currentValue !== '') {
                valueInputState.currentValue = '-' + valueInputState.currentValue;
            }
            if (elements.valueInput) elements.valueInput.value = valueInputState.currentValue;
        };
    }
    
    if (backspaceBtn) {
        backspaceBtn.onclick = () => {
            valueInputState.currentValue = valueInputState.currentValue.slice(0, -1);
            if (elements.valueInput) elements.valueInput.value = valueInputState.currentValue;
        };
    }
    
    if (clearBtn) {
        clearBtn.onclick = () => {
            valueInputState.currentValue = '';
            if (elements.valueInput) elements.valueInput.value = '';
        };
    }
    
    if (exitBtn) {
        exitBtn.onclick = () => {
            hideValueInputDialog();
        };
    }
    
    if (confirmBtn) {
        confirmBtn.onclick = () => {
            confirmValueInput();
        };
    }
}

// 确认数值输入
function confirmValueInput() {
    // 检查是否是程式号输入，如果是则由 reliabilityIndex.html 处理，这里跳过
    const modal = document.getElementById('valueInputModal');
    if (modal && modal.dataset.inputType === 'programNumber') {
        console.log('[数值输入] 程式号输入由 reliabilityIndex.html 处理，跳过 app.js 验证');
        return;
    }
    
    // 先验证并格式化输入
    validateAndFormatInput();
    
    const value = parseFloat(elements.valueInput.value);
    
    if (isNaN(value) || value < valueInputState.minValue || value > valueInputState.maxValue) {
        showAlert(`请输入${valueInputState.minValue}到${valueInputState.maxValue}之间的有效数值`, '输入错误', 'warning');
        return;
    }
    
    // 格式化数值（整数不补零，直接显示）
    const formattedValue = valueInputState.decimalPlaces === 0 ? 
        Math.round(value).toString() : 
        value.toFixed(valueInputState.decimalPlaces);
    
    // 更新显示
    if (valueInputState.currentTarget) {
        valueInputState.currentTarget.textContent = formattedValue;
        
        // 更新设备状态和临时存储
        if (valueInputState.currentTarget.id === 'targetTempDisplay') {
            deviceState.targetTemp = value;
            // 保存温度设定值的临时修改
            window.tempTargetTemp = value;
            window.isTargetTempModified = true;
            valueInputState.currentTarget.classList.add('modified');
            console.log('[定值设定] 温度设定值已临时修改为:', value);
        } else if (valueInputState.currentTarget.id === 'targetHumidityDisplay') {
            deviceState.targetHumidity = value;
            // 保存湿度设定值的临时修改
            window.tempTargetHumidity = value;
            window.isTargetHumidityModified = true;
            valueInputState.currentTarget.classList.add('modified');
            console.log('[定值设定] 湿度设定值已临时修改为:', value);
        }
    }
    
    hideValueInputDialog();
}

// 切换程式运行状态
function toggleProgramRun() {
    deviceState.isRunning = !deviceState.isRunning;
    
    if (deviceState.isRunning) {
        deviceState.startTime = new Date();
        const programRunBtn = document.getElementById('programRunBtn');
        if (programRunBtn) {
            programRunBtn.textContent = '停止';
            programRunBtn.classList.add('stopped');
        }
        
        // 计算剩余时间（示例：设置2小时）
        deviceState.remainingTime = 2 * 60 * 60 * 1000; // 2小时
        
        // 更新状态显示
        updateTestStatus('试验运行');
    } else {
        deviceState.startTime = null;
        deviceState.remainingTime = null;
        const programRunBtn = document.getElementById('programRunBtn');
        if (programRunBtn) {
            programRunBtn.textContent = '运行';
            programRunBtn.classList.remove('stopped');
        }
        
        // 停止时功率归零
        deviceState.tempPower = 0;
        deviceState.humidityPower = 0;
        const programTempPower = document.getElementById('programTempPower');
        const programHumidityPower = document.getElementById('programHumidityPower');
        if (programTempPower) programTempPower.textContent = '0.0%';
        if (programHumidityPower) programHumidityPower.textContent = '0.0%';
        
        // 更新状态显示
        updateTestStatus('试验停止');
    }
}

// 进入设备控制页面
function enterDevice(deviceId) {
    currentDeviceId = deviceId;
    console.log(`[进入设备] 设备ID: ${deviceId}`);
    
    // 隐藏返回主页按钮和整个 monitor-header（进入二级页面）
    const backHomeBtn = document.getElementById('backHomeBtn');
    const refreshDataBtn = document.getElementById('refreshDataBtn');
    const oeeAnalysisBtn = document.getElementById('oeeAnalysisBtn');
    const monitorTitle = document.getElementById('monitorTitle');
    const userInfo = document.getElementById('userInfo');
    const monitorHeader = document.querySelector('.monitor-header');
    const monitorDatetime = document.getElementById('monitorDatetime');
    
    if (backHomeBtn) {
        backHomeBtn.style.display = 'none';
        console.log('[进入设备] 已隐藏返回主页按钮');
    }
    
    // 隐藏刷新数据和OEE分析按钮
    if (refreshDataBtn && oeeAnalysisBtn) {
        refreshDataBtn.style.display = 'none';
        oeeAnalysisBtn.style.display = 'none';
        console.log('[进入设备] 已隐藏刷新数据和OEE分析按钮');
    }
    
    // 隐藏监控系统标题
    if (monitorTitle) {
        monitorTitle.style.display = 'none';
        console.log('[进入设备] 已隐藏监控系统标题');
    }
    
    // 隐藏用户信息
    if (userInfo) {
        userInfo.style.display = 'none';
        console.log('[进入设备] 已隐藏用户信息');
    }
    
    // 隐藏整个 monitor-header
    if (monitorHeader) {
        monitorHeader.style.display = 'none';
        console.log('[进入设备] 已隐藏 monitor-header');
    }
    
    // 隐藏全局时间（二级页面使用 menu-datetime）
    if (monitorDatetime) {
        monitorDatetime.style.display = 'none';
        console.log('[进入设备] 已隐藏全局时间');
    }
    
    // 先同步获取该设备的最新数据
    fetch(`/iot/data/latest?device_id=${encodeURIComponent(deviceId)}`, { cache: 'no-store' })
        .then(r => r.json())
        .then(d => {
            if (d) {
                window.currentDeviceLatestData = d;
                console.log(`[进入设备] 获取最新数据成功，run_mode: ${d.run_mode}, run_status: ${d.run_status}`);
            }
            
            // 然后导航到菜单页面
            navigateTo('menu');
            
            // 更新所有页面的设备ID显示
            updateDeviceIdDisplay(deviceId);
        })
        .catch(err => {
            console.error('[进入设备] 获取最新数据失败:', err);
            // 即使失败也继续导航
            navigateTo('menu');
            updateDeviceIdDisplay(deviceId);
        });
}

// 将enterDevice暴露到window对象（供reliabilityIndex.html调用）
window.enterDevice = enterDevice;

// 更新所有页面的设备ID显示
function updateDeviceIdDisplay(deviceId) {
    // 更新菜单页面的设备ID
    const menuDeviceIdElement = document.querySelector('#menuPage .device-id');
    if (menuDeviceIdElement) {
        menuDeviceIdElement.textContent = deviceId;
    }

    // 更新定值试验页面的设备ID
    const constantDeviceIdElement = document.getElementById('constantDeviceId');
    if (constantDeviceIdElement) {
        constantDeviceIdElement.textContent = deviceId;
    }

    // 更新程式试验页面的设备ID（如果需要的话）
    const programDeviceIdElement = document.getElementById('programDeviceId');
    if (programDeviceIdElement) {
        programDeviceIdElement.textContent = deviceId;
    }

    // 更新菜单按钮状态
    updateMenuButtons();

    // 更新试验状态文本
    updateTestStatusText();

    // 更新温湿度模块连接状态
    updateModuleConnectionStatus();
}

// 获取当前设备的运行模式
function getCurrentDeviceRunMode() {
    if (!currentDeviceId) return null;

    // 优先使用最新拉取的数据（app.js维护）
    if (window.currentDeviceLatestData && window.currentDeviceLatestData.run_mode !== undefined) {
        const mode = window.currentDeviceLatestData.run_mode;
        console.log(`[模式获取] 从 currentDeviceLatestData 获取 run_mode: ${mode} (类型: ${typeof mode})`);
        return String(mode); // 统一转换为字符串
    }

    // 回退：从 deviceList 获取（reliabilityIndex.html维护）
    if (typeof deviceList !== 'undefined') {
        for (let device of deviceList) {
            if (device.id === currentDeviceId) {
                // 从原始数据中获取run_mode
                const mode = device.raw ? device.raw.run_mode || device.raw.runMode : null;
                console.log(`[模式获取] 从 deviceList 获取 run_mode: ${mode} (类型: ${typeof mode})`);
                return String(mode); // 统一转换为字符串
            }
        }
    }
    
    console.warn(`[模式获取] 未找到设备 ${currentDeviceId} 的 run_mode`);
    return null;
}

// 获取当前设备的运行状态 (0=停止, 1=运行, 2=暂停)
function getCurrentDeviceRunStatus() {
    if (!currentDeviceId) return null;

    // 优先使用最新拉取的数据（app.js维护）
    if (window.currentDeviceLatestData && window.currentDeviceLatestData.run_status !== undefined) {
        const status = window.currentDeviceLatestData.run_status;
        console.log(`[状态获取] 从 currentDeviceLatestData 获取 run_status: ${status} (类型: ${typeof status})`);
        return String(status); // 统一转换为字符串
    }

    // 回退：从 deviceList 获取（reliabilityIndex.html维护）
    if (typeof deviceList !== 'undefined') {
        for (let device of deviceList) {
            if (device.id === currentDeviceId) {
                // 从原始数据中获取run_status
                const status = device.raw ? device.raw.run_status || device.raw.runStatus : null;
                console.log(`[状态获取] 从 deviceList 获取 run_status: ${status} (类型: ${typeof status})`);
                return String(status); // 统一转换为字符串
            }
        }
    }
    
    console.warn(`[状态获取] 未找到设备 ${currentDeviceId} 的 run_status`);
    return null;
}

// 更新菜单页面按钮状态
function updateMenuButtons() {
    const runMode = getCurrentDeviceRunMode();
    const runStatus = getCurrentDeviceRunStatus();
    
    console.log(`[菜单按钮] 更新菜单按钮状态，run_mode: ${runMode}, run_status: ${runStatus}`);

    // 获取菜单按钮
    const constantBtn = document.querySelector('.menu-item-yellow');
    const programBtn = document.querySelector('.menu-item-red');

    // 如果设备处于停止状态 (run_status === 0)，启用所有按钮
    if (runStatus === '0') {
        console.log('[菜单按钮] 设备停止状态 → 启用所有按钮');
        if (constantBtn) {
            constantBtn.disabled = false;
            constantBtn.classList.remove('disabled');
            constantBtn.title = '';
        }
        if (programBtn) {
            programBtn.disabled = false;
            programBtn.classList.remove('disabled');
            programBtn.title = '';
        }
    } else if (runMode === '0') {
        // 设备运行中 - 程式模式，禁用定值试验，启用程式试验
        console.log('[菜单按钮] 程式模式运行中 → 禁用定值试验，启用程式试验');
        if (constantBtn) {
            constantBtn.disabled = true;
            constantBtn.classList.add('disabled');
            constantBtn.title = '当前设备运行在程式模式，无法进入定值试验';
        }
        if (programBtn) {
            programBtn.disabled = false;
            programBtn.classList.remove('disabled');
            programBtn.title = '';
        }
    } else if (runMode === '1') {
        // 设备运行中 - 定值模式，禁用程式试验，启用定值试验
        console.log('[菜单按钮] 定值模式运行中 → 禁用程式试验，启用定值试验');
        if (constantBtn) {
            constantBtn.disabled = false;
            constantBtn.classList.remove('disabled');
            constantBtn.title = '';
        }
        if (programBtn) {
            programBtn.disabled = true;
            programBtn.classList.add('disabled');
            programBtn.title = '当前设备运行在定值模式，无法进入程式试验';
        }
    } else {
        // 未知模式或无数据 - 启用所有按钮
        console.warn(`[菜单按钮] 未知模式(${runMode}) → 启用所有按钮`);
        if (constantBtn) {
            constantBtn.disabled = false;
            constantBtn.classList.remove('disabled');
            constantBtn.title = '';
        }
        if (programBtn) {
            programBtn.disabled = false;
            programBtn.classList.remove('disabled');
            programBtn.title = '';
        }
    }
}

// 根据run_status获取状态文本和按钮文本
function getStatusDisplay(runStatus) {
    const statusValue = String(runStatus);

    switch (statusValue) {
        case '0':
            return { statusText: '停止', buttonText: '运行', buttonClass: '' };
        case '1':
            return { statusText: '运行', buttonText: '停止', buttonClass: 'stopped' };
        case '2':
            return { statusText: '暂停', buttonText: '运行', buttonClass: 'paused' };
        default:
            return { statusText: '未知', buttonText: '运行', buttonClass: '' };
    }
}

// 更新程式试验状态信息框
function updateProgramStatusBox() {
    // 获取当前设备的数据
    if (!currentDeviceId) return;

    // 从设备列表中获取当前设备的数据
    const currentDevice = deviceList.find(device => device.id === currentDeviceId);
    if (!currentDevice || !currentDevice.raw) return;

    const data = currentDevice.raw;

    // 更新程式号（直接显示整数，不补零）
    const programNumberElement = document.getElementById('programStatusNumber');
    if (programNumberElement && data.set_program_number != null) {
        programNumberElement.textContent = String(data.set_program_number);
    }

    // 更新段号 - 使用 running_step
    const segmentElement = document.getElementById('programStatusSegment');
    if (segmentElement) {
        if (data.running_step != null) {
            segmentElement.textContent = String(data.running_step).padStart(2, '0');
        }
    }

    // 更新程式循环 - program_cycles/program_total_cycles
    const cycleElement = document.getElementById('programStatusCycle');
    if (cycleElement) {
        if (data.program_cycles != null && data.program_total_cycles != null) {
            cycleElement.textContent = String(data.program_cycles).padStart(2, '0') + '/' + String(data.program_total_cycles).padStart(2, '0');
        }
    }

    // 更新段循环 - running_step/total_steps
    const segmentCycleElement = document.getElementById('programStatusSegmentCycle');
    if (segmentCycleElement) {
        if (data.running_step != null && data.total_steps != null) {
            segmentCycleElement.textContent = String(data.running_step).padStart(2, '0') + '/' + String(data.total_steps).padStart(2, '0');
        }
    }

    // 更新总段数（直接显示整数，不补零）
    const totalSegmentsElement = document.getElementById('programStatusTotalSegments');
    if (totalSegmentsElement && data.total_steps != null) {
        totalSegmentsElement.textContent = String(data.total_steps);
    }
}

// 更新试验状态显示文本
function updateTestStatusText() {
    const runMode = getCurrentDeviceRunMode();
    const runStatus = getCurrentDeviceRunStatus();
    const statusDisplay = getStatusDisplay(runStatus);

    // 根据运行模式调整状态文本
    let finalStatusText = statusDisplay.statusText;
    if (runMode === '0' && statusDisplay.statusText === '停止') {
        finalStatusText = '程式停止';
    }

    // 更新定值试验页面的状态
    if (elements.constantStatus) {
        elements.constantStatus.textContent = finalStatusText;
        elements.constantStatus.classList.toggle('running', statusDisplay.statusText === '运行');
        elements.constantStatus.classList.toggle('paused', statusDisplay.statusText === '暂停');
    }

    // 更新程式试验页面的状态
    if (elements.programStatus) {
        elements.programStatus.textContent = finalStatusText;
        elements.programStatus.classList.toggle('running', statusDisplay.statusText === '运行');
        elements.programStatus.classList.toggle('paused', statusDisplay.statusText === '暂停');
    }

    // 控制程式试验状态信息框的显示/隐藏
    const programStatusBox = document.getElementById('programStatusBox');
    if (programStatusBox) {
        // 当试验运行或暂停时显示状态框，停止时隐藏
        if (statusDisplay.statusText === '运行' || statusDisplay.statusText === '暂停') {
            programStatusBox.style.display = 'block';
            // 更新状态框数据
            updateProgramStatusBox();
        } else {
            programStatusBox.style.display = 'none';
        }
    }

    // 更新运行按钮
    updateRunButtons(statusDisplay);
}

// 获取当前设备的运行状态
function getCurrentDeviceRunStatus() {
    if (!currentDeviceId) return null;

    // 优先使用最新拉取的数据（app.js维护）
    if (window.currentDeviceLatestData && window.currentDeviceLatestData.run_status !== undefined) {
        const status = window.currentDeviceLatestData.run_status;
        console.log(`[状态获取] 从 currentDeviceLatestData 获取 run_status: ${status}`);
        return status;
    }

    // 回退：从 deviceList 获取（reliabilityIndex.html维护）
    if (typeof deviceList !== 'undefined') {
        for (let device of deviceList) {
            if (device.id === currentDeviceId) {
                // 从原始数据中获取run_status
                const status = device.raw ? device.raw.run_status || device.raw.runStatus : null;
                console.log(`[状态获取] 从 deviceList 获取 run_status: ${status}`);
                return status;
            }
        }
    }
    
    console.warn(`[状态获取] 未找到设备 ${currentDeviceId} 的 run_status`);
    return null;
}

// 处理暂停按钮点击
function handlePauseButtonClick() {
    // 如果正在执行命令，显示命令详情
    if (isExecutingCommand && window.currentExecutingCommand) {
        // 检查是否是暂停命令
        if (window.currentExecutingCommand.set_run_status === '2') {
            showExecutingCommandInfo();
            return;
        }
    }
    
    // 否则执行正常的暂停逻辑
    handlePauseCommand();
}

// 处理暂停命令
function handlePauseCommand() {
    if (!currentDeviceId) {
        showAlert('未选择设备，无法发送暂停命令', '提示', 'warning');
        return;
    }

    const currentRunStatus = getCurrentDeviceRunStatus();
    const runMode = getCurrentDeviceRunMode();

    // 如果是停止状态，提示无需暂停
    if (currentRunStatus === '0') {
        showAlert('当前处于停止状态，无需暂停。', '提示', 'info');
        return;
    }

    // 只有在运行状态下才能暂停
    if (currentRunStatus !== '1') {
        showAlert('只有在运行状态下才能暂停试验', '提示', 'warning');
        return;
    }

    // 立即更新UI到暂停状态
    const pauseStatusDisplay = getStatusDisplay('2'); // 暂停状态
    updateUIForStatus(pauseStatusDisplay, runMode);

    // 发送暂停命令
    sendPauseCommand(runMode);
}

// 发送暂停命令（独立函数，便于管理）
function sendPauseCommand(runMode) {
    if (!currentDeviceId) {
        showAlert('未选择设备，无法发送命令', '提示', 'warning');
        return;
    }

    // 获取用户名
    const username = localStorage.getItem('username') || 'admin';

    // 构造暂停命令数据
    const commandData = {
        device_id: currentDeviceId,
        valueorprogram: runMode === '0' ? '0' : '1',
        set_run_status: '2', // 暂停
        create_by: username
    };

    // 如果是定值模式，添加定值参数
    if (runMode === '1') {
        const targetTemp = elements.targetTempDisplay ? elements.targetTempDisplay.textContent : '25.0';
        const targetHum = elements.targetHumidityDisplay ? elements.targetHumidityDisplay.textContent : '60.0';
        commandData.fixed_temp_set = targetTemp;
        commandData.fixed_hum_set = targetHum;
    } else {
        // 如果是程式模式，添加程式参数
        let programNumber = '001';
        if (typeof window.tempProgramNumber !== 'undefined' && window.tempProgramNumber !== null) {
            programNumber = String(window.tempProgramNumber);
        } else {
            const programNumberDisplay = document.getElementById('programNumberDisplay');
            if (programNumberDisplay) {
                programNumber = programNumberDisplay.textContent;
            }
        }
        commandData.set_program_number = programNumber;
        commandData.set_program_no = programNumber;
    }

    console.log('[暂停命令] 发送暂停命令:', commandData);

    fetch('/iot/createCommand', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(commandData)
    })
    .then(async response => {
        const contentType = response.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
            throw new Error('服务器返回了非JSON格式的响应');
        }
        return response.json();
    })
    .then(data => {
        if (data.success) {
            const commandId = data.id || data.commandId;
            console.log(`[暂停命令] 暂停命令发送成功！命令ID: ${commandId}`);
            
            // 保存命令详情
            window.currentExecutingCommand = {
                id: commandId,
                device_id: commandData.device_id,
                valueorprogram: commandData.valueorprogram,
                set_run_status: '2', // 暂停
                fixed_temp_set: commandData.fixed_temp_set,
                fixed_hum_set: commandData.fixed_hum_set,
                set_program_number: commandData.set_program_number,
                set_program_no: commandData.set_program_no,
                create_by: commandData.create_by,
                create_at: new Date().toISOString()
            };
            
            // 设置命令执行状态标志
            isExecutingCommand = true;
            executingCommandStatus = '2';
            
            // 更新暂停按钮为"暂停中"
            updatePauseButtonExecuting();
            
            // 定时检查命令执行状态
            let checkCount = 0;
            const checkInterval = setInterval(() => {
                checkCount++;
                const currentStatus = getCurrentDeviceRunStatus();
                
                if (currentStatus === '2') {
                    // 暂停命令已执行
                    clearInterval(checkInterval);
                    isExecutingCommand = false;
                    executingCommandStatus = null;
                    window.currentExecutingCommand = null;
                    console.log(`[暂停命令] ✅ 暂停命令执行完成（共检查${checkCount}次）`);
                    
                    // 标记命令为已完成
                    if (commandId) {
                        markCommandAsFinished(commandId);
                    }
                    
                    // 恢复暂停按钮状态
                    updatePauseButtonNormal();
                } else {
                    // 命令还在执行中
                    updatePauseButtonExecuting();
                    if (checkCount % 3 === 0) {
                        console.log(`[暂停命令] 第${checkCount}次检查，状态: ${currentStatus}, 期望: 2`);
                    }
                }
            }, COMMAND_CHECK_CONFIG.checkInterval);
        } else {
            showAlert('暂停命令发送失败：' + (data.message || '未知错误'), '错误', 'error');
            // 恢复之前的状态
            const originalStatus = getStatusDisplay(getCurrentDeviceRunStatus());
            updateUIForStatus(originalStatus, getCurrentDeviceRunMode());
        }
    })
    .catch(error => {
        console.error('[暂停命令] 发送失败:', error);
        showAlert('暂停命令发送失败：' + error.message, '错误', 'error');
        const originalStatus = getStatusDisplay(getCurrentDeviceRunStatus());
        updateUIForStatus(originalStatus, getCurrentDeviceRunMode());
    });
}

// 更新暂停按钮为"暂停中"状态
function updatePauseButtonExecuting() {
    const chartBtn = elements.chartBtn;
    const programChartBtn = document.getElementById('programChartBtn');
    
    if (chartBtn) {
        chartBtn.textContent = '暂停中';
        chartBtn.classList.add('executing');
        chartBtn.title = '点击查看正在执行的暂停命令详情';
    }
    if (programChartBtn) {
        programChartBtn.textContent = '暂停中';
        programChartBtn.classList.add('executing');
        programChartBtn.title = '点击查看正在执行的暂停命令详情';
    }
}

// 恢复暂停按钮为正常状态
function updatePauseButtonNormal() {
    const chartBtn = elements.chartBtn;
    const programChartBtn = document.getElementById('programChartBtn');
    
    if (chartBtn) {
        chartBtn.textContent = '暂停';
        chartBtn.classList.remove('executing');
        chartBtn.title = '';
    }
    if (programChartBtn) {
        programChartBtn.textContent = '暂停';
        programChartBtn.classList.remove('executing');
        programChartBtn.title = '';
    }
}

// 发送运行命令
function sendRunCommand(runStatus, runMode) {
    if (!currentDeviceId) {
        showAlert('未选择设备，无法发送命令', '提示', 'warning');
        return;
    }

    // 获取用户名（从localStorage）
    const username = localStorage.getItem('username') || 'admin';

    // 构造命令数据
    const commandData = {
        device_id: currentDeviceId,
        valueorprogram: runMode === '0' ? '0' : '1', // 程式模式=0, 定值模式=1
        set_run_status: runStatus,
        create_by: username
    };

    // 如果是定值模式，添加定值参数
    if (runMode === '1') {
        // 从页面获取当前设置的温湿度值
        const targetTemp = elements.targetTempDisplay ? elements.targetTempDisplay.textContent : '25.0';
        const targetHum = elements.targetHumidityDisplay ? elements.targetHumidityDisplay.textContent : '60.0';
        commandData.fixed_temp_set = targetTemp;
        commandData.fixed_hum_set = targetHum;
    } else {
        // 如果是程式模式，添加程式参数
        // 获取临时设置的程式号或从显示中获取
        let programNumber = '001';
        if (typeof window.tempProgramNumber !== 'undefined' && window.tempProgramNumber !== null) {
            programNumber = String(window.tempProgramNumber);
        } else {
            const programNumberDisplay = document.getElementById('programNumberDisplay');
            if (programNumberDisplay) {
                programNumber = programNumberDisplay.textContent;
            }
        }
        commandData.set_program_number = programNumber;
        commandData.set_program_no = programNumber; // 也设置set_program_no字段
    }

    console.log('发送命令数据:', commandData);

    fetch('/iot/createCommand', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(commandData)
    })
    .then(async response => {
        const contentType = response.headers.get('content-type');

        if (!contentType || !contentType.includes('application/json')) {
            const text = await response.text();
            throw new Error('服务器返回了非JSON格式的响应（状态码: ' + response.status + '）');
        }

        return response.json();
    })
    .then(data => {
        if (data.success) {
            const actionText = runStatus === '1' ? '启动' : (runStatus === '0' ? '停止' : '暂停');
            const commandId = data.id || data.commandId; // 保存命令ID
            console.log(`命令发送成功！命令ID: ${commandId || 'N/A'}`);
            
            // 保存命令详情（用于显示执行中信息）
            window.currentExecutingCommand = {
                id: commandId,
                device_id: commandData.device_id,
                valueorprogram: commandData.valueorprogram,
                set_run_status: commandData.set_run_status,
                fixed_temp_set: commandData.fixed_temp_set,
                fixed_hum_set: commandData.fixed_hum_set,
                set_program_number: commandData.set_program_number,
                set_program_no: commandData.set_program_no,
                create_by: commandData.create_by,
                create_at: new Date().toISOString()
            };
            
            // 设置命令执行状态标志
            isExecutingCommand = true;
            executingCommandStatus = commandData.set_run_status;
            
            // 立即检查命令执行状态
            checkCommandExecutionStatus(commandData.set_run_status);
            
            // 定时检查命令执行状态，直到命令被执行（无超时限制）
            let checkCount = 0;
            const checkInterval = setInterval(() => {
                checkCount++;
                const currentStatus = getCurrentDeviceRunStatus();
                
                if (currentStatus === commandData.set_run_status) {
                    // 命令已执行，清除定时器和标志位
                    clearInterval(checkInterval);
                    isExecutingCommand = false;
                    executingCommandStatus = null;
                    window.currentExecutingCommand = null; // 清除命令详情
                    console.log(`[命令检查] ✅ 命令执行完成（共检查${checkCount}次）`);
                    
                    // 标记命令为已完成
                    if (commandId) {
                        markCommandAsFinished(commandId);
                    }
                    
                    // 恢复正常按钮状态
                    const statusDisplay = getStatusDisplay(currentStatus);
                    updateRunButtons(statusDisplay);
                } else {
                    // 命令还未执行，继续显示"执行中"
                    checkCommandExecutionStatus(commandData.set_run_status);
                    if (checkCount % 3 === 0) {
                        // 每3次检查（15秒）输出一次进度日志
                        console.log(`[命令检查] 第${checkCount}次检查，状态: ${currentStatus}, 期望: ${commandData.set_run_status}`);
                    }
                }
            }, COMMAND_CHECK_CONFIG.checkInterval);
        } else {
            // 命令发送失败，恢复之前的UI状态
            showAlert('命令发送失败：' + (data.message || '未知错误'), '错误', 'error');
            // 清除标志位
            isExecutingCommand = false;
            executingCommandStatus = null;
            window.currentExecutingCommand = null; // 清除命令详情
            // 恢复到发送命令前的状态
            const originalStatus = getStatusDisplay(getCurrentDeviceRunStatus());
            updateUIForStatus(originalStatus, getCurrentDeviceRunMode());
        }
    })
    .catch(error => {
        // 网络错误等，恢复之前的UI状态
        console.error('发送命令失败:', error);
        showAlert('发送命令失败：' + error.message, '错误', 'error');
        // 清除标志位
        isExecutingCommand = false;
        executingCommandStatus = null;
        window.currentExecutingCommand = null; // 清除命令详情
        const originalStatus = getStatusDisplay(getCurrentDeviceRunStatus());
        updateUIForStatus(originalStatus, getCurrentDeviceRunMode());
    });
}

// 检查命令执行状态
function checkCommandExecutionStatus(expectedStatus) {
    const currentStatus = getCurrentDeviceRunStatus();
    
    // 如果当前状态与期望状态不一致，说明命令还在执行中
    if (currentStatus !== expectedStatus) {
        // 确保标志位设置正确
        if (!isExecutingCommand) {
            isExecutingCommand = true;
            executingCommandStatus = expectedStatus;
        }
        
        // 更新按钮显示为"执行中"（不禁用，允许点击查看详情）
        const runBtn = elements.runBtn;
        const programRunBtn = document.getElementById('programRunBtn');
        
        if (runBtn) {
            runBtn.textContent = '执行中';
            runBtn.disabled = false; // 允许点击
            runBtn.classList.add('executing');
            runBtn.title = '点击查看正在执行的命令详情';
        }
        if (programRunBtn) {
            programRunBtn.textContent = '执行中';
            programRunBtn.disabled = false; // 允许点击
            programRunBtn.classList.add('executing');
            programRunBtn.title = '点击查看正在执行的命令详情';
        }
        
        console.log(`命令执行中... 当前状态: ${currentStatus}, 期望状态: ${expectedStatus}`);
    } else {
        // 状态已同步，命令执行完成
        console.log(`命令执行完成！状态已同步: ${currentStatus}`);
    }
}

// 更新温湿度模块连接状态
function updateModuleConnectionStatus() {
    if (!currentDeviceId) return;

    // 查找当前设备
    for (let device of deviceList) {
        if (device.id === currentDeviceId) {
            // 从原始数据中获取module_connection
            const moduleConnection = device.raw ? device.raw.module_connection || device.raw.moduleConnection : null;
            const moduleStatus = formatModuleConnection(moduleConnection);

            // 更新菜单页面的温湿度模块连接状态
            const errorMessageElement = document.querySelector('#menuPage .error-message');
            if (errorMessageElement) {
                errorMessageElement.textContent = moduleStatus.message;

                // 根据状态设置不同的样式类
                errorMessageElement.className = 'error-message ' + (moduleStatus.colorClass || '');
            }
            break;
        }
    }
}

// 更新运行按钮状态
function updateRunButtons(statusDisplay) {
    // 如果正在执行命令，不更新按钮状态（保持"执行中"状态）
    if (isExecutingCommand) {
        return;
    }
    
    // 更新定值试验页面的运行按钮
    if (elements.runBtn) {
        elements.runBtn.textContent = statusDisplay.buttonText;
        elements.runBtn.className = 'action-btn action-btn-primary ' + statusDisplay.buttonClass;
        elements.runBtn.disabled = false;
        elements.runBtn.classList.remove('executing');
        elements.runBtn.title = ''; // 清除tooltip
    }

    // 更新程式试验页面的运行按钮
    const programRunBtn = document.getElementById('programRunBtn');
    if (programRunBtn) {
        programRunBtn.textContent = statusDisplay.buttonText;
        programRunBtn.className = 'action-btn action-btn-primary ' + statusDisplay.buttonClass;
        programRunBtn.disabled = false;
        programRunBtn.classList.remove('executing');
        programRunBtn.title = ''; // 清除tooltip
    }
}

// 返回设备监控首页
function backToMonitor() {
    navigateTo('deviceMonitor');
    
    // 显示返回主页按钮和整个 monitor-header（返回一级页面）
    const backHomeBtn = document.getElementById('backHomeBtn');
    const refreshDataBtn = document.getElementById('refreshDataBtn');
    const oeeAnalysisBtn = document.getElementById('oeeAnalysisBtn');
    const monitorTitle = document.getElementById('monitorTitle');
    const userInfo = document.getElementById('userInfo');
    const monitorHeader = document.querySelector('.monitor-header');
    const monitorDatetime = document.getElementById('monitorDatetime');
    
    // 显示整个 monitor-header
    if (monitorHeader) {
        monitorHeader.style.display = 'block';
        console.log('[返回监控] 已显示 monitor-header');
    }
    
    // 显示全局时间
    if (monitorDatetime) {
        monitorDatetime.style.display = 'block';
        console.log('[返回监控] 已显示全局时间');
    }
    
    if (backHomeBtn) {
        backHomeBtn.style.display = 'flex';
        console.log('[返回监控] 已显示返回主页按钮');
    }
    
    // 显示刷新数据和OEE分析按钮
    if (refreshDataBtn && oeeAnalysisBtn) {
        refreshDataBtn.style.display = 'inline-block';
        oeeAnalysisBtn.style.display = 'inline-block';
        console.log('[返回监控] 已显示刷新数据和OEE分析按钮');
    }
    
    // 显示监控系统标题
    if (monitorTitle) {
        monitorTitle.style.display = 'block';
        console.log('[返回监控] 已显示监控系统标题');
    }
    
    // 显示用户信息
    if (userInfo) {
        userInfo.style.display = 'flex';
        console.log('[返回监控] 已显示用户信息');
    }
    
    // 重新启动设备列表自动刷新
    if (typeof window.startDeviceListAutoRefresh === 'function') {
        setTimeout(() => {
            window.startDeviceListAutoRefresh();
        }, 200);
    }
}

// 更新日期时间
function updateDateTime() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    
    const timeString = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    
    if (elements.monitorDatetime) elements.monitorDatetime.textContent = timeString;
    if (elements.menuDatetime) elements.menuDatetime.textContent = timeString;
    if (elements.constantDatetime) elements.constantDatetime.textContent = timeString;
    if (elements.programDatetime) elements.programDatetime.textContent = timeString;
}

// 开始运行时间计数
function startRuntimeCounter() {
    setInterval(() => {
        // 优先使用从数据库获取的数据，如果没有运行状态或开始时间，则使用设备状态
        const currentPage = document.querySelector('.page.active');
        const isConstantPage = currentPage && currentPage.id === 'constantPage';
        const isProgramPage = currentPage && currentPage.id === 'programPage';
        
        // 如果正在显示试验页面，则使用数据库数据（由fetchLatestData更新）
        // 否则使用本地计算的运行时间
        if (isConstantPage || isProgramPage) {
            // 数据由fetchLatestData函数更新，这里不做处理
            return;
        }
        
        if (deviceState.isRunning && deviceState.startTime) {
            const elapsed = new Date() - deviceState.startTime;
            const hours = Math.floor(elapsed / 3600000);
            const minutes = Math.floor((elapsed % 3600000) / 60000);
            const seconds = Math.floor((elapsed % 60000) / 1000);
            
            const runtimeText = `${hours}H ${String(minutes).padStart(2, '0')}M ${String(seconds).padStart(2, '0')}S`;
            if (elements.runtime) elements.runtime.textContent = runtimeText;
            if (elements.programRuntime) elements.programRuntime.textContent = runtimeText;
            
            // 计算和显示剩余时间
            if (deviceState.remainingTime !== null) {
                const remaining = deviceState.remainingTime - elapsed;
                if (remaining > 0) {
                    const remHours = Math.floor(remaining / 3600000);
                    const remMinutes = Math.floor((remaining % 3600000) / 60000);
                    const remSeconds = Math.floor((remaining % 60000) / 1000);
                    const remainingText = `${remHours}H ${String(remMinutes).padStart(2, '0')}M ${String(remSeconds).padStart(2, '0')}S`;
                    if (elements.remainingTime) elements.remainingTime.textContent = remainingText;
                    if (elements.programRemainingTime) elements.programRemainingTime.textContent = remainingText;
                } else {
                    if (elements.remainingTime) elements.remainingTime.textContent = '0H 00M 00S';
                    if (elements.programRemainingTime) elements.programRemainingTime.textContent = '0H 00M 00S';
                }
            }
        } else {
            if (elements.runtime) elements.runtime.textContent = '0H 00M 00S';
            if (elements.programRuntime) elements.programRuntime.textContent = '0H 00M 00S';
            if (elements.remainingTime) elements.remainingTime.textContent = '--';
            if (elements.programRemainingTime) elements.programRemainingTime.textContent = '--';
        }
    }, 1000);
}

// 更新页面数据（被 fetchLatestData 和 fetchLatestDataAndCheckCommands 复用）
function updatePageWithLatestData(d) {
    if (!d) return;
    
    // ========================================
    // 重要：保存最新数据到全局变量（window对象，确保跨文件同步）
    // ========================================
    window.currentDeviceLatestData = d;
    console.log(`[数据更新] 更新 window.currentDeviceLatestData，run_status: ${d.run_status}, run_mode: ${d.run_mode}`);
            
            // 定值试验页面数据更新
            if (elements.currentTemp && d.temperature != null) elements.currentTemp.textContent = Number(d.temperature).toFixed(2);
            if (elements.currentHumidity && d.humidity != null) elements.currentHumidity.textContent = Number(d.humidity).toFixed(2);
            
            // 判断定值试验是否在运行中（定值模式且运行状态）
            const isConstantRunning = d && d.run_mode === '1' && 
                                     (d.run_status === '运行' || d.run_status === '1' || d.run_status === 1 || 
                                      (typeof d.run_status === 'string' && d.run_status.includes('运行')));
            
            // 温度设定值：运行时清除临时修改，停止时保留用户设置
            if (elements.targetTempDisplay && d.set_temperature != null) {
                if (isConstantRunning) {
                    // 运行状态：显示服务器返回的实际值，清除临时修改标识（但不清除临时值，停止后还能恢复）
                    elements.targetTempDisplay.textContent = Number(d.set_temperature).toFixed(1);
                    elements.targetTempDisplay.classList.remove('modified');
                } else if (typeof window.isTargetTempModified !== 'undefined' && 
                           window.isTargetTempModified && 
                           window.tempTargetTemp !== null) {
                    // 停止状态且有临时修改，保留用户设置的值，不被数据库值覆盖
                    elements.targetTempDisplay.textContent = Number(window.tempTargetTemp).toFixed(1);
                    elements.targetTempDisplay.classList.add('modified');
                    console.log(`[数据更新] 保持用户临时设置的温度设定值: ${window.tempTargetTemp}`);
                } else {
                    // 停止状态且无临时修改，显示数据库的值
                    elements.targetTempDisplay.textContent = Number(d.set_temperature).toFixed(1);
                    elements.targetTempDisplay.classList.remove('modified');
                }
            }
            
            // 湿度设定值：运行时清除临时修改，停止时保留用户设置
            if (elements.targetHumidityDisplay && d.set_humidity != null) {
                if (isConstantRunning) {
                    // 运行状态：显示服务器返回的实际值，清除临时修改标识（但不清除临时值，停止后还能恢复）
                    elements.targetHumidityDisplay.textContent = Number(d.set_humidity).toFixed(1);
                    elements.targetHumidityDisplay.classList.remove('modified');
                } else if (typeof window.isTargetHumidityModified !== 'undefined' && 
                           window.isTargetHumidityModified && 
                           window.tempTargetHumidity !== null) {
                    // 停止状态且有临时修改，保留用户设置的值，不被数据库值覆盖
                    elements.targetHumidityDisplay.textContent = Number(window.tempTargetHumidity).toFixed(1);
                    elements.targetHumidityDisplay.classList.add('modified');
                    console.log(`[数据更新] 保持用户临时设置的湿度设定值: ${window.tempTargetHumidity}`);
                } else {
                    // 停止状态且无临时修改，显示数据库的值
                    elements.targetHumidityDisplay.textContent = Number(d.set_humidity).toFixed(1);
                    elements.targetHumidityDisplay.classList.remove('modified');
                }
            }
            
            if (elements.tempPower && d.power_temperature != null) elements.tempPower.textContent = String(d.power_temperature) + '%';
            if (elements.humidityPower && d.power_humidity != null) elements.humidityPower.textContent = String(d.power_humidity) + '%';
            
            // 程式试验页面数据更新
            const programCurrentTemp = document.getElementById('programCurrentTemp');
            const programCurrentHumidity = document.getElementById('programCurrentHumidity');
            const programNumberDisplay = document.getElementById('programNumberDisplay');
            const totalSegmentsDisplay = document.getElementById('totalSegmentsDisplay');
            const programTempPower = document.getElementById('programTempPower');
            const programHumidityPower = document.getElementById('programHumidityPower');
            const programRuntime = document.getElementById('programRuntime');
            const programRemainingTime = document.getElementById('programRemainingTime');
            
            if (programCurrentTemp && d.temperature != null) programCurrentTemp.textContent = Number(d.temperature).toFixed(2);
            if (programCurrentHumidity && d.humidity != null) programCurrentHumidity.textContent = Number(d.humidity).toFixed(2);
            
            // 判断是否运行中（run_status为运行状态）
            const isRunning = d && (d.run_status === '运行' || d.run_status === '1' || d.run_status === 1 || 
                                   (typeof d.run_status === 'string' && d.run_status.includes('运行')));
            
            // 更新程式试验页面的温度面板第一行：运行时显示设定温度，停止时显示程式号
            const programTempLabel = document.getElementById('programTempLabel');
            if (isRunning) {
                // 运行状态：显示设定温度
                if (programTempLabel) {
                    programTempLabel.textContent = '设定值';
                }
                if (programNumberDisplay && d.set_temperature != null) {
                    programNumberDisplay.textContent = Number(d.set_temperature).toFixed(1);
                }
                // 运行时清除临时修改标识的样式（但不清除临时值，停止后还能恢复）
                if (programNumberDisplay) {
                    programNumberDisplay.classList.remove('modified');
                }
            } else {
                // 停止状态：显示程式号
                if (programTempLabel) {
                    programTempLabel.textContent = '程式号';
                }
                if (programNumberDisplay && d.set_program_number != null) {
                    // 检查用户是否有临时修改程式号
                    if (typeof window.isProgramNumberModified !== 'undefined' && 
                        window.isProgramNumberModified && 
                        window.tempProgramNumber !== null) {
                        // 有临时修改，保留用户设置的值，不被数据库值覆盖
                        programNumberDisplay.textContent = String(window.tempProgramNumber);
                        programNumberDisplay.classList.add('modified');
                        console.log(`[数据更新] 保持用户临时设置的程式号: ${window.tempProgramNumber}`);
                    } else {
                        // 无临时修改，显示数据库的值（直接显示整数，不补零）
                        programNumberDisplay.textContent = String(d.set_program_number);
                        programNumberDisplay.classList.remove('modified');
                    }
                }
            }
            
            // 更新程式试验页面的湿度面板第一行：运行时显示设定湿度，停止时显示总段数
            const programHumLabel = document.getElementById('programHumLabel');
            if (isRunning) {
                // 运行状态：显示设定湿度
                if (programHumLabel) {
                    programHumLabel.textContent = '设定值';
                }
                if (totalSegmentsDisplay && d.set_humidity != null) {
                    totalSegmentsDisplay.textContent = Number(d.set_humidity).toFixed(1);
                }
            } else {
                // 停止状态：显示总段数
                if (programHumLabel) {
                    programHumLabel.textContent = '总段数';
                }
                if (totalSegmentsDisplay && d.total_steps != null) {
                    // 直接显示整数，不补零
                    totalSegmentsDisplay.textContent = String(d.total_steps);
                }
            }
            
            if (programTempPower && d.power_temperature != null) programTempPower.textContent = String(d.power_temperature) + '%';
            if (programHumidityPower && d.power_humidity != null) programHumidityPower.textContent = String(d.power_humidity) + '%';
            
            // 程式试验页面的运行时间和剩余时间
            if (programRuntime && d.run_hours != null && d.run_minutes != null && d.run_seconds != null) {
                const h = parseInt(String(d.run_hours), 10) || 0;
                const m = parseInt(String(d.run_minutes), 10) || 0;
                const s = parseInt(String(d.run_seconds), 10) || 0;
                programRuntime.textContent = `${h}H ${String(m).padStart(2,'0')}M ${String(s).padStart(2,'0')}S`;
            }
            if (programRemainingTime && d.step_remaining_hours != null && d.step_remaining_minutes != null && d.step_remaining_seconds != null) {
                const rh = parseInt(String(d.step_remaining_hours), 10) || 0;
                const rm = parseInt(String(d.step_remaining_minutes), 10) || 0;
                const rs = parseInt(String(d.step_remaining_seconds), 10) || 0;
                programRemainingTime.textContent = `${rh}H ${String(rm).padStart(2,'0')}M ${String(rs).padStart(2,'0')}S`;
            }
            
            // 定值试验页面的运行时间和剩余时间
            if (elements.runtime && d.run_hours != null && d.run_minutes != null && d.run_seconds != null) {
                const h = parseInt(String(d.run_hours), 10) || 0;
                const m = parseInt(String(d.run_minutes), 10) || 0;
                const s = parseInt(String(d.run_seconds), 10) || 0;
                elements.runtime.textContent = `${h}H ${String(m).padStart(2,'0')}M ${String(s).padStart(2,'0')}S`;
            }
            if (elements.remainingTime && d.step_remaining_hours != null && d.step_remaining_minutes != null && d.step_remaining_seconds != null) {
                const rh = parseInt(String(d.step_remaining_hours), 10) || 0;
                const rm = parseInt(String(d.step_remaining_minutes), 10) || 0;
                const rs = parseInt(String(d.step_remaining_seconds), 10) || 0;
                elements.remainingTime.textContent = `${rh}H ${String(rm).padStart(2,'0')}M ${String(rs).padStart(2,'0')}S`;
            }
            
            // 更新试验状态
            if (d.run_status != null) {
                const statusDisplay = getStatusDisplay(d.run_status);
                const runMode = getCurrentDeviceRunMode();

                // 根据运行模式调整状态文本
                let finalStatusText = statusDisplay.statusText;
                if (runMode === '0' && statusDisplay.statusText === '停止') {
                    finalStatusText = '程式停止';
                }

                // 更新状态显示
                if (elements.constantStatus) {
                    elements.constantStatus.textContent = finalStatusText;
                    elements.constantStatus.classList.toggle('running', statusDisplay.statusText === '运行');
                    elements.constantStatus.classList.toggle('paused', statusDisplay.statusText === '暂停');
                }
                if (elements.programStatus) {
                    elements.programStatus.textContent = finalStatusText;
                    elements.programStatus.classList.toggle('running', statusDisplay.statusText === '运行');
                    elements.programStatus.classList.toggle('paused', statusDisplay.statusText === '暂停');
                }

                // 更新运行按钮
                updateRunButtons(statusDisplay);
            }
}

// 更新传感器数据
function fetchLatestData() {
    // 根据当前选择的设备ID获取数据
    const url = currentDeviceId ? `/iot/data/latest?device_id=${encodeURIComponent(currentDeviceId)}` : '/iot/data/latest';
    fetch(url)
        .then(r => r.json())
        .then(d => {
            updatePageWithLatestData(d);
        })
        .catch(() => {});
}

// 页面导航功能
function navigateTo(page) {
    // 检查是否允许进入试验页面
    if (page === 'constant' || page === 'program') {
        const runMode = getCurrentDeviceRunMode();
        const runStatus = getCurrentDeviceRunStatus();

        console.log(`[页面导航] 检查进入权限 - 页面: ${page}, 运行模式: ${runMode}, 运行状态: ${runStatus}`);

        // 如果设备处于停止状态 (run_status === 0)，允许进入任何页面
        if (runStatus === '0') {
            console.log('[页面导航] 设备处于停止状态，允许进入任何试验页面');
        } else {
            // 设备正在运行或暂停，需要检查模式匹配
            if (page === 'constant' && runMode === '0') {
                // 当前是程式模式，不允许进入定值试验
                showAlert('当前设备运行在程式模式，无法进入定值试验页面\n请先停止设备后再切换', '模式不匹配', 'warning');
                return;
            }

            if (page === 'program' && runMode === '1') {
                // 当前是定值模式，不允许进入程式试验
                showAlert('当前设备运行在定值模式，无法进入程式试验页面\n请先停止设备后再切换', '模式不匹配', 'warning');
                return;
            }
        }
    }

    // 隐藏所有页面
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));

    // 显示目标页面
    let pageId = '';
    switch(page) {
        case 'deviceMonitor':
            pageId = 'deviceMonitorPage';
            break;
        case 'menu':
            pageId = 'menuPage';
            break;
        case 'constant':
            pageId = 'constantPage';
            break;
        case 'program':
            pageId = 'programPage';
            break;
        case 'settings':
            pageId = 'settingsPage';
            break;
        case 'communication':
            pageId = 'communicationPage';
            break;
        case 'appointment':
            pageId = 'appointmentPage';
            break;
        case 'valueSettings':
            pageId = 'valueSettingsPage';
            break;
        case 'programEdit':
            pageId = 'programEditPage';
            break;
        case 'standby':
            // 待机功能暂未实现
            return;
        default:
            pageId = 'deviceMonitorPage';
    }
    
    const targetPage = document.getElementById(pageId);
    if (targetPage) {
        targetPage.classList.add('active');
        
        // 控制 monitor-header 和其子元素的显示/隐藏
        const backHomeBtn = document.getElementById('backHomeBtn');
        const refreshDataBtn = document.getElementById('refreshDataBtn');
        const oeeAnalysisBtn = document.getElementById('oeeAnalysisBtn');
        const monitorTitle = document.getElementById('monitorTitle');
        const userInfo = document.getElementById('userInfo');
        const monitorHeader = document.querySelector('.monitor-header');
        const monitorDatetime = document.getElementById('monitorDatetime');
        
        // 控制整个 monitor-header
        if (monitorHeader) {
            if (pageId === 'deviceMonitorPage') {
                // 一级页面：显示 monitor-header
                monitorHeader.style.display = 'block';
                console.log('[页面导航] 进入一级页面，显示 monitor-header');
            } else {
                // 二级和三级页面：隐藏 monitor-header
                monitorHeader.style.display = 'none';
                console.log('[页面导航] 进入二/三级页面，隐藏 monitor-header');
            }
        }
        
        // 控制全局时间显示
        if (monitorDatetime) {
            if (pageId === 'menuPage') {
                // 二级页面：隐藏全局时间（使用 menu-datetime）
                monitorDatetime.style.display = 'none';
                console.log('[页面导航] 进入二级页面，隐藏全局时间');
            } else {
                // 一级和三级页面：显示全局时间
                monitorDatetime.style.display = 'block';
                console.log('[页面导航] 进入一/三级页面，显示全局时间');
            }
        }
        
        if (backHomeBtn) {
            if (pageId === 'deviceMonitorPage') {
                // 一级页面：显示返回主页按钮
                backHomeBtn.style.display = 'flex';
                console.log('[页面导航] 进入一级页面，显示返回主页按钮');
            } else {
                // 二级和三级页面：隐藏返回主页按钮
                backHomeBtn.style.display = 'none';
                console.log('[页面导航] 进入二/三级页面，隐藏返回主页按钮');
            }
        }
        
        // 控制刷新数据和OEE分析按钮的显示/隐藏
        if (refreshDataBtn && oeeAnalysisBtn) {
            if (pageId === 'deviceMonitorPage') {
                // 一级页面：显示刷新数据和OEE分析按钮
                refreshDataBtn.style.display = 'inline-block';
                oeeAnalysisBtn.style.display = 'inline-block';
                console.log('[页面导航] 进入一级页面，显示刷新数据和OEE分析按钮');
            } else {
                // 二级和三级页面：隐藏刷新数据和OEE分析按钮
                refreshDataBtn.style.display = 'none';
                oeeAnalysisBtn.style.display = 'none';
                console.log('[页面导航] 进入二/三级页面，隐藏刷新数据和OEE分析按钮');
            }
        }
        
        // 控制监控系统标题的显示/隐藏
        if (monitorTitle) {
            if (pageId === 'deviceMonitorPage') {
                // 一级页面：显示监控系统标题
                monitorTitle.style.display = 'block';
                console.log('[页面导航] 进入一级页面，显示监控系统标题');
            } else {
                // 二级和三级页面：隐藏监控系统标题
                monitorTitle.style.display = 'none';
                console.log('[页面导航] 进入二/三级页面，隐藏监控系统标题');
            }
        }
        
        // 控制用户信息的显示/隐藏
        if (userInfo) {
            if (pageId === 'deviceMonitorPage') {
                // 一级页面：显示用户信息
                userInfo.style.display = 'flex';
                console.log('[页面导航] 进入一级页面，显示用户信息');
            } else {
                // 二级和三级页面：隐藏用户信息
                userInfo.style.display = 'none';
                console.log('[页面导航] 进入二/三级页面，隐藏用户信息');
            }
        }
        
        // 当进入菜单页面时，更新按钮状态
        if (pageId === 'menuPage') {
            // 延迟一点执行，确保 window.currentDeviceLatestData 已更新
            setTimeout(() => {
                updateMenuButtons();
                updateTestStatusText();
            }, 100);
        }

        // 当进入定值试验或程式试验页面时，立即同步数据和设备ID
        if (pageId === 'constantPage' || pageId === 'programPage') {
            if (currentDeviceId) {
                updateDeviceIdDisplay(currentDeviceId);
            }
            
            // 先刷新数据，再检查未完成的命令（确保有最新数据）
            fetchLatestDataAndCheckCommands();
        }
    }
}

// 先刷新最新数据，再检查未完成的命令
function fetchLatestDataAndCheckCommands() {
    if (!currentDeviceId) {
        console.warn('未选择设备，跳过数据刷新和命令检查');
        return;
    }

    console.log(`[命令检查] 开始刷新设备数据: ${currentDeviceId}`);
    const url = `/iot/data/latest?device_id=${encodeURIComponent(currentDeviceId)}`;
    
    fetch(url, { cache: 'no-store' })
        .then(r => r.json())
        .then(d => {
            if (!d) {
                console.warn('[命令检查] 未获取到设备数据');
                return;
            }
            
            console.log(`[命令检查] 设备数据刷新成功, run_status: ${d.run_status}`);
            
            // 更新页面显示数据（复用 fetchLatestData 的逻辑）
            updatePageWithLatestData(d);
            
            // 数据刷新完成后，立即检查未完成的命令
            console.log('[命令检查] 开始检查未完成命令...');
            checkPendingCommands();
        })
        .catch(error => {
            console.error('[命令检查] 刷新数据失败:', error);
            // 即使刷新失败，也尝试检查未完成命令
            checkPendingCommands();
        });
}

// 检查未完成的命令
function checkPendingCommands() {
    if (!currentDeviceId) {
        console.warn('[命令检查] 未选择设备，跳过未完成命令检查');
        return;
    }

    console.log(`[命令检查] 查询未完成命令: ${currentDeviceId}`);
    
    // 从后端API获取该设备未完成的命令
    fetch(`/iot/command/pending?device_id=${encodeURIComponent(currentDeviceId)}`, {
        cache: 'no-store'
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('获取未完成命令失败');
        }
        return response.json();
    })
    .then(data => {
        if (data.success && data.command) {
            // 有未完成的命令
            const command = data.command;
            const expectedStatus = command.set_run_status;
            const currentStatus = getCurrentDeviceRunStatus();
            
            console.log('[命令检查] ✓ 发现未完成的命令:', command);
            console.log(`[命令检查] 对比状态 - 期望: ${expectedStatus}, 当前: ${currentStatus}`);
            
            // 如果状态不一致，说明命令还在执行中
            if (currentStatus !== expectedStatus) {
                // 保存命令详情
                window.currentExecutingCommand = command;
                
                // 设置命令执行状态标志
                isExecutingCommand = true;
                executingCommandStatus = expectedStatus;
                
                console.log(`[命令检查] ⚠ 状态不一致，命令未执行完成，显示"执行中"状态`);
                
                // 根据命令类型显示不同的执行状态
                if (expectedStatus === '2') {
                    // 暂停命令
                    updatePauseButtonExecuting();
                } else {
                    // 运行/停止命令
                    checkCommandExecutionStatus(expectedStatus);
                }
                
                // 启动定时检查，直到命令完成（无超时限制）
                let checkCount = 0;
                const checkInterval = setInterval(() => {
                    checkCount++;
                    const latestStatus = getCurrentDeviceRunStatus();
                    
                    if (latestStatus === expectedStatus) {
                        // 命令已执行完成
                        clearInterval(checkInterval);
                        isExecutingCommand = false;
                        executingCommandStatus = null;
                        window.currentExecutingCommand = null; // 清除命令详情
                        console.log(`[命令检查] ✅ 未完成命令已执行完成（共检查${checkCount}次）`);
                        
                        // 通知后端更新命令状态为已完成
                        markCommandAsFinished(command.id);
                        
                        // 根据命令类型恢复对应按钮状态
                        if (expectedStatus === '2') {
                            // 暂停命令完成，恢复暂停按钮
                            updatePauseButtonNormal();
                        } else {
                            // 运行/停止命令完成，恢复运行按钮
                            const statusDisplay = getStatusDisplay(latestStatus);
                            updateRunButtons(statusDisplay);
                        }
                    } else {
                        // 命令还在执行中，继续等待
                        if (expectedStatus === '2') {
                            // 暂停命令执行中
                            updatePauseButtonExecuting();
                        } else {
                            // 运行/停止命令执行中
                            checkCommandExecutionStatus(expectedStatus);
                        }
                        
                        if (checkCount % 3 === 0) {
                            // 每3次检查（15秒）输出一次进度日志
                            console.log(`[命令检查] 第${checkCount}次检查，状态: ${latestStatus}, 期望: ${expectedStatus}`);
                        }
                    }
                }, COMMAND_CHECK_CONFIG.checkInterval);
            } else {
                // 状态已一致，但命令未标记为完成，通知后端更新
                console.log('[命令检查] ✓ 状态已一致，命令已执行完成但未标记，通知后端更新');
                markCommandAsFinished(command.id);
            }
        } else {
            console.log('[命令检查] ✓ 没有未完成的命令');
        }
    })
    .catch(error => {
        console.error('[命令检查] ✗ 检查未完成命令失败:', error);
    });
}

// 标记命令为已完成
function markCommandAsFinished(commandId) {
    if (!commandId) {
        console.warn('命令ID为空，无法标记为已完成');
        return;
    }

    fetch('/iot/command/finish', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            id: commandId,
            is_finished: 1
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            console.log('命令已标记为完成，ID:', commandId);
        } else {
            console.error('标记命令为完成失败:', data.message);
        }
    })
    .catch(error => {
        console.error('标记命令为完成请求失败:', error);
    });
}

// 页面加载完成后初始化
window.addEventListener('DOMContentLoaded', init);
