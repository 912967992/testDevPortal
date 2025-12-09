// 全局变量
let currentPage = 1;
const pageSize = 20;
let totalPages = 1;
let chartInstances = {};
let currentData = [];
let allSampleData = []; // 保存按样品查询时的所有数据（用于模态框展示历史数据）
let currentSearchType = 'device'; // 当前搜索类型：'device' 或 'sample'

// 页面加载时初始化
document.addEventListener('DOMContentLoaded', function() {
    initPage();
    bindEvents();
    setDefaultTimeRange();
});

// 初始化页面
function initPage() {
    // 从URL参数或localStorage获取用户名
    const urlParams = new URLSearchParams(window.location.search);
    const username = urlParams.get('username') || localStorage.getItem('username') || '';
    
    if (username) {
        document.getElementById('username').textContent = username;
        localStorage.setItem('username', username);
    }
    
    // 初始化图表实例
    chartInstances.temperature = echarts.init(document.getElementById('temperatureChart'));
    chartInstances.humidity = echarts.init(document.getElementById('humidityChart'));
    chartInstances.comparison = echarts.init(document.getElementById('comparisonChart'));
    
    // 响应窗口大小变化
    window.addEventListener('resize', function() {
        Object.values(chartInstances).forEach(chart => {
            if (chart) {
                chart.resize();
            }
        });
    });
    
    // 初始化查询模式
    switchSearchMode('device');
}

// 切换查询模式
function switchSearchMode(mode) {
    currentSearchType = mode;
    
    // 更新模式卡片样式
    document.querySelectorAll('.mode-card').forEach(card => {
        card.classList.remove('active');
    });
    document.querySelector(`.mode-card[data-mode="${mode}"]`).classList.add('active');
    
    // 切换表单显示
    const deviceForm = document.getElementById('deviceSearchForm');
    const sampleForm = document.getElementById('sampleSearchForm');
    
    if (mode === 'device') {
        deviceForm.style.display = 'block';
        sampleForm.style.display = 'none';
        document.getElementById('searchModeTitle').textContent = '设备数据查询';
        document.getElementById('searchTips').textContent = '请输入设备ID，可查询该设备的所有温箱历史数据';
        
        // 清空样品查询字段
        document.getElementById('sampleCategory').value = '';
        document.getElementById('sampleModel').value = '';
        document.getElementById('sampleTester').value = '';
    } else {
        deviceForm.style.display = 'none';
        sampleForm.style.display = 'block';
        document.getElementById('searchModeTitle').textContent = '样品数据查询';
        document.getElementById('searchTips').textContent = '根据样品信息查询，支持模糊匹配。至少填写一个字段。';
        
        // 清空设备查询字段
        document.getElementById('deviceId').value = '';
    }
    
    // 重置结果
    hideCharts();
    hideStats();
    hideTable();
    hideEmptyState();
    hideVisualizationSection();
    hideSampleStatusSection();
    currentPage = 1;
    window.chartData = null;
}

// 绑定事件
function bindEvents() {
    // 回车键搜索
    document.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            searchData();
        }
    });
    
    // ESC键关闭模态框
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('sampleHistoryModal');
            if (modal && modal.style.display === 'flex') {
                closeSampleHistoryModal();
            }
        }
    });
}

// 设置默认时间范围（可选，不清空时间字段）
function setDefaultTimeRange() {
    // 不再自动设置时间，让用户可以自由选择是否使用时间过滤器
    // 如果需要默认值，可以取消下面的注释
    /*
    const now = new Date();
    const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    
    const formatDateTime = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    };
    
    document.getElementById('startTime').value = formatDateTime(sevenDaysAgo);
    document.getElementById('endTime').value = formatDateTime(now);
    */
}

// 搜索数据
async function searchData() {
    // 使用全局变量 currentSearchType（已通过 switchSearchMode 设置）
    
    // 清空样品详情缓存
    if (window.sampleDetailCache) {
        window.sampleDetailCache = {};
    }
    
    const startTime = document.getElementById('startTime').value;
    const endTime = document.getElementById('endTime').value;
    
    // 如果设置了时间，验证时间范围
    if (startTime && endTime) {
        if (new Date(startTime) > new Date(endTime)) {
            alert('开始时间不能晚于结束时间');
            return;
        }
    }
    
    showLoading();
    hideEmptyState();
    hideCharts(); // 查询时默认隐藏图表
    hideVisualizationSection(); // 隐藏可视化按钮
    
    try {
        let url = '/api/data/search?page=' + currentPage + '&pageSize=' + pageSize;
        
        // 只有设置了时间才添加到URL参数中
        if (startTime) {
            url += '&startTime=' + encodeURIComponent(startTime);
        }
        if (endTime) {
            url += '&endTime=' + encodeURIComponent(endTime);
        }
        
        if (currentSearchType === 'device') {
            const deviceId = document.getElementById('deviceId').value.trim();
            // 允许不输入设备ID，查询所有设备的数据
            if (deviceId) {
                url += '&deviceId=' + encodeURIComponent(deviceId);
            }
            // 如果不输入设备ID，不添加deviceId参数，后端会查询所有设备
        } else {
            const category = document.getElementById('sampleCategory').value.trim();
            const model = document.getElementById('sampleModel').value.trim();
            const tester = document.getElementById('sampleTester').value.trim();
            
            if (category) {
                url += '&category=' + encodeURIComponent(category);
            }
            if (model) {
                url += '&model=' + encodeURIComponent(model);
            }
            if (tester) {
                url += '&tester=' + encodeURIComponent(tester);
            }
        }
        
        const response = await fetch(url);
        const result = await response.json();
        
        hideLoading();
        
        if (result.success && result.data) {
            currentData = result.data.list || [];
            
            // 如果是按样品查询，保存所有数据用于模态框展示历史数据
            if (currentSearchType === 'sample') {
                // 需要获取所有数据，先查询所有数据（不分页）
                allSampleData = [];
                
                // 重新查询所有数据（不分页）
                let allDataUrl = '/api/data/search?page=1&pageSize=10000'; // 使用大pageSize获取所有数据
                if (startTime) {
                    allDataUrl += '&startTime=' + encodeURIComponent(startTime);
                }
                if (endTime) {
                    allDataUrl += '&endTime=' + encodeURIComponent(endTime);
                }
                const category = document.getElementById('sampleCategory').value.trim();
                const model = document.getElementById('sampleModel').value.trim();
                const tester = document.getElementById('sampleTester').value.trim();
                
                if (category) {
                    allDataUrl += '&category=' + encodeURIComponent(category);
                }
                if (model) {
                    allDataUrl += '&model=' + encodeURIComponent(model);
                }
                if (tester) {
                    allDataUrl += '&tester=' + encodeURIComponent(tester);
                }
                
                try {
                    const allDataResponse = await fetch(allDataUrl);
                    const allDataResult = await allDataResponse.json();
                    if (allDataResult.success && allDataResult.data) {
                        allSampleData = allDataResult.data.list || [];
                        // 按样品分组数据
                        groupSampleData(allSampleData);
                    }
                } catch (error) {
                    console.error('获取所有样品数据失败:', error);
                    // 如果获取失败，使用当前页数据
                    allSampleData = currentData;
                }
            } else {
                allSampleData = [];
            }
            
            // 打印样品调试信息到控制台（用于调试）
            if (result.data.sampleDebugInfo && result.data.sampleDebugInfo.length > 0) {
                console.log('========== 样品测试状态调试信息 ==========');
                result.data.sampleDebugInfo.forEach((sample, index) => {
                    const debugInfo = {
                        '设备ID': sample.deviceId,
                        '品类': sample.category,
                        '型号': sample.model,
                        '创建时间': sample.createdAt,
                        '更新时间': sample.updatedAt,
                        '是否在测试中': sample.isTesting,
                        '状态说明': sample.message
                    };
                    // 添加测试时长信息
                    if (sample.testStartTime || sample.testEndTime || sample.testDurationFormatted) {
                        debugInfo['测试开始时间'] = sample.testStartTime;
                        debugInfo['测试结束时间'] = sample.testEndTime || '进行中';
                        debugInfo['测试时长'] = sample.testDurationFormatted || '-';
                    }
                    console.log(`样品 ${index + 1}:`, debugInfo);
                });
                console.log('==========================================');
            }
            
            if (currentData.length === 0) {
                showEmptyState();
                hideCharts();
                hideStats();
                hideTable();
            } else {
                hideEmptyState();
                displayData(result.data);
                // 不自动显示图表，只显示可视化按钮
                prepareCharts(currentData);
                // 传递当前页数据和总数用于统计（注意：这里只统计当前页，如果需要统计全部数据，需要修改后端接口）
                displayStats({ list: currentData, total: result.data.total });
                // 保存sampleDebugInfo供toggleSampleHistory使用
                window.lastSampleDebugInfo = result.data.sampleDebugInfo;
                
                // 如果是按样品查询，显示样品状态
                if (currentSearchType === 'sample' && result.data.sampleDebugInfo && result.data.sampleDebugInfo.length > 0) {
                    displaySampleStatus(result.data.sampleDebugInfo);
                } else {
                    hideSampleStatusSection();
                }
                
                displayTable(currentData, result.data.total, result.data.sampleDebugInfo);
                // 显示数据可视化按钮
                showVisualizationSection();
            }
        } else {
            alert('查询失败：' + (result.message || '未知错误'));
            showEmptyState();
        }
    } catch (error) {
        console.error('查询错误:', error);
        hideLoading();
        alert('查询失败，请稍后重试');
        showEmptyState();
    }
}

// 显示数据
function displayData(data) {
    // 数据已在其他函数中处理
}

// 显示统计信息
function displayStats(data) {
    const stats = calculateStats(data.list || []);
    
    document.getElementById('totalCount').textContent = data.total || 0;
    document.getElementById('avgTemperature').textContent = stats.avgTemp.toFixed(2) + '℃';
    document.getElementById('avgHumidity').textContent = stats.avgHumidity.toFixed(2) + '%';
    
    const startTime = document.getElementById('startTime').value;
    const endTime = document.getElementById('endTime').value;
    const timeRangeText = formatTimeRange(startTime, endTime);
    document.getElementById('timeRange').textContent = timeRangeText || '全部时间';
    
    document.getElementById('statsSection').style.display = 'grid';
}

// 计算统计数据
function calculateStats(dataList) {
    let totalTemp = 0;
    let totalHumidity = 0;
    let count = 0;
    
    dataList.forEach(item => {
        if (item.temperature != null) {
            totalTemp += parseFloat(item.temperature);
            count++;
        }
        if (item.humidity != null) {
            totalHumidity += parseFloat(item.humidity);
        }
    });
    
    return {
        avgTemp: count > 0 ? totalTemp / count : 0,
        avgHumidity: count > 0 ? totalHumidity / count : 0
    };
}

// 格式化时间范围
function formatTimeRange(startTime, endTime) {
    if (!startTime && !endTime) {
        return null; // 返回null，让调用者显示"全部时间"
    }
    if (startTime && endTime) {
        const start = new Date(startTime);
        const end = new Date(endTime);
        return start.toLocaleDateString('zh-CN') + ' 至 ' + end.toLocaleDateString('zh-CN');
    }
    if (startTime) {
        const start = new Date(startTime);
        return '从 ' + start.toLocaleDateString('zh-CN') + ' 起';
    }
    if (endTime) {
        const end = new Date(endTime);
        return '至 ' + end.toLocaleDateString('zh-CN') + ' 止';
    }
    return null;
}

// 准备图表数据（不显示）
function prepareCharts(dataList) {
    if (!dataList || dataList.length === 0) return;
    
    // 按时间排序
    const sortedData = [...dataList].sort((a, b) => {
        return new Date(a.createdAt) - new Date(b.createdAt);
    });
    
    const timeData = sortedData.map(item => formatDateTime(item.createdAt));
    const tempData = sortedData.map(item => item.temperature || null);
    const humidityData = sortedData.map(item => item.humidity || null);
    const setTempData = sortedData.map(item => item.setTemperature || null);
    const setHumidityData = sortedData.map(item => item.setHumidity || null);
    
    // 保存数据到全局变量，供切换显示时使用
    window.chartData = {
        timeData: timeData,
        tempData: tempData,
        humidityData: humidityData,
        setTempData: setTempData,
        setHumidityData: setHumidityData
    };
}

// 显示图表
function displayCharts(dataList) {
    if (!dataList || dataList.length === 0) return;
    
    // 按时间排序
    const sortedData = [...dataList].sort((a, b) => {
        return new Date(a.createdAt) - new Date(b.createdAt);
    });
    
    const timeData = sortedData.map(item => formatDateTime(item.createdAt));
    const tempData = sortedData.map(item => item.temperature || null);
    const humidityData = sortedData.map(item => item.humidity || null);
    const setTempData = sortedData.map(item => item.setTemperature || null);
    const setHumidityData = sortedData.map(item => item.setHumidity || null);
    
    // 温度图表
    const tempOption = {
        title: {
            text: '温度变化趋势',
            left: 'center',
            textStyle: { fontSize: 16, fontWeight: 'bold' }
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['实际温度', '设定温度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: {
            type: 'value',
            name: '温度(℃)'
        },
        series: [
            {
                name: '实际温度',
                type: 'line',
                data: tempData,
                smooth: true,
                itemStyle: { color: '#6366f1' },
                areaStyle: { color: 'rgba(99, 102, 241, 0.1)' }
            },
            {
                name: '设定温度',
                type: 'line',
                data: setTempData,
                smooth: true,
                itemStyle: { color: '#f59e0b' },
                lineStyle: { type: 'dashed' }
            }
        ]
    };
    
    // 湿度图表
    const humidityOption = {
        title: {
            text: '湿度变化趋势',
            left: 'center',
            textStyle: { fontSize: 16, fontWeight: 'bold' }
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['实际湿度', '设定湿度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: {
            type: 'value',
            name: '湿度(%)'
        },
        series: [
            {
                name: '实际湿度',
                type: 'line',
                data: humidityData,
                smooth: true,
                itemStyle: { color: '#10b981' },
                areaStyle: { color: 'rgba(16, 185, 129, 0.1)' }
            },
            {
                name: '设定湿度',
                type: 'line',
                data: setHumidityData,
                smooth: true,
                itemStyle: { color: '#f59e0b' },
                lineStyle: { type: 'dashed' }
            }
        ]
    };
    
    // 对比图表
    const comparisonOption = {
        title: {
            text: '温度与湿度对比',
            left: 'center',
            textStyle: { fontSize: 16, fontWeight: 'bold' }
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['温度', '湿度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: [
            {
                type: 'value',
                name: '温度(℃)',
                position: 'left',
                axisLine: { lineStyle: { color: '#6366f1' } },
                axisLabel: { color: '#6366f1' }
            },
            {
                type: 'value',
                name: '湿度(%)',
                position: 'right',
                axisLine: { lineStyle: { color: '#10b981' } },
                axisLabel: { color: '#10b981' }
            }
        ],
        series: [
            {
                name: '温度',
                type: 'line',
                data: tempData,
                smooth: true,
                itemStyle: { color: '#6366f1' },
                yAxisIndex: 0
            },
            {
                name: '湿度',
                type: 'line',
                data: humidityData,
                smooth: true,
                itemStyle: { color: '#10b981' },
                yAxisIndex: 1
            }
        ]
    };
    
    chartInstances.temperature.setOption(tempOption);
    chartInstances.humidity.setOption(humidityOption);
    chartInstances.comparison.setOption(comparisonOption);
    
    document.getElementById('chartsSection').style.display = 'flex';
}

// 获取运行状态文本说明
function getRunStatusText(status) {
    const statusMap = {
        '0': '停止',
        '1': '运行',
        '2': '暂停'
    };
    return statusMap[status] || status;
}

// 获取运行模式文本说明
function getRunModeText(mode) {
    const modeMap = {
        '0': '程式试验',
        '1': '定值试验'
    };
    return modeMap[mode] || mode;
}

// 格式化运行状态显示
function formatRunStatus(item) {
    const runStatus = item.runStatus || '';
    const runMode = item.runMode || '';
    
    if (!runStatus && !runMode) {
        return '<span style="color: #9ca3af;">-</span>';
    }
    
    let html = '<div style="text-align: left; line-height: 1.6;">';
    
    if (runStatus) {
        const statusText = getRunStatusText(runStatus);
        html += `<div style="margin-bottom: 2px;">
            <span style="font-weight: 600; color: #6366f1; font-size: 12px;">状态:</span> 
            <span style="font-size: 13px; color: #1e293b;">${runStatus} (${statusText})</span>
        </div>`;
    }
    
    if (runMode) {
        const modeText = getRunModeText(runMode);
        html += `<div>
            <span style="font-weight: 600; color: #8b5cf6; font-size: 12px;">模式:</span> 
            <span style="font-size: 13px; color: #1e293b;">${runMode} (${modeText})</span>
        </div>`;
    }
    
    html += '</div>';
    return html;
}

// 按样品分组数据
function groupSampleData(dataList) {
    // 数据已经按样品查询，这里主要是为了后续扩展
    return dataList;
}

// 显示表格
function displayTable(dataList, total, sampleDebugInfo) {
    const tbody = document.getElementById('dataTableBody');
    tbody.innerHTML = '';
    
    let sampleMap = null; // 用于按样品查询时的分组数据
    
    // 如果是按样品查询，只显示每个样品的最新一条数据（当前温箱数据）
    if (currentSearchType === 'sample' && allSampleData.length > 0) {
        // 按样品分组，每个样品只显示最新的一条
        sampleMap = new Map();
        
        allSampleData.forEach(item => {
            if (item.samples && item.samples.length > 0) {
                item.samples.forEach(sample => {
                    // 使用sample.id作为唯一标识进行分组，确保每个样品ID独立汇总
                    const sampleKey = `sample_${sample.id || 'unknown'}`;
                    
                    if (!sampleMap.has(sampleKey)) {
                        sampleMap.set(sampleKey, {
                            sample: sample,
                            latestData: item,
                            allData: []
                        });
                    }
                    
                    const sampleInfo = sampleMap.get(sampleKey);
                    // 只有当这条数据确实包含该样品ID时才添加到allData中
                    // 检查sampleId和waitId字段是否包含该样品ID
                    const sampleIdStr = String(sample.id || '');
                    const itemSampleId = item.sampleId || '';
                    const itemWaitId = item.waitId || '';
                    
                    if (containsId(itemSampleId, sampleIdStr) || containsId(itemWaitId, sampleIdStr)) {
                        sampleInfo.allData.push(item);
                        
                        // 更新最新数据（按时间排序，最新的在前）
                        if (new Date(item.createdAt) > new Date(sampleInfo.latestData.createdAt)) {
                            sampleInfo.latestData = item;
                        }
                    }
                });
            }
        });
        
        // 显示每个样品的最新数据
        let rowIndex = 0;
        sampleMap.forEach((sampleInfo, sampleKey) => {
            const item = sampleInfo.latestData;
            const sample = sampleInfo.sample;
            
            // 主行：显示最新数据
            const mainRow = document.createElement('tr');
            mainRow.className = 'sample-main-row';
            mainRow.dataset.sampleKey = sampleKey;
            
            const sampleInfoHtml = formatSampleInfo([sample], rowIndex);
            const runStatusHtml = formatRunStatus(item);
            
            mainRow.innerHTML = `
                <td>${rowIndex + 1}</td>
                <td>${item.deviceId || '-'}</td>
                <td>${sampleInfoHtml}</td>
                <td>${item.temperature != null ? item.temperature.toFixed(2) : '-'}</td>
                <td>${item.humidity != null ? item.humidity.toFixed(2) : '-'}</td>
                <td>${item.setTemperature != null ? item.setTemperature.toFixed(2) : '-'}</td>
                <td>${item.setHumidity != null ? item.setHumidity.toFixed(2) : '-'}</td>
                <td>${runStatusHtml}</td>
                <td>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span>${formatDateTime(item.createdAt)}</span>
                        <button class="btn-view-history" onclick="showSampleHistoryModal('${sampleKey}')" 
                                style="padding: 4px 12px; font-size: 12px; background: #6366f1; color: white; border: none; border-radius: 4px; cursor: pointer; transition: all 0.2s;">
                            查看
                        </button>
                    </div>
                </td>
            `;
            tbody.appendChild(mainRow);
            
            rowIndex++;
        });
    } else {
        // 按设备ID查询或普通查询：显示所有数据
        dataList.forEach((item, index) => {
            const row = document.createElement('tr');
            
            // 格式化样品信息（传递行索引用于生成唯一ID）
            const sampleInfoHtml = formatSampleInfo(item.samples || [], (currentPage - 1) * pageSize + index);
            // 格式化运行状态
            const runStatusHtml = formatRunStatus(item);
            
            row.innerHTML = `
                <td>${(currentPage - 1) * pageSize + index + 1}</td>
                <td>${item.deviceId || '-'}</td>
                <td>${sampleInfoHtml}</td>
                <td>${item.temperature != null ? item.temperature.toFixed(2) : '-'}</td>
                <td>${item.humidity != null ? item.humidity.toFixed(2) : '-'}</td>
                <td>${item.setTemperature != null ? item.setTemperature.toFixed(2) : '-'}</td>
                <td>${item.setHumidity != null ? item.setHumidity.toFixed(2) : '-'}</td>
                <td>${runStatusHtml}</td>
                <td>${formatDateTime(item.createdAt)}</td>
            `;
            tbody.appendChild(row);
        });
    }
    
    // 如果是按样品查询，且是最后一页，在最后一条数据后显示测试状态
    if (sampleDebugInfo && sampleDebugInfo.length > 0 && dataList.length > 0) {
        // 检查是否是最后一页
        const isLastPage = currentPage >= totalPages;
        if (isLastPage) {
            // 获取最后一条数据（按时间排序，最后一条是最新的）
            const lastItem = dataList[dataList.length - 1];
            
            // 找到对应的样品信息（通过设备ID匹配，如果有多个样品，取第一个）
            const matchingSample = sampleDebugInfo.find(sample => {
                // 检查设备ID是否匹配
                return lastItem.deviceId === sample.deviceId;
            });
            
            // 如果没找到，尝试通过样品信息匹配
            let finalSample = matchingSample;
            if (!finalSample && lastItem.samples && lastItem.samples.length > 0) {
                const sampleInfo = lastItem.samples[0];
                finalSample = sampleDebugInfo.find(sample => {
                    return sample.category === sampleInfo.category && 
                           sample.model === sampleInfo.model &&
                           sample.deviceId === lastItem.deviceId;
                });
            }
            
            // 如果还是没找到，使用第一个样品信息（通常按样品查询时只有一个样品）
            if (!finalSample && sampleDebugInfo.length > 0) {
                finalSample = sampleDebugInfo[0];
            }
            
            if (finalSample) {
                const isTesting = finalSample.isTesting === true || finalSample.isTesting === 'true';
                const statusIcon = isTesting ? '🟢' : '🔴';
                const statusText = isTesting ? '测试进行中' : '测试已结束';
                
                // 在最后一行后添加状态行
                const statusRow = document.createElement('tr');
                statusRow.className = 'test-status-row';
                statusRow.innerHTML = `
                    <td colspan="9" style="background: linear-gradient(135deg, ${isTesting ? '#f0fdf4' : '#fef2f2'} 0%, ${isTesting ? '#dcfce7' : '#fee2e2'} 100%); padding: 16px; text-align: center; border-top: 2px solid ${isTesting ? '#10b981' : '#ef4444'};">
                        <div style="display: flex; align-items: center; justify-content: center; gap: 12px; font-size: 15px; font-weight: 600;">
                            <span style="font-size: 18px;">${statusIcon}</span>
                            <span style="color: ${isTesting ? '#065f46' : '#991b1b'};">
                                ${statusText}
                            </span>
                            <span style="color: #64748b; font-size: 13px; font-weight: 400; margin-left: 8px;">
                                (${finalSample.message || '未知状态'})
                            </span>
                        </div>
                    </td>
                `;
                tbody.appendChild(statusRow);
            }
        }
    }
    
    // 更新分页（按样品查询时不显示分页，因为只显示最新数据）
    if (currentSearchType === 'sample' && sampleMap) {
        // 按样品查询时，不显示分页控件
        const pagination = document.getElementById('pagination');
        pagination.innerHTML = `<div class="page-info">共找到 ${sampleMap.size} 个样品，点击"查看"按钮可查看每个样品的完整历史数据</div>`;
    } else {
        totalPages = Math.ceil(total / pageSize);
        updatePagination(total);
    }
    
    document.getElementById('tableSection').style.display = 'block';
}

// 格式化样品信息显示
function formatSampleInfo(samples, rowIndex) {
    if (!samples || samples.length === 0) {
        return '<span style="color: #9ca3af;">暂无样品信息</span>';
    }
    
    if (currentSearchType === 'device') {
        // 按设备ID搜索：显示等待样品和测试样品信息，可点击弹出
        const testingSamples = samples.filter(s => s.type === 'testing');
        const waitingSamples = samples.filter(s => s.type === 'waiting');
        
        let html = '<div style="text-align: left; line-height: 1.6;">';
        
        // 显示测试中的样品
        if (testingSamples.length > 0) {
            html += `<div style="margin-bottom: 8px;">
                <span style="font-weight: 600; color: #10b981; font-size: 11px;">🟢 测试中:</span>
            </div>`;
            testingSamples.forEach((sample, idx) => {
                const sampleKey = `testing_${rowIndex}_${idx}`;
                // 将样品数据存储到全局变量中
                if (!window.sampleDetailCache) {
                    window.sampleDetailCache = {};
                }
                window.sampleDetailCache[sampleKey] = sample;
                
                html += `<div style="margin-bottom: 4px; padding: 6px 10px; background: #f0fdf4; border-radius: 4px; cursor: pointer; border: 1px solid #10b981; transition: all 0.2s;" 
                    onclick="showSampleDetail('${sampleKey}')"
                    onmouseover="this.style.background='#dcfce7'; this.style.transform='translateX(2px)'"
                    onmouseout="this.style.background='#f0fdf4'; this.style.transform='translateX(0)'">
                    <div style="font-size: 11px; color: #dc2626; font-weight: 600; margin-bottom: 2px;">🏷️ ID: ${sample.id || '-'}</div>
                    <div style="font-size: 12px; color: #065f46;">
                        ${(sample.category || '-').replace(/</g, '&lt;').replace(/>/g, '&gt;')} - ${(sample.model || '-').replace(/</g, '&lt;').replace(/>/g, '&gt;')} (${(sample.tester || '-').replace(/</g, '&lt;').replace(/>/g, '&gt;')})
                    </div>
                </div>`;
            });
        }
        
        // 显示等候中的样品
        if (waitingSamples.length > 0) {
            html += `<div style="margin-top: 8px; margin-bottom: 8px;">
                <span style="font-weight: 600; color: #f59e0b; font-size: 11px;">🟡 等候中:</span>
            </div>`;
            waitingSamples.forEach((sample, idx) => {
                const sampleKey = `waiting_${rowIndex}_${idx}`;
                // 将样品数据存储到全局变量中
                if (!window.sampleDetailCache) {
                    window.sampleDetailCache = {};
                }
                window.sampleDetailCache[sampleKey] = sample;
                
                html += `<div style="margin-bottom: 4px; padding: 6px 10px; background: #fffbeb; border-radius: 4px; cursor: pointer; border: 1px solid #f59e0b; transition: all 0.2s;" 
                    onclick="showSampleDetail('${sampleKey}')"
                    onmouseover="this.style.background='#fef3c7'; this.style.transform='translateX(2px)'"
                    onmouseout="this.style.background='#fffbeb'; this.style.transform='translateX(0)'">
                    <div style="font-size: 11px; color: #dc2626; font-weight: 600; margin-bottom: 2px;">🏷️ ID: ${sample.id || '-'}</div>
                    <div style="font-size: 12px; color: #92400e;">
                        ${(sample.category || '-').replace(/</g, '&lt;').replace(/>/g, '&gt;')} - ${(sample.model || '-').replace(/</g, '&lt;').replace(/>/g, '&gt;')} (${(sample.tester || '-').replace(/</g, '&lt;').replace(/>/g, '&gt;')})
                    </div>
                </div>`;
            });
        }
        
        // 如果都没有，显示暂无
        if (testingSamples.length === 0 && waitingSamples.length === 0) {
            html += '<span style="color: #9ca3af; font-size: 12px;">暂无样品信息</span>';
        }
        
        html += '</div>';
        return html;
    } else {
        // 按样品信息搜索：显示样品信息和 status
        const sample = samples[0];
        const sampleId = sample.id || '-';
        const category = sample.category || '-';
        const model = sample.model || '-';
        const tester = sample.tester || '-';
        const status = sample.status || '-';
        
        // 状态显示映射
        const statusMap = {
            'WAITING': { text: '预约等候', color: '#f59e0b', bg: '#fffbeb' },
            'TESTING': { text: '测试中', color: '#10b981', bg: '#f0fdf4' },
            'COMPLETED': { text: '测试完成', color: '#6366f1', bg: '#eef2ff' },
            'CANCELLED': { text: '已取消', color: '#ef4444', bg: '#fef2f2' }
        };
        
        const statusInfo = statusMap[status] || { text: status, color: '#6b7280', bg: '#f9fafb' };
        
        let html = '<div style="text-align: left; line-height: 1.6;">';
        
        html += `<div style="margin-bottom: 8px;">
            <span style="display: inline-flex; align-items: center; padding: 4px 10px; background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%); border: 1.5px solid #dc2626; border-radius: 6px; font-size: 12px; font-weight: 700; color: #dc2626;">
                <span style="margin-right: 6px;">🏷️</span>
                <span>样品ID: ${sampleId}</span>
            </span>
        </div>`;
        html += `<div style="margin-bottom: 2px;">
            <span style="font-weight: 600; color: #6366f1; font-size: 12px;">品类:</span> 
            <span style="font-size: 13px;">${category}</span>
        </div>`;
        html += `<div style="margin-bottom: 2px;">
            <span style="font-weight: 600; color: #8b5cf6; font-size: 12px;">型号:</span> 
            <span style="font-size: 13px;">${model}</span>
        </div>`;
        html += `<div style="margin-bottom: 2px;">
            <span style="font-weight: 600; color: #10b981; font-size: 12px;">测试人员:</span> 
            <span style="font-size: 13px;">${tester}</span>
        </div>`;
        html += `<div>
            <span style="font-weight: 600; color: #374151; font-size: 12px;">状态:</span> 
            <span style="display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; background: ${statusInfo.bg}; color: ${statusInfo.color}; font-weight: 600;">
                ${statusInfo.text}
            </span>
        </div>`;
        html += '</div>';
        
        return html;
    }
}

// 显示样品详情弹窗
function showSampleDetail(sampleKey) {
    // 从缓存中获取样品数据
    if (!window.sampleDetailCache || !window.sampleDetailCache[sampleKey]) {
        console.error('样品数据不存在:', sampleKey);
        return;
    }
    const sample = window.sampleDetailCache[sampleKey];
    // 创建弹窗
    const modal = document.createElement('div');
    modal.id = 'sampleDetailModal';
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 10000;
        animation: fadeIn 0.3s;
    `;
    
    const statusMap = {
        'WAITING': { text: '预约等候', color: '#f59e0b', bg: '#fffbeb' },
        'TESTING': { text: '测试中', color: '#10b981', bg: '#f0fdf4' },
        'COMPLETED': { text: '测试完成', color: '#6366f1', bg: '#eef2ff' },
        'CANCELLED': { text: '已取消', color: '#ef4444', bg: '#fef2f2' }
    };
    
    const statusInfo = statusMap[sample.status] || { text: sample.status || '-', color: '#6b7280', bg: '#f9fafb' };
    const typeText = sample.type === 'testing' ? '测试中' : '等候中';
    const typeColor = sample.type === 'testing' ? '#10b981' : '#f59e0b';
    
    modal.innerHTML = `
        <div style="background: white; border-radius: 12px; padding: 24px; max-width: 500px; width: 90%; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); animation: slideIn 0.3s;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h3 style="margin: 0; color: #111827; font-size: 18px; font-weight: 700;">样品详细信息</h3>
                <button onclick="closeSampleDetail()" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #6b7280; padding: 0; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; border-radius: 4px; transition: all 0.2s;" 
                    onmouseover="this.style.background='#f3f4f6'" onmouseout="this.style.background='none'">×</button>
            </div>
            <div style="line-height: 1.8;">
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">类型:</span> 
                    <span style="margin-left: 8px; padding: 4px 12px; border-radius: 4px; font-size: 13px; background: ${sample.type === 'testing' ? '#f0fdf4' : '#fffbeb'}; color: ${typeColor}; font-weight: 600;">
                        ${typeText}
                    </span>
                </div>
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">品类:</span> 
                    <span style="margin-left: 8px; color: #111827; font-size: 14px;">${sample.category || '-'}</span>
                </div>
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">型号:</span> 
                    <span style="margin-left: 8px; color: #111827; font-size: 14px;">${sample.model || '-'}</span>
                </div>
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">测试人员:</span> 
                    <span style="margin-left: 8px; color: #111827; font-size: 14px;">${sample.tester || '-'}</span>
                </div>
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">状态:</span> 
                    <span style="margin-left: 8px; padding: 4px 12px; border-radius: 4px; font-size: 13px; background: ${statusInfo.bg}; color: ${statusInfo.color}; font-weight: 600;">
                        ${statusInfo.text}
                    </span>
                </div>
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">创建时间:</span> 
                    <span style="margin-left: 8px; color: #111827; font-size: 14px;">${formatDateTime(sample.createdAt || '')}</span>
                </div>
                ${sample.updatedAt ? `
                <div style="margin-bottom: 12px;">
                    <span style="font-weight: 600; color: #374151; font-size: 14px;">更新时间:</span> 
                    <span style="margin-left: 8px; color: #111827; font-size: 14px;">${formatDateTime(sample.updatedAt)}</span>
                </div>
                ` : ''}
            </div>
        </div>
    `;
    
    // 点击背景关闭
    modal.onclick = function(e) {
        if (e.target === modal) {
            closeSampleDetail();
        }
    };
    
    document.body.appendChild(modal);
    
    // 添加动画样式
    if (!document.getElementById('sampleDetailModalStyles')) {
        const style = document.createElement('style');
        style.id = 'sampleDetailModalStyles';
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes slideIn {
                from { transform: translateY(-20px); opacity: 0; }
                to { transform: translateY(0); opacity: 1; }
            }
        `;
        document.head.appendChild(style);
    }
}

// 关闭样品详情弹窗
function closeSampleDetail() {
    const modal = document.getElementById('sampleDetailModal');
    if (modal) {
        modal.remove();
    }
}

// 更新分页控件
function updatePagination(total) {
    const pagination = document.getElementById('pagination');
    pagination.innerHTML = '';
    
    if (totalPages <= 1) {
        pagination.innerHTML = `<div class="page-info">共 ${total} 条数据</div>`;
        return;
    }
    
    pagination.innerHTML = `
        <button ${currentPage === 1 ? 'disabled' : ''} onclick="goToPage(${currentPage - 1})">上一页</button>
        <div class="page-info">第 ${currentPage} / ${totalPages} 页，共 ${total} 条</div>
        <button ${currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${currentPage + 1})">下一页</button>
    `;
}

// 跳转页面
function goToPage(page) {
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    searchData();
}

// 重置搜索
function resetSearch() {
    // 重置表单字段（保留当前查询模式）
    document.getElementById('deviceId').value = '';
    document.getElementById('sampleCategory').value = '';
    document.getElementById('sampleModel').value = '';
    document.getElementById('sampleTester').value = '';
    clearTimeRange();
    
    // 不切换查询模式，保持用户当前选择的模式
    // switchSearchMode('device'); // 已移除，保持当前模式
    
    // 隐藏结果
    hideCharts();
    hideStats();
    hideTable();
    hideEmptyState();
    hideVisualizationSection();
    hideSampleStatusSection();
    
    currentPage = 1;
    window.chartData = null; // 清除图表数据
}

// 设置快速时间范围
function setQuickTime(days) {
    const now = new Date();
    const pastDate = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
    
    const formatDateTime = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    };
    
    document.getElementById('startTime').value = formatDateTime(pastDate);
    document.getElementById('endTime').value = formatDateTime(now);
}

// 清空时间范围
function clearTimeRange() {
    document.getElementById('startTime').value = '';
    document.getElementById('endTime').value = '';
}

// 切换图表显示/隐藏
function toggleChart(chartName) {
    const chartContainer = document.querySelector(`#${chartName}Chart`).parentElement.parentElement;
    const isHidden = chartContainer.style.display === 'none';
    chartContainer.style.display = isHidden ? 'block' : 'none';
    
    if (isHidden) {
        setTimeout(() => {
            chartInstances[chartName]?.resize();
        }, 100);
    }
}

// 切换数据可视化（显示/隐藏图表）
function toggleVisualization() {
    const chartsSection = document.getElementById('chartsSection');
    const visualizationBtnText = document.getElementById('visualizationBtnText');
    const isHidden = chartsSection.style.display === 'none' || chartsSection.style.display === '';
    
    if (isHidden) {
        // 显示图表
        if (window.chartData) {
            renderCharts(window.chartData);
        } else if (currentData && currentData.length > 0) {
            displayCharts(currentData);
        }
        chartsSection.style.display = 'flex';
        visualizationBtnText.textContent = '隐藏数据可视化';
    } else {
        // 隐藏图表
        chartsSection.style.display = 'none';
        visualizationBtnText.textContent = '显示数据可视化';
    }
}

// 渲染图表（使用已准备的数据）
function renderCharts(chartData) {
    const { timeData, tempData, humidityData, setTempData, setHumidityData } = chartData;
    
    // 温度图表
    const tempOption = {
        title: {
            text: '温度变化趋势',
            left: 'center',
            textStyle: { fontSize: 16, fontWeight: 'bold' }
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['实际温度', '设定温度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: {
            type: 'value',
            name: '温度(℃)'
        },
        series: [
            {
                name: '实际温度',
                type: 'line',
                data: tempData,
                smooth: true,
                itemStyle: { color: '#6366f1' },
                areaStyle: { color: 'rgba(99, 102, 241, 0.1)' }
            },
            {
                name: '设定温度',
                type: 'line',
                data: setTempData,
                smooth: true,
                itemStyle: { color: '#f59e0b' },
                lineStyle: { type: 'dashed' }
            }
        ]
    };
    
    // 湿度图表
    const humidityOption = {
        title: {
            text: '湿度变化趋势',
            left: 'center',
            textStyle: { fontSize: 16, fontWeight: 'bold' }
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['实际湿度', '设定湿度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: {
            type: 'value',
            name: '湿度(%)'
        },
        series: [
            {
                name: '实际湿度',
                type: 'line',
                data: humidityData,
                smooth: true,
                itemStyle: { color: '#10b981' },
                areaStyle: { color: 'rgba(16, 185, 129, 0.1)' }
            },
            {
                name: '设定湿度',
                type: 'line',
                data: setHumidityData,
                smooth: true,
                itemStyle: { color: '#f59e0b' },
                lineStyle: { type: 'dashed' }
            }
        ]
    };
    
    // 对比图表
    const comparisonOption = {
        title: {
            text: '温度与湿度对比',
            left: 'center',
            textStyle: { fontSize: 16, fontWeight: 'bold' }
        },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['温度', '湿度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: [
            {
                type: 'value',
                name: '温度(℃)',
                position: 'left',
                axisLine: { lineStyle: { color: '#6366f1' } },
                axisLabel: { color: '#6366f1' }
            },
            {
                type: 'value',
                name: '湿度(%)',
                position: 'right',
                axisLine: { lineStyle: { color: '#10b981' } },
                axisLabel: { color: '#10b981' }
            }
        ],
        series: [
            {
                name: '温度',
                type: 'line',
                data: tempData,
                smooth: true,
                itemStyle: { color: '#6366f1' },
                yAxisIndex: 0
            },
            {
                name: '湿度',
                type: 'line',
                data: humidityData,
                smooth: true,
                itemStyle: { color: '#10b981' },
                yAxisIndex: 1
            }
        ]
    };
    
    chartInstances.temperature.setOption(tempOption);
    chartInstances.humidity.setOption(humidityOption);
    chartInstances.comparison.setOption(comparisonOption);
    
    // 等待DOM更新后调整图表大小
    setTimeout(() => {
        Object.values(chartInstances).forEach(chart => {
            if (chart) {
                chart.resize();
            }
        });
    }, 100);
}

// 导出数据
function exportData() {
    if (!currentData || currentData.length === 0) {
        alert('没有数据可导出');
        return;
    }
    
    // 构建CSV内容
    // 如果是按样品查询，添加样品ID列
    const includeSampleId = currentSearchType === 'sample';
    let csv = '序号,设备ID' + (includeSampleId ? ',样品ID' : '') + ',品类,型号,测试人员,温度(℃),湿度(%),设定温度(℃),设定湿度(%),运行状态,运行模式,记录时间\n';
    
    currentData.forEach((item, index) => {
        // 获取样品信息（取最新的样品）
        let sampleId = '-';
        let category = '-';
        let model = '-';
        let tester = '-';
        
        if (item.samples && item.samples.length > 0) {
            const sample = item.samples[0]; // 取最新的样品
            sampleId = sample.id || '-';
            category = sample.category || '-';
            model = sample.model || '-';
            tester = sample.tester || '-';
        }
        
        // 获取运行状态和模式
        const runStatus = item.runStatus || '-';
        const runMode = item.runMode || '-';
        
        if (includeSampleId) {
            csv += `${index + 1},${item.deviceId || ''},${sampleId},${category},${model},${tester},` +
                   `${item.temperature || ''},${item.humidity || ''},${item.setTemperature || ''},${item.setHumidity || ''},${runStatus},${runMode},` +
                   `${formatDateTime(item.createdAt)}\n`;
        } else {
            csv += `${index + 1},${item.deviceId || ''},${category},${model},${tester},` +
                   `${item.temperature || ''},${item.humidity || ''},${item.setTemperature || ''},${item.setHumidity || ''},${runStatus},${runMode},` +
                   `${formatDateTime(item.createdAt)}\n`;
        }
    });
    
    // 创建下载链接
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `温箱数据_${new Date().getTime()}.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

// 格式化日期时间
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

// 显示/隐藏函数
function showLoading() {
    document.getElementById('loadingOverlay').style.display = 'flex';
}

function hideLoading() {
    document.getElementById('loadingOverlay').style.display = 'none';
}

function showEmptyState() {
    document.getElementById('emptyState').style.display = 'block';
}

function hideEmptyState() {
    document.getElementById('emptyState').style.display = 'none';
}

function hideCharts() {
    document.getElementById('chartsSection').style.display = 'none';
}

function hideStats() {
    document.getElementById('statsSection').style.display = 'none';
}

function hideTable() {
    document.getElementById('tableSection').style.display = 'none';
}

function showVisualizationSection() {
    document.getElementById('visualizationSection').style.display = 'block';
}

function hideVisualizationSection() {
    document.getElementById('visualizationSection').style.display = 'none';
}

function hideSampleStatusSection() {
    document.getElementById('sampleStatusSection').style.display = 'none';
}

// 显示样品测试状态
function displaySampleStatus(sampleDebugInfo) {
    const statusSection = document.getElementById('sampleStatusSection');
    const statusList = document.getElementById('sampleStatusList');
    const statusSummary = document.getElementById('sampleStatusSummary');
    
    if (!sampleDebugInfo || sampleDebugInfo.length === 0) {
        statusSection.style.display = 'none';
        return;
    }
    
    // 统计样品状态
    const testingCount = sampleDebugInfo.filter(s => s.isTesting === true || s.isTesting === 'true').length;
    const finishedCount = sampleDebugInfo.length - testingCount;
    
    statusSummary.innerHTML = `共找到 ${sampleDebugInfo.length} 个样品，其中 <span style="color: #10b981; font-weight: 600;">${testingCount} 个正在测试</span>，<span style="color: #ef4444; font-weight: 600;">${finishedCount} 个已完成</span>`;
    
    statusList.innerHTML = '';
    
    sampleDebugInfo.forEach((sample, index) => {
        const statusItem = document.createElement('div');
        statusItem.className = 'sample-status-item';
        
        const isTesting = sample.isTesting === true || sample.isTesting === 'true';
        const statusClass = isTesting ? 'status-testing' : 'status-finished';
        const statusIcon = isTesting ? '🟢' : '🔴';
        const statusText = isTesting ? '测试进行中' : '测试已结束';
        
        // 格式化时间段显示
        const formatPeriods = (periods, type) => {
            if (!periods || periods.length === 0) {
                return `<div style="color: #9ca3af; font-size: 12px; margin-top: 4px;">暂无${type}记录</div>`;
            }
            
            let html = `<div style="margin-top: 8px;">
                <div style="font-size: 12px; font-weight: 600; color: ${type === '测试' ? '#10b981' : '#f59e0b'}; margin-bottom: 4px;">
                    ${type === '测试' ? '🟢' : '🟡'} ${type}时间段 (共${periods.length}段):
                </div>`;
            
            periods.forEach((period, idx) => {
                const startTime = period.startTime ? formatDateTime(period.startTime) : '-';
                const endTime = period.endTime ? formatDateTime(period.endTime) : '-';
                const duration = calculateDuration(period.startTime, period.endTime);
                
                html += `<div style="padding: 6px 10px; margin-bottom: 4px; background: ${type === '测试' ? '#f0fdf4' : '#fffbeb'}; border-left: 3px solid ${type === '测试' ? '#10b981' : '#f59e0b'}; border-radius: 4px; font-size: 12px;">
                    <div style="font-weight: 600; color: #374151; margin-bottom: 2px;">${type}阶段 ${idx + 1}</div>
                    <div style="color: #6b7280; line-height: 1.5;">
                        <div>开始: ${startTime}</div>
                        <div>结束: ${endTime}</div>
                        ${duration ? `<div style="color: ${type === '测试' ? '#10b981' : '#f59e0b'}; font-weight: 600; margin-top: 2px;">⏱️ 持续时长: ${duration}</div>` : ''}
                    </div>
                </div>`;
            });
            
            html += '</div>';
            return html;
        };
        
        const testingPeriodsHtml = formatPeriods(sample.testingPeriods || [], '测试');
        const waitingPeriodsHtml = formatPeriods(sample.waitingPeriods || [], '等候');
        
        // 显示测试时长信息
        let testDurationHtml = '';
        if (sample.testStartTime || sample.testEndTime || sample.testDurationFormatted) {
            const startTime = sample.testStartTime ? formatDateTime(sample.testStartTime) : '-';
            const endTime = sample.testEndTime ? formatDateTime(sample.testEndTime) : (isTesting ? '进行中' : '-');
            const duration = sample.testDurationFormatted || '-';
            
            testDurationHtml = `
                <div style="margin-top: 12px; padding: 12px; background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); border: 1.5px solid #3b82f6; border-radius: 8px;">
                    <div style="font-size: 13px; font-weight: 700; color: #1e40af; margin-bottom: 8px; display: flex; align-items: center;">
                        <span style="margin-right: 6px;">⏱️</span>
                        <span>测试时长统计</span>
                    </div>
                    <div style="font-size: 12px; color: #374151; line-height: 1.8;">
                        <div><strong>开始时间:</strong> ${startTime}</div>
                        <div><strong>结束时间:</strong> ${endTime}</div>
                        <div style="margin-top: 6px; padding-top: 6px; border-top: 1px solid #bfdbfe;">
                            <strong style="color: #1e40af; font-size: 13px;">总时长:</strong> 
                            <span style="color: #1e40af; font-weight: 700; font-size: 14px; margin-left: 6px;">${duration}</span>
                        </div>
                    </div>
                </div>
            `;
        }
        
        statusItem.innerHTML = `
            <div class="sample-status-item-header">
                <div class="sample-status-item-title">
                    <span class="sample-status-number">${index + 1}</span>
                    <div class="sample-status-info">
                        <div class="sample-status-name">
                            ${sample.category || '-'} - ${sample.model || '-'}
                            ${sample.id ? `<span style="display: inline-flex; align-items: center; margin-left: 8px; padding: 3px 8px; background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%); border: 1.5px solid #dc2626; border-radius: 5px; font-size: 11px; font-weight: 700; color: #dc2626;">
                                <span style="margin-right: 4px;">🏷️</span>
                                <span>ID: ${sample.id}</span>
                            </span>` : ''}
                        </div>
                        <div class="sample-status-meta">
                            <span>设备: ${sample.deviceId || '-'}</span>
                            ${sample.tester ? `<span>测试人员: ${sample.tester}</span>` : ''}
                        </div>
                    </div>
                </div>
                <div class="sample-status-badge ${statusClass}">
                    <span class="status-icon">${statusIcon}</span>
                    <span class="status-text">${statusText}</span>
                </div>
            </div>
            <div class="sample-status-item-message">
                ${sample.message || '未知状态'}
            </div>
            ${testDurationHtml}
            <div style="margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb;">
                ${testingPeriodsHtml}
                ${waitingPeriodsHtml}
            </div>
        `;
        
        statusList.appendChild(statusItem);
    });
    
    statusSection.style.display = 'block';
}

// 计算时间段持续时长
function calculateDuration(startTime, endTime) {
    if (!startTime || !endTime) {
        return null;
    }
    
    try {
        const start = new Date(startTime);
        const end = new Date(endTime);
        const diffMs = end - start;
        
        if (diffMs < 0) {
            return null;
        }
        
        const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
        const seconds = Math.floor((diffMs % (1000 * 60)) / 1000);
        
        let duration = '';
        if (days > 0) {
            duration += `${days}天 `;
        }
        if (hours > 0) {
            duration += `${hours}小时 `;
        }
        if (minutes > 0) {
            duration += `${minutes}分钟 `;
        }
        if (seconds > 0 || duration === '') {
            duration += `${seconds}秒`;
        }
        
        return duration.trim();
    } catch (e) {
        console.error('计算时长失败:', e);
        return null;
    }
}

// 显示样品历史数据模态框
function showSampleHistoryModal(sampleKey) {
    // 从sampleKey中提取样品ID
    const sampleIdMatch = sampleKey.match(/sample_(\d+)/);
    if (!sampleIdMatch) {
        alert('无效的样品标识');
        return;
    }
    const targetSampleId = sampleIdMatch[1];
    
    // 找到对应的样品数据
    const sampleMap = new Map();
    
    // 先按样品ID分组，收集包含该样品ID的数据
    allSampleData.forEach(item => {
        // 检查该数据是否包含目标样品ID（从sampleId或waitId字段）
        const itemSampleId = item.sampleId || '';
        const itemWaitId = item.waitId || '';
        const containsTargetSample = containsId(itemSampleId, targetSampleId) || containsId(itemWaitId, targetSampleId);
        
        if (containsTargetSample) {
            // 如果数据包含目标样品ID，从samples字段中查找对应的样品信息
            if (item.samples && item.samples.length > 0) {
                item.samples.forEach(sample => {
                    if (String(sample.id) === targetSampleId) {
                        const key = `sample_${sample.id || 'unknown'}`;
                        
                        if (!sampleMap.has(key)) {
                            sampleMap.set(key, {
                                sample: sample,
                                latestData: item,
                                allData: [],
                                deviceId: item.deviceId // 保存设备ID，用于后续查找下一条数据
                            });
                        }
                        
                        const sampleInfo = sampleMap.get(key);
                        sampleInfo.allData.push(item);
                        
                        if (new Date(item.createdAt) > new Date(sampleInfo.latestData.createdAt)) {
                            sampleInfo.latestData = item;
                        }
                    }
                });
            } else {
                // 如果samples字段为空，但数据包含目标样品ID，也需要处理
                // 这种情况可能发生在数据转换时样品信息未正确关联
                const key = `sample_${targetSampleId}`;
                if (!sampleMap.has(key)) {
                    // 尝试从window.lastSampleDebugInfo中获取样品信息
                    let sampleInfo = null;
                    if (window.lastSampleDebugInfo) {
                        sampleInfo = window.lastSampleDebugInfo.find(s => String(s.id) === targetSampleId);
                    }
                    
                    sampleMap.set(key, {
                        sample: sampleInfo || { id: targetSampleId },
                        latestData: item,
                        allData: [],
                        deviceId: item.deviceId
                    });
                }
                
                const sampleInfo = sampleMap.get(key);
                sampleInfo.allData.push(item);
                
                if (new Date(item.createdAt) > new Date(sampleInfo.latestData.createdAt)) {
                    sampleInfo.latestData = item;
                }
            }
        }
    });
    
    // 对于每个样品，查找下一条不包含该样品ID的数据（用于标记测试结束时间）
    sampleMap.forEach((sampleInfo, key) => {
        const sample = sampleInfo.sample;
        const sampleIdStr = String(sample.id || targetSampleId);
        const deviceId = sampleInfo.deviceId;
        
        if (sampleInfo.allData.length > 0) {
            // 按时间排序，找到最后一条包含该样品ID的数据
            const sortedData = [...sampleInfo.allData].sort((a, b) => 
                new Date(a.createdAt) - new Date(b.createdAt)
            );
            const lastContainingData = sortedData[sortedData.length - 1];
            
            // 在所有数据中查找该设备在最后一条包含该样品ID的数据之后的第一条数据
            // 这条数据可能不包含该样品ID，但需要包含进来用于标记测试结束时间
            // 注意：需要从所有返回的数据中查找，包括samples字段为空的数据
            const allDeviceData = allSampleData.filter(item => item.deviceId === deviceId);
            allDeviceData.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
            
            const lastContainingIndex = allDeviceData.findIndex(item => 
                item.id === lastContainingData.id || 
                (item.createdAt === lastContainingData.createdAt && item.deviceId === lastContainingData.deviceId)
            );
            
            // 如果找到了最后一条包含该样品ID的数据，且下一条数据存在且不包含该样品ID，则添加
            if (lastContainingIndex >= 0 && lastContainingIndex < allDeviceData.length - 1) {
                const nextData = allDeviceData[lastContainingIndex + 1];
                const nextSampleId = nextData.sampleId || '';
                const nextWaitId = nextData.waitId || '';
                
                // 如果下一条数据不包含该样品ID，则添加（用于标记测试结束时间）
                if (!containsId(nextSampleId, sampleIdStr) && !containsId(nextWaitId, sampleIdStr)) {
                    // 检查是否已经添加过（避免重复）
                    const alreadyAdded = sampleInfo.allData.some(item => 
                        item.id === nextData.id || 
                        (item.createdAt === nextData.createdAt && item.deviceId === nextData.deviceId)
                    );
                    if (!alreadyAdded) {
                        sampleInfo.allData.push(nextData);
                    }
                }
            }
        }
    });
    
    const sampleInfo = sampleMap.get(sampleKey);
    if (!sampleInfo) {
        alert('未找到样品数据');
        return;
    }
    
    const sample = sampleInfo.sample;
    const allHistoryData = sampleInfo.allData
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)); // 按时间倒序
    
    // 将数据分成等候区域和测试区域
    const sampleIdStr = String(sample.id || targetSampleId);
    const waitingData = []; // 等候区域数据（wait_id包含该样品ID）
    const testingData = []; // 测试区域数据（sample_id包含该样品ID，以及测试结束标记数据）
    
    allHistoryData.forEach(item => {
        const itemSampleId = item.sampleId || '';
        const itemWaitId = item.waitId || '';
        const inSampleId = containsId(itemSampleId, sampleIdStr);
        const inWaitId = containsId(itemWaitId, sampleIdStr);
        
        if (inSampleId) {
            // 在sample_id中，属于测试区域
            testingData.push(item);
        } else if (inWaitId) {
            // 在wait_id中，属于等候区域
            waitingData.push(item);
        } else {
            // 不包含该样品ID，可能是测试结束标记数据，也归入测试区域
            testingData.push(item);
        }
    });
    
    // 设置模态框标题和样品信息
    document.getElementById('modalSampleTitle').textContent = 
        `${sample.category || '-'} - ${sample.model || '-'} 历史数据`;
    
    // 显示样品基本信息
    const statusMap = {
        'WAITING': { text: '预约等候', color: '#f59e0b', bg: '#fffbeb' },
        'TESTING': { text: '测试中', color: '#10b981', bg: '#f0fdf4' },
        'COMPLETED': { text: '测试完成', color: '#6366f1', bg: '#eef2ff' },
        'CANCELLED': { text: '已取消', color: '#ef4444', bg: '#fef2f2' }
    };
    const statusInfo = statusMap[sample.status] || { text: sample.status || '-', color: '#6b7280', bg: '#f9fafb' };
    
    document.getElementById('modalSampleInfo').innerHTML = `
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; padding: 16px; background: #f9fafb; border-radius: 8px; margin-bottom: 16px;">
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 6px;">样品ID</div>
                <div style="display: inline-flex; align-items: center; padding: 6px 12px; background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%); border: 1.5px solid #dc2626; border-radius: 6px; font-size: 13px; font-weight: 700; color: #dc2626;">
                    <span style="margin-right: 6px;">🏷️</span>
                    <span>${sample.id || '-'}</span>
                </div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">品类</div>
                <div style="font-size: 14px; font-weight: 600; color: #111827;">${sample.category || '-'}</div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">型号</div>
                <div style="font-size: 14px; font-weight: 600; color: #111827;">${sample.model || '-'}</div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">测试人员</div>
                <div style="font-size: 14px; font-weight: 600; color: #111827;">${sample.tester || '-'}</div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">设备ID</div>
                <div style="font-size: 14px; font-weight: 600; color: #111827;">${sampleInfo.latestData.deviceId || '-'}</div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">状态</div>
                <span style="display: inline-block; padding: 4px 12px; border-radius: 4px; font-size: 12px; background: ${statusInfo.bg}; color: ${statusInfo.color}; font-weight: 600;">
                    ${statusInfo.text}
                </span>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">数据总数</div>
                <div style="font-size: 14px; font-weight: 600; color: #111827;">${allHistoryData.length} 条</div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">🟢 测试区域</div>
                <div style="font-size: 14px; font-weight: 600; color: #10b981;">${testingData.length} 条</div>
            </div>
            <div>
                <div style="font-size: 12px; color: #6b7280; margin-bottom: 4px;">🟡 等候区域</div>
                <div style="font-size: 14px; font-weight: 600; color: #f59e0b;">${waitingData.length} 条</div>
            </div>
        </div>
    `;
    
    // 按时间排序（从新到旧）
    waitingData.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    testingData.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    
    // 识别结束标记数据
    // 测试结束标记：在测试区域中，不包含该样品ID在sample_id和wait_id中的数据
    const testingEndMarkers = new Set();
    testingData.forEach(item => {
        const itemSampleId = item.sampleId || '';
        const itemWaitId = item.waitId || '';
        const inSampleId = containsId(itemSampleId, sampleIdStr);
        const inWaitId = containsId(itemWaitId, sampleIdStr);
        if (!inSampleId && !inWaitId) {
            // 这是测试结束标记数据
            testingEndMarkers.add(item.id || item.createdAt);
        }
    });
    
    // 等候结束标记：在等候区域中，找出最后一条包含该样品ID在wait_id中的数据
    // 然后从所有数据中找出下一条不包含该样品ID在wait_id中的数据
    // 如果这条下一条数据在等候区域中，则标记为等候结束
    const waitingEndMarkers = new Set();
    if (waitingData.length > 0) {
        // 按时间排序（从早到晚）找到最后一条等候数据
        const sortedWaitingData = [...waitingData].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
        const lastWaitingData = sortedWaitingData[sortedWaitingData.length - 1];
        
        // 在所有数据中查找该设备在最后一条等候数据之后的第一条数据
        const allSortedData = [...allHistoryData].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
        const lastWaitingIndex = allSortedData.findIndex(item => 
            item.id === lastWaitingData.id || 
            (item.createdAt === lastWaitingData.createdAt && item.deviceId === lastWaitingData.deviceId)
        );
        
        // 如果找到了最后一条等候数据，且下一条数据存在且不包含该样品ID在wait_id中
        if (lastWaitingIndex >= 0 && lastWaitingIndex < allSortedData.length - 1) {
            const nextData = allSortedData[lastWaitingIndex + 1];
            const nextWaitId = nextData.waitId || '';
            const nextInWaitId = containsId(nextWaitId, sampleIdStr);
            // 如果下一条数据不包含该样品ID在wait_id中，且是同一设备，则最后一条等候数据是等候结束标记
            if (!nextInWaitId && nextData.deviceId === lastWaitingData.deviceId) {
                waitingEndMarkers.add(lastWaitingData.id || lastWaitingData.createdAt);
            }
        } else if (lastWaitingIndex >= 0) {
            // 如果没有下一条数据，最后一条等候数据就是等候结束标记
            waitingEndMarkers.add(lastWaitingData.id || lastWaitingData.createdAt);
        }
    }
    
    // 填充历史数据表格，分成两个区域
    const tbody = document.getElementById('modalHistoryTableBody');
    tbody.innerHTML = '';
    
    let globalIndex = 1;
    
    // 测试区域
    if (testingData.length > 0) {
        const sectionRow = document.createElement('tr');
        sectionRow.className = 'data-section-header';
        sectionRow.innerHTML = `
            <td colspan="8" style="background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%); 
                                   border: 2px solid #10b981; border-radius: 8px; padding: 12px; 
                                   font-weight: 700; font-size: 15px; color: #065f46; text-align: center;">
                🟢 测试区域 (共 ${testingData.length} 条数据)
            </td>
        `;
        tbody.appendChild(sectionRow);
        
        testingData.forEach((item, index) => {
            const row = document.createElement('tr');
            row.className = 'testing-data-row';
            const runStatusHtml = formatRunStatus(item);
            
            // 判断是否是测试结束标记
            const isEndMarker = testingEndMarkers.has(item.id || item.createdAt);
            const endMarkerStyle = isEndMarker ? 'background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%); border-left: 4px solid #ef4444;' : '';
            const endMarkerBadge = isEndMarker ? '<span style="display: inline-block; padding: 2px 8px; background: #ef4444; color: white; border-radius: 4px; font-size: 11px; font-weight: 600; margin-left: 8px;">测试结束</span>' : '';
            
            row.innerHTML = `
                <td style="${endMarkerStyle}">${globalIndex++}${endMarkerBadge}</td>
                <td style="${endMarkerStyle}">${item.deviceId || '-'}</td>
                <td style="${endMarkerStyle}">${item.temperature != null ? item.temperature.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${item.humidity != null ? item.humidity.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${item.setTemperature != null ? item.setTemperature.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${item.setHumidity != null ? item.setHumidity.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${runStatusHtml}</td>
                <td style="${endMarkerStyle}">${formatDateTime(item.createdAt)}</td>
            `;
            tbody.appendChild(row);
        });
    }
    
    // 等候区域
    if (waitingData.length > 0) {
        const sectionRow = document.createElement('tr');
        sectionRow.className = 'data-section-header';
        sectionRow.innerHTML = `
            <td colspan="8" style="background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%); 
                                   border: 2px solid #f59e0b; border-radius: 8px; padding: 12px; 
                                   font-weight: 700; font-size: 15px; color: #92400e; text-align: center; margin-top: 20px;">
                🟡 等候区域 (共 ${waitingData.length} 条数据)
            </td>
        `;
        tbody.appendChild(sectionRow);
        
        waitingData.forEach((item, index) => {
            const row = document.createElement('tr');
            row.className = 'waiting-data-row';
            const runStatusHtml = formatRunStatus(item);
            
            // 判断是否是等候结束标记
            const isEndMarker = waitingEndMarkers.has(item.id || item.createdAt);
            const endMarkerStyle = isEndMarker ? 'background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%); border-left: 4px solid #ef4444;' : '';
            const endMarkerBadge = isEndMarker ? '<span style="display: inline-block; padding: 2px 8px; background: #ef4444; color: white; border-radius: 4px; font-size: 11px; font-weight: 600; margin-left: 8px;">等候结束</span>' : '';
            
            row.innerHTML = `
                <td style="${endMarkerStyle}">${globalIndex++}${endMarkerBadge}</td>
                <td style="${endMarkerStyle}">${item.deviceId || '-'}</td>
                <td style="${endMarkerStyle}">${item.temperature != null ? item.temperature.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${item.humidity != null ? item.humidity.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${item.setTemperature != null ? item.setTemperature.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${item.setHumidity != null ? item.setHumidity.toFixed(2) : '-'}</td>
                <td style="${endMarkerStyle}">${runStatusHtml}</td>
                <td style="${endMarkerStyle}">${formatDateTime(item.createdAt)}</td>
            `;
            tbody.appendChild(row);
        });
    }
    
    // 如果没有数据，显示提示
    if (waitingData.length === 0 && testingData.length === 0) {
        const emptyRow = document.createElement('tr');
        emptyRow.innerHTML = `
            <td colspan="8" style="text-align: center; padding: 40px; color: #9ca3af;">
                暂无历史数据
            </td>
        `;
        tbody.appendChild(emptyRow);
    }
    
    // 渲染图表
    renderModalCharts(allHistoryData);
    
    // 渲染过程时间轴图
    renderModalProcessChart(sample, sampleInfo);
    
    // 显示模态框
    document.getElementById('sampleHistoryModal').style.display = 'flex';
    
    // 等待DOM更新后调整图表大小
    setTimeout(() => {
        if (window.modalChartInstances) {
            Object.values(window.modalChartInstances).forEach(chart => {
                if (chart) {
                    chart.resize();
                }
            });
        }
        if (window.modalProcessChartInstance) {
            window.modalProcessChartInstance.resize();
        }
    }, 300);
}

// 渲染模态框中的图表
function renderModalCharts(dataList) {
    if (!dataList || dataList.length === 0) {
        return;
    }
    
    // 按时间排序（从早到晚）
    const sortedData = [...dataList].sort((a, b) => {
        return new Date(a.createdAt) - new Date(b.createdAt);
    });
    
    const timeData = sortedData.map(item => formatDateTime(item.createdAt));
    const tempData = sortedData.map(item => item.temperature || null);
    const humidityData = sortedData.map(item => item.humidity || null);
    const setTempData = sortedData.map(item => item.setTemperature || null);
    const setHumidityData = sortedData.map(item => item.setHumidity || null);
    
    // 初始化图表实例（如果不存在）
    if (!window.modalChartInstances) {
        window.modalChartInstances = {};
    }
    
    // 销毁旧的图表实例
    if (window.modalChartInstances.temperature) {
        window.modalChartInstances.temperature.dispose();
    }
    if (window.modalChartInstances.humidity) {
        window.modalChartInstances.humidity.dispose();
    }
    if (window.modalChartInstances.comparison) {
        window.modalChartInstances.comparison.dispose();
    }
    
    // 创建新的图表实例
    window.modalChartInstances.temperature = echarts.init(document.getElementById('modalTemperatureChart'));
    window.modalChartInstances.humidity = echarts.init(document.getElementById('modalHumidityChart'));
    window.modalChartInstances.comparison = echarts.init(document.getElementById('modalComparisonChart'));
    
    // 温度图表
    const tempOption = {
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['实际温度', '设定温度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: {
            type: 'value',
            name: '温度(℃)'
        },
        series: [
            {
                name: '实际温度',
                type: 'line',
                data: tempData,
                smooth: true,
                itemStyle: { color: '#6366f1' },
                areaStyle: { color: 'rgba(99, 102, 241, 0.1)' }
            },
            {
                name: '设定温度',
                type: 'line',
                data: setTempData,
                smooth: true,
                itemStyle: { color: '#f59e0b' },
                lineStyle: { type: 'dashed' }
            }
        ]
    };
    
    // 湿度图表
    const humidityOption = {
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['实际湿度', '设定湿度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: {
            type: 'value',
            name: '湿度(%)'
        },
        series: [
            {
                name: '实际湿度',
                type: 'line',
                data: humidityData,
                smooth: true,
                itemStyle: { color: '#10b981' },
                areaStyle: { color: 'rgba(16, 185, 129, 0.1)' }
            },
            {
                name: '设定湿度',
                type: 'line',
                data: setHumidityData,
                smooth: true,
                itemStyle: { color: '#f59e0b' },
                lineStyle: { type: 'dashed' }
            }
        ]
    };
    
    // 对比图表
    const comparisonOption = {
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {
            data: ['温度', '湿度'],
            bottom: 10
        },
        xAxis: {
            type: 'category',
            data: timeData,
            boundaryGap: false
        },
        yAxis: [
            {
                type: 'value',
                name: '温度(℃)',
                position: 'left',
                axisLine: { lineStyle: { color: '#6366f1' } },
                axisLabel: { color: '#6366f1' }
            },
            {
                type: 'value',
                name: '湿度(%)',
                position: 'right',
                axisLine: { lineStyle: { color: '#10b981' } },
                axisLabel: { color: '#10b981' }
            }
        ],
        series: [
            {
                name: '温度',
                type: 'line',
                data: tempData,
                smooth: true,
                itemStyle: { color: '#6366f1' },
                yAxisIndex: 0
            },
            {
                name: '湿度',
                type: 'line',
                data: humidityData,
                smooth: true,
                itemStyle: { color: '#10b981' },
                yAxisIndex: 1
            }
        ]
    };
    
    window.modalChartInstances.temperature.setOption(tempOption);
    window.modalChartInstances.humidity.setOption(humidityOption);
    window.modalChartInstances.comparison.setOption(comparisonOption);
}

// 检查字符串是否包含指定的ID（支持逗号分隔的多个ID）
function containsId(idString, targetId) {
    if (!idString || !targetId) return false;
    const idStr = String(idString).trim();
    const targetStr = String(targetId).trim();
    if (idStr === targetStr) return true;
    const ids = idStr.split(',').map(id => id.trim());
    return ids.includes(targetStr);
}

// 渲染模态框中的过程时间轴图
function renderModalProcessChart(sample, sampleInfo) {
    const processSection = document.getElementById('modalProcessSection');
    const processChartContainer = document.getElementById('modalProcessChart');
    
    // 显示过程图区域
    processSection.style.display = 'block';
    
    // 获取样品ID
    const sampleId = sample.id;
    if (!sampleId) {
        processChartContainer.innerHTML = `
            <div style="display: flex; align-items: center; justify-content: center; height: 100%; color: #6b7280; font-size: 14px;">
                <div style="text-align: center;">
                    <div style="font-size: 48px; margin-bottom: 12px;">📊</div>
                    <div>样品ID不存在，无法分析过程</div>
                </div>
            </div>
        `;
        return;
    }
    
    // 从 allHistoryData 中分析测试和等候过程
    // 需要从 showSampleHistoryModal 函数中获取 allHistoryData
    // 由于 allHistoryData 是在 showSampleHistoryModal 中定义的，我们需要传递它
    // 但为了不改变函数签名，我们从 sampleInfo.allData 获取
    const allHistoryData = sampleInfo.allData || [];
    
    if (allHistoryData.length === 0) {
        processChartContainer.innerHTML = `
            <div style="display: flex; align-items: center; justify-content: center; height: 100%; color: #6b7280; font-size: 14px;">
                <div style="text-align: center;">
                    <div style="font-size: 48px; margin-bottom: 12px;">📊</div>
                    <div>暂无历史数据</div>
                </div>
            </div>
        `;
        return;
    }
    
    // 按时间排序（从早到晚）
    const sortedData = [...allHistoryData].sort((a, b) => {
        return new Date(a.createdAt) - new Date(b.createdAt);
    });
    
    // 分析时间段：遍历数据，识别测试和等候的连续时间段
    const testingPeriods = []; // 测试时间段列表
    const waitingPeriods = []; // 等候时间段列表
    
    let currentTestingStart = null;
    let currentTestingLastTime = null;  // 记录最后一次在测试中的时间（包含该ID的最后一条记录）
    let currentWaitingStart = null;
    let currentWaitingLastTime = null;  // 记录最后一次在等候中的时间（包含该ID的最后一条记录）
    
    console.log('[过程图] 开始分析数据，样品ID:', sampleId, '数据条数:', sortedData.length);
    
    for (let i = 0; i < sortedData.length; i++) {
        const data = sortedData[i];
        const dataTime = new Date(data.createdAt);
        if (!dataTime || isNaN(dataTime.getTime())) continue;
        
        // 检查 sample_id 是否包含该样品ID（测试过程）
        // 注意：后端返回的字段名是 sampleId 和 waitId（驼峰命名）
        const sampleIdField = data.sampleId || '';
        const waitIdField = data.waitId || '';
        
        const inSampleId = containsId(sampleIdField, sampleId);
        // 检查 wait_id 是否包含该样品ID（等候过程）
        const inWaitId = containsId(waitIdField, sampleId);
        
        // 调试前几条数据
        if (i < 5) {
            console.log(`[过程图] 数据${i}:`, {
                time: formatDateTime(data.createdAt),
                sampleId: sampleIdField,
                waitId: waitIdField,
                targetSampleId: sampleId,
                inSampleId: inSampleId,
                inWaitId: inWaitId
            });
        }
        
        // 处理测试时间段
        if (inSampleId) {
            // 在测试中
            if (currentTestingStart === null) {
                // 开始新的测试时间段，使用当前记录的created_at时间（第一次出现sample_id的时间）
                currentTestingStart = dataTime;
            }
            // 更新最后一次在测试中的时间（这是sample_id最后一次出现的记录时间）
            currentTestingLastTime = dataTime;
            
            // 如果之前有等候时间段，结束它（测试优先级高于等候）
            // 等候时间段在当前记录时间结束（这是状态从等候变为测试的时刻）
            if (currentWaitingStart !== null) {
                // 使用当前记录的created_at作为结束时间（这是wait_id中不再包含该ID的时刻）
                waitingPeriods.push({
                    startTime: currentWaitingStart,
                    endTime: dataTime  // 使用当前记录的created_at作为结束时间（wait_id不再包含该ID的时刻）
                });
                currentWaitingStart = null;
                currentWaitingLastTime = null;
            }
        } else {
            // 不在测试中（sample_id不包含该ID）
            if (currentTestingStart !== null) {
                // 测试时间段结束
                // 使用当前记录的created_at作为结束时间（这是sample_id中不再包含该ID的第一条记录时间）
                testingPeriods.push({
                    startTime: currentTestingStart,
                    endTime: dataTime  // 使用sample_id中不再包含该ID的第一条记录时间作为结束时间（与等候逻辑一致）
                });
                currentTestingStart = null;
                currentTestingLastTime = null;
            }
        }
        
        // 处理等候时间段
        // 判断逻辑：wait_id 包含样品ID 且 sample_id 不包含，就是等候过程
        // 开始时间：wait_id首次包含该样品ID的记录时间（created_at）
        // 结束时间：wait_id中不再包含该样品ID的第一条记录时间（created_at）
        if (inWaitId && !inSampleId) {
            // 在等候中，且不在测试中
            if (currentWaitingStart === null) {
                // 开始新的等候时间段，使用当前记录的created_at时间（第一次出现wait_id的时间）
                currentWaitingStart = dataTime;
            }
            // 更新最后一次在等候中的时间（这是wait_id最后一次出现的记录时间）
            // 但结束时间应该使用wait_id不再包含该ID的第一条记录时间，所以这里只记录，不用于结束时间
            currentWaitingLastTime = dataTime;
        } else {
            // wait_id 中不再包含该样品ID，或者已经在测试中了
            if (currentWaitingStart !== null) {
                // 如果已经在测试中（inSampleId为true），等候时间段已经在上面处理了，这里不需要重复处理
                if (!inSampleId) {
                    // 不在测试中，且wait_id不再包含该ID，结束等候时间段
                    // 使用当前记录的created_at作为结束时间（这是wait_id中不再包含该ID的第一条记录时间）
                    waitingPeriods.push({
                        startTime: currentWaitingStart,
                        endTime: dataTime  // 使用wait_id中不再包含该ID的第一条记录时间作为结束时间
                    });
                    currentWaitingStart = null;
                    currentWaitingLastTime = null;
                }
            }
        }
    }
    
    // 处理最后的时间段（如果还在进行中，说明数据已经遍历完但时间段还没结束）
    // 对于测试时间段：使用最后一次包含sample_id的记录时间作为结束时间
    // 对于等候时间段：使用最后一次包含wait_id的记录时间作为结束时间
    if (currentTestingStart !== null && currentTestingLastTime !== null) {
        testingPeriods.push({
            startTime: currentTestingStart,
            endTime: currentTestingLastTime  // 使用最后一次包含sample_id的记录时间
        });
    }
    if (currentWaitingStart !== null && currentWaitingLastTime !== null) {
        waitingPeriods.push({
            startTime: currentWaitingStart,
            endTime: currentWaitingLastTime  // 使用最后一次包含wait_id的记录时间（如果数据遍历完还在等候）
        });
    }
    
    console.log('[过程图] 样品ID:', sampleId);
    console.log('[过程图] 测试时间段数量:', testingPeriods.length, testingPeriods);
    console.log('[过程图] 等候时间段数量:', waitingPeriods.length, waitingPeriods);
    
    // 详细输出每个时间段
    if (testingPeriods.length > 0) {
        console.log('[过程图] 测试时间段详情:');
        testingPeriods.forEach((period, idx) => {
            console.log(`  测试阶段${idx + 1}: ${formatDateTime(period.startTime)} ~ ${formatDateTime(period.endTime)}`);
        });
    }
    if (waitingPeriods.length > 0) {
        console.log('[过程图] 等候时间段详情:');
        waitingPeriods.forEach((period, idx) => {
            console.log(`  等候阶段${idx + 1}: ${formatDateTime(period.startTime)} ~ ${formatDateTime(period.endTime)}`);
        });
    }
    
    if (testingPeriods.length === 0 && waitingPeriods.length === 0) {
        processChartContainer.innerHTML = `
            <div style="display: flex; align-items: center; justify-content: center; height: 100%; color: #6b7280; font-size: 14px;">
                <div style="text-align: center;">
                    <div style="font-size: 48px; margin-bottom: 12px;">📊</div>
                    <div>暂无测试或等候时间段数据</div>
                    <div style="font-size: 12px; margin-top: 8px; color: #9ca3af;">该样品可能还没有开始测试或等候</div>
                </div>
            </div>
        `;
        return;
    }
    
    // 合并所有时间段并按时间排序
    const allPeriods = [];
    testingPeriods.forEach((period, index) => {
        allPeriods.push({
            name: `测试阶段 ${index + 1}`,
            start: period.startTime.getTime(),
            end: period.endTime.getTime(),
            type: 'testing',
            color: '#10b981'
        });
    });
    waitingPeriods.forEach((period, index) => {
        allPeriods.push({
            name: `等候阶段 ${index + 1}`,
            start: period.startTime.getTime(),
            end: period.endTime.getTime(),
            type: 'waiting',
            color: '#f59e0b'
        });
    });
    
    if (allPeriods.length === 0) {
        processSection.style.display = 'none';
        return;
    }
    
    // 按开始时间排序
    allPeriods.sort((a, b) => a.start - b.start);
    
    // 计算时间范围
    const minTime = Math.min(...allPeriods.map(p => p.start));
    const maxTime = Math.max(...allPeriods.map(p => p.end));
    
    console.log('[图表数据] 时间段数据:', allPeriods);
    console.log('[图表数据] 时间范围:', {
        minTime: formatDateTime(new Date(minTime)),
        maxTime: formatDateTime(new Date(maxTime)),
        minTimestamp: minTime,
        maxTimestamp: maxTime
    });
    
    // 准备图表数据 - 每个时间段对应一个category（y轴标签）
    const categories = allPeriods.map((p, index) => {
        const icon = p.type === 'testing' ? '🟢' : '🟡';
        const typeText = p.type === 'testing' ? '测试' : '等候';
        return `${icon} ${typeText}${index + 1}`;
    });
    
    // 销毁旧的图表实例
    if (window.modalProcessChartInstance) {
        window.modalProcessChartInstance.dispose();
    }
    
    // 创建新的图表实例
    window.modalProcessChartInstance = echarts.init(processChartContainer);
    
    // 准备系列数据 - 对于时间轴类型的xAxis，bar图表需要特殊处理
    // 方法：每个时间段使用开始时间作为数据点，y值为1（用于定位），然后用markArea绘制时间段
    const seriesData = allPeriods.map((period, index) => {
        const duration = calculateDuration(new Date(period.start), new Date(period.end));
        // 确保时间戳是数字类型
        const startTs = Number(period.start);
        const endTs = Number(period.end);
        const timeSpan = endTs - startTs;  // 时间段长度（毫秒）
        
        // 使用开始时间作为x坐标，y值为1（用于在category上定位）
        const dataItem = {
            value: startTs,  // 单个值：时间戳（x坐标）
            name: period.name,
            itemStyle: {
                color: period.color
            },
            label: {
                show: false  // 不显示标签
            }
        };
        console.log(`[图表数据] 时间段${index + 1} (${period.name}):`, {
            start: formatDateTime(new Date(period.start)),
            end: formatDateTime(new Date(period.end)),
            startTimestamp: startTs,
            endTimestamp: endTs,
            timeSpan: timeSpan,
            timeSpanHours: (timeSpan / (1000 * 60 * 60)).toFixed(2),
            value: dataItem.value,
            valueType: typeof dataItem.value,
            duration: duration
        });
        return dataItem;
    });
    
    console.log('[图表数据] 系列数据:', seriesData);
    console.log('[图表数据] 系列数据长度:', seriesData.length);
    
    // 计算时间轴范围，用于设置xAxis的min和max
    const timeRange = maxTime - minTime;
    const padding = Math.max(timeRange * 0.1, 30 * 60 * 1000);  // 至少30分钟的边距，或10%的范围
    const xAxisMin = minTime - padding;
    const xAxisMax = maxTime + padding;
    
    console.log('[图表配置] 时间轴范围:', {
        min: formatDateTime(new Date(xAxisMin)),
        max: formatDateTime(new Date(xAxisMax)),
        minTimestamp: xAxisMin,
        maxTimestamp: xAxisMax
    });
    
    // 配置选项
    const option = {
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'line',
                lineStyle: {
                    color: '#999',
                    type: 'dashed'
                }
            },
            formatter: function(params) {
                const param = params[0];
                const period = allPeriods[param.dataIndex];
                const startTime = formatDateTime(new Date(period.start));
                const endTime = formatDateTime(new Date(period.end));
                const duration = calculateDuration(new Date(period.start), new Date(period.end));
                return `
                    <div style="padding: 8px;">
                        <div style="font-weight: 600; margin-bottom: 6px; color: ${period.color};">
                            ${period.type === 'testing' ? '🟢 测试阶段' : '🟡 等候阶段'}
                        </div>
                        <div style="font-size: 12px; line-height: 1.6;">
                            <div><strong>开始时间:</strong> ${startTime}</div>
                            <div><strong>结束时间:</strong> ${endTime}</div>
                            <div style="margin-top: 4px; color: ${period.color};"><strong>持续时长:</strong> ${duration || '-'}</div>
                        </div>
                    </div>
                `;
            }
        },
        grid: {
            left: '15%',
            right: '8%',
            top: '10%',
            bottom: '25%'
        },
        xAxis: {
            type: 'time',
            name: '时间',
            nameLocation: 'middle',
            nameGap: 35,
            nameTextStyle: {
                fontSize: 12,
                fontWeight: 'bold'
            },
            // 设置时间轴范围，确保所有时间段都能显示
            min: xAxisMin,
            max: xAxisMax,
            scale: true,  // 启用自适应缩放
            axisLabel: {
                formatter: function(value) {
                    const date = new Date(value);
                    return date.toLocaleString('zh-CN', {
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit'
                    });
                },
                rotate: 30,
                fontSize: 10
            },
            splitLine: {
                show: true,
                lineStyle: {
                    type: 'dashed',
                    color: '#e5e7eb'
                }
            }
        },
        yAxis: {
            type: 'category',
            data: categories,  // 使用准备好的categories数组
            inverse: true,
            axisLabel: {
                fontSize: 11,
                fontWeight: 'bold'
            },
            axisLine: {
                show: true,
                lineStyle: {
                    color: '#e5e7eb'
                }
            },
            splitLine: {
                show: false
            }
        },
        series: [
            {
                name: '过程时间轴',
                type: 'bar',
                data: seriesData,  // 每个数据项对应一个category，value为[start, end]时间戳数组
                // 对于时间轴类型的xAxis，ECharts不支持value数组格式的bar图表
                // 使用markArea来绘制时间段
                markArea: {
                    silent: false,
                    itemStyle: {
                        opacity: 0.8
                    },
                    label: {
                        show: true,
                        position: 'inside',
                        formatter: function(params) {
                            const period = allPeriods[params.dataIndexInside];
                            if (period) {
                                const duration = calculateDuration(new Date(period.start), new Date(period.end));
                                if (duration) {
                                    const parts = duration.split(' ');
                                    if (parts.length > 0) {
                                        return parts[0] + (parts[1] || '');
                                    }
                                }
                            }
                            return '';
                        },
                        fontSize: 11,
                        color: '#fff',
                        fontWeight: 'bold'
                    },
                    data: allPeriods.map((period, index) => {
                        // markArea需要指定xAxis和yAxis坐标
                        // yAxis使用category的索引
                        const startTs = Number(period.start);
                        const endTs = Number(period.end);
                        const timeSpan = endTs - startTs;
                        // 如果时间段为0或太小（小于1分钟），设置最小宽度（至少5分钟的可视宽度）
                        const minTimeSpan = 5 * 60 * 1000;  // 5分钟（毫秒）
                        const actualEndTs = timeSpan < minTimeSpan ? startTs + minTimeSpan : endTs;
                        
                        return [{
                            name: period.name,
                            xAxis: startTs,
                            yAxis: index,  // 对应category的索引
                            itemStyle: {
                                color: period.color,
                                opacity: 0.8
                            }
                        }, {
                            xAxis: actualEndTs,
                            yAxis: index  // 对应category的索引
                        }];
                    })
                },
                // 基础数据点（用于定位y轴位置）
                // 数据值为开始时间，但柱状图本身会被隐藏，只显示markArea
                barMinHeight: 40,  // 最小高度
                barCategoryGap: '30%',  // 类别间距
                xAxisIndex: 0,
                yAxisIndex: 0,
                coordinateSystem: 'cartesian2d',
                // 隐藏柱状图本身，只显示markArea（因为markArea已经能完整显示时间段）
                itemStyle: {
                    opacity: 0  // 完全隐藏柱状图，只显示markArea
                },
                // 设置barWidth为0，因为主要使用markArea来显示时间段
                barWidth: 0,  // 不显示柱状图
                emphasis: {
                    itemStyle: {
                        shadowBlur: 10,
                        shadowColor: 'rgba(0, 0, 0, 0.3)',
                        borderWidth: 2,
                        borderColor: '#fff'
                    }
                },
                large: false,
                largeThreshold: 1000,
                sampling: 'none',
                animation: true
            }
        ],
        legend: {
            show: true,
            bottom: 5,
            data: [
                { name: '测试阶段', icon: 'rect', itemStyle: { color: '#10b981' } },
                { name: '等候阶段', icon: 'rect', itemStyle: { color: '#f59e0b' } }
            ]
        }
    };
    
    console.log('[图表渲染] 准备渲染图表，时间段数量:', allPeriods.length);
    window.modalProcessChartInstance.setOption(option);
    
    // 等待图表渲染完成后，强制调整大小并输出调试信息
    setTimeout(() => {
        if (window.modalProcessChartInstance) {
            window.modalProcessChartInstance.resize();
            const optionData = window.modalProcessChartInstance.getOption();
            console.log('[图表渲染] 图表已渲染');
            console.log('[图表渲染] 系列数据数量:', optionData.series && optionData.series[0] ? optionData.series[0].data.length : 0);
            if (optionData.series && optionData.series[0] && optionData.series[0].data) {
                optionData.series[0].data.forEach((item, idx) => {
                    console.log(`[图表渲染] 数据项${idx}:`, item);
                });
            }
        }
    }, 200);
}

// 关闭样品历史数据模态框
function closeSampleHistoryModal() {
    document.getElementById('sampleHistoryModal').style.display = 'none';
    
    // 销毁图表实例以释放内存
    if (window.modalChartInstances) {
        if (window.modalChartInstances.temperature) {
            window.modalChartInstances.temperature.dispose();
        }
        if (window.modalChartInstances.humidity) {
            window.modalChartInstances.humidity.dispose();
        }
        if (window.modalChartInstances.comparison) {
            window.modalChartInstances.comparison.dispose();
        }
        window.modalChartInstances = null;
    }
    
    // 销毁过程图实例
    if (window.modalProcessChartInstance) {
        window.modalProcessChartInstance.dispose();
        window.modalProcessChartInstance = null;
    }
}

// 返回温箱页面
function goToReliabilityLab() {
    const username = localStorage.getItem('username') || '';
    const job = localStorage.getItem('job') || '';
    
    const baseUrl = window.location.origin;
    let url = baseUrl + '/reliabilityIndex';
    
    const params = [];
    if (username) {
        params.push('username=' + encodeURIComponent(username));
    }
    if (job) {
        params.push('job=' + encodeURIComponent(job));
    }
    if (params.length > 0) {
        url += '?' + params.join('&');
    }
    
    // 检查是否在钉钉环境中
    const isDingTalk = typeof dd !== 'undefined' && dd.env && dd.env.platform !== 'notInDingTalk';
    
    if (isDingTalk) {
        dd.openLink({
            url: url,
            success: function() {
                console.log('成功返回温箱页面');
            },
            fail: function(err) {
                console.error('dd.openLink fail:', err);
                window.location.href = url;
            }
        });
    } else {
        window.location.href = url;
    }
}

