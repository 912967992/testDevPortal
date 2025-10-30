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
    
    // 曲线按钮
    if (elements.chartBtn) {
        elements.chartBtn.addEventListener('click', () => {
            alert('曲线功能待开发');
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
    
    // 程式曲线按钮
    const programChartBtn = document.getElementById('programChartBtn');
    if (programChartBtn) {
        programChartBtn.addEventListener('click', () => {
            alert('曲线功能待开发');
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
        if (elements.modalTitle) {
            elements.modalTitle.textContent = deviceState.isRunning ? '停止' : '运行';
        }
    }
}

// 隐藏运行确认窗口
function hideRunConfirm() {
    if (elements.runConfirmModal) {
        elements.runConfirmModal.style.display = 'none';
    }
}

// 确认运行/停止
function confirmRun() {
    hideRunConfirm();
    
    // 检查是哪个页面的运行按钮
    const currentPage = document.querySelector('.page.active');
    if (currentPage && currentPage.id === 'programPage') {
        toggleProgramRun();
    } else {
        toggleRun();
    }
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
    
    // 更新菜单页面的设备ID显示
    const deviceIdElement = document.querySelector('#menuPage .device-id');
    if (deviceIdElement) {
        deviceIdElement.textContent = deviceId;
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
    fetch('/iot/data/latest')
        .then(r => r.json())
        .then(d => {
            if (!d) return;
            if (elements.currentTemp && d.temperature != null) elements.currentTemp.textContent = Number(d.temperature).toFixed(2);
            if (elements.currentHumidity && d.humidity != null) elements.currentHumidity.textContent = Number(d.humidity).toFixed(2);
            if (elements.targetTempDisplay && d.set_temperature != null) elements.targetTempDisplay.textContent = Number(d.set_temperature).toFixed(2);
            if (elements.targetHumidityDisplay && d.set_humidity != null) elements.targetHumidityDisplay.textContent = Number(d.set_humidity).toFixed(2);
            const programTempPower = document.getElementById('programTempPower');
            const programHumidityPower = document.getElementById('programHumidityPower');
            if (elements.tempPower && d.power_temperature != null) elements.tempPower.textContent = String(d.power_temperature) + '%';
            if (elements.humidityPower && d.power_humidity != null) elements.humidityPower.textContent = String(d.power_humidity) + '%';
            if (programTempPower && d.power_temperature != null) programTempPower.textContent = String(d.power_temperature) + '%';
            if (programHumidityPower && d.power_humidity != null) programHumidityPower.textContent = String(d.power_humidity) + '%';
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
    }
}

// 页面加载完成后初始化
window.addEventListener('DOMContentLoaded', init);
