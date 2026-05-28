<template>
  <div class="files-layout">
    <!-- 左侧筛选栏 -->
    <div class="files-sidebar">
      <div class="sidebar-search">
        <el-input v-model="search" placeholder="搜索文件" size="small" clearable @input="onSearch" />
      </div>

      <div class="sidebar-tab" :class="{ active: activeFilter === 'all' }" @click="setFilter('all')">
        <el-icon><Folder /></el-icon> 全部文件
      </div>
      <div class="sidebar-tab" :class="{ active: activeFilter === 'recent' }" @click="setFilter('recent')">
        <el-icon><Timer /></el-icon> 最近使用
      </div>

      <div class="sidebar-divider" />

      <div class="sidebar-section-title">筛选</div>

      <div class="sidebar-tab" :class="{ active: activeFilter === 'sender' }" @click="setFilter('sender')">
        <el-icon><User /></el-icon> 发送者
        <span v-if="selectedSenderId" class="filter-dot"></span>
      </div>
      <div v-if="activeFilter === 'sender'" class="sub-list">
        <div v-for="s in senders" :key="s.senderId" class="sub-item"
             :class="{ active: selectedSenderId === s.senderId }"
             @click="selectSender(s)">{{ s.senderName }}</div>
        <div v-if="!senders.length" class="sub-empty">暂无数据</div>
      </div>

      <div class="sidebar-tab" :class="{ active: activeFilter === 'conversation' }" @click="setFilter('conversation')">
        <el-icon><ChatDotRound /></el-icon> 会话
        <span v-if="selectedConversationId" class="filter-dot"></span>
      </div>
      <div v-if="activeFilter === 'conversation'" class="sub-list">
        <div v-for="c in conversations" :key="c.conversationId" class="sub-item"
             :class="{ active: selectedConversationId === c.conversationId }"
             @click="selectConversation(c)">{{ c.conversationName }}</div>
        <div v-if="!conversations.length" class="sub-empty">暂无数据</div>
      </div>

      <div class="sidebar-tab" :class="{ active: activeFilter === 'type' }" @click="setFilter('type')">
        <el-icon><Document /></el-icon> 类型
        <span v-if="selectedFileType" class="filter-dot"></span>
      </div>
      <div v-if="activeFilter === 'type'" class="sub-list">
        <div v-for="t in fileTypes" :key="t" class="sub-item"
             :class="{ active: selectedFileType === t }"
             @click="selectFileType(t)">{{ typeLabel(t) }}</div>
      </div>

      <div class="sidebar-footer">
        <div class="file-stats">{{ totalFiles }} 个文件</div>
      </div>
    </div>

    <!-- 右侧文件列表 -->
    <div class="files-content" ref="contentRef" @scroll="onScroll">
      <div class="content-header">
        <div class="content-title">
          {{ activeFilter === 'all' ? '全部文件' : activeFilter === 'recent' ? '最近使用' : '筛选结果' }}
        </div>
        <div class="content-count">{{ files.length }} 项</div>
      </div>

      <div class="files-grid">
        <div v-for="f in files" :key="f.id" class="file-card" @click="downloadFile(f)">
          <div class="file-icon" :style="{ background: fileIconColor(f.fileType) }">
            {{ fileIconLabel(f.fileType) }}
          </div>
          <div class="file-info">
            <div class="file-name" :title="f.fileName">{{ f.fileName }}</div>
            <div class="file-meta">
              <span class="file-size">{{ formatSize(f.fileSize) }}</span>
              <span class="file-sep">·</span>
              <span>{{ f.senderName }}</span>
              <span class="file-sep" v-if="f.conversationName">·</span>
              <span v-if="f.conversationName">{{ f.conversationName }}</span>
            </div>
          </div>
          <div class="file-time">{{ formatTime(f.createdAt) }}</div>
          <el-icon class="file-dl" :size="16"><Download /></el-icon>
        </div>
      </div>
      <div v-if="loading" class="load-tip"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
      <div v-else-if="!hasMore && files.length" class="load-tip">— 全部加载完毕 —</div>
      <div v-if="!loading && !files.length" class="empty">
        <el-icon :size="48" color="#ddd"><Folder /></el-icon>
        <div>暂无文件</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request'
import { Folder, Timer, User, ChatDotRound, Document, Download, Loading } from '@element-plus/icons-vue'

const files = ref([]), loading = ref(false), contentRef = ref(null)
const page = ref(1), hasMore = ref(true), search = ref('')
const activeFilter = ref('all')
const selectedSenderId = ref(null), selectedConversationId = ref(null), selectedFileType = ref(null)
const senders = ref([]), conversations = ref([])

const fileTypes = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'zip', 'rar', 'mp3', 'mp4', 'jpg', 'png', 'gif', 'exe']
const typeLabel = (t) => {
  const map = { doc:'Word', docx:'Word', xls:'Excel', xlsx:'Excel', ppt:'PPT', pptx:'PPT', pdf:'PDF', txt:'文本', zip:'压缩包', rar:'压缩包', mp3:'音频', mp4:'视频', jpg:'图片', png:'图片', gif:'图片', exe:'程序' }
  return map[t] || t
}
const totalFiles = computed(() => files.value.length)

const fileIconColor = (ext) => {
  const map = { pdf:'#e74c3c', doc:'#2b7bd6', docx:'#2b7bd6', xls:'#27ae60', xlsx:'#27ae60', ppt:'#e67e22', pptx:'#e67e22', txt:'#607d8b', zip:'#f39c12', rar:'#f39c12', mp3:'#9b59b6', wav:'#9b59b6', mp4:'#3498db', mov:'#3498db', avi:'#3498db', jpg:'#e91e63', jpeg:'#e91e63', png:'#e91e63', gif:'#e91e63' }
  return map[ext] || '#999'
}
const fileIconLabel = (ext) => {
  const map = { pdf:'PDF', doc:'W', docx:'W', xls:'X', xlsx:'X', ppt:'P', pptx:'P', txt:'TXT', zip:'ZIP', rar:'RAR', mp3:'♫', wav:'♫', mp4:'▶', mov:'▶', avi:'▶', jpg:'IMG', jpeg:'IMG', png:'IMG', gif:'GIF', exe:'EXE' }
  return map[ext] || '?'
}
const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(1) + ' GB'
}
const formatTime = (ts) => ts ? new Date(ts).toLocaleDateString('zh-CN', { year:'numeric', month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' }) : ''

const setFilter = (f) => {
  if (activeFilter.value === f) { activeFilter.value = 'all'; selectedSenderId.value = null; selectedConversationId.value = null; selectedFileType.value = null }
  else activeFilter.value = f
  resetAndLoad()
}
const selectSender = (s) => { selectedSenderId.value = s.senderId; resetAndLoad() }
const selectConversation = (c) => { selectedConversationId.value = c.conversationId; resetAndLoad() }
const selectFileType = (t) => { selectedFileType.value = t; resetAndLoad() }
const resetAndLoad = () => { page.value = 1; files.value = []; hasMore.value = true; loadFiles() }

const loadFiles = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (selectedFileType.value) params.fileType = selectedFileType.value
    if (selectedSenderId.value) params.senderId = selectedSenderId.value
    if (selectedConversationId.value) params.conversationId = selectedConversationId.value
    const res = await request.get('/file-feed/feed', { params })
    if (res.code === 0) {
      const data = res.data
      files.value.push(...(data.items || []))
      hasMore.value = data.items && data.items.length >= 20
      page.value++
    }
  } catch(e) {} finally { loading.value = false }
}

const loadSenders = async () => { try { const res = await request.get('/file-feed/feed/senders'); if (res.code===0) senders.value = res.data } catch(e) {} }
const loadConversations = async () => { try { const res = await request.get('/file-feed/feed/conversations'); if (res.code===0) conversations.value = res.data } catch(e) {} }

const downloadFile = (f) => {
  let dl = f.fileUrl
  if (!dl.startsWith('http')) {
    if (dl.includes('/')) dl = '/api/files/download/' + dl.substring(dl.indexOf('/') + 1)
    else dl = '/api/files/download/' + dl
  }
  const a = document.createElement('a'); a.href = dl; a.download = f.fileName; a.click()
}

const onScroll = () => {
  const el = contentRef.value
  if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 20 && hasMore.value && !loading.value) loadFiles()
}
const onSearch = () => { resetAndLoad() }

onMounted(() => { loadFiles(); loadSenders(); loadConversations() })
</script>

<style scoped>
.files-layout { flex:1; display:flex; overflow:hidden; }

/* 左侧 */
.files-sidebar { width:220px; min-width:220px; background:#f5f5f5; border-right:1px solid #e2e2e2; display:flex; flex-direction:column; padding:12px 0; }
.sidebar-search { padding:0 12px; margin-bottom:6px; }
.sidebar-tab { display:flex; align-items:center; gap:8px; padding:9px 16px; margin:1px 8px; border-radius:6px; font-size:13px; color:#444; cursor:pointer; transition:all 0.15s; }
.sidebar-tab:hover { background:rgba(0,0,0,0.04); }
.sidebar-tab.active { background:rgba(7,193,96,0.08); color:#07c160; font-weight:500; }
.sidebar-divider { height:1px; background:rgba(0,0,0,0.06); margin:8px 12px; }
.sidebar-section-title { font-size:11px; color:#aaa; padding:4px 16px; text-transform:uppercase; letter-spacing:1px; }
.sub-list { padding:0 0 4px; }
.sub-item { padding:7px 16px 7px 40px; margin:0 8px; border-radius:4px; font-size:12px; color:#666; cursor:pointer; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; transition:all 0.15s; }
.sub-item:hover { background:rgba(0,0,0,0.03); }
.sub-item.active { color:#07c160; background:rgba(7,193,96,0.06); font-weight:500; }
.sub-empty { padding:8px 16px 8px 40px; font-size:12px; color:#ccc; }
.filter-dot { width:6px; height:6px; border-radius:50%; background:#07c160; margin-left:auto; }
.sidebar-footer { margin-top:auto; padding:12px 16px; border-top:1px solid rgba(0,0,0,0.05); }
.file-stats { font-size:11px; color:#aaa; }

/* 右侧 */
.files-content { flex:1; overflow-y:auto; background:#fafafa; }
.content-header { display:flex; align-items:baseline; justify-content:space-between; padding:20px 24px 12px; }
.content-title { font-size:18px; font-weight:600; color:#333; }
.content-count { font-size:12px; color:#999; }
.files-grid { display:flex; flex-direction:column; gap:4px; padding:0 16px 16px; }
.file-card { display:flex; align-items:center; gap:14px; padding:12px 16px; background:#fff; border-radius:10px; cursor:pointer; transition:all 0.15s; }
.file-card:hover { background:#fafafa; box-shadow:0 2px 12px rgba(0,0,0,0.06); }
.file-icon { width:44px; height:44px; display:flex; align-items:center; justify-content:center; flex-shrink:0; border-radius:10px; color:#fff; font-size:11px; font-weight:700; }
.file-info { flex:1; min-width:0; }
.file-name { font-size:14px; font-weight:500; color:#333; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file-meta { font-size:12px; color:#999; margin-top:2px; display:flex; align-items:center; gap:4px; }
.file-sep { color:#ddd; }
.file-size { color:#666; font-weight:500; }
.file-time { font-size:12px; color:#bbb; flex-shrink:0; white-space:nowrap; }
.file-dl { color:#ccc; flex-shrink:0; opacity:0; transition:opacity 0.15s; }
.file-card:hover .file-dl { opacity:1; color:#07c160; }
.load-tip { text-align:center; padding:32px; color:#999; font-size:13px; display:flex; align-items:center; justify-content:center; gap:6px; }
.empty { display:flex; flex-direction:column; align-items:center; gap:8px; padding:80px 0; color:#ccc; font-size:14px; }
</style>
