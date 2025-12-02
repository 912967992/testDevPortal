// 全局变量
let currentPage = 1;
const pageSize = 20;
let totalPages = 1;
let chartInstances = {};
let currentData = [];

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
}

// 绑定事件
function bindEvents() {
    // 查询方式切换
    const searchType = document.getElementById('searchType');
    searchType.addEventListener('change', function() {
        const value = this.value;
        const deviceSearchGroup = document.getElementById('deviceSearchGroup');
        const sampleSearchGroup = document.getElementById('sampleSearchGroup');
        const sampleModelGroup = document.getElementById('sampleModelGroup');
        
        const sampleTesterGroup = document.getElementById('sampleTesterGroup');
        
        if (value === 'device') {
            deviceSearchGroup.style.display = 'block';
            sampleSearchGroup.style.display = 'none';
            sampleModelGroup.style.display = 'none';
            sampleTesterGroup.style.display = 'none';
        } else {
            deviceSearchGroup.style.display = 'none';
            sampleSearchGroup.style.display = 'block';
            sampleModelGroup.style.display = 'block';
            sampleTesterGroup.style.display = 'block';
        }
    });
    
    // 回车键搜索
    document.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            searchData();
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
    const searchType = document.getElementById('searchType').value;
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
        
        if (searchType === 'device') {
            const deviceId = document.getElementById('deviceId').value.trim();
            if (deviceId) {
                url += '&deviceId=' + encodeURIComponent(deviceId);
            }
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
            
            // 打印样品调试信息到控制台（用于调试）
            if (result.data.sampleDebugInfo && result.data.sampleDebugInfo.length > 0) {
                console.log('========== 样品测试状态调试信息 ==========');
                result.data.sampleDebugInfo.forEach((sample, index) => {
                    console.log(`样品 ${index + 1}:`, {
                        '设备ID': sample.deviceId,
                        '品类': sample.category,
                        '型号': sample.model,
                        '创建时间': sample.createdAt,
                        '更新时间': sample.updatedAt,
                        '是否在测试中': sample.isTesting,
                        '状态说明': sample.message
                    });
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

// 显示表格
function displayTable(dataList, total, sampleDebugInfo) {
    const tbody = document.getElementById('dataTableBody');
    tbody.innerHTML = '';
    
    dataList.forEach((item, index) => {
        const row = document.createElement('tr');
        
        // 格式化样品信息
        const sampleInfoHtml = formatSampleInfo(item.samples || []);
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
    
    // 更新分页
    totalPages = Math.ceil(total / pageSize);
    updatePagination(total);
    
    document.getElementById('tableSection').style.display = 'block';
}

// 格式化样品信息显示
function formatSampleInfo(samples) {
    if (!samples || samples.length === 0) {
        return '<span style="color: #9ca3af;">暂无样品信息</span>';
    }
    
    // 只显示最新的样品信息（因为通常一个时间点的数据对应一个样品）
    // 如果有多条样品，显示第一条（最新的）
    const sample = samples[0];
    const category = sample.category || '-';
    const model = sample.model || '-';
    const tester = sample.tester || '-';
    
    let html = '<div style="text-align: left; line-height: 1.6;">';
    
    // 如果有多条样品，显示数量提示
    if (samples.length > 1) {
        html += `<div style="font-size: 11px; color: #6b7280; margin-bottom: 4px;">
            共 ${samples.length} 个样品（显示最新）
        </div>`;
    }
    
    html += `<div style="margin-bottom: 2px;">
        <span style="font-weight: 600; color: #6366f1; font-size: 12px;">品类:</span> 
        <span style="font-size: 13px;">${category}</span>
    </div>`;
    html += `<div style="margin-bottom: 2px;">
        <span style="font-weight: 600; color: #8b5cf6; font-size: 12px;">型号:</span> 
        <span style="font-size: 13px;">${model}</span>
    </div>`;
    html += `<div>
        <span style="font-weight: 600; color: #10b981; font-size: 12px;">测试人员:</span> 
        <span style="font-size: 13px;">${tester}</span>
    </div>`;
    html += '</div>';
    
    return html;
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
    document.getElementById('searchType').value = 'device';
    document.getElementById('deviceId').value = '';
    document.getElementById('sampleCategory').value = '';
    document.getElementById('sampleModel').value = '';
    document.getElementById('sampleTester').value = '';
    setDefaultTimeRange();
    
    // 切换显示
    document.getElementById('deviceSearchGroup').style.display = 'block';
    document.getElementById('sampleSearchGroup').style.display = 'none';
    document.getElementById('sampleModelGroup').style.display = 'none';
    document.getElementById('sampleTesterGroup').style.display = 'none';
    
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
    let csv = '序号,设备ID,品类,型号,测试人员,温度(℃),湿度(%),设定温度(℃),设定湿度(%),运行状态,运行模式,记录时间\n';
    
    currentData.forEach((item, index) => {
        // 获取样品信息（取最新的样品）
        let category = '-';
        let model = '-';
        let tester = '-';
        
        if (item.samples && item.samples.length > 0) {
            const sample = item.samples[0]; // 取最新的样品
            category = sample.category || '-';
            model = sample.model || '-';
            tester = sample.tester || '-';
        }
        
        // 获取运行状态和模式
        const runStatus = item.runStatus || '-';
        const runMode = item.runMode || '-';
        
        csv += `${index + 1},${item.deviceId || ''},${category},${model},${tester},` +
               `${item.temperature || ''},${item.humidity || ''},${item.setTemperature || ''},${item.setHumidity || ''},${runStatus},${runMode},` +
               `${formatDateTime(item.createdAt)}\n`;
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
    
    if (!sampleDebugInfo || sampleDebugInfo.length === 0) {
        statusSection.style.display = 'none';
        return;
    }
    
    statusList.innerHTML = '';
    
    sampleDebugInfo.forEach((sample, index) => {
        const statusItem = document.createElement('div');
        statusItem.className = 'sample-status-item';
        
        const isTesting = sample.isTesting === true || sample.isTesting === 'true';
        const statusClass = isTesting ? 'status-testing' : 'status-finished';
        const statusIcon = isTesting ? '🟢' : '🔴';
        const statusText = isTesting ? '测试进行中' : '测试已结束';
        
        statusItem.innerHTML = `
            <div class="sample-status-item-header">
                <div class="sample-status-item-title">
                    <span class="sample-status-number">${index + 1}</span>
                    <div class="sample-status-info">
                        <div class="sample-status-name">
                            ${sample.category || '-'} - ${sample.model || '-'}
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
        `;
        
        statusList.appendChild(statusItem);
    });
    
    statusSection.style.display = 'block';
}

// 返回首页
function goToHome() {
    const username = localStorage.getItem('username') || '';
    const job = localStorage.getItem('job') || '';
    
    const baseUrl = window.location.origin;
    let url = baseUrl + '/home';
    
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
                console.log('成功返回首页');
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

