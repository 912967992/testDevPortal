// 当前设备ID
let currentDeviceId = '';

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
    
    // 每秒更新时间
    setInterval(updateDateTime, 1000);
    
    // 每3秒拉取一次最新数据库数据
    setInterval(fetchLatestData, 3000);
}

// 绑定事件
function bindEvents() {
    // 定值试验页面的运行按钮
    if (elements.runBtn) {
        elements.runBtn.addEventListener('click', showRunConfirm);
    }
    
    // 程式试验页面的运行按钮
    const programRunBtn = document.getElementById('programRunBtn');
    if (programRunBtn) {
        programRunBtn.addEventListener('click', showRunConfirm);
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
            handlePauseCommand();
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
            handlePauseCommand();
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
            confirmMessage = '确定要启动试验吗？<br><small>设备将开始运行试验程序，30秒后将自动刷新状态确认执行结果。</small>';
        } else if (currentRunStatus === '1') {
            confirmTitle = '停止试验';
            confirmMessage = '确定要停止试验吗？<br><small>设备将立即停止当前试验，30秒后将自动刷新状态确认执行结果。</small>';
        } else if (currentRunStatus === '2') {
            confirmTitle = '继续试验';
            confirmMessage = '确定要继续试验吗？<br><small>设备将从暂停状态恢复运行，30秒后将自动刷新状态确认执行结果。</small>';
        } else {
            confirmMessage = '确定要执行此操作吗？<br><small>命令将发送至设备，执行后将在30秒后自动刷新状态。</small>';
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
        alert(`请输入${valueInputState.minValue}到${valueInputState.maxValue}之间的有效数值`);
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
    // 先验证并格式化输入
    validateAndFormatInput();
    
    const value = parseFloat(elements.valueInput.value);
    
    if (isNaN(value) || value < valueInputState.minValue || value > valueInputState.maxValue) {
        alert(`请输入${valueInputState.minValue}到${valueInputState.maxValue}之间的有效数值`);
        return;
    }
    
    // 格式化数值
    const formattedValue = valueInputState.decimalPlaces === 0 ? 
        Math.round(value).toString().padStart(3, '0') : 
        value.toFixed(valueInputState.decimalPlaces);
    
    // 更新显示
    if (valueInputState.currentTarget) {
        valueInputState.currentTarget.textContent = formattedValue;
        
        // 更新设备状态
        if (valueInputState.currentTarget.id === 'targetTempDisplay') {
            deviceState.targetTemp = value;
        } else if (valueInputState.currentTarget.id === 'targetHumidityDisplay') {
            deviceState.targetHumidity = value;
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
    navigateTo('menu');

    // 更新所有页面的设备ID显示
    updateDeviceIdDisplay(deviceId);
}

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

    // 查找当前设备
    for (let device of deviceList) {
        if (device.id === currentDeviceId) {
            // 从原始数据中获取run_mode
            return device.raw ? device.raw.run_mode || device.raw.runMode : null;
        }
    }
    return null;
}

// 更新菜单页面按钮状态
function updateMenuButtons() {
    const runMode = getCurrentDeviceRunMode();

    // 获取菜单按钮
    const constantBtn = document.querySelector('.menu-item-yellow');
    const programBtn = document.querySelector('.menu-item-red');

    if (runMode === '0') {
        // 程式模式 - 禁用定值试验，启用程式试验
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
        // 定值模式 - 禁用程式试验，启用定值试验
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
            return { statusText: '暂停', buttonText: '继续', buttonClass: 'paused' };
        default:
            return { statusText: '未知', buttonText: '运行', buttonClass: '' };
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

    // 更新运行按钮
    updateRunButtons(statusDisplay);
}

// 获取当前设备的运行状态
function getCurrentDeviceRunStatus() {
    if (!currentDeviceId) return null;

    // 查找当前设备
    for (let device of deviceList) {
        if (device.id === currentDeviceId) {
            // 从原始数据中获取run_status
            return device.raw ? device.raw.run_status || device.raw.runStatus : null;
        }
    }
    return null;
}

// 处理暂停命令
function handlePauseCommand() {
    if (!currentDeviceId) {
        showResult('未选择设备，无法发送暂停命令', 'error');
        return;
    }

    const currentRunStatus = getCurrentDeviceRunStatus();
    const runMode = getCurrentDeviceRunMode();

    // 只有在运行状态下才能暂停
    if (currentRunStatus !== '1') {
        showResult('只有在运行状态下才能暂停试验', 'error');
        return;
    }

    // 立即更新UI到暂停状态
    const pauseStatusDisplay = getStatusDisplay('2'); // 暂停状态
    updateUIForStatus(pauseStatusDisplay, runMode);

    // 发送暂停命令
    sendRunCommand('2', runMode);
}

// 发送运行命令
function sendRunCommand(runStatus, runMode) {
    if (!currentDeviceId) {
        showResult('未选择设备，无法发送命令', 'error');
        return;
    }

    // 构造命令数据
    const commandData = {
        device_id: currentDeviceId,
        valueorprogram: runMode === '0' ? '0' : '1', // 程式模式=0, 定值模式=1
        set_run_status: runStatus,
        create_by: 'admin'
    };

    // 如果是定值模式，添加定值参数
    if (runMode === '1') {
        commandData.fixed_temp_set = '25.0'; // 默认温度
        commandData.fixed_hum_set = '60.0';  // 默认湿度
    } else {
        // 如果是程式模式，添加程式参数
        commandData.set_program_number = '001'; // 默认程式号
    }

    // 显示发送状态
    showResult('正在发送命令...', 'loading');

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
            const actionText = runStatus === '1' ? '启动' : '停止';
            showResult(`命令发送成功！${actionText}命令已执行`, 'success');

            // 延迟刷新数据以显示最新状态（服务器可能需要时间处理）
            setTimeout(() => {
                fetchLatestData();
            }, 30000);
        } else {
            // 命令发送失败，恢复之前的UI状态
            showResult('命令发送失败：' + (data.message || '未知错误'), 'error');
            // 恢复到发送命令前的状态
            const originalStatus = getStatusDisplay(getCurrentDeviceRunStatus());
            updateUIForStatus(originalStatus, getCurrentDeviceRunMode());
        }
    })
    .catch(error => {
        // 网络错误等，恢复之前的UI状态
        showResult('发送命令失败：' + error.message, 'error');
        const originalStatus = getStatusDisplay(getCurrentDeviceRunStatus());
        updateUIForStatus(originalStatus, getCurrentDeviceRunMode());
    });
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
    // 更新定值试验页面的运行按钮
    if (elements.runBtn) {
        elements.runBtn.textContent = statusDisplay.buttonText;
        elements.runBtn.className = 'action-btn action-btn-primary ' + statusDisplay.buttonClass;
    }

    // 更新程式试验页面的运行按钮
    const programRunBtn = document.getElementById('programRunBtn');
    if (programRunBtn) {
        programRunBtn.textContent = statusDisplay.buttonText;
        programRunBtn.className = 'action-btn action-btn-primary ' + statusDisplay.buttonClass;
    }
}

// 返回设备监控首页
function backToMonitor() {
    navigateTo('deviceMonitor');
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

// 更新传感器数据
function fetchLatestData() {
    // 根据当前选择的设备ID获取数据
    const url = currentDeviceId ? `/iot/data/latest?device_id=${encodeURIComponent(currentDeviceId)}` : '/iot/data/latest';
    fetch(url)
        .then(r => r.json())
        .then(d => {
            if (!d) return;
            
            // 定值试验页面数据更新
            if (elements.currentTemp && d.temperature != null) elements.currentTemp.textContent = Number(d.temperature).toFixed(2);
            if (elements.currentHumidity && d.humidity != null) elements.currentHumidity.textContent = Number(d.humidity).toFixed(2);
            if (elements.targetTempDisplay && d.set_temperature != null) elements.targetTempDisplay.textContent = Number(d.set_temperature).toFixed(2);
            if (elements.targetHumidityDisplay && d.set_humidity != null) elements.targetHumidityDisplay.textContent = Number(d.set_humidity).toFixed(2);
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
            if (programNumberDisplay && d.set_program_number != null) {
                const progNum = String(d.set_program_number);
                programNumberDisplay.textContent = progNum.padStart(3, '0');
            }
            if (totalSegmentsDisplay && d.total_steps != null) {
                const totalSteps = String(d.total_steps);
                totalSegmentsDisplay.textContent = totalSteps.padStart(2, '0');
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
        })
        .catch(() => {});
}

// 页面导航功能
function navigateTo(page) {
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
        
        // 当进入菜单页面时，更新按钮状态
        if (pageId === 'menuPage') {
            updateMenuButtons();
            updateTestStatusText();
        }

        // 当进入定值试验或程式试验页面时，立即同步数据和设备ID
        if (pageId === 'constantPage' || pageId === 'programPage') {
            if (currentDeviceId) {
                updateDeviceIdDisplay(currentDeviceId);
            }
            fetchLatestData();
        }
    }
}

// 页面加载完成后初始化
window.addEventListener('DOMContentLoaded', init);
