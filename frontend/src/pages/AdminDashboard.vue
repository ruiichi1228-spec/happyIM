<template>
  <div class="admin-layout">
    <!-- 顶栏 -->
    <div class="admin-header">
      <span class="admin-title">HappyIM 管理后台</span>
      <div class="admin-header-right">
        <span class="admin-user">{{ adminInfo?.nickname || adminInfo?.username }}</span>
        <el-button size="small" @click="handleLogout">退出</el-button>
      </div>
    </div>

    <!-- 标签页 -->
    <div class="admin-body">
      <el-tabs v-model="activeTab" type="border-card" class="admin-tabs">
        <!-- 仪表盘 -->
        <el-tab-pane label="仪表盘" name="dashboard">
          <div class="stat-grid">
            <div class="stat-card">
              <div class="stat-icon" style="background:#e1f3fb"><el-icon :size="28" color="#409eff"><UserFilled /></el-icon></div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalUsers ?? '-' }}</div>
                <div class="stat-label">总用户数</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon" style="background:#e8f8e0"><el-icon :size="28" color="#67c23a"><User /></el-icon></div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.todayNewUsers ?? '-' }}</div>
                <div class="stat-label">今日新增</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon" style="background:#fef0e6"><el-icon :size="28" color="#e6a23c"><ChatDotRound /></el-icon></div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalGroups ?? '-' }}</div>
                <div class="stat-label">总群组数</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon" style="background:#e1f3fb"><el-icon :size="28" color="#409eff""><Connection /></el-icon></div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.onlineUsers ?? '-' }}</div>
                <div class="stat-label">在线用户</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon" style="background:#f5e6fc"><el-icon :size="28" color="#9b59b6""><ChatLineRound /></el-icon></div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.todayMessages ?? '-' }}</div>
                <div class="stat-label">今日消息数</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon" style="background:#e8f8e0"><el-icon :size="28" color="#67c23a"><Folder /></el-icon></div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalFiles ?? '-' }}</div>
                <div class="stat-label">文件总数</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="users">
          <div class="toolbar">
            <el-input v-model="userKeyword" placeholder="搜索用户名/昵称/邮箱" clearable style="width:280px" @input="loadUsers" />
          </div>
          <el-table :data="users" v-loading="userLoading" stripe>
            <el-table-column prop="id" label="ID" width="100" />
            <el-table-column label="头像" width="60">
              <template #default="{ row }"><el-avatar :src="row.avatarUrl" :size="32" shape="square">{{ row.nickname?.charAt(0) }}</el-avatar></template>
            </el-table-column>
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column prop="email" label="邮箱" min-width="200" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="注册时间" width="170" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" @click="showUserDetail(row)">详情</el-button>
                <el-button v-if="row.status === 1" size="small" type="danger" @click="toggleUserStatus(row)">禁用</el-button>
                <el-button v-else size="small" type="success" @click="toggleUserStatus(row)">启用</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination background layout="prev, pager, next" :total="userTotal" :page-size="userPageSize" v-model:current-page="userPage" @current-change="loadUsers" />
          </div>
        </el-tab-pane>

        <!-- 群聊管理 -->
        <el-tab-pane label="群聊管理" name="groups">
          <div class="toolbar">
            <el-input v-model="groupKeyword" placeholder="搜索群名称" clearable style="width:280px" @input="loadGroups" />
          </div>
          <el-table :data="groups" v-loading="groupLoading" stripe>
            <el-table-column prop="id" label="ID" width="100" />
            <el-table-column prop="name" label="群名称" min-width="180" />
            <el-table-column prop="ownerId" label="群主ID" width="100" />
            <el-table-column prop="memberCount" label="成员数" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">{{ row.status === 0 ? '正常' : '已解散' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" @click="showGroupMembers(row)">成员</el-button>
                <el-button v-if="row.status === 0" size="small" type="danger" @click="dissolveGroup(row)">解散</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination background layout="prev, pager, next" :total="groupTotal" :page-size="groupPageSize" v-model:current-page="groupPage" @current-change="loadGroups" />
          </div>
        </el-tab-pane>

        <!-- 敏感词管理 -->
        <el-tab-pane label="敏感词管理" name="words">
          <div class="toolbar">
            <el-input v-model="newWord" placeholder="输入新敏感词" style="width:280px" @keyup.enter="addWord" />
            <el-button type="primary" @click="addWord" style="margin-left:8px">添加</el-button>
          </div>
          <el-table :data="sensitiveWords" v-loading="wordLoading" stripe>
            <el-table-column prop="id" label="ID" width="100" />
            <el-table-column prop="word" label="词语" min-width="300" />
            <el-table-column prop="createdTime" label="添加时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="deleteWord(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="userDetailVisible" title="用户详情" width="480px" destroy-on-close>
      <div class="detail-card" v-if="detailUser">
        <el-avatar :src="detailUser.avatarUrl" :size="64" shape="square">{{ detailUser.nickname?.charAt(0) }}</el-avatar>
        <el-descriptions :column="2" border style="margin-top:16px">
          <el-descriptions-item label="ID">{{ detailUser.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detailUser.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detailUser.nickname }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detailUser.email }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detailUser.gender == 1 ? '男' : detailUser.gender == 2 ? '女' : '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detailUser.status === 1 ? 'success' : 'danger'" size="small">{{ detailUser.status === 1 ? '正常' : '禁用' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="签名" :span="2">{{ detailUser.signature || '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ detailUser.createdTime }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ detailUser.lastLoginTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 群成员弹窗 -->
    <el-dialog v-model="memberVisible" title="群成员" width="500px" destroy-on-close>
      <el-table :data="members" stripe max-height="400">
        <el-table-column label="头像" width="60">
          <template #default="{ row }"><el-avatar :src="row.avatarUrl" :size="32" shape="square">{{ row.nickname?.charAt(0) }}</el-avatar></template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 1 ? 'danger' : row.role === 2 ? 'warning' : 'info'">
              {{ row.role === 1 ? '群主' : row.role === 2 ? '管理' : '成员' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, User, ChatDotRound, Connection, ChatLineRound, Folder } from '@element-plus/icons-vue'

const router = useRouter()
const activeTab = ref('dashboard')
const adminInfo = ref(null)

const adminAxios = axios.create({ baseURL: '' })
adminAxios.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
adminAxios.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_info')
      router.push('/admin/login')
    }
    return Promise.reject(err)
  }
)

const handleLogout = () => {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_info')
  router.push('/admin/login')
}

// ==================== 仪表盘 ====================
const stats = ref({})

const loadDashboard = async () => {
  try {
    const res = await adminAxios.get('/api/admin/dashboard')
    if (res.data.code === 0) stats.value = res.data.data
  } catch (e) { /* */ }
}

// ==================== 用户管理 ====================
const users = ref([]), userLoading = ref(false), userTotal = ref(0)
const userKeyword = ref(''), userPage = ref(1), userPageSize = ref(10)

const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await adminAxios.get('/api/admin/users', {
      params: { keyword: userKeyword.value, page: userPage.value, pageSize: userPageSize.value }
    })
    if (res.data.code === 0) {
      users.value = res.data.data.list; userTotal.value = res.data.data.total
    }
  } catch (e) { /* */ } finally { userLoading.value = false }
}

const toggleUserStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 "${row.nickname}" 吗？`, '确认操作', { type: 'warning' })
    const res = await adminAxios.put(`/api/admin/users/${row.id}/status`, { status: newStatus })
    if (res.data.code === 0) { row.status = newStatus; ElMessage.success(`${action}成功`) }
  } catch (e) { /* cancelled or error */ }
}

const detailUser = ref(null), userDetailVisible = ref(false)
const showUserDetail = async (row) => {
  try {
    const res = await adminAxios.get(`/api/admin/users/${row.id}`)
    if (res.data.code === 0) { detailUser.value = res.data.data; userDetailVisible.value = true }
  } catch (e) { /* */ }
}

// ==================== 群组管理 ====================
const groups = ref([]), groupLoading = ref(false), groupTotal = ref(0)
const groupKeyword = ref(''), groupPage = ref(1), groupPageSize = ref(10)

const loadGroups = async () => {
  groupLoading.value = true
  try {
    const res = await adminAxios.get('/api/admin/groups', {
      params: { keyword: groupKeyword.value, page: groupPage.value, pageSize: groupPageSize.value }
    })
    if (res.data.code === 0) {
      groups.value = res.data.data.list; groupTotal.value = res.data.data.total
    }
  } catch (e) { /* */ } finally { groupLoading.value = false }
}

const members = ref([]), memberVisible = ref(false)
const showGroupMembers = async (row) => {
  try {
    const res = await adminAxios.get(`/api/admin/groups/${row.id}/members`)
    if (res.data.code === 0) { members.value = res.data.data; memberVisible.value = true }
  } catch (e) { /* */ }
}

const dissolveGroup = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要解散群 "${row.name}" 吗？此操作不可撤销。`, '确认解散', { type: 'warning' })
    const res = await adminAxios.put(`/api/admin/groups/${row.id}/dissolve`)
    if (res.data.code === 0) { row.status = 1; ElMessage.success('群已解散') }
  } catch (e) { /* */ }
}

// ==================== 敏感词 ====================
const sensitiveWords = ref([]), wordLoading = ref(false), newWord = ref('')

const loadWords = async () => {
  wordLoading.value = true
  try {
    const res = await adminAxios.get('/api/admin/sensitive-words')
    if (res.data.code === 0) sensitiveWords.value = res.data.data
  } catch (e) { /* */ } finally { wordLoading.value = false }
}

const addWord = async () => {
  if (!newWord.value.trim()) return
  try {
    const res = await adminAxios.post('/api/admin/sensitive-words', { word: newWord.value.trim() })
    if (res.data.code === 0) { newWord.value = ''; loadWords(); ElMessage.success('已添加') }
    else ElMessage.error(res.data.message)
  } catch (e) { ElMessage.error('添加失败') }
}

const deleteWord = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除敏感词 "${row.word}" 吗？`, '确认删除', { type: 'warning' })
    const res = await adminAxios.delete(`/api/admin/sensitive-words/${row.id}`)
    if (res.data.code === 0) { loadWords(); ElMessage.success('已删除') }
  } catch (e) { /* */ }
}

// ==================== 初始化 ====================
onMounted(() => {
  const info = localStorage.getItem('admin_info')
  if (!info) { router.push('/admin/login'); return }
  adminInfo.value = JSON.parse(info)
  loadDashboard()
  loadUsers()
  loadGroups()
  loadWords()
})
</script>

<style scoped>
.admin-layout { display:flex; flex-direction:column; height:100vh; background:#f0f2f5; }

.admin-header {
  display:flex; justify-content:space-between; align-items:center;
  height:50px; padding:0 24px; background:#fff;
  border-bottom:1px solid #e4e7ed; flex-shrink:0;
}
.admin-title { font-size:16px; font-weight:600; color:#303133; }
.admin-header-right { display:flex; align-items:center; gap:12px; }
.admin-user { font-size:13px; color:#606266; }

.admin-body { flex:1; overflow:hidden; padding:20px 24px; }
.admin-tabs { height:100%; }
.admin-tabs :deep(.el-tabs__content) { overflow-y:auto; padding:16px 0; }

/* ===== 仪表盘 ===== */
.stat-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:16px; }
.stat-card {
  display:flex; align-items:center; gap:16px; padding:20px;
  background:#fff; border-radius:8px; box-shadow:0 1px 4px rgba(0,0,0,0.06);
}
.stat-icon {
  width:56px; height:56px; border-radius:12px;
  display:flex; align-items:center; justify-content:center; flex-shrink:0;
}
.stat-value { font-size:24px; font-weight:700; color:#303133; }
.stat-label { font-size:13px; color:#909399; margin-top:2px; }

/* ===== 通用 ===== */
.toolbar { margin-bottom:12px; display:flex; align-items:center; }
.pagination-wrap { margin-top:16px; display:flex; justify-content:center; }
.detail-card { display:flex; flex-direction:column; align-items:center; }
</style>
