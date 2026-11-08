<template>
  <div class="shell">
    <aside class="sidebar" :class="{ open: mobileOpen }">
      <div class="brand"><div class="brand-icon">S</div><div><b>SYY · TEST</b><small>AGENT PLATFORM</small></div><button class="mobile-close" @click="mobileOpen=false">×</button></div>
      <div class="workspace"><span class="workspace-mark">⌘</span><div><b>测试工作空间</b><small>本地项目 · MySQL</small></div></div>
      <nav>
        <p>工作空间</p>
        <button v-for="item in nav" :key="item.id" :class="{active:view===item.id}" @click="go(item.id)"><span>{{ item.icon }}</span>{{ item.label }}<i v-if="item.id==='apis' && apis.length">{{ apis.length }}</i></button>
      </nav>
      <div class="sidebar-bottom"><div class="agent-card"><div class="agent-orb">✦</div><b>SYY Test Agent</b><small>接口理解 · 用例生成 · 结果评估</small><button @click="go('cases')">进入 Agent 工作台 →</button></div><div class="user"><span>SY</span><div><b>SYY Workspace</b><small>开发环境</small></div></div></div>
    </aside>
    <div v-if="mobileOpen" class="scrim" @click="mobileOpen=false"></div>

    <main class="main">
      <header class="topbar"><div class="crumb"><button class="mobile-menu" @click="mobileOpen=true">☰</button><span>工作空间</span><b>/</b><strong>{{ title }}</strong></div><div class="top-actions"><span class="health"><i :class="health"></i>{{ healthText }}</span><span class="version">V11 · DELIVERY</span></div></header>
      <div class="content">
        <section v-if="view==='overview'">
          <div class="heading"><div><span class="eyebrow">SYY AGENTIC API QUALITY</span><h1>测试总览</h1><p>从 OpenAPI 契约到智能评估报告，构建完整的接口自动化测试闭环。</p></div><button class="primary" @click="go('apis')">＋ 导入 OpenAPI</button></div>
          <div class="hero"><div><span class="hero-tag">✦ AGENT AUTOMATION</span><h2>让 Agent 设计测试，<br>让执行引擎验证质量。</h2><p>接口解析、RAG 增强用例生成、HTTP 执行、LLM Judge 与真实报告统一协作。</p><div class="hero-buttons"><button class="light" @click="go('apis')">开始导入 →</button><button class="ghost" @click="go('pipeline')">运行测试任务</button></div></div><div class="hero-visual"><div class="ring ring-a"></div><div class="ring ring-b"></div><div class="core">SYY</div><span class="node n1">API</span><span class="node n2">RAG</span><span class="node n3">QA</span></div></div>
          <div class="metrics"><article><small>已导入接口</small><strong>{{ apis.length }}</strong><span>当前选中文档</span></article><article><small>测试用例</small><strong>{{ cases.length }}</strong><span>持久化到 MySQL</span></article><article><small>测试任务</small><strong>{{ tasks.length }}</strong><span>历史执行记录</span></article><article><small>累计通过率</small><strong>{{ summary.passRate || 0 }}<em>%</em></strong><span>基于真实执行结果</span></article></div>
          <div class="two-col"><section class="panel"><div class="panel-title"><div><h3>自动化测试工作流</h3><p>五个核心阶段构成完整闭环</p></div><span class="pill">END TO END</span></div><div class="workflow"><button v-for="(step,i) in steps" :key="step.id" @click="go(step.id)"><b>0{{i+1}}</b><span class="step-icon">{{step.icon}}</span><span><strong>{{step.name}}</strong><small>{{step.desc}}</small></span><i>→</i></button></div></section><section class="panel"><div class="panel-title"><div><h3>最近测试任务</h3><p>来自数据库的真实任务记录</p></div><button class="text-btn" @click="go('tasks')">全部任务 →</button></div><div v-if="tasks.length" class="recent-list"><div v-for="task in tasks.slice(0,5)" :key="task.id" class="recent-row"><span class="status-dot" :class="task.status"></span><div><b>{{task.taskName}}</b><small>{{formatTime(task.createdAt)}} · {{task.totalCount || 0}} 条用例</small></div><strong>{{task.status}}</strong></div></div><div v-else class="empty">尚无任务，导入接口并启动第一次测试。</div></section></div>
        </section>

        <section v-else-if="view==='apis'">
          <div class="heading"><div><span class="eyebrow">API CONTRACT MANAGEMENT</span><h1>接口管理</h1><p>导入 OpenAPI / Swagger 文档，解析接口定义并保存到 MySQL。</p></div><button class="primary" @click="fileInput?.click()">＋ 选择文档</button></div>
          <input ref="fileInput" class="hidden" type="file" accept=".json,.yaml,.yml,application/json,text/yaml,application/yaml" @change="onFile" />
          <div class="upload" :class="{dragging}" @dragover.prevent="dragging=true" @dragleave.prevent="dragging=false" @drop.prevent="onDrop"><div class="upload-icon">⇧</div><div class="upload-copy"><b>{{ selectedFile ? selectedFile.name : '导入 OpenAPI 文档' }}</b><p>拖拽 JSON / YAML 文件到这里，或点击选择。支持 Swagger 2.0 / OpenAPI 3.x。</p><small>文档、接口定义及原始契约将持久化保存</small></div><button class="secondary" :disabled="!selectedFile || uploading" @click="upload">{{uploading?'解析并保存中…':'开始解析'}}</button></div>
          <div v-if="notice" class="notice" :class="noticeType">{{notice}}</div>
          <section class="panel doc-panel"><div class="panel-title"><div><h3>已导入文档</h3><p>{{documents.length}} 份文档 · {{apis.length}} 个接口定义</p></div><select v-model.number="selectedDocumentId" @change="selectDocument"><option :value="null">选择 OpenAPI 文档</option><option v-for="doc in documents" :key="doc.id" :value="doc.id">{{doc.title || doc.name}} · #{{doc.id}}</option></select></div><div v-if="selectedDocument" class="doc-meta"><div><small>文档版本</small><b>{{selectedDocument.specificationVersion}} / {{selectedDocument.version}}</b></div><div><small>原始 Server</small><b>{{selectedDocument.serverUrl || '未定义'}}</b></div><div><small>导入时间</small><b>{{formatTime(selectedDocument.importedAt)}}</b></div></div><div class="table-wrap"><table><thead><tr><th>Method</th><th>接口路径</th><th>接口描述</th><th>Tags</th><th>操作</th></tr></thead><tbody><tr v-for="api in apis" :key="api.id"><td><span class="method" :class="api.method?.toLowerCase()">{{api.method}}</span></td><td class="mono">{{api.path}}</td><td>{{api.description || api.operationId || '—'}}</td><td>{{api.tags || '—'}}</td><td><button class="text-btn" @click="go('cases')">查看用例</button></td></tr></tbody></table><div v-if="!apis.length" class="empty">选择或导入一份 OpenAPI 文档后，这里会展示解析结果。</div></div></section>
        </section>

        <section v-else-if="view==='cases'">
          <div class="heading"><div><span class="eyebrow">AGENT + RAG TEST DESIGN</span><h1>Agent 用例工作台</h1><p>基于接口契约与 MySQL 持久化向量知识库，生成可执行的结构化测试用例。</p></div><button class="primary" :disabled="!selectedDocumentId || generating" @click="generate">{{generating?'Agent 生成中…':'✦ AI 生成测试用例'}}</button></div>
          <div class="panel agent-context"><div><small>当前文档</small><b>{{selectedDocument?.title || '尚未选择文档'}}</b><span>{{apis.length}} 个接口 · RAG 知识块 {{ragChunksLabel}}</span></div><button class="secondary" @click="go('apis')">管理接口文档</button></div>
          <div v-if="generationMessage" class="notice success">{{generationMessage}}</div><div v-if="error" class="notice error">{{error}}</div>
          <section v-if="scenarioCoverage" class="panel coverage-panel"><div class="panel-title"><div><h3>场景覆盖校验</h3><p>每个接口必须至少覆盖正常、异常、边界三类场景</p></div><span class="status-pill" :class="scenarioCoverage.complete ? 'PASS' : 'ERROR'">{{scenarioCoverage.complete ? '覆盖完整' : '待补齐场景'}}</span></div><div class="coverage-grid"><div v-for="item in scenarioCoverage.apis" :key="item.apiId" class="coverage-item"><b>{{item.endpoint}}</b><span>正常 {{item.scenarioCounts.POSITIVE || 0}} · 异常 {{item.scenarioCounts.NEGATIVE || 0}} · 边界 {{item.scenarioCounts.BOUNDARY || 0}}</span><small v-if="item.missingScenarios.length">缺少：{{item.missingScenarios.join('、')}}</small><small v-else>三类场景均已覆盖</small></div></div></section>
          <section class="panel"><div class="panel-title"><div><h3>结构化测试用例</h3><p>包含正常、异常、边界、权限和参数校验场景</p></div><span class="pill">{{cases.length}} CASES</span></div><div class="table-wrap"><table><thead><tr><th>用例名称</th><th>接口</th><th>场景</th><th>优先级</th><th>请求定义</th><th>预期结果</th></tr></thead><tbody><tr v-for="item in cases" :key="item.id"><td><b>{{item.caseName}}</b><small class="subline">{{item.description}}</small></td><td class="mono">{{apiLabel(item.apiId)}}</td><td><span class="case-type">{{item.caseType}}</span></td><td>{{item.priority}}</td><td class="truncate">{{item.requestJson}}</td><td class="truncate">{{item.expectResult}}</td></tr></tbody></table><div v-if="!cases.length" class="empty">还没有测试用例。选择接口文档后点击 AI 生成，生成结果会自动保存到 MySQL。</div></div></section>
        </section>

        <section v-else-if="view==='pipeline'">
          <div class="heading"><div><span class="eyebrow">HTTP EXECUTION ENGINE</span><h1>运行测试任务</h1><p>调用真实目标接口，执行断言并由 Qwen LLM-as-a-Judge 进行二次评估。</p></div></div>
          <div class="panel run-config"><div class="form-grid"><label>测试任务名称<input v-model="taskName" placeholder="例如：电商 API 回归测试" /></label><label>OpenAPI 文档<select v-model.number="selectedDocumentId" @change="selectDocument"><option :value="null">请选择文档</option><option v-for="doc in documents" :key="doc.id" :value="doc.id">{{doc.title || doc.name}}</option></select></label><label class="full">目标服务 Base URL<input v-model="baseUrl" placeholder="例如：http://localhost:8081" /><small>如果导入文档的 Server 地址不可访问，可在这里覆盖。只填写域名和基础路径，不要重复填写接口路径。</small></label></div><div class="run-actions"><div><b>{{apis.length}} 个接口 · {{cases.length}} 条已生成用例</b><small>如果尚无用例，启动任务时会先调用 Agent 自动生成。</small></div><button class="primary" :disabled="!selectedDocumentId || running" @click="runTask">{{running?'正在执行真实 HTTP 测试…':'▷ 生成并执行测试任务'}}</button></div></div>
          <section v-if="activeTaskProgress" class="panel result-panel"><div class="panel-title"><div><h3>后台任务进度 · #{{activeTaskProgress.taskId}}</h3><p>{{activeTaskProgress.status}} · {{activeTaskProgress.completedCount}} / {{activeTaskProgress.totalCount}} 条用例</p></div><strong>{{activeTaskProgress.progressPercent}}%</strong></div><div class="progress-track"><div class="progress-value" :style="{ width: activeTaskProgress.progressPercent + '%' }"></div></div><div class="result-metrics"><div><small>通过</small><b class="green-text">{{activeTaskProgress.passedCount}}</b></div><div><small>失败</small><b class="red-text">{{activeTaskProgress.failedCount}}</b></div><div><small>错误</small><b>{{activeTaskProgress.errorCount}}</b></div></div></section>
          <div v-if="error" class="notice error">{{error}}</div>
          <section v-if="runResult" class="panel result-panel"><div class="panel-title"><div><h3>本次执行结果</h3><p>任务 #{{runResult.task.id}} · {{runResult.task.status}}</p></div><span class="pill">{{runResult.executions.length}} EXECUTIONS</span></div><div class="result-metrics"><div><small>总用例</small><b>{{runResult.report.totalCount}}</b></div><div><small>通过</small><b class="green-text">{{runResult.report.successCount}}</b></div><div><small>失败</small><b class="red-text">{{runResult.report.failCount}}</b></div><div><small>错误</small><b>{{runResult.report.errorCount}}</b></div><div><small>通过率</small><b>{{runResult.report.passRate}}%</b></div></div><p class="analysis-text">{{runResult.report.aiAnalysis}}</p><div class="table-wrap"><table><thead><tr><th>用例 ID</th><th>HTTP</th><th>目标 URL</th><th>状态码</th><th>耗时</th><th>评分</th><th>Judge</th><th>结果</th><th>响应摘要</th><th>评估原因</th></tr></thead><tbody><tr v-for="e in runResult.executions" :key="e.id"><td>#{{e.caseId}}</td><td>{{e.requestMethod}}</td><td class="mono truncate">{{e.requestUrl}}</td><td>{{e.responseStatus || '—'}}</td><td>{{e.durationMs}} ms</td><td>{{e.score}}</td><td>{{e.judgeAvailable ? '已评估' : '不可用/跳过'}}</td><td><span class="status-pill" :class="e.status">{{e.status}}</span></td><td class="truncate">{{e.actualResult}}</td><td>{{e.evaluationReason}}</td></tr></tbody></table></div></section>
        </section>

        <section v-else-if="view==='tasks'">
          <div class="heading"><div><span class="eyebrow">TASK HISTORY</span><h1>测试任务</h1><p>查看持久化任务、执行记录与每条用例的实际响应。</p></div><button class="secondary" @click="loadTasks">↻ 刷新</button></div>
          <div class="panel"><div class="panel-title"><div><h3>任务列表</h3><p>{{tasks.length}} 条历史任务</p></div></div><div class="table-wrap"><table><thead><tr><th>任务名称</th><th>文档 ID</th><th>用例数</th><th>通过 / 失败 / 错误</th><th>状态</th><th>创建时间</th><th></th></tr></thead><tbody><tr v-for="task in tasks" :key="task.id"><td><b>{{task.taskName}}</b><small class="subline">#{{task.id}}</small></td><td>{{task.documentId}}</td><td>{{task.totalCount}}</td><td>{{task.passedCount}} / {{task.failedCount}} / {{task.errorCount}}</td><td><span class="status-pill" :class="task.status">{{task.status}}</span></td><td>{{formatTime(task.createdAt)}}</td><td><button class="text-btn" @click="openTask(task)">执行详情 →</button></td></tr></tbody></table><div v-if="!tasks.length" class="empty">暂无任务记录。</div></div></div>
          <section v-if="selectedTask" class="panel detail-panel"><div class="panel-title"><div><h3>{{selectedTask.taskName}} · 执行明细</h3><p>任务 #{{selectedTask.id}}</p></div></div><div class="table-wrap"><table><thead><tr><th>用例 ID</th><th>接口</th><th>目标 URL</th><th>HTTP 状态</th><th>耗时</th><th>评分</th><th>Judge</th><th>结果</th><th>响应摘要</th><th>评估原因</th></tr></thead><tbody><tr v-for="e in taskExecutions" :key="e.id"><td>{{e.caseId}}</td><td class="mono">{{apiLabel(e.apiId)}}</td><td class="mono truncate">{{e.requestUrl}}</td><td>{{e.responseStatus || '—'}}</td><td>{{e.durationMs}} ms</td><td>{{e.score}}</td><td>{{e.judgeAvailable ? '已评估' : '不可用/跳过'}}</td><td><span class="status-pill" :class="e.status">{{e.status}}</span></td><td class="truncate">{{e.actualResult}}</td><td>{{e.evaluationReason}}</td></tr></tbody></table><div v-if="!taskExecutions.length" class="empty">该任务还没有执行记录。</div></div></section>
        </section>

        <section v-else-if="view==='schedules'">
          <div class="heading"><div><span class="eyebrow">CRON · AUTOMATED REGRESSION</span><h1>定时任务调度</h1><p>按 Spring 六段 Cron 表达式自动提交异步测试任务，执行结果可在任务列表查看。</p></div><span class="pill">ASIA/SHANGHAI</span></div>
          <section class="panel run-config">
            <div class="panel-title"><div><h3>创建定时回归计划</h3><p>调度器每 15 秒扫描一次到期计划，触发后立即提交后台任务。</p></div></div>
            <div class="form-grid">
              <label>计划名称<input v-model="scheduleName" placeholder="每日 API 回归" /></label>
              <label>OpenAPI 文档<select v-model.number="selectedDocumentId" @change="selectDocument"><option :value="null">请选择文档</option><option v-for="doc in documents" :key="doc.id" :value="doc.id">{{doc.title || doc.name}}</option></select></label>
              <label>Cron 表达式<input v-model="scheduleCron" placeholder="0 0 9 * * *" /><small>六段格式：秒 分 时 日 月 周；例如每天 09:00：0 0 9 * * *</small></label>
              <label>目标 Base URL<input v-model="scheduleBaseUrl" :placeholder="selectedDocument?.serverUrl || '可留空，使用文档 Server'" /></label>
            </div>
            <div class="run-actions"><div><b>自动生成/复用用例 → 并发执行 → LLM Judge → 整批报告</b><small>请确认目标服务允许按该频率进行自动化测试。</small></div><button class="primary" :disabled="scheduleSaving || !selectedDocumentId" @click="saveSchedule">{{scheduleSaving ? '创建中…' : '＋ 创建定时计划'}}</button></div>
          </section>
          <div v-if="error" class="notice error">{{error}}</div>
          <section class="panel detail-panel"><div class="panel-title"><div><h3>已配置计划</h3><p>{{schedules.length}} 条调度规则</p></div><button class="secondary" @click="loadSchedules">↻ 刷新</button></div>
            <div class="table-wrap"><table><thead><tr><th>计划名称</th><th>文档</th><th>Cron</th><th>下次执行</th><th>上次执行</th><th>状态</th><th>操作</th></tr></thead><tbody>
              <tr v-for="schedule in schedules" :key="schedule.id"><td><b>{{schedule.scheduleName}}</b><small class="subline">{{schedule.taskName}}</small></td><td>#{{schedule.documentId}}</td><td class="mono">{{schedule.cronExpression}}</td><td>{{formatTime(schedule.nextRunAt)}}</td><td>{{formatTime(schedule.lastRunAt)}}</td><td><span class="status-pill" :class="schedule.enabled ? 'PASS' : 'ERROR'">{{schedule.enabled ? '启用' : '暂停'}}</span></td><td><button class="text-btn" @click="toggleSchedule(schedule)">{{schedule.enabled ? '暂停' : '启用'}}</button><button class="text-btn" @click="removeSchedule(schedule)">删除</button></td></tr>
            </tbody></table><div v-if="!schedules.length" class="empty">暂无定时计划。创建后由后端调度器自动触发。</div></div>
          </section>
        </section>

        <section v-else-if="view==='reports'">
          <div class="heading"><div><span class="eyebrow">QUALITY INSIGHTS · ECHARTS</span><h1>真实测试报告</h1><p>所有统计来自 MySQL 中的测试任务、执行记录和报告数据，不使用演示固定值。</p></div><button class="secondary" @click="loadReports">↻ 刷新报告</button></div>
          <div class="metrics report-metrics"><article><small>测试任务</small><strong>{{summary.taskCount || 0}}</strong><span>历史任务数量</span></article><article><small>执行用例</small><strong>{{summary.totalCount || 0}}</strong><span>累计执行数量</span></article><article><small>通过用例</small><strong class="green-text">{{summary.passedCount || 0}}</strong><span>断言及评估通过</span></article><article><small>累计通过率</small><strong>{{summary.passRate || 0}}<em>%</em></strong><span>真实执行统计</span></article></div>
          <div class="chart-grid"><section class="panel"><div class="panel-title"><div><h3>测试通过率趋势</h3><p>按测试报告生成时间统计</p></div></div><div ref="passChartEl" class="chart"></div></section><section class="panel"><div class="panel-title"><div><h3>接口失败分布</h3><p>按接口路径聚合 FAIL / ERROR</p></div></div><div ref="qualityChartEl" class="chart"></div></section></div>
          <div v-if="reportError" class="notice error">{{reportError}}</div>
        </section>
      </div>
      <footer>© 2026 SYY Agent Test Platform V11 <span>OpenAPI · Agent + RAG · HTTP Executor · LLM Judge · MySQL</span></footer>
    </main>
    <transition name="fade"><div v-if="toast" class="toast">{{toast}}</div></transition>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  checkHealth, uploadOpenApi, getDocuments, getApis, getCases, generateCases, getScenarioCoverage, getKnowledgeStatus,
  getTasks, getTask, getTaskExecutions, getTaskProgress, getTaskReport, createTestTask,
  getSchedules, createSchedule, setScheduleEnabled, deleteSchedule,
  getReportSummary, getReportTrend, getReportQuality
} from './api/platformApi.js'

const nav = [
  { id: 'overview', label: '测试总览', icon: '▦' },
  { id: 'apis', label: '接口管理', icon: '⌘' },
  { id: 'cases', label: 'Agent 用例工作台', icon: '✦' },
  { id: 'pipeline', label: 'HTTP 测试执行', icon: '▷' },
  { id: 'tasks', label: '任务与执行记录', icon: '☷' },
  { id: 'schedules', label: '定时任务调度', icon: '◷' },
  { id: 'reports', label: '测试报告', icon: '▥' }
]
const steps = [
  { id: 'apis', icon: '⇧', name: 'OpenAPI 解析', desc: '接口契约与定义持久化' },
  { id: 'cases', icon: '✦', name: 'Agent + RAG', desc: '生成结构化测试用例' },
  { id: 'pipeline', icon: '▷', name: 'HTTP Executor', desc: '真实请求与断言执行' },
  { id: 'tasks', icon: '◈', name: 'LLM-as-a-Judge', desc: '智能评估并保存结果' },
  { id: 'reports', icon: '▥', name: '真实测试报告', desc: 'MySQL 统计与 ECharts' }
]
const view = ref('overview')
const mobileOpen = ref(false)
const health = ref('checking')
const documents = ref([])
const selectedDocumentId = ref(null)
const selectedDocument = computed(() => documents.value.find(d => d.id === selectedDocumentId.value) || null)
const apis = ref([])
const cases = ref([])
const scenarioCoverage = ref(null)
const tasks = ref([])
const schedules = ref([])
const activeTaskProgress = ref(null)
const scheduleName = ref('每日 API 回归')
const scheduleCron = ref('0 0 9 * * *')
const scheduleBaseUrl = ref('')
const scheduleSaving = ref(false)
const selectedFile = ref(null)
const fileInput = ref(null)
const dragging = ref(false)
const uploading = ref(false)
const generating = ref(false)
const running = ref(false)
const notice = ref('')
const noticeType = ref('success')
const error = ref('')
const generationMessage = ref('')
const ragChunksLabel = ref('导入接口后建立')
const taskName = ref('SYY API 自动化测试任务')
const baseUrl = ref('')
const runResult = ref(null)
const selectedTask = ref(null)
const taskExecutions = ref([])
const summary = ref({})
const reportTrend = ref({ dates: [], passRate: [] })
const reportQuality = ref({ interfaces: [], failed: [] })
const reportError = ref('')
const passChartEl = ref(null)
const qualityChartEl = ref(null)
const toast = ref('')
let toastTimer = null
let passChart = null
let qualityChart = null

const title = computed(() => nav.find(n => n.id === view.value)?.label || '测试总览')
const healthText = computed(() => health.value === 'online' ? '后端已连接' : health.value === 'checking' ? '检测后端中' : '后端离线')

function go(id) {
  view.value = id
  mobileOpen.value = false
  error.value = ''
  if (id === 'tasks') loadTasks()
  if (id === 'schedules') loadSchedules()
  if (id === 'reports') loadReports()
  if (id === 'cases') { loadCases(); loadKnowledgeStatus() }
}
function notify(message) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2800)
}
function formatTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
function apiLabel(apiId) {
  const api = apis.value.find(item => item.id === apiId)
  return api ? `${api.method} ${api.path}` : `API #${apiId}`
}
async function refreshHealth() {
  try { await checkHealth(); health.value = 'online' } catch { health.value = 'offline' }
}
async function loadDocuments() {
  documents.value = await getDocuments()
  if (!selectedDocumentId.value && documents.value.length) selectedDocumentId.value = documents.value[0].id
  if (selectedDocumentId.value) await selectDocument()
}
async function selectDocument() {
  if (!selectedDocumentId.value) { apis.value = []; cases.value = []; scenarioCoverage.value = null; selectedDocumentId.value = null; baseUrl.value = ''; return }
  const [apiRows, caseRows, coverage] = await Promise.all([getApis(selectedDocumentId.value), getCases(selectedDocumentId.value), getScenarioCoverage(selectedDocumentId.value)])
  apis.value = apiRows || []
  cases.value = caseRows || []
  scenarioCoverage.value = coverage || null
  baseUrl.value = selectedDocument.value?.serverUrl || ''
  runResult.value = null
}
function onFile(event) { selectedFile.value = event.target.files?.[0] || null; notice.value = '' }
function onDrop(event) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  if (!/\.(json|ya?ml)$/i.test(file.name)) { notice.value = '仅支持 JSON / YAML 格式的 Swagger / OpenAPI 文档。'; noticeType.value = 'error'; return }
  selectedFile.value = file
}
async function upload() {
  if (!selectedFile.value) return
  uploading.value = true; notice.value = ''; error.value = ''
  try {
    const result = await uploadOpenApi(selectedFile.value)
    documents.value = await getDocuments()
    selectedDocumentId.value = result.document.id
    await selectDocument()
    noticeType.value = 'success'
    notice.value = `解析并保存成功：${result.apiCount} 个接口。RAG：${result.ragIndexStatus}`
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    notify('OpenAPI 文档已保存到 MySQL')
  } catch (e) { noticeType.value = 'error'; notice.value = e.message || '文档导入失败' }
  finally { uploading.value = false }
}
async function loadCases() {
  if (!selectedDocumentId.value) return
  try {
    const [rows, coverage] = await Promise.all([getCases(selectedDocumentId.value), getScenarioCoverage(selectedDocumentId.value)])
    cases.value = rows || []
    scenarioCoverage.value = coverage || null
  } catch (e) { error.value = e.message }
}
async function loadKnowledgeStatus() {
  try { const result = await getKnowledgeStatus(); ragChunksLabel.value = `${result.chunkCount} 个持久化向量块` } catch { ragChunksLabel.value = 'RAG 状态暂不可用' }
}
async function generate() {
  if (!selectedDocumentId.value) return
  generating.value = true; error.value = ''; generationMessage.value = ''
  try {
    cases.value = await generateCases(selectedDocumentId.value)
    await loadCases()
    generationMessage.value = `Agent + RAG 已生成/复用并持久化 ${cases.value.length} 条启用用例，正常/异常/边界覆盖已校验。`
    await loadKnowledgeStatus()
    notify('结构化测试用例生成完成')
  } catch (e) { error.value = e.message || 'Agent 生成失败' }
  finally { generating.value = false }
}
async function runTask() {
  if (!selectedDocumentId.value) return
  running.value = true; error.value = ''; runResult.value = null; activeTaskProgress.value = null
  try {
    const submitted = await createTestTask({ documentId: selectedDocumentId.value, taskName: taskName.value, baseUrl: baseUrl.value || null, caseIds: null })
    activeTaskProgress.value = await getTaskProgress(submitted.taskId)
    notify(`任务 #${submitted.taskId} 已进入队列，后台并发执行中`)
    tasks.value = await getTasks()
    const terminal = ['COMPLETED', 'FAILED']
    while (activeTaskProgress.value && !terminal.includes(activeTaskProgress.value.status)) {
      await new Promise(resolve => window.setTimeout(resolve, 1200))
      activeTaskProgress.value = await getTaskProgress(submitted.taskId)
      await loadTasks()
    }
    if (activeTaskProgress.value.status === 'FAILED') throw new Error(activeTaskProgress.value.errorMessage || '测试任务执行失败')
    const [task, executions, report] = await Promise.all([
      getTask(submitted.taskId), getTaskExecutions(submitted.taskId), getTaskReport(submitted.taskId)
    ])
    runResult.value = { task, executions, report }
    await loadCases()
    notify(`任务完成：${report.passRate}% 通过率`)
  } catch (e) { error.value = e.message || '测试任务执行失败' }
  finally { running.value = false }
}

async function loadTasks() {
  try { tasks.value = await getTasks() } catch (e) { error.value = e.message }
}
async function loadSchedules() {
  try { schedules.value = await getSchedules() } catch (e) { error.value = e.message }
}
async function saveSchedule() {
  if (!selectedDocumentId.value) { error.value = '请先选择 OpenAPI 文档'; return }
  scheduleSaving.value = true; error.value = ''
  try {
    await createSchedule({ documentId: selectedDocumentId.value, scheduleName: scheduleName.value,
      taskName: taskName.value, baseUrl: scheduleBaseUrl.value || baseUrl.value || null,
      cronExpression: scheduleCron.value, enabled: true })
    await loadSchedules(); notify('定时任务已创建')
  } catch (e) { error.value = e.message }
  finally { scheduleSaving.value = false }
}
async function toggleSchedule(schedule) {
  try { await setScheduleEnabled(schedule.id, !schedule.enabled); await loadSchedules() }
  catch (e) { error.value = e.message }
}
async function removeSchedule(schedule) {
  if (!window.confirm(`确定删除定时任务“${schedule.scheduleName}”吗？`)) return
  try { await deleteSchedule(schedule.id); await loadSchedules(); notify('定时任务已删除') }
  catch (e) { error.value = e.message }
}
async function openTask(task) {
  selectedTask.value = task
  try { taskExecutions.value = await getTaskExecutions(task.id) } catch (e) { error.value = e.message }
}
async function loadReports() {
  reportError.value = ''
  try {
    const [s, trend, quality] = await Promise.all([getReportSummary(), getReportTrend(), getReportQuality()])
    summary.value = s || {}
    reportTrend.value = trend || { dates: [], passRate: [] }
    reportQuality.value = quality || { interfaces: [], failed: [] }
    await nextTick()
    renderCharts()
  } catch (e) { reportError.value = e.message || '报告加载失败' }
}
function renderCharts() {
  const echarts = window.echarts
  if (!echarts) return
  if (passChartEl.value) {
    passChart ||= echarts.init(passChartEl.value)
    passChart.setOption({ tooltip: { trigger: 'axis' }, grid: { left: 42, right: 20, top: 25, bottom: 38 }, xAxis: { type: 'category', data: reportTrend.value.dates, axisLabel: { color: '#8b96aa' } }, yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%', color: '#8b96aa' }, splitLine: { lineStyle: { color: '#edf0f5' } } }, series: [{ name: '通过率', type: 'line', smooth: true, data: reportTrend.value.passRate, symbolSize: 8, lineStyle: { width: 3, color: '#5d73ee' }, itemStyle: { color: '#5d73ee' }, areaStyle: { color: 'rgba(93,115,238,.12)' } }] }, true)
  }
  if (qualityChartEl.value) {
    qualityChart ||= echarts.init(qualityChartEl.value)
    qualityChart.setOption({ tooltip: { trigger: 'axis' }, grid: { left: 120, right: 25, top: 20, bottom: 25 }, xAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#8b96aa' }, splitLine: { lineStyle: { color: '#edf0f5' } } }, yAxis: { type: 'category', data: reportQuality.value.interfaces, axisLabel: { color: '#68758c', width: 105, overflow: 'truncate' } }, series: [{ type: 'bar', data: reportQuality.value.failed, barMaxWidth: 22, itemStyle: { color: '#e9a05c', borderRadius: [0, 6, 6, 0] } }] }, true)
  }
}
function resizeCharts() { passChart?.resize(); qualityChart?.resize() }
watch(view, async (value) => {
  if (value === 'reports') { await nextTick(); renderCharts() }
  else { passChart?.dispose(); qualityChart?.dispose(); passChart = null; qualityChart = null }
})
onMounted(async () => {
  await refreshHealth()
  try { await loadDocuments(); await loadTasks() } catch (e) { error.value = e.message }
  window.addEventListener('resize', resizeCharts)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  window.clearTimeout(toastTimer)
  passChart?.dispose(); qualityChart?.dispose()
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Manrope:wght@500;600;700;800&family=Noto+Sans+SC:wght@400;500;600;700;800&display=swap');
:root{font-family:'DM Sans','Noto Sans SC',sans-serif;color:#1d2b45;background:#f5f7fb;font-synthesis:none;text-rendering:optimizeLegibility;--blue:#526df5;--navy:#111d35;--muted:#8d99ad;--line:#e9edf4}*{box-sizing:border-box}body{margin:0;min-width:320px;min-height:100vh}button,input,select{font:inherit}button{cursor:pointer}.shell{min-height:100vh;display:flex;background:#f6f8fc}.sidebar{width:250px;min-width:250px;background:#111c32;color:#eaf0ff;display:flex;flex-direction:column;padding:24px 16px 16px;z-index:10}.brand{display:flex;align-items:center;gap:11px;padding:0 8px 28px}.brand-icon{width:38px;height:38px;border-radius:12px;background:linear-gradient(145deg,#8293ff,#596bf0);display:grid;place-items:center;color:white;font:800 21px Manrope;box-shadow:0 8px 18px #526df544}.brand b{display:block;font:800 15px Manrope;letter-spacing:.7px}.brand small{display:block;color:#8290aa;font-size:9px;letter-spacing:2px;margin-top:3px}.mobile-close{display:none;margin-left:auto;background:none;border:0;color:#fff;font-size:23px}.workspace{display:flex;align-items:center;gap:10px;padding:11px;border:1px solid #293752;border-radius:12px;background:#1a2842;margin-bottom:27px}.workspace-mark{width:32px;height:32px;border-radius:9px;background:#2b3b5b;color:#a9b7ff;display:grid;place-items:center}.workspace b,.workspace small{display:block}.workspace b{font-size:11px}.workspace small{font-size:9px;color:#8998b3;margin-top:3px}.sidebar nav p{font-size:9px;color:#687894;letter-spacing:1.5px;padding:0 10px;margin:0 0 10px}.sidebar nav button{width:100%;height:42px;border:0;background:transparent;color:#a9b5cb;text-align:left;border-radius:9px;padding:0 11px;display:flex;align-items:center;gap:11px;font-size:11px;margin:3px 0}.sidebar nav button span{font-size:15px;width:17px;text-align:center}.sidebar nav button.active,.sidebar nav button:hover{background:#263653;color:#fff}.sidebar nav button.active{box-shadow:inset 3px 0 #7c8dff}.sidebar nav button i{font-style:normal;margin-left:auto;background:#40558a;color:#dce4ff;border-radius:10px;padding:2px 7px;font-size:9px}.sidebar-bottom{margin-top:auto}.agent-card{padding:14px;border-radius:12px;background:linear-gradient(145deg,#26385e,#1c2b49);border:1px solid #35486d;display:flex;flex-direction:column;align-items:flex-start}.agent-orb{width:28px;height:28px;display:grid;place-items:center;border-radius:9px;background:#6577e9;color:#fff;margin-bottom:10px}.agent-card b{font-size:11px}.agent-card small{font-size:9px;color:#9cabc7;margin:5px 0 12px}.agent-card button{background:none;border:0;color:#aebcff;padding:0;font-size:9px}.user{display:flex;align-items:center;gap:9px;padding:18px 6px 2px}.user>span{width:30px;height:30px;border-radius:9px;background:#334665;display:grid;place-items:center;font-size:10px}.user b,.user small{display:block}.user b{font-size:10px}.user small{font-size:9px;color:#8392ad;margin-top:3px}.main{min-width:0;flex:1;display:flex;flex-direction:column}.topbar{height:66px;border-bottom:1px solid #edf0f5;background:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 34px}.crumb{display:flex;align-items:center;gap:11px;color:#9aa5b6;font-size:10px}.crumb b{font-weight:400;color:#c5ccda}.crumb strong{color:#4b5972;font-weight:600}.top-actions{display:flex;align-items:center;gap:17px}.health{display:flex;align-items:center;gap:7px;font-size:10px;color:#75839b}.health i,.status-dot{width:7px;height:7px;border-radius:50%;background:#e6aa5d}.health i.online,.status-dot.COMPLETED{background:#23b68b}.health i.offline{background:#e56d6d}.version{font-size:9px;letter-spacing:1px;color:#9ca8bb;border:1px solid #e9edf4;padding:6px 9px;border-radius:6px}.mobile-menu{display:none}.content{width:100%;max-width:1500px;margin:0 auto;padding:31px 34px 45px;flex:1}.heading{display:flex;justify-content:space-between;align-items:flex-end;gap:18px;margin-bottom:23px}.eyebrow{font:700 9px Manrope;color:#7182e7;letter-spacing:1.7px}.heading h1{font-size:27px;margin:7px 0 5px;color:#1b2942;letter-spacing:-.6px}.heading p{font-size:11px;color:#94a0b2;margin:0}.primary,.secondary,.light,.ghost{border:0;border-radius:9px;padding:11px 17px;font-size:10px;font-weight:700;white-space:nowrap}.primary{background:#566ff3;color:white;box-shadow:0 7px 17px #526df52b}.primary:disabled,.secondary:disabled{opacity:.5;cursor:not-allowed}.secondary{background:#fff;border:1px solid #e5eaf2;color:#586780}.hero{min-height:248px;border-radius:17px;padding:31px 37px;display:flex;align-items:center;justify-content:space-between;position:relative;overflow:hidden;color:#fff;background:linear-gradient(112deg,#1b2c52,#30457b 62%,#4c5ba5);margin-bottom:17px}.hero-tag{font-size:9px;letter-spacing:1.6px;color:#b8c5ff}.hero h2{font-size:25px;line-height:1.5;margin:13px 0 7px}.hero p{font-size:10px;line-height:1.8;color:#c1cce6;max-width:550px}.hero-buttons{display:flex;gap:10px;margin-top:18px}.light{background:#fff;color:#344a83}.ghost{border:1px solid #ffffff55;background:#ffffff12;color:#e9edff}.hero-visual{width:245px;height:200px;position:relative;display:grid;place-items:center;margin-right:6%}.ring{position:absolute;border:1px solid #ffffff28;border-radius:50%;transform:rotate(-20deg)}.ring-a{width:190px;height:145px}.ring-b{width:140px;height:190px}.core{width:72px;height:72px;border-radius:23px;background:linear-gradient(145deg,#8294ff,#5a6fe7);display:grid;place-items:center;font:800 17px Manrope;box-shadow:0 0 35px #8b9aff70}.node{position:absolute;border:1px solid #ffffff45;background:#ffffff18;border-radius:8px;padding:7px 10px;font:700 9px Manrope}.n1{top:18px;left:15px}.n2{right:4px;top:74px}.n3{bottom:12px;left:48px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:13px;margin-bottom:17px}.metrics article{background:#fff;border:1px solid #edf0f5;border-radius:12px;padding:16px 18px;display:flex;flex-direction:column;gap:5px}.metrics small{font-size:9px;color:#98a4b6}.metrics strong{font:800 25px Manrope;color:#2d3d5a}.metrics strong em{font-style:normal;font-size:12px;margin-left:3px}.metrics article span{font-size:9px;color:#adb6c4}.green-text{color:#20a982!important}.red-text{color:#d86d6d!important}.two-col{display:grid;grid-template-columns:1.1fr .9fr;gap:15px}.panel{background:#fff;border:1px solid #edf0f5;border-radius:13px;padding:19px 20px;min-width:0}.panel-title{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:15px}.panel-title h3{font-size:13px;margin:0;color:#3b4a64}.panel-title p{font-size:9px;color:#a1acbc;margin:5px 0 0}.pill{font-size:8px;letter-spacing:1px;color:#7888e8;background:#f0f2ff;padding:6px 8px;border-radius:6px}.workflow{display:flex;flex-direction:column}.workflow button{display:flex;align-items:center;gap:12px;border:0;background:none;text-align:left;padding:12px 3px;border-bottom:1px solid #f0f2f6}.workflow button:last-child{border-bottom:0}.workflow button>b{font:700 9px Manrope;color:#9da9bc}.step-icon{width:32px;height:32px;display:grid;place-items:center;border-radius:9px;background:#eff2ff;color:#6379ef}.workflow strong,.workflow small{display:block}.workflow strong{font-size:10px;color:#53617a}.workflow small{font-size:8px;color:#a5afbd;margin-top:4px}.workflow button>i{font-style:normal;margin-left:auto;color:#a6b0c0}.recent-list{display:flex;flex-direction:column}.recent-row{display:flex;align-items:center;gap:9px;padding:11px 0;border-bottom:1px solid #f0f2f6}.recent-row:last-child{border-bottom:0}.recent-row div{flex:1;min-width:0}.recent-row b,.recent-row small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.recent-row b{font-size:9px;color:#56647c}.recent-row small{font-size:8px;color:#a6b0bf;margin-top:4px}.recent-row>strong{font-size:8px;color:#8d9ab0}.status-dot{flex-shrink:0}.status-dot.FAILED{background:#e46f6f}.status-dot.RUNNING{background:#e7ac56}.empty{padding:28px 12px;text-align:center;color:#a3adbc;font-size:10px}.text-btn{border:0;background:none;color:#6579ed;font-size:9px;padding:4px}.hidden{display:none}.upload{display:flex;align-items:center;gap:16px;padding:22px;border:1px dashed #cbd4eb;background:#fff;border-radius:13px;margin-bottom:15px;transition:.2s}.upload.dragging{background:#f0f3ff;border-color:#6478ed}.upload-icon{width:52px;height:52px;display:grid;place-items:center;border-radius:15px;background:#eef1ff;color:#6378f2;font-size:25px}.upload-copy{flex:1}.upload-copy b{font-size:12px;color:#45546d}.upload-copy p{font-size:9px;color:#909db1;margin:6px 0}.upload-copy small{font-size:8px;color:#aab4c3}.doc-panel{padding-bottom:5px}.doc-panel select,.form-grid input,.form-grid select{height:38px;border:1px solid #e5eaf2;border-radius:8px;background:#fff;padding:0 10px;color:#56647c;font-size:10px;min-width:180px}.doc-meta{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;background:#f8f9fc;padding:13px;border-radius:9px;margin-bottom:15px}.doc-meta small,.doc-meta b{display:block}.doc-meta small{font-size:8px;color:#9aa6b8;margin-bottom:5px}.doc-meta b{font-size:9px;color:#5a6880;word-break:break-all}.table-wrap{width:100%;overflow:auto}table{width:100%;border-collapse:collapse;min-width:700px}th{text-align:left;font-size:8px;letter-spacing:.4px;color:#9ba7b8;font-weight:600;background:#fafbfc;padding:11px 10px}td{padding:12px 10px;border-bottom:1px solid #f0f2f6;font-size:9px;color:#66748b;vertical-align:middle}td b{color:#4c5a73}td small.subline{display:block;color:#a7b1bf;font-size:8px;margin-top:4px}.method{display:inline-block;padding:4px 7px;border-radius:5px;font:700 8px Manrope;background:#edf2ff;color:#5471dc}.method.post{background:#eaf8f1;color:#269b73}.method.put,.method.patch{background:#fff5e8;color:#d18a3c}.method.delete{background:#fff0ef;color:#d36d6d}.mono{font-family:ui-monospace,Consolas,monospace;color:#5465a0}.notice{padding:11px 14px;border-radius:8px;font-size:9px;margin:12px 0}.notice.success{background:#eef9f4;color:#248e70}.notice.error{background:#fff1f0;color:#c76363}.agent-context{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.agent-context small,.agent-context b,.agent-context span{display:block}.agent-context small{font-size:8px;color:#9ba7b7}.agent-context b{font-size:12px;color:#4c5b74;margin:4px 0}.agent-context span{font-size:8px;color:#9da9b8}.case-type{background:#f1f2ff;color:#7775c9;border-radius:5px;padding:4px 6px;font-size:8px}.truncate{max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.run-config{padding:22px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:17px}.form-grid label{display:flex;flex-direction:column;gap:7px;font-size:9px;color:#748199}.form-grid input,.form-grid select{width:100%;height:40px}.form-grid .full{grid-column:1/-1}.form-grid small{font-size:8px;color:#a2adbd}.run-actions{border-top:1px solid #eff1f5;margin-top:20px;padding-top:17px;display:flex;align-items:center;justify-content:space-between;gap:15px}.run-actions b,.run-actions small{display:block}.run-actions b{font-size:10px;color:#56647b}.run-actions small{font-size:8px;color:#a4afbe;margin-top:5px}.result-panel{margin-top:16px}.result-metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:9px}.result-metrics div{background:#f8f9fc;border-radius:8px;padding:12px}.result-metrics small,.result-metrics b{display:block}.result-metrics small{font-size:8px;color:#9ba7b7}.result-metrics b{font:700 19px Manrope;color:#475773;margin-top:6px}.analysis-text{font-size:9px;line-height:1.8;color:#7d899d;background:#f8f9fc;padding:12px;border-radius:8px;margin:14px 0;white-space:pre-wrap;overflow-wrap:anywhere}.coverage-panel{margin-bottom:14px}.coverage-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:9px}.coverage-item{display:flex;flex-direction:column;gap:6px;padding:11px;background:#f8f9fc;border-radius:8px;min-width:0}.coverage-item b{font-size:10px;color:#4c5b74;overflow-wrap:anywhere}.coverage-item span{font-size:9px;color:#75839a}.coverage-item small{font-size:8px;color:#a0aabb}.coverage-item small:first-of-type{color:#d18a3c}.status-pill{font-size:8px;padding:4px 7px;border-radius:5px;background:#eef9f4;color:#239a74}.status-pill.FAIL,.status-pill.FAILED{background:#fff0ef;color:#d46a6a}.status-pill.ERROR{background:#fff5e8;color:#c58b46}.status-pill.RUNNING,.status-pill.QUEUED{background:#fff6e9;color:#c38b45}.progress-track{height:9px;border-radius:9px;background:#edf0f6;overflow:hidden;margin:12px 0}.progress-value{height:100%;background:linear-gradient(90deg,#6378f2,#35b68c);transition:width .25s}.detail-panel{margin-top:15px}.report-metrics{margin-top:5px}.chart-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}.chart{height:300px;width:100%}footer{display:flex;justify-content:space-between;padding:16px 34px;border-top:1px solid #edf0f5;background:#fff;color:#a0aaba;font-size:8px}footer span{color:#9ba7b7}.toast{position:fixed;right:25px;bottom:25px;padding:12px 17px;background:#1c2c4b;color:#fff;border-radius:9px;font-size:10px;box-shadow:0 12px 30px #17274430;z-index:30}.fade-enter-active,.fade-leave-active{transition:.2s}.fade-enter-from,.fade-leave-to{opacity:0;transform:translateY(6px)}
@media(max-width:1100px){.content{padding:25px}.sidebar{width:225px;min-width:225px}.hero-visual{margin-right:0}.metrics{grid-template-columns:repeat(2,1fr)}}
@media(max-width:820px){.sidebar{position:fixed;left:-260px;top:0;bottom:0;width:250px;min-width:250px;transition:left .2s}.sidebar.open{left:0}.mobile-close{display:block}.scrim{position:fixed;inset:0;background:#101a2d80;z-index:9}.mobile-menu{display:block;border:0;background:none;color:#586780}.topbar{padding:0 18px}.content{padding:22px 18px}.two-col,.chart-grid{grid-template-columns:1fr}.hero-visual{opacity:.4;position:absolute;right:-30px}.hero>div:first-child{position:relative;z-index:1}.form-grid{grid-template-columns:1fr}.form-grid .full{grid-column:auto}}
@media(max-width:560px){.heading{align-items:flex-start;flex-direction:column}.heading h1{font-size:23px}.hero{padding:24px 20px;min-height:260px}.hero h2{font-size:21px}.hero-visual{right:-110px;opacity:.22}.metrics{gap:8px}.metrics article{padding:13px}.metrics strong{font-size:21px}.upload{align-items:flex-start;flex-wrap:wrap;padding:15px}.upload-copy{min-width:calc(100% - 70px)}.upload .secondary{width:100%}.doc-meta{grid-template-columns:1fr}.doc-panel .panel-title,.agent-context{align-items:flex-start;flex-direction:column}.doc-panel select{width:100%}.run-actions{align-items:flex-start;flex-direction:column}.run-actions .primary{width:100%}.result-metrics{grid-template-columns:repeat(2,1fr)}footer{gap:8px;flex-direction:column;padding:14px 18px}}
</style>
