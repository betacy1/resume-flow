import { getBackendUrl, saveAuth, getAuth, type StoredAuth } from '../services/storageService';

const backendUrlInput = document.getElementById('backend-url') as HTMLInputElement;
const btnSave = document.getElementById('btn-save') as HTMLButtonElement;
const saveMsg = document.getElementById('save-msg')!;
const lastReportEl = document.getElementById('last-report')!;

async function init() {
  backendUrlInput.value = await getBackendUrl();
  await renderLastReport();
}

/** 展示最近一次一键填写报告（由 popup 写入会话存储） */
async function renderLastReport() {
  const { lastFillReport } = await chrome.storage.session.get('lastFillReport');
  if (!lastFillReport) return;
  const lines = [
    `时间：${lastFillReport.time}`,
    `总字段 ${lastFillReport.total}，已填 ${lastFillReport.filled}，跳过 ${lastFillReport.skipped}，待确认 ${lastFillReport.needConfirm ?? 0}，未匹配 ${lastFillReport.unmatched}`,
  ];
  for (const detail of lastFillReport.details || []) {
    lines.push(`· ${detail}`);
  }
  lastReportEl.textContent = lines.join('\n');
  lastReportEl.style.whiteSpace = 'pre-wrap';
}

btnSave.addEventListener('click', async () => {
  const backendUrl = backendUrlInput.value.trim();
  if (!backendUrl) {
    saveMsg.textContent = '请输入后端地址';
    saveMsg.style.color = '#f56c6c';
    return;
  }
  const auth = await getAuth();
  if (auth) {
    const updated: StoredAuth = { ...auth, backendUrl };
    await saveAuth(updated);
  } else {
    await new Promise<void>((resolve) => {
      chrome.storage.local.set({ rf_backend_url: backendUrl }, () => resolve());
    });
  }
  saveMsg.textContent = '保存成功';
  saveMsg.style.color = '#67c23a';
});

init();
