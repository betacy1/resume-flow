import { login, logout, isLoggedIn, getStoredAuth } from '../services/authService';
import {
  addFieldKeyword,
  createMaterial,
  getCustomFields,
  getMaterials,
  getTemplates,
  updateCustomField,
  type CustomFieldItem,
  type MaterialItem,
  type TemplateItem,
} from '../services/apiClient';
import { getSelectedTemplate, saveSelectedTemplate } from '../services/storageService';
import { MessageType } from '../utils/events';

const loginSection = document.getElementById('login-section')!;
const mainSection = document.getElementById('main-section')!;
const backendUrlInput = document.getElementById('backend-url') as HTMLInputElement;
const usernameInput = document.getElementById('username') as HTMLInputElement;
const passwordInput = document.getElementById('password') as HTMLInputElement;
const btnLogin = document.getElementById('btn-login') as HTMLButtonElement;
const loginMsg = document.getElementById('login-msg')!;
const userInfo = document.getElementById('user-info')!;
const btnLogout = document.getElementById('btn-logout')!;
const templateSelect = document.getElementById('template-select') as HTMLSelectElement;
const btnScanFill = document.getElementById('btn-scan-fill') as HTMLButtonElement;
const fillResult = document.getElementById('fill-result')!;

const fieldSelect = document.getElementById('field-select') as HTMLSelectElement;
const fieldValue = document.getElementById('field-value') as HTMLTextAreaElement;
const btnSaveField = document.getElementById('btn-save-field') as HTMLButtonElement;

const materialTypeSelect = document.getElementById('material-type') as HTMLSelectElement;
const materialTitleInput = document.getElementById('material-title') as HTMLInputElement;
const materialContentInput = document.getElementById('material-content') as HTMLTextAreaElement;
const btnAddMaterial = document.getElementById('btn-add-material') as HTMLButtonElement;
const materialButtons = document.getElementById('material-buttons')!;

const confirmCard = document.getElementById('confirm-card')!;
const confirmList = document.getElementById('confirm-list')!;
const unmatchedCard = document.getElementById('unmatched-card')!;
const unmatchedList = document.getElementById('unmatched-list')!;

let templates: TemplateItem[] = [];
let fields: CustomFieldItem[] = [];
let materials: MaterialItem[] = [];
/** 最近一次一键填写的标签页，供确认填充使用 */
let lastFillTabId: number | null = null;

const materialTypes: Record<string, string> = {
  SELF_EVALUATION: '自我评价',
  INTERNSHIP: '实习经历',
  PROJECT: '项目经历',
  AI_COLLABORATION: 'AI协作经历',
  CAREER_PLAN: '职业规划',
  HOBBY: '兴趣特长',
  WHY_COMPANY: '为什么选择本公司',
  WHY_POSITION: '为什么选择本岗位',
  SUPPLEMENT: '补充信息',
};

async function init() {
  fillMaterialTypeOptions();
  if (await isLoggedIn()) {
    const auth = await getStoredAuth();
    backendUrlInput.value = auth?.backendUrl || 'http://localhost:8080';
    showMainSection(auth?.username || '');
    await loadAll();
  } else {
    showLoginSection();
  }
}

function showLoginSection() {
  loginSection.style.display = '';
  mainSection.style.display = 'none';
}

function showMainSection(username: string) {
  loginSection.style.display = 'none';
  mainSection.style.display = '';
  userInfo.textContent = `用户: ${username}`;
}

btnLogin.addEventListener('click', async () => {
  const backendUrl = backendUrlInput.value.trim();
  const username = usernameInput.value.trim();
  const password = passwordInput.value.trim();
  if (!backendUrl || !username || !password) {
    showLoginMsg('请填写完整登录信息', 'error');
    return;
  }
  btnLogin.disabled = true;
  try {
    const result = await login(username, password, backendUrl);
    showMainSection(result.username);
    showLoginMsg('登录成功', 'success');
    await loadAll();
  } catch (e: any) {
    showLoginMsg(e.message || '登录失败', 'error');
  } finally {
    btnLogin.disabled = false;
  }
});

btnLogout.addEventListener('click', async () => {
  await logout();
  showLoginSection();
});

templateSelect.addEventListener('change', async () => {
  const id = Number(templateSelect.value || 0);
  if (id) {
    const name = templateSelect.selectedOptions[0]?.textContent || '';
    await saveSelectedTemplate(id, name);
  } else {
    await saveSelectedTemplate(0, '');
  }
  await loadFields();
  await loadMaterials();
});

fieldSelect.addEventListener('change', () => {
  const item = fields.find((f) => String(f.id) === fieldSelect.value);
  fieldValue.value = item?.fieldValue || '';
});

btnSaveField.addEventListener('click', async () => {
  const item = fields.find((f) => String(f.id) === fieldSelect.value);
  if (!item?.id) return;
  item.fieldValue = fieldValue.value;
  await updateCustomField(item.id, item);
  fillResult.textContent = '字段内容已保存';
  fillResult.className = 'msg success';
});

btnAddMaterial.addEventListener('click', async () => {
  const title = materialTitleInput.value.trim();
  const content = materialContentInput.value.trim();
  const materialType = materialTypeSelect.value;
  if (!title || !content) {
    fillResult.textContent = '请填写素材标题与内容';
    fillResult.className = 'msg error';
    return;
  }
  const selectedTemplateId = Number(templateSelect.value || 0);
  await createMaterial({
    title,
    content,
    materialType,
    templateId: selectedTemplateId || undefined,
    enabled: true,
    wordLimitType: '500字',
  });
  materialTitleInput.value = '';
  materialContentInput.value = '';
  await loadMaterials();
  fillResult.textContent = '素材已新增';
  fillResult.className = 'msg success';
});

btnScanFill.addEventListener('click', async () => {
  btnScanFill.disabled = true;
  fillResult.textContent = '';
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab?.id) throw new Error('无法获取当前标签页');
    const templateId = templateSelect.value ? Number(templateSelect.value) : null;
    const audienceType = templates.find((t) => String(t.id) === templateSelect.value)?.audienceType;
    const response = await chrome.tabs.sendMessage(tab.id, {
      type: MessageType.SCAN_AND_FILL,
      templateId,
      audienceType,
    });
    if (response?.error) {
      throw new Error(response.error);
    }
    lastFillTabId = tab.id;
    const confirmText = response.needConfirm ? `，待确认${response.needConfirm}` : '';
    fillResult.textContent = `总${response.total}，已填${response.filled}，跳过${response.skipped}，敏感${response.sensitive}，未匹配${response.unmatched}${confirmText}`;
    fillResult.className = 'msg success';
    renderConfirmItems(response.confirmItems || []);
    renderUnmatchedFields(response.unmatchedFields || []);
    saveReport(response);
  } catch (e: any) {
    fillResult.textContent = `错误: ${e.message}`;
    fillResult.className = 'msg error';
  } finally {
    btnScanFill.disabled = false;
  }
});

/** 渲染待确认字段（置信度 0.5~0.75，不自动填，由用户确认后填入） */
function renderConfirmItems(items: { fieldId: string; matchedFieldName: string; confidence: number; value: string }[]) {
  confirmList.innerHTML = '';
  confirmCard.style.display = items.length > 0 ? '' : 'none';
  for (const item of items) {
    const row = document.createElement('div');
    row.className = 'field-list-item';
    const label = document.createElement('div');
    label.className = 'item-label';
    label.textContent = `${item.matchedFieldName}（${item.confidence.toFixed(2)}）：${item.value.slice(0, 40)}${item.value.length > 40 ? '…' : ''}`;
    const actions = document.createElement('div');
    actions.className = 'item-actions';
    const btn = document.createElement('button');
    btn.className = 'btn-confirm';
    btn.textContent = '确认填入';
    btn.addEventListener('click', async () => {
      if (!lastFillTabId) return;
      const res = await chrome.tabs.sendMessage(lastFillTabId, {
        type: MessageType.CONFIRM_FILL,
        fieldId: item.fieldId,
        matchedFieldName: item.matchedFieldName,
        confidence: item.confidence,
        value: item.value,
      });
      label.textContent = res?.success ? `已填入：${item.matchedFieldName}` : '填入失败';
      btn.disabled = true;
    });
    actions.appendChild(btn);
    row.appendChild(label);
    row.appendChild(actions);
    confirmList.appendChild(row);
  }
}

/** 渲染未匹配字段，支持将页面字段标签绑定到简历字段（形成新的匹配关键词） */
function renderUnmatchedFields(items: { fieldId: string; label: string }[]) {
  unmatchedList.innerHTML = '';
  unmatchedCard.style.display = items.length > 0 ? '' : 'none';
  for (const item of items.slice(0, 20)) {
    const row = document.createElement('div');
    row.className = 'field-list-item';
    const label = document.createElement('div');
    label.className = 'item-label';
    label.textContent = item.label || item.fieldId;
    const actions = document.createElement('div');
    actions.className = 'item-actions';
    const select = document.createElement('select');
    select.innerHTML = '<option value="">选择简历字段…</option>';
    for (const f of fields) {
      const option = document.createElement('option');
      option.value = String(f.id || 0);
      option.textContent = `${f.fieldName} (${f.fieldKey})`;
      select.appendChild(option);
    }
    const btn = document.createElement('button');
    btn.className = 'btn-confirm';
    btn.textContent = '绑定';
    btn.addEventListener('click', async () => {
      const fieldId = Number(select.value || 0);
      const keyword = (item.label || '').trim();
      if (!fieldId || !keyword) {
        label.textContent = '请先选择简历字段';
        return;
      }
      try {
        await addFieldKeyword(fieldId, keyword);
        label.textContent = `已绑定到：${fields.find((f) => f.id === fieldId)?.fieldName || ''}`;
        btn.disabled = true;
      } catch (e: any) {
        label.textContent = `绑定失败: ${e.message}`;
      }
    });
    actions.appendChild(select);
    actions.appendChild(btn);
    row.appendChild(label);
    row.appendChild(actions);
    unmatchedList.appendChild(row);
  }
}

/** 保存最近一次填写报告到会话存储，便于在 options 页查看 */
function saveReport(response: any) {
  chrome.storage?.session?.set?.({
    lastFillReport: {
      time: new Date().toLocaleString(),
      total: response.total,
      filled: response.filled,
      skipped: response.skipped,
      sensitive: response.sensitive,
      needConfirm: response.needConfirm,
      unmatched: response.unmatched,
      details: response.details,
    },
  });
}

async function loadAll() {
  await loadTemplates();
  await loadFields();
  await loadMaterials();
}

async function loadTemplates() {
  templates = await getTemplates();
  templateSelect.innerHTML = '<option value="">不使用模板</option>';
  templates.forEach((t) => {
    const option = document.createElement('option');
    option.value = String(t.id);
    option.textContent = t.name + (t.isDefault ? '（默认）' : '');
    option.title = t.description || '';
    templateSelect.appendChild(option);
  });
  const selected = await getSelectedTemplate();
  if (selected?.id) templateSelect.value = String(selected.id);
  // 默认选中模板（后端已标记 isDefault，如大厂互联网版）
  if (!templateSelect.value) {
    const def = templates.find((t) => t.isDefault);
    if (def) templateSelect.value = String(def.id);
  }
}

async function loadFields() {
  const templateId = Number(templateSelect.value || 0) || undefined;
  fields = await getCustomFields(templateId);
  fieldSelect.innerHTML = '';
  fields.forEach((f) => {
    const option = document.createElement('option');
    option.value = String(f.id || 0);
    option.textContent = `${f.fieldName} (${f.fieldKey})`;
    fieldSelect.appendChild(option);
  });
  if (fields.length > 0) {
    fieldSelect.value = String(fields[0].id);
    fieldValue.value = fields[0].fieldValue || '';
  } else {
    fieldValue.value = '';
  }
}

async function loadMaterials() {
  const templateId = Number(templateSelect.value || 0) || undefined;
  materials = await getMaterials(templateId);
  materialButtons.innerHTML = '';
  if (materials.length === 0) {
    const tip = document.createElement('div');
    tip.className = 'msg';
    tip.textContent = '暂无素材';
    materialButtons.appendChild(tip);
    return;
  }
  materials.forEach((m) => {
    const btn = document.createElement('button');
    const typeLabel = materialTypes[m.materialType] || m.materialType;
    btn.textContent = `填入${m.shortName || typeLabel}`;
    btn.title = m.title;
    btn.addEventListener('click', async () => {
      await fillCurrentInput(m.content);
    });
    materialButtons.appendChild(btn);
  });
}

async function fillCurrentInput(content: string) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) return;
  const response = await chrome.tabs.sendMessage(tab.id, {
    type: MessageType.FILL_MATERIAL,
    content,
  });
  if (response?.success) {
    fillResult.textContent = '已填入当前输入框';
    fillResult.className = 'msg success';
  } else {
    fillResult.textContent = response?.message || '填入失败';
    fillResult.className = 'msg error';
  }
}

function fillMaterialTypeOptions() {
  materialTypeSelect.innerHTML = '';
  Object.entries(materialTypes).forEach(([value, label]) => {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = label;
    materialTypeSelect.appendChild(option);
  });
}

function showLoginMsg(text: string, type: 'success' | 'error') {
  loginMsg.textContent = text;
  loginMsg.className = `msg ${type}`;
}

init();
