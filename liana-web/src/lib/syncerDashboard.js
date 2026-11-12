function countValue(group, key) {
  return Number(group?.[key] || 0);
}

function totalCounts(group = {}) {
  return Object.values(group).reduce((sum, value) => sum + Number(value || 0), 0);
}

function failedTasks(summary = {}) {
  return Array.isArray(summary.recentTasks)
    ? summary.recentTasks.filter((task) => task.status === 'FAILED')
    : [];
}

export function normalizeSyncerSummary(summary = {}) {
  const outbox = summary.outbox || {};
  const syncTasks = summary.syncTasks || {};
  const retryRecords = summary.retryRecords || {};
  const pendingOutbox = countValue(outbox, 'NEW') + countValue(outbox, 'FAILED');
  const pendingTasks = countValue(syncTasks, 'PENDING') + countValue(syncTasks, 'FAILED');
  const failedTaskCount = countValue(syncTasks, 'FAILED');
  const pendingRetryCount = countValue(retryRecords, 'PENDING');
  const recentFailures = failedTasks(summary);

  return {
    pendingOutbox,
    pendingTasks,
    failedTaskCount,
    pendingRetryCount,
    totalOutbox: totalCounts(outbox),
    totalTasks: totalCounts(syncTasks),
    totalRetries: totalCounts(retryRecords),
    recentFailures,
    outbox,
    syncTasks,
    retryRecords,
    recentOutbox: Array.isArray(summary.recentOutbox) ? summary.recentOutbox : [],
    recentTasks: Array.isArray(summary.recentTasks) ? summary.recentTasks : [],
    recentRetries: Array.isArray(summary.recentRetries) ? summary.recentRetries : [],
  };
}

export function syncerDashboardSection(summary = {}) {
  const normalized = normalizeSyncerSummary(summary);
  const failedHint = normalized.recentFailures[0]?.lastError || '最近失败任务';
  return {
    title: '同步补偿监控',
    scope: 'Outbox / Sync Task / Retry',
    metrics: [
      {
        label: '待处理 Outbox',
        value: normalized.pendingOutbox,
        hint: `总量 ${normalized.totalOutbox}`,
        tone: normalized.pendingOutbox > 0 ? 'warning' : 'success',
      },
      {
        label: '待处理任务',
        value: normalized.pendingTasks,
        hint: `总量 ${normalized.totalTasks}`,
        tone: normalized.pendingTasks > 0 ? 'warning' : 'success',
      },
      {
        label: '失败任务',
        value: normalized.failedTaskCount,
        hint: normalized.failedTaskCount > 0 ? failedHint : '暂无失败任务',
        tone: normalized.failedTaskCount > 0 ? 'danger' : 'success',
      },
      {
        label: '待重试记录',
        value: normalized.pendingRetryCount,
        hint: `重试记录 ${normalized.totalRetries}`,
        tone: normalized.pendingRetryCount > 0 ? 'warning' : 'success',
      },
    ],
    breakdowns: [
      {
        title: 'Outbox 状态',
        items: statusItems(normalized.outbox),
      },
      {
        title: '同步任务状态',
        items: statusItems(normalized.syncTasks),
      },
    ],
  };
}

export function statusItems(counts = {}) {
  return Object.entries(counts).map(([label, value]) => ({
    label,
    value,
    status: label,
  }));
}
