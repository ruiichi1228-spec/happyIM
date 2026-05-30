<template>
  <div class="admin-layout">
    <div class="admin-sidebar">
      <div class="admin-logo">HappyIM</div>
      <div class="admin-nav">
        <div v-for="m in menu" :key="m.key" class="nav-item" :class="{ active: activeTab === m.key }" @click="activeTab = m.key">
          <el-icon :size="18"><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </div>
      </div>
      <div class="admin-sidebar-footer">
        <span>{{ adminInfo?.nickname }}</span>
        <el-button size="small" text @click="handleLogout">退出</el-button>
      </div>
    </div>

    <div class="admin-main">
      <!-- 仪表盘 -->
      <template v-if="activeTab === 'dashboard'">
        <h2 class="page-title">仪表盘</h2>
        <div class="stat-grid">
          <div v-for="c in statCards" :key="c.label" class="stat-card" :style="{ borderLeftColor: c.color }">
            <div class="stat-card-body">
              <span class="stat-num">{{ c.value }}</span>
              <span class="stat-label">{{ c.label }}</span>
            </div>
            <el-icon :size="32" :color="c.color"><component :is="c.icon" /></el-icon>
          </div>
        </div>
        <div class="quick-actions">
          <el-button type="primary" @click="activeTab='users'">用户管理</el-button>
          <el-button type="success" @click="activeTab='groups'">群聊管理</el-button>
          <el-button type="warning" @click="activeTab='announcements'">发布公告</el-button>
          <el-button type="danger" @click="activeTab='words'">敏感词</el-button>
        </div>
      </template>

      <!-- 用户管理 -->
      <template v-if="activeTab === 'users'">
        <h2 class="page-title">用户管理</h2>
        <div class="table-toolbar">
          <el-input v-model="userKeyword" placeholder="搜索用户名/昵称/邮箱" clearable style="width:300px" @input="loadUsers" prefix-icon="Search" />
        </div>
        <el-table :data="users" v-loading="userLoading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="头像" width="60">
            <template #default="{ row }"><el-avatar :src="row.avatarUrl" :size="32" shape="square">{{ row.nickname?.charAt(0) }}</el-avatar></template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }"><el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'正常':'禁用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="showUserDetail(row)">详情</el-button>
              <el-button size="small" :type="row.status===1?'danger':'success'" @click="toggleUserStatus(row)">{{ row.status===1?'禁用':'启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap"><el-pagination background layout="prev,pager,next" :total="userTotal" :page-size="userPageSize" v-model:current-page="userPage" @current-change="loadUsers" /></div>
      </template>

      <!-- 群聊管理 -->
      <template v-if="activeTab === 'groups'">
        <h2 class="page-title">群聊管理</h2>
        <div class="table-toolbar">
          <el-input v-model="groupKeyword" placeholder="搜索群名称" clearable style="width:300px" @input="loadGroups" prefix-icon="Search" />
        </div>
        <el-table :data="groups" v-loading="groupLoading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="群名称" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }"><el-tag :type="row.status===0?'success':'info'" size="small">{{ row.status===0?'正常':'已解散' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="memberCount" label="成员" width="60" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="showGroupMembers(row)">成员</el-button>
              <el-button v-if="row.status===0" size="small" type="danger" @click="dissolveGroup(row)">解散</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap"><el-pagination background layout="prev,pager,next" :total="groupTotal" :page-size="groupPageSize" v-model:current-page="groupPage" @current-change="loadGroups" /></div>
      </template>

      <!-- 敏感词 -->
      <template v-if="activeTab === 'words'">
        <h2 class="page-title">敏感词管理</h2>
        <div class="table-toolbar">
          <el-input v-model="newWord" placeholder="输入新敏感词" style="width:280px" @keyup.enter="addWord" />
          <el-button type="primary" @click="addWord" style="margin-left:8px">添加</el-button>
        </div>
        <el-table :data="sensitiveWords" v-loading="wordLoading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="word" label="词语" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }"><el-button size="small" type="danger" @click="deleteWord(row)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 文件管理 -->
      <template v-if="activeTab === 'files'">
        <h2 class="page-title">文件管理</h2>
        <div class="table-toolbar">
          <el-input v-model="fileKeyword" placeholder="搜索文件名" clearable style="width:220px" @input="loadFiles" />
          <el-select v-model="fileType" placeholder="类型" clearable style="width:100px;margin-left:8px" @change="loadFiles">
            <el-option label="全部" value="all" /><el-option label="图片" value="image" /><el-option label="视频" value="video" /><el-option label="文件" value="file" />
          </el-select>
        </div>
        <el-table :data="files" v-loading="fileLoading" stripe>
          <el-table-column label="文件名" prop="fileName" />
          <el-table-column label="类型" width="80"><template #default="{ row }"><el-tag size="small">{{ row.fileType||row.messageType }}</el-tag></template></el-table-column>
          <el-table-column label="大小" width="100"><template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="{ row }"><el-button size="small" type="danger" @click="deleteFile(row)">删除</el-button></template></el-table-column>
        </el-table>
        <div class="pagination-wrap"><el-pagination background layout="prev,pager,next" :total="fileTotal" :page-size="filePageSize" v-model:current-page="filePage" @current-change="loadFiles" /></div>
      </template>

      <!-- 公告 -->
      <template v-if="activeTab === 'announcements'">
        <h2 class="page-title">系统公告</h2>
        <div><el-input v-model="annContent" type="textarea" :rows="3" placeholder="输入公告内容..." style="margin-bottom:8px" /><el-button type="primary" @click="publishAnn">发布公告</el-button></div>
        <el-table :data="announcements" v-loading="annLoading" stripe style="margin-top:16px">
          <el-table-column prop="content" label="内容" /><el-table-column prop="createdTime" label="时间" width="170" />
          <el-table-column label="操作" width="80"><template #default="{ row }"><el-button size="small" type="danger" @click="deleteAnn(row)">删除</el-button></template></el-table-column>
        </el-table>
      </template>
    </div>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="userDetailVisible" title="用户详情" width="480px" destroy-on-close>
      <div v-if="detailUser" class="detail-card">
        <el-avatar :src="detailUser.avatarUrl" :size="64" shape="square">{{ detailUser.nickname?.charAt(0) }}</el-avatar>
        <el-descriptions :column="2" border style="margin-top:16px">
          <el-descriptions-item label="ID">{{ detailUser.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detailUser.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detailUser.nickname }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detailUser.email }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detailUser.status===1?'success':'danger'" size="small">{{ detailUser.status===1?'正常':'禁用' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ detailUser.createdTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 群成员弹窗 -->
    <el-dialog v-model="memberVisible" title="群成员" width="500px">
      <el-table :data="members" stripe max-height="400">
        <el-table-column label="头像" width="60"><template #default="{ row }"><el-avatar :src="row.avatarUrl" :size="32" shape="square">{{ row.nickname?.charAt(0) }}</el-avatar></template></el-table-column>
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column label="角色" width="80"><template #default="{ row }"><el-tag size="small" :type="row.role===1?'danger':row.role===2?'warning':'info'">{{ row.role===1?'群主':row.role===2?'管理':'成员' }}</el-tag></template></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, User, ChatDotRound, Connection, ChatLineRound, Folder, Search, DataBoard, Notebook } from '@element-plus/icons-vue'

const router = useRouter()
const activeTab = ref('dashboard')
const adminInfo = ref(null)

const menu = [
  { key:'dashboard', label:'仪表盘', icon:'DataBoard' },
  { key:'users', label:'用户管理', icon:'User' },
  { key:'groups', label:'群聊管理', icon:'ChatDotRound' },
  { key:'words', label:'敏感词', icon:'Notebook' },
  { key:'files', label:'文件管理', icon:'Folder' },
  { key:'announcements', label:'系统公告', icon:'ChatLineRound' },
]

const adminAxios = axios.create({ baseURL: '' })
adminAxios.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
adminAxios.interceptors.response.use(res => res, err => {
  if (err.response?.status === 401 || err.response?.status === 403) {
    localStorage.removeItem('admin_token'); localStorage.removeItem('admin_info'); router.push('/admin/login')
  }
  return Promise.reject(err)
})

const handleLogout = () => { localStorage.removeItem('admin_token'); localStorage.removeItem('admin_info'); router.push('/admin/login') }

const stats = ref({})
const statCards = computed(() => [
  { label:'总用户数', value:stats.value.totalUsers??'-', icon:'UserFilled', color:'#409eff' },
  { label:'今日新增', value:stats.value.todayNewUsers??'-', icon:'User', color:'#67c23a' },
  { label:'总群组数', value:stats.value.totalGroups??'-', icon:'ChatDotRound', color:'#e6a23c' },
  { label:'在线用户', value:stats.value.onlineUsers??'-', icon:'Connection', color:'#409eff' },
  { label:'今日消息', value:stats.value.todayMessages??'-', icon:'ChatLineRound', color:'#9b59b6' },
  { label:'文件总数', value:stats.value.totalFiles??'-', icon:'Folder', color:'#67c23a' },
])

const loadDashboard = async () => { try { const res = await adminAxios.get('/api/admin/dashboard'); if (res.data.code===0) stats.value=res.data.data } catch(e){} }

// ---- 用户管理 ----
const users=ref([]), userLoading=ref(false), userTotal=ref(0), userKeyword=ref(''), userPage=ref(1), userPageSize=ref(10)
const loadUsers = async () => { userLoading.value=true; try { const res=await adminAxios.get('/api/admin/users',{params:{keyword:userKeyword.value,page:userPage.value,pageSize:userPageSize.value}}); if (res.data.code===0){ users.value=res.data.data.list; userTotal.value=res.data.data.total } } catch(e){} finally { userLoading.value=false } }
const toggleUserStatus = async row => { const s=row.status===1?0:1; try { await ElMessageBox.confirm(`确定${s?'启用':'禁用'}"${row.nickname}"?`,'确认',{type:'warning'}); const res=await adminAxios.put(`/api/admin/users/${row.id}/status`,{status:s}); if (res.data.code===0){ row.status=s; ElMessage.success('已更新') } } catch(e){} }
const detailUser=ref(null), userDetailVisible=ref(false)
const showUserDetail = async row => { try { const res=await adminAxios.get(`/api/admin/users/${row.id}`); if (res.data.code===0){ detailUser.value=res.data.data; userDetailVisible.value=true } } catch(e){} }

// ---- 群聊管理 ----
const groups=ref([]), groupLoading=ref(false), groupTotal=ref(0), groupKeyword=ref(''), groupPage=ref(1), groupPageSize=ref(10)
const loadGroups = async () => { groupLoading.value=true; try { const res=await adminAxios.get('/api/admin/groups',{params:{keyword:groupKeyword.value,page:groupPage.value,pageSize:groupPageSize.value}}); if (res.data.code===0){ groups.value=res.data.data.list; groupTotal.value=res.data.data.total } } catch(e){} finally { groupLoading.value=false } }
const members=ref([]), memberVisible=ref(false)
const showGroupMembers = async row => { try { const res=await adminAxios.get(`/api/admin/groups/${row.id}/members`); if (res.data.code===0){ members.value=res.data.data; memberVisible.value=true } } catch(e){} }
const dissolveGroup = async row => { try { await ElMessageBox.confirm(`确定解散"${row.name}"?`,'确认',{type:'warning'}); const res=await adminAxios.put(`/api/admin/groups/${row.id}/dissolve`); if (res.data.code===0){ row.status=1; ElMessage.success('已解散') } } catch(e){} }

// ---- 敏感词 ----
const sensitiveWords=ref([]), wordLoading=ref(false), newWord=ref('')
const loadWords = async () => { wordLoading.value=true; try { const res=await adminAxios.get('/api/admin/sensitive-words'); if (res.data.code===0) sensitiveWords.value=res.data.data } catch(e){} finally { wordLoading.value=false } }
const addWord = async () => { if (!newWord.value.trim()) return; try { const res=await adminAxios.post('/api/admin/sensitive-words',{word:newWord.value.trim()}); if (res.data.code===0){ newWord.value=''; loadWords(); ElMessage.success('已添加') } else ElMessage.error(res.data.message) } catch(e){} }
const deleteWord = async row => { try { await ElMessageBox.confirm(`删除"${row.word}"?`,'确认',{type:'warning'}); await adminAxios.delete(`/api/admin/sensitive-words/${row.id}`); loadWords(); ElMessage.success('已删除') } catch(e){} }

// ---- 文件管理 ----
const files=ref([]), fileLoading=ref(false), fileTotal=ref(0), fileKeyword=ref(''), fileType=ref('all'), filePage=ref(1), filePageSize=ref(10)
const loadFiles = async () => { fileLoading.value=true; try { const res=await adminAxios.get('/api/admin/files',{params:{keyword:fileKeyword.value,fileType:fileType.value,page:filePage.value,pageSize:filePageSize.value}}); if (res.data.code===0){ files.value=res.data.data.list; fileTotal.value=res.data.data.total } } catch(e){} finally { fileLoading.value=false } }
const formatFileSize = b => { if (!b) return '-'; if (b<1024) return b+' B'; if (b<1048576) return (b/1024).toFixed(1)+' KB'; if (b<1073741824) return (b/1048576).toFixed(1)+' MB'; return (b/1073741824).toFixed(2)+' GB' }
const deleteFile = async row => { try { await ElMessageBox.confirm(`删除"${row.fileName}"?`,'确认',{type:'warning'}); await adminAxios.delete(`/api/admin/files/${row.messageId}`); loadFiles(); ElMessage.success('已删除') } catch(e){} }

// ---- 公告 ----
const announcements=ref([]), annLoading=ref(false), annContent=ref('')
const loadAnnouncements = async () => { annLoading.value=true; try { const res=await adminAxios.get('/api/admin/announcements'); if (res.data.code===0) announcements.value=res.data.data } catch(e){} finally { annLoading.value=false } }
const publishAnn = async () => { if (!annContent.value.trim()) return; try { await adminAxios.post('/api/admin/announcements',{content:annContent.value.trim()}); annContent.value=''; loadAnnouncements(); ElMessage.success('公告已发送') } catch(e){} }
const deleteAnn = async row => { try { await ElMessageBox.confirm('删除该公告?','确认',{type:'warning'}); await adminAxios.delete(`/api/admin/announcements/${row.id}`); loadAnnouncements(); ElMessage.success('已删除') } catch(e){} }

onMounted(() => {
  const info = localStorage.getItem('admin_info')
  if (!info) { router.push('/admin/login'); return }
  adminInfo.value = JSON.parse(info)
  loadDashboard(); loadUsers(); loadGroups(); loadWords(); loadFiles(); loadAnnouncements()
})
</script>

<style scoped>
.admin-layout { display:flex; height:100vh; background:#f5f7fa; }
.admin-sidebar { width:200px; background:#fff; border-right:1px solid #e8e8e8; display:flex; flex-direction:column; }
.admin-logo { padding:20px; font-size:20px; font-weight:700; color:#07c160; letter-spacing:1px; }
.admin-nav { flex:1; padding:8px; }
.nav-item { display:flex; align-items:center; gap:10px; padding:10px 12px; border-radius:8px; cursor:pointer; font-size:14px; color:#555; transition:all .2s; }
.nav-item:hover { background:#f0f0f0; }
.nav-item.active { background:#07c16015; color:#07c160; font-weight:500; }
.admin-sidebar-footer { padding:16px; border-top:1px solid #f0f0f0; font-size:13px; color:#999; display:flex; justify-content:space-between; align-items:center; }
.admin-main { flex:1; overflow-y:auto; padding:24px 32px; }
.page-title { font-size:20px; font-weight:600; color:#303133; margin-bottom:20px; }

.stat-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:16px; margin-bottom:24px; }
.stat-card { background:#fff; border-radius:10px; padding:20px; display:flex; justify-content:space-between; align-items:center; border-left:4px solid #409eff; box-shadow:0 1px 4px rgba(0,0,0,0.04); }
.stat-num { font-size:28px; font-weight:700; color:#303133; display:block; }
.stat-label { font-size:13px; color:#909399; margin-top:4px; display:block; }

.quick-actions { display:flex; gap:8px; }

.table-toolbar { margin-bottom:12px; }
.pagination-wrap { margin-top:16px; display:flex; justify-content:center; }
.detail-card { display:flex; flex-direction:column; align-items:center; }
</style>
