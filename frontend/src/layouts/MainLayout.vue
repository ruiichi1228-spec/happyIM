<template>
  <!-- 新公告浮动横幅 -->
  <transition name="slide-down">
    <div v-if="announcePopup" class="announce-float">
      <span class="announce-float-icon">📢</span>
      <span class="announce-float-text">{{ announcePopup }}</span>
      <el-icon class="announce-float-close" @click="announcePopup = ''"><Close /></el-icon>
    </div>
  </transition>
  <div class="app-wrapper">
    <div class="layout">
    <!-- 左侧导航栏 60px -->
    <div class="nav-bar">
      <div class="nav-top">
        <el-tooltip content="个人资料" placement="right" :show-after="400">
          <el-avatar class="nav-avatar" shape="square" :size="36" :src="userInfo?.avatarUrl" @click="profileVisible = true">
            {{ userInfo?.nickname?.charAt(0) || '我' }}
          </el-avatar>
        </el-tooltip>

        <el-tooltip content="聊天" placement="right" :show-after="400">
          <div class="nav-item" :class="{ active: activeTab === 'chat' }" @click="go('/chat')">
            <el-badge :value="totalUnread" :hidden="!totalUnread" :max="99">
              <el-icon :color="activeTab === 'chat' ? '#07c160' : '#bbb'" :size="22"><ChatLineRound /></el-icon>
            </el-badge>
          </div>
        </el-tooltip>

        <el-tooltip content="联系人" placement="right" :show-after="400">
          <div class="nav-item" :class="{ active: activeTab === 'contacts' }" @click="go('/contacts')">
            <el-badge :value="pendingCount" :hidden="!pendingCount" :max="99">
              <el-icon :color="activeTab === 'contacts' ? '#07c160' : '#bbb'" :size="22">
                <svg viewBox="0 0 1024 1024" width="22" height="22">
                  <path d="M108.1 928.8c-28.8 0-52.3-6.9-71.6-21.9-21.4-16.6-33.1-39-34.2-65.7v-3.7c-0.5-15-1.1-31.5 4.3-48.6C17.3 753.6 41.3 728 77.1 712c51.8-23 103.6-45.4 155.4-67.8l5.3-2.1c6.4-2.7 13.4-5.3 20.3-8s14.4-5.3 21.4-8.5c7.5-3.2 9.6-4.8 9.6-5.3 0 0 1.1-3.2 0.5-15.5 0-5.3-2.1-11.2-6.4-17.6l-8.5-13.9c-14.4-23.5-29.9-47.5-43.8-72.1-15-26.2-25.6-53.9-32.6-83.9-5.3-23.5-6.9-48.6-3.7-77.4 3.2-33.1 11.8-65.2 24.6-94.5 27.8-62.5 67.3-104.7 120.7-129.8 68.4-31.5 137.8-26.2 202.4 16 34.7 23 62.5 53.9 83.9 95.6 24.6 47.5 36.3 98.3 34.7 151.7-1.1 35.8-13.9 67.8-24.6 91.3-13.4 28.8-29.9 55-45.9 79.1-2.7 4.3-5.9 8.5-8.5 12.8-6.4 9.6-12.8 18.7-17.6 28.3-5.9 10.7-8 21.4-6.4 31.5 10.1 8.5 23.5 13.9 36.3 18.7l67.3 25.6c37.4 14.4 74.8 28.3 112.2 43.3 41.1 16 67.8 46.5 77.4 87.1 4.8 20.3 4.8 41.7 0 61.4-5.9 25.1-23.5 57.2-74.2 68.4-9.1 2.1-18.7 2.7-30.4 2.7-0.1-0.3-638.4-0.3-638.4-0.3z m319.4-777.1c-20.8 0-42.2 5.3-63.6 15.5-40.1 18.7-70.5 52.3-92.4 102.6-10.7 24-17.1 50.2-19.8 78-2.1 23.5-1.6 42.7 2.7 60.4 5.9 24.6 15 48.1 26.7 70 12.8 23 27.2 46.5 41.1 68.9l8.5 13.9c8.5 13.9 12.8 27.2 13.4 41.1 1.1 35.8-8 52.3-37.9 66.2-7.5 3.7-15.5 6.4-23.5 9.6-5.9 2.1-11.8 4.3-17.1 6.9l-5.3 2.1c-49.7 21.9-99.3 43.8-149 66.2-21.4 9.6-34.7 24.6-41.1 44.9-2.7 9.1-2.1 20.3-1.6 32v3.7c0.5 11.8 4.8 20.8 14.4 27.8 9.6 7.5 21.9 11.2 38.5 11.2v24.6-24.6h612.1c8 0 13.9-0.5 18.7-1.6 25.6-5.9 32-19.2 34.2-30.4 2.7-12.9 2.7-26.1 0-39-5.3-24.6-19.8-41.1-44.9-50.7-35.8-14.4-71.6-28.3-106.8-42.2-21.9-8.5-43.3-16.6-65.2-25.1-15.5-6.4-33.6-13.9-49.7-27.2-8-6.4-13.4-16.6-15.5-26.2-4.3-21.4-0.5-43.3 10.7-64.1 5.9-11.2 12.8-21.9 19.8-32 2.7-3.7 5.3-8 8-11.8 14.4-22.4 29.4-46.5 40.6-71.6 8.5-19.2 18.7-44.3 19.2-71 1.1-43.8-8-86-27.8-125.5-17.1-33.1-37.9-57.7-64.1-75.3-27.8-18.2-55.5-27.3-83.3-27.3z" :fill="activeTab === 'contacts' ? '#07c160' : '#bbb'"/>
                </svg>
              </el-icon>
            </el-badge>
          </div>
        </el-tooltip>

        <el-tooltip content="文件管理" placement="right" :show-after="400">
          <div class="nav-item" :class="{ active: activeTab === 'files' }" @click="go('/files')">
            <el-icon :color="activeTab === 'files' ? '#07c160' : '#bbb'" :size="22"><Folder /></el-icon>
          </div>
        </el-tooltip>

        <el-tooltip content="朋友圈" placement="right" :show-after="400">
          <div class="nav-item" @click="showMoments = true">
            <el-badge :value="momentNoticeCount" :hidden="!momentNoticeCount" :max="99">
              <el-icon :color="activeTab === 'moments' ? '#07c160' : '#bbb'" :size="22"><Orange /></el-icon>
            </el-badge>
          </div>
        </el-tooltip>

        <el-tooltip content="广场" placement="right" :show-after="400">
          <div class="nav-item" @click="showSquare = true">
            <el-badge :value="squareNoticeCount" :hidden="!squareNoticeCount" :max="99">
              <el-icon color="#bbb" :size="22"><el-icon><Ship /></el-icon></el-icon>
            </el-badge>
          </div>
        </el-tooltip>
      </div>

      <div class="nav-bottom">
        <!-- 公告铃铛 -->
        <el-popover placement="right" :width="320" trigger="click" @show="loadAnnouncements">
          <template #reference>
            <div class="nav-item" style="position:relative">
              <el-badge :value="announceCount" :hidden="!announceCount" :max="99">
                <el-icon color="#bbb" :size="20"><Bell /></el-icon>
              </el-badge>
            </div>
          </template>
          <div class="ann-pop-title">系统公告</div>
          <div class="ann-pop-list" v-if="announceList.length">
            <div v-for="a in announceList" :key="a.id" class="ann-pop-item">
              <div class="ann-pop-content">{{ a.content }}</div>
              <div class="ann-pop-time">{{ formatAnnTime(a.createdTime) }}</div>
            </div>
          </div>
          <div v-else class="ann-pop-empty">暂无公告</div>
        </el-popover>
        <el-tooltip :content="soundMuted ? '已静音' : '消息提示音'" placement="right" :show-after="400">
          <div class="nav-item" @click="toggleMuteSound">
            <el-icon :color="soundMuted ? '#fa5151' : '#bbb'" :size="20"><MuteNotification /></el-icon>
          </div>
        </el-tooltip>
        <el-tooltip content="设置" placement="right" :show-after="400">
          <div class="nav-item" @click="settingsVisible = true">
            <el-icon color="#bbb" :size="20"><Setting /></el-icon>
          </div>
        </el-tooltip>
        <el-tooltip :content="wsConnected ? 'WebSocket 已连接' : 'WebSocket 未连接'" placement="right" :show-after="400">
          <div class="ws-status">
            <span class="ws-dot" :class="wsConnected ? 'online' : 'offline'"></span>
          </div>
        </el-tooltip>
      </div>
    </div>

    <!-- 右侧内容 -->
    <div class="content">
      <RouterView />
    </div>

    <!-- 个人资料弹窗 -->
    <el-dialog v-model="profileVisible" title="个人资料" width="520px" align-center :close-on-click-modal="true" destroy-on-close>
      <div class="settings-dialog-body">
        <div class="avatar-section">
          <el-upload class="avatar-uploader" :show-file-list="false" :http-request="handleAvatarUpload" :before-upload="beforeAvatarUpload">
            <el-avatar shape="square" :size="64" :src="settingsForm.avatarUrl">{{ settingsForm.nickname?.charAt(0) || '我' }}</el-avatar>
            <div class="avatar-mask">更换头像</div>
          </el-upload>
        </div>
        <div class="info-grid">
          <div class="info-row"><span class="info-label">用户ID</span><span class="info-value">{{ settingsForm.id }}</span></div>
          <div class="info-row"><span class="info-label">用户名</span><span class="info-value">{{ settingsForm.username }}</span></div>
          <div class="info-row"><span class="info-label">邮箱</span><span class="info-value">{{ settingsForm.email }}</span></div>
          <div class="info-row"><span class="info-label">昵称</span><el-input v-model="settingsForm.nickname" size="small" style="width:200px" placeholder="设置昵称" maxlength="32" /></div>
          <div class="info-row"><span class="info-label">性别</span>
            <el-radio-group v-model="settingsForm.gender" size="small">
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </div>
          <div class="info-row"><span class="info-label">签名</span><el-input v-model="settingsForm.signature" type="textarea" :rows="2" style="width:340px;min-height: 80px" placeholder="个人签名" maxlength="60" show-word-limit /></div>
          <div class="info-row"><span class="info-label">说明</span><el-input v-model="settingsForm.description" type="textarea" :rows="3" style="width:340px;min-height: 100px" placeholder="其它说明" maxlength="250" show-word-limit /></div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <div class="footer-left">
            <el-button link type="primary" @click="goResetPassword">修改密码</el-button>
            <el-button link type="danger" @click="handleLogout">退出登录</el-button>
          </div>
          <div class="footer-right">
            <el-button @click="profileVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="handleSaveSettings">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 设置弹窗 -->
    <el-dialog v-model="settingsVisible" title="设置" width="540px" align-center :close-on-click-modal="true" destroy-on-close>
      <div class="settings-dialog-body">
        <!-- 外观 -->
        <div class="settings-card">
          <div class="set-card-header">
            <el-icon :size="18" color="#07c160"><BrushFilled /></el-icon>
            <span class="set-card-title">外观设置</span>
          </div>
          <div class="set-card-body">
            <div class="set-row">
              <div class="set-row-left">
                <div class="set-row-title">深色模式</div>
                <div class="set-row-desc">切换深色与浅色主题</div>
              </div>
              <el-switch :model-value="isDark" @change="toggleDark" />
            </div>
            <div class="set-row">
              <div class="set-row-left">
                <div class="set-row-title">字体大小</div>
                <div class="set-row-desc">调整界面字体大小</div>
              </div>
              <el-radio-group v-model="fontSize" size="small">
                <el-radio-button value="small">小</el-radio-button>
                <el-radio-button value="normal">中</el-radio-button>
                <el-radio-button value="large">大</el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </div>

        <!-- 消息通知 -->
        <div class="settings-card">
          <div class="set-card-header">
            <el-icon :size="18" color="#07c160"><Bell /></el-icon>
            <span class="set-card-title">消息通知</span>
          </div>
          <div class="set-card-body">
            <div class="set-row">
              <div class="set-row-left">
                <div class="set-row-title">声音提醒</div>
                <div class="set-row-desc">收到新消息时播放提示音</div>
              </div>
              <el-switch v-model="notifySound" />
            </div>
            <div class="set-row">
              <div class="set-row-left">
                <div class="set-row-title">桌面通知</div>
                <div class="set-row-desc">收到新消息时弹窗提醒</div>
              </div>
              <el-switch v-model="notifyDesktop" />
            </div>
          </div>
        </div>

        <!-- 隐私 -->
        <div class="settings-card">
          <div class="set-card-header">
            <el-icon :size="18" color="#07c160"><Lock /></el-icon>
            <span class="set-card-title">隐私</span>
          </div>
          <div class="set-card-body">
            <div class="set-row">
              <div class="set-row-left">
                <div class="set-row-title">通过 ID 搜索到我</div>
                <div class="set-row-desc">允许他人通过账号查找到你</div>
              </div>
              <el-switch v-model="allowSearch" />
            </div>
          </div>
        </div>

        <!-- 关于 -->
        <div class="settings-card">
          <div class="set-card-header">
            <el-icon :size="18" color="#07c160"><InfoFilled /></el-icon>
            <span class="set-card-title">关于 HappyIM</span>
          </div>
          <div class="set-card-body">
            <div class="about-grid">
              <div class="about-item"><span class="about-label">产品</span><span class="about-val">HappyIM Web</span></div>
              <div class="about-item"><span class="about-label">版本</span><span class="about-val">1.0.0 <span style="color:#07c160;font-size:11px">beta</span></span></div>
              <div class="about-item"><span class="about-label">浏览器</span><span class="about-val">{{ browserInfo }}</span></div>
              <div class="about-item"><span class="about-label">Cookie</span><span class="about-val">{{ cookieSupport ? '已启用' : '已禁用' }}</span></div>
              <div class="about-item"><span class="about-label">平台</span><span class="about-val">{{ platformInfo }}</span></div>
              <div class="about-item"><span class="about-label">语言</span><span class="about-val">{{ navLang }}</span></div>
              <div class="about-item"><span class="about-label">分辨率</span><span class="about-val">{{ scrWidth }} × {{ scrHeight }} · {{ scrDepth }}bit</span></div>
              <div class="about-item"><span class="about-label">在线状态</span><span class="about-val" :style="{color: wsConnected ? '#07c160' : '#fa5151'}">{{ wsConnected ? '已连接' : '未连接' }}</span></div>
              <div class="about-item"><span class="about-label">服务器</span><span class="about-val">{{ API_BASE_URL }}</span></div>
            </div>
            <!-- <div class="about-actions">
              <el-button size="small" text>检查更新</el-button>
              <el-button size="small" text>用户协议</el-button>
              <el-button size="small" text>隐私政策</el-button>
            </div> -->
            <div class="about-copyright">Copyright 2024-2026 HappyIM. All rights reserved.</div>
          </div>
        </div>
      </div>
    </el-dialog>

  <div v-if="showMoments" class="moment-mask" @click.self="showMoments = false">
    <div class="moment-popup">
      <MomentsPage @close="showMoments = false" />
    </div>
  </div>

  <div v-if="showSquare" class="moment-mask" @click.self="showSquare = false">
    <div class="moment-popup" style="width:720px;">
      <SquarePage @close="showSquare = false" />
    </div>
  </div>
</div>
</div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, provide } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request, { clearAuth } from '@/utils/request'
import { useWebSocket } from '@/utils/websocket'
import { toggleMute, isMuted, playMsgSound, playFriendSound, playMomentSound, playSquareSound } from '@/utils/sound'
import { useTheme } from '@/utils/theme'
const { isDark, toggle: toggleDark } = useTheme()
const { connect, send, onMessage, connected: wsConnected } = useWebSocket()
const soundMuted = ref(isMuted())
const toggleMuteSound = () => { soundMuted.value = toggleMute() }
import axios from 'axios'
import { API_BASE_URL } from '@/config/index'
import { ChatLineRound, Setting, PictureFilled, Folder, BrushFilled, Bell, Lock, InfoFilled, Microphone } from '@element-plus/icons-vue'
import MomentsPage from '@/pages/MomentsPage.vue'
import SquarePage from '@/pages/SquarePage.vue'
import { ElMessage, ElMessageBox, ElUpload } from 'element-plus'

const router = useRouter()
const route = useRoute()

const activeTab = computed(() => {
  const path = route.path
  if (path.startsWith('/chat')) return 'chat'
  if (path.startsWith('/contacts')) return 'contacts'
  if (path.startsWith('/files')) return 'files'
  return ''
})

const pendingCount = ref(0)
const showMoments = ref(false)
const showSquare = ref(false)
const settingsVisible = ref(false)
const profileVisible = ref(false)
const momentNoticeCount = ref(0)
const squareNoticeCount = ref(0)
const fetchMomentNotices = async () => { try { const res = await request.get('/moments/summary'); if (res.code===0) momentNoticeCount.value = res.data.momentUnread || 0 } catch(e) {} }
const fetchSquareNotices = async () => { try { const res = await request.get('/square/summary'); if (res.code===0) squareNoticeCount.value = res.data.squareUnread || 0 } catch(e) {} }
// 离开聊天页时清除 WS 当前会话，确保消息通知正常
watch(() => route.path, (path) => {
  if (!path.startsWith('/chat')) {
    send({ action: 'leave_conversation', data: {} })
  }
})

const announcePopup = ref('')
let announceTimer = null
const showAnnounceFloat = (content) => {
  announcePopup.value = content
  clearTimeout(announceTimer)
  announceTimer = setTimeout(() => { announcePopup.value = '' }, 30000)
}

const announceList = ref([])
const announceCount = ref(0)
const fetchAnnounceUnread = async () => {
  try {
    const res = await request.get('/admin/announcements/unread')
    if (res.code === 0) announceCount.value = res.data
  } catch(e) {}
}
const loadAnnouncements = async () => {
  try {
    const res = await request.get('/admin/announcements')
    if (res.code === 0) announceList.value = res.data
    announceCount.value = 0
    request.put('/admin/announcements/read').catch(()=>{})
  } catch(e) {}
}
const formatAnnTime = (ts) => {
  if (!ts) return ''
  return new Date(ts).toLocaleDateString('zh-CN', { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' })
}
const totalUnread = ref(0)
const updateTotalUnread = (val) => { totalUnread.value = val }
provide('updateUnread', updateTotalUnread)
provide('momentNoticeCount', momentNoticeCount)
provide('fetchMomentNotices', fetchMomentNotices)
provide('squareNoticeCount', squareNoticeCount)
provide('fetchSquareNotices', fetchSquareNotices)
const userInfo = ref(null)

const go = (path) => router.push(path)

// ===== 弹窗 =====
watch(profileVisible, (val) => {
  if (val) loadSettingsForm()
})

const saving = ref(false)
const fontSize = ref('normal')
const notifySound = ref(true)
const notifyDesktop = ref(true)
const allowSearch = ref(true)
const settingsForm = ref({ id: 0, username: '', email: '', nickname: '', avatarUrl: '', gender: 0, signature: '', description: '' })
const browserInfo = navigator.userAgent.includes('Chrome') ? 'Chrome ' + (navigator.userAgent.match(/Chrome\/(\d+)/)?.[1] || '') : navigator.userAgent.includes('Firefox') ? 'Firefox' : navigator.appName
const platformInfo = navigator.platform || (navigator.userAgent.includes('Win') ? 'Windows' : navigator.userAgent.includes('Mac') ? 'macOS' : navigator.userAgent.includes('Linux') ? 'Linux' : '未知')
const cookieSupport = navigator.cookieEnabled
const navLang = navigator.language
const scrWidth = screen.width, scrHeight = screen.height, scrDepth = screen.colorDepth

const loadSettingsForm = async () => {
  try {
    const res = await request.get('/users/me')
    if (res.code === 0) {
      const u = res.data
      const info = { userId: u.id, username: u.username, email: u.email, nickname: u.nickname, avatarUrl: u.avatarUrl, gender: u.gender, signature: u.signature, description: u.description }
      settingsForm.value = {
        id: u.id || 0,
        username: u.username || '',
        email: u.email || '',
        nickname: u.nickname || '',
        avatarUrl: u.avatarUrl || '',
        gender: u.gender || 0,
        signature: u.signature || '',
        description: u.description || ''
      }
      userInfo.value = info
      localStorage.setItem('user_info', JSON.stringify(info))
    }
  } catch (e) {
    const info = localStorage.getItem('user_info')
    if (info) {
      const u = JSON.parse(info)
      settingsForm.value = {
        id: u.userId || 0,
        username: u.username || '',
        email: u.email || '',
        nickname: u.nickname || '',
        avatarUrl: u.avatarUrl || '',
        gender: u.gender || 0,
        signature: u.signature || '',
        description: u.description || ''
      }
    }
  }
}

const beforeAvatarUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传图片'); return false }
  if (!isLt5M) { ElMessage.error('图片大小不能超过5MB'); return false }
  return true
}

const handleAvatarUpload = async (options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const token = localStorage.getItem('access_token')
    const res = await axios.post(`${API_BASE_URL}/files/upload-avatar`, formData, {
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'multipart/form-data' }
    })
    if (res.data?.code === 0) {
      ElMessage.success('头像上传成功')
      // 重新拉取用户信息以更新头像URL
      loadSettingsForm()
    } else {
      ElMessage.error(res.data?.message || '上传失败')
    }
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

const handleSaveSettings = async () => {
  saving.value = true
  try {
    const res = await request.put('/users/me', {
      nickname: settingsForm.value.nickname,
      gender: settingsForm.value.gender,
      signature: settingsForm.value.signature,
      description: settingsForm.value.description
    })
    if (res.code === 0) {
      const info = JSON.parse(localStorage.getItem('user_info') || '{}')
      info.nickname = settingsForm.value.nickname
      info.gender = settingsForm.value.gender
      info.signature = settingsForm.value.signature
      info.description = settingsForm.value.description
      localStorage.setItem('user_info', JSON.stringify(info))
      userInfo.value = info
      ElMessage.success('保存成功')
      profileVisible.value = false
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const goResetPassword = () => {
  profileVisible.value = false
  router.push('/reset-password')
}

const handleLogout = () => {
  profileVisible.value = false
  ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' }).then(() => {
    const refreshToken = localStorage.getItem('refresh_token')
    const accessToken = localStorage.getItem('access_token')
    if (refreshToken) {
      request.post('/auth/logout', { refreshToken }, { headers: { Authorization: `Bearer ${accessToken}` } }).catch(() => {})
    }
    clearAuth()
    router.push('/login')
  }).catch(() => {})
}

// ===== 初始化 =====
const fetchPendingCount = async () => {
  try {
    const res = await request.get('/friends/requests')
    if (res.code === 0) pendingCount.value = res.data.pendingCount || 0
  } catch (e) { console.error(e) }
}

const fetchUserInfo = async () => {
  try {
    const res = await request.get('/users/me')
    if (res.code === 0) {
      const u = res.data
      userInfo.value = { userId: u.id, username: u.username, email: u.email, nickname: u.nickname, avatarUrl: u.avatarUrl }
      localStorage.setItem('user_info', JSON.stringify(userInfo.value))
    }
  } catch (e) {
    const info = localStorage.getItem('user_info')
    if (info) userInfo.value = JSON.parse(info)
  }
}

onMounted(() => {
  fetchUserInfo()
  fetchPendingCount()
  fetchMomentNotices()
  fetchSquareNotices()
  fetchAnnounceUnread()
  connect()
  onMessage((msg) => {
    if (msg.action === 'event') {
      const type = msg.data?.type
      if (type === 'friend_notify') { fetchPendingCount(); playFriendSound() }
      else if (type === 'moment_notify') { fetchMomentNotices(); playMomentSound() }
      else if (type === 'square_notify') { fetchSquareNotices(); playSquareSound() }
    }
    if (msg.action === 'event' && msg.data?.type === 'announcement') {
      fetchAnnounceUnread()
      showAnnounceFloat(msg.data.content)
    }
    if (msg.action === 'new_message') { playMsgSound(); totalUnread.value++ }
  })
})
</script>

<style scoped>
.app-wrapper { width:100%; max-width:60%; min-width:900px; margin:0 auto; display:flex; padding:12px 0; height:100vh; box-sizing:border-box; }
.layout { width:100%;
  display: flex;
  height: 100%;
  overflow: hidden;
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.15);
  background: #f0f0f0;
}

.nav-bar {
  border-radius: 14px 0 0 14px;
  width: 60px;
  min-width: 60px;
  height: 100%;
  background: #2e2e2e;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
}

.nav-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.nav-avatar {
  margin-bottom: 6px;
  border: 1px solid #555;
  cursor: pointer;
}

.nav-item {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.nav-item:hover { background: #3a3a3a; }
.nav-item.active { background: #3a3a3a; }

.nav-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.ws-dot {
  display: block;
  width: 8px; height: 8px;
  border-radius: 50%;
  margin-bottom: 10px;
  margin-top: 10px;
}
.ws-dot.online { background: #07c160; box-shadow: 0 0 4px #07c160; }
.ws-dot.offline { background: #fa5151; }

.content {
  flex: 1;
  display: flex;
  overflow: hidden;
  border-radius: 0 14px 14px 0;
}

/* 设置弹窗 */
.settings-dialog-body {
  padding: 0 4px;
  min-height: 240px;
  max-height: 60vh;
  overflow-y: auto;
}
/* 设置卡片 */
.settings-card { background: #fafafa; border-radius: 10px; margin-bottom: 12px; overflow: hidden; }
.set-card-header { display: flex; align-items: center; gap: 8px; padding: 14px 16px 10px; }
.set-card-title { font-size: 14px; font-weight: 600; color: #333; }
.set-card-body { padding: 0 16px 14px; }
.set-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-top: 1px solid #f0f0f0; }
.set-row:first-child { border-top: none; }
.set-row-title { font-size: 13px; color: #333; }
.set-row-desc { font-size: 11px; color: #aaa; margin-top: 2px; }
.about-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 16px; }
.about-item { display: flex; gap: 6px; font-size: 12px; line-height: 1.8; }
.about-label { color: #999; white-space: nowrap; }
.about-val { color: #555; }
.about-actions { display: flex; gap: 4px; margin-top: 12px; padding-top: 10px; border-top: 1px solid #eee; }
.about-copyright { text-align: center; font-size: 11px; color: #bbb; margin-top: 10px; }
.info-grid { display: flex; flex-direction: column; gap: 8px; }
.info-row { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.info-row:has(textarea) { align-items: flex-start; }
.info-label { color: #888; width: 60px; flex-shrink: 0; text-align: right; }
.info-value { color: #333; }

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 12px;
}

.avatar-uploader {
  cursor: pointer;
  position: relative;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.35);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-uploader:hover .avatar-mask {
  opacity: 1;
}

.form-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-item label {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
}

.moment-mask { position:fixed; inset:0; background:rgba(0,0,0,0.5); z-index:2000; display:flex; justify-content:center; overflow-y:auto; }
.moment-popup { width:550px; margin-top:5vh; height:90vh; background:#fff; position:relative; display:flex; flex-direction:column; overflow:hidden; }
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.footer-left {
  display: flex;
  gap: 8px;
}

.footer-right {
  display: flex;
  gap: 8px;
}

.ann-pop-title { font-weight:600; font-size:15px; padding-bottom:10px; border-bottom:1px solid #f0f0f0; margin-bottom:8px; }
.ann-pop-list { max-height:300px; overflow-y:auto; }
.ann-pop-item { padding:10px 0; border-bottom:1px solid #f9f9f9; }
.ann-pop-item:last-child { border-bottom:none; }
.ann-pop-content { font-size:14px; color:#333; line-height:1.5; }
.ann-pop-time { font-size:12px; color:#999; margin-top:4px; }
.ann-pop-empty { text-align:center; color:#ccc; padding:20px; font-size:13px; }
</style>

<style>
body {
  background: url('https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1920&q=80') center/cover fixed;
}
body::before {
  content:''; position:fixed; inset:0; pointer-events:none; z-index:0;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  background: rgba(22,26,36,0.55);
}
#app { position:relative; z-index:1; }
::-webkit-scrollbar { width:6px; height:6px; }
::-webkit-scrollbar-track { background:transparent; }
::-webkit-scrollbar-thumb { background:rgba(0,0,0,0.15); border-radius:3px; }
::-webkit-scrollbar-thumb:hover { background:rgba(0,0,0,0.25); }
html.dark ::-webkit-scrollbar-thumb { background:rgba(255,255,255,0.12); }
html.dark ::-webkit-scrollbar-thumb:hover { background:rgba(255,255,255,0.2); }
.el-dialog { border-radius:14px !important; }
.el-dialog__header { padding:20px 24px 12px !important; border-radius:14px 14px 0 0; }
.el-dialog__body { padding:16px 24px 20px !important; }
.el-dialog__footer { padding:12px 24px 20px !important; border-radius:0 0 14px 14px; }
.el-overlay { backdrop-filter:blur(4px); -webkit-backdrop-filter:blur(4px); background:rgba(0,0,0,0.3) !important; }
.el-message-box { border-radius:14px !important; }
.el-popover { border-radius:10px !important; }
.el-message { border-radius:10px !important; }
/* 全局暗色变量 */
html.dark { --bg-nav:#0d0d1c; --bg-sidebar:#141428; --bg-primary:#1a1a30; --bg-secondary:#1f1f36; --bg-card:#262644; --bg-input:#2e2e4c; --text:#e0e0e0; --text-dim:#aaa; --border:rgba(255,255,255,0.06); }
html.dark body { background:#0f0f1a; }
html.dark .layout, html.dark .chat-main, html.dark .files-content,
html.dark .contacts-layout .main-panel, html.dark .contacts-layout,
html.dark .moment-popup { background:var(--bg-primary) !important; }
html.dark .nav-bar { background:var(--bg-nav) !important; }
html.dark .chat-header { background:var(--bg-secondary) !important; border-color:var(--border) !important; }
html.dark .files-sidebar, html.dark .contacts-layout .sidebar { background:var(--bg-sidebar) !important; border-color:var(--border) !important; }
html.dark .moment-header, html.dark .leaderboard { background:var(--bg-secondary) !important; border-color:var(--border) !important; }
html.dark .chat-input-area { background:var(--bg-primary) !important; border-color:var(--border) !important; }
html.dark .session-list { background:var(--bg-sidebar) !important; border-color:var(--border) !important; }
html.dark .detail-card, html.dark .info-rows, html.dark .member-section,
html.dark .el-dialog, html.dark .settings-card, html.dark .context-menu,
html.dark .recording-modal, html.dark .manage-popover, html.dark .drawer-body,
html.dark .auth-card { background:var(--bg-card) !important; }
html.dark .text-bubble, html.dark .file-card, html.dark .media-card,
html.dark .card-msg, html.dark .loc-card, html.dark .audio-card,
html.dark .el-input__inner, html.dark .el-textarea__inner, html.dark input, html.dark textarea { background:var(--bg-input) !important; color:var(--text) !important; }
html.dark .msg-self .text-bubble { background:#0d4a2e !important; }
html.dark .moment-comments, html.dark .post-comments,
html.dark .quote-banner, html.dark .quote-inline, html.dark .quote-bar { background:var(--bg-input) !important; }
html.dark .quote-banner { border-left-color:#666 !important; }

/* 统一文字颜色 */
html.dark, html.dark .chat-name, html.dark .session-name, html.dark .friend-name,
html.dark .request-name, html.dark .search-name, html.dark .detail-card-name,
html.dark .info-val, html.dark .section-title, html.dark .section-header,
html.dark .invite-right-title, html.dark .profile-name, html.dark .notice-title,
html.dark .sender-name, html.dark .set-card-title, html.dark .set-row-title,
html.dark .el-dialog__title, html.dark .rec-timer, html.dark .moment-text,
html.dark .post-text, html.dark .moment-nick, html.dark .post-nick,
html.dark .file-card-name, html.dark .card-msg-name, html.dark .loc-addr,
html.dark .lb-header, html.dark .publish-header-name, html.dark .drawer-header,
html.dark .auth-card .title { color:var(--text) !important; }
html.dark .session-preview, html.dark .sender-name-text, html.dark .session-time,
html.dark .chat-group-tag, html.dark .info-label, html.dark .detail-card-sub,
html.dark .request-sub, html.dark .search-sub, html.dark .profile-label,
html.dark .profile-desc, html.dark .profile-signature, html.dark .file-time,
html.dark .file-meta, html.dark .file-dl, html.dark .load-tip, html.dark .empty,
html.dark .sub-item, html.dark .time-divider span, html.dark .drawer-sub,
html.dark .read-item .read-value, html.dark .edit-label, html.dark .rec-hint,
html.dark .manage-item, html.dark .manage-title, html.dark .lb-name,
html.dark .post-time, html.dark .moment-time-text, html.dark .notice-item,
html.dark .notice-time, html.dark .about-val, html.dark .auth-card .subtitle,
html.dark .card-msg-sub, html.dark .loc-sub, html.dark .file-card-size,
html.dark .audio-sec, html.dark .publish-count { color:var(--text-dim) !important; }
html.dark .invite-friend, html.dark .invite-picked, html.dark .ctx-item,
html.dark .profile-row span:last-child, html.dark .profile-desc { color:var(--text) !important; }

/* 统一边框 */
html.dark .session-list, html.dark .chat-header, html.dark .chat-input-area,
html.dark .files-sidebar, html.dark .contacts-layout .sidebar,
html.dark .info-rows .info-row, html.dark .request-card, html.dark .search-item,
html.dark .moment-item, html.dark .post-item, html.dark .notice-item,
html.dark .notice-header, html.dark .lb-header, html.dark .publish-toolbar,
html.dark .profile-card .profile-section, html.dark .manage-title,
html.dark .moment-header, html.dark .leaderboard, html.dark .quote-bar,
html.dark .drawer-divider, html.dark .sidebar-divider, html.dark .divider,
html.dark .settings-divider, html.dark .section-divider { border-color:var(--border) !important; }

/* hover 统一 */
html.dark .session-item:hover, html.dark .friend-item:hover,
html.dark .sidebar-tab:hover, html.dark .invite-friend:hover,
html.dark .invite-picked:hover, html.dark .notice-item:hover,
html.dark .manage-item:hover, html.dark .ctx-item:hover,
html.dark .file-card:hover { background:rgba(255,255,255,0.04) !important; }
html.dark .session-item.active, html.dark .friend-item.active,
html.dark .invite-friend.picked { background:rgba(7,193,96,0.12) !important; }

/* 按钮 */
html.dark .el-button:not(.el-button--primary) { background:rgba(255,255,255,0.06); color:var(--text); border-color:var(--border); }
html.dark .el-button:not(.el-button--primary):hover { background:rgba(255,255,255,0.1); }

/* 阴影 */
html.dark .text-bubble, html.dark .file-card, html.dark .media-card,
html.dark .card-msg, html.dark .loc-card { box-shadow:none; }
html.dark .msg-self .text-bubble::after,
html.dark .msg-row:not(.msg-self) .text-bubble::after { display:none; }

/* 提示框 */
html.dark .el-message-box { background:var(--bg-card) !important; }
html.dark .el-message-box__title, html.dark .el-message-box__message { color:var(--text) !important; }
html.dark .el-popover { background:var(--bg-card) !important; color:var(--text) !important; }
html.dark .el-select-dropdown, html.dark .el-dropdown-menu { background:var(--bg-card) !important; }
html.dark .el-select-dropdown__item, html.dark .el-dropdown-menu__item { color:var(--text) !important; }
html.dark .el-select-dropdown__item:hover, html.dark .el-dropdown-menu__item:hover { background:rgba(255,255,255,0.04) !important; }
html.dark .el-popper__arrow::before { background:var(--bg-card) !important; border-color:var(--border) !important; }
/* 抽屉 */
html.dark .chat-drawer { background:var(--bg-card) !important; }
html.dark .drawer-section { background:var(--bg-card) !important; }
html.dark .drawer-action-item { color:var(--text) !important; border-color:var(--border) !important; }
html.dark .member-item, html.dark .member-card { color:var(--text) !important; }
html.dark .member-name { color:var(--text-dim) !important; }
/* 文件页头部 */
html.dark .content-title { color:var(--text) !important; }
html.dark .content-count { color:var(--text-dim) !important; }
html.dark .file-name { color:var(--text) !important; }
html.dark .file-size, html.dark .file-meta, html.dark .file-time { color:var(--text-dim) !important; }
html.dark .file-card-icon { color:#fff !important; }
html.dark .file-card { background:var(--bg-card) !important; }
html.dark .file-card:hover { background:rgba(255,255,255,0.06) !important; }
html.dark .files-content { background:var(--bg-primary) !important; }
/* 联系人页头部 */
html.dark .detail-title { color:var(--text) !important; }
html.dark .panel-title { color:var(--text) !important; }
html.dark .section-title { color:var(--text) !important; }
html.dark .placeholder span { color:var(--text-dim) !important; }
html.dark .detail-card-name, html.dark .detail-card-sub, html.dark .detail-card-desc { color:var(--text) !important; }
html.dark .request-card .request-info div { color:var(--text) !important; }
html.dark .request-sub { color:var(--text-dim) !important; }
html.dark .empty-hint { color:var(--text-dim) !important; }
html.dark .info-row .info-val { color:var(--text) !important; }
html.dark .info-row .info-label { color:var(--text-dim) !important; }
html.dark .member-card-name { color:var(--text) !important; }
html.dark .member-card-sub { color:var(--text-dim) !important; }
html.dark .profile-pop-name { color:var(--text) !important; }
html.dark .profile-pop-gender, html.dark .profile-pop-sig { color:var(--text-dim) !important; }
html.dark .profile-pop-section { border-color:var(--border) !important; }
html.dark .profile-pop-label { color:var(--text-dim) !important; }
html.dark .profile-pop-row span:first-child { color:var(--text-dim) !important; }
html.dark .profile-pop-row span:last-child { color:var(--text) !important; }
html.dark .profile-card-pop, html.dark .profile-pop-desc { color:var(--text) !important; }
/* element-plus 内部白色干掉 */
html.dark .el-dialog, html.dark .el-drawer, html.dark .el-drawer__body,
html.dark .el-drawer__header, html.dark .el-dialog__header,
html.dark .el-dialog__body, html.dark .el-dialog__footer,
html.dark .el-radio-group, html.dark .el-checkbox__label { background:transparent !important; color:var(--text) !important; }
html.dark .el-drawer { background:var(--bg-card) !important; }
html.dark .el-radio__label { color:var(--text) !important; }
html.dark .el-checkbox__label { color:var(--text) !important; }
html.dark .el-switch__label * { color:var(--text-dim) !important; }
html.dark .el-tabs__item { color:var(--text-dim) !important; }
html.dark .el-tabs__item.is-active { color:#07c160 !important; }
html.dark .el-tabs__nav-wrap::after { background:var(--border) !important; }
html.dark .el-empty__description p { color:var(--text-dim) !important; }
html.dark .el-divider__text { background:var(--bg-primary) !important; color:var(--text-dim) !important; }
html.dark .el-tag { background:rgba(255,255,255,0.08) !important; color:var(--text) !important; border-color:var(--border) !important; }
html.dark .el-tag--success { background:rgba(7,193,96,0.15) !important; color:#07c160 !important; }
html.dark .el-progress__text { color:var(--text-dim) !important; }
html.dark .el-avatar { background:rgba(255,255,255,0.08) !important; color:var(--text) !important; }
.el-badge__content { border: none !important; }
html.dark .el-badge__content { background:#fa5151 !important; color:#fff !important; border: none !important; }
/* 全部输入框 */
html.dark input:not([type=file]), html.dark textarea, html.dark .el-input__wrapper,
html.dark .el-textarea__inner, html.dark .el-input__inner,
html.dark .el-select .el-input__wrapper { background:var(--bg-input) !important; color:var(--text) !important; box-shadow:none !important; }
html.dark input::placeholder, html.dark textarea::placeholder,
html.dark .el-input__inner::placeholder { color:#666 !important; }
/* 清除多余白色残留 */
html.dark .el-dialog, html.dark .el-overlay-dialog, html.dark .el-overlay { background:transparent !important; }
html.dark .el-dialog { background:var(--bg-card) !important; }
html.dark .el-dialog__headerbtn .el-dialog__close { color:var(--text-dim) !important; }
html.dark .files-layout { background:var(--bg-primary) !important; }
html.dark .files-sidebar { background:var(--bg-sidebar) !important; border-color:var(--border) !important; }
html.dark .files-content { background:var(--bg-primary) !important; }
html.dark .sidebar-search .el-input__wrapper { background:var(--bg-input) !important; }
html.dark .sidebar-tab { color:var(--text-dim) !important; }
html.dark .sidebar-tab:hover { background:rgba(255,255,255,0.04) !important; }
html.dark .sidebar-tab.active { background:rgba(7,193,96,0.12) !important; color:#07c160 !important; }
html.dark .sidebar-section-title, html.dark .sidebar-label { color:var(--text-dim) !important; }
html.dark .sub-item { color:var(--text-dim) !important; }
html.dark .sub-item.active { color:#07c160 !important; }
html.dark .sub-item:hover { background:rgba(255,255,255,0.04) !important; }
html.dark .file-stats { color:var(--text-dim) !important; }
html.dark .sub-empty { color:#666 !important; }
/* 群编辑区 */
html.dark .group-editable, html.dark .group-readonly { background:transparent !important; }
html.dark .read-item, html.dark .edit-item { color:var(--text) !important; }
/* 聊天输入框 */
html.dark .chat-input-area textarea, html.dark .chat-input-area .el-textarea__inner { background:var(--bg-primary) !important; color:var(--text) !important; border:none !important; }
html.dark .toolbar { background:transparent !important; }
html.dark .tool-icon { color:#aaa !important; }
html.dark .tool-icon:hover { color:#07c160 !important; }
html.dark .svg-icon svg, html.dark .svg-icon svg path { fill:#aaa !important; color:#aaa !important; }
/* 时间分隔线 */
html.dark .time-divider span { color:var(--text-dim) !important; background:rgba(255,255,255,0.06) !important; }
/* 朋友圈/广场通知弹窗 */
html.dark .notice-container, html.dark .moment-notice-popper { background:var(--bg-card) !important; }
html.dark .notice-header { background:rgba(255,255,255,0.04) !important; border-color:var(--border) !important; }
html.dark .notice-item, html.dark .info-bottom { color:var(--text-dim) !important; }
html.dark .notice-empty { color:var(--text-dim) !important; }
html.dark .notice-clear { color:#8ab4f8 !important; }
/* 查找用户 */
html.dark .search-result-item, html.dark .search-item { color:var(--text) !important; background:var(--bg-card) !important; }
html.dark .search-result-name, html.dark .search-name { color:var(--text) !important; }
html.dark .search-result-sub, html.dark .search-sub { color:var(--text-dim) !important; }
html.dark .search-result-item { border-color:var(--border) !important; }
html.dark .settings-dialog-body, html.dark .info-grid { background:transparent !important; }
html.dark .settings-dialog-body .info-row, html.dark .settings-dialog-body .info-value,
html.dark .settings-dialog-body .info-label { color:var(--text) !important; }
html.dark .settings-dialog-body .el-input__inner { color:var(--text) !important; }
html.dark .settings-dialog-body .el-radio__label { color:var(--text) !important; }
/* 好友请求/验证弹窗 */
html.dark .el-form-item__label { color:var(--text-dim) !important; }
/* 表情选择器 */
html.dark .emoji-panel { background:var(--bg-card) !important; }
html.dark .emoji-panel h4 { color:var(--text-dim) !important; }
html.dark .moment-popup, html.dark .moments-wrapper, html.dark .square-wrapper,
html.dark .moment-body, html.dark .post-list, html.dark .square-body { background:var(--bg-primary) !important; }
html.dark .moment-header, html.dark .square-header { background:var(--bg-secondary) !important; border-color:var(--border) !important; }
html.dark .moment-content, html.dark .moment-cover { background:var(--bg-primary) !important; }
html.dark .moment-item, html.dark .post-item { border-color:var(--border) !important; }
html.dark .moment-comments, html.dark .post-comments { background:var(--bg-input) !important; }
html.dark .moment-popup .header-icon, html.dark .square-wrapper .header-icon { color:#ccc !important; }
html.dark .moment-popup .title, html.dark .square-wrapper .title { color:var(--text) !important; }
html.dark .square-wrapper .leaderboard { background:var(--bg-secondary) !important; border-color:var(--border) !important; }
html.dark .square-wrapper .lb-item:hover { background:rgba(255,255,255,0.04) !important; }
html.dark .moment-comments, html.dark .post-comments { background:var(--bg-input) !important; }
html.dark .moment-comment, html.dark .post-comment { color:var(--text) !important; }
html.dark .comment-name, html.dark .comment-reply { color:#8ab4f8 !important; }
html.dark .comment-del { color:var(--text-dim) !important; }
html.dark .moment-likes, html.dark .post-likes { color:var(--text-dim) !important; }

/* 公告弹窗暗色 */
html.dark .ann-pop-title { color:#e0e0e0 !important; border-color:#333 !important; }
html.dark .ann-pop-item { border-color:#2a2a2a !important; }
html.dark .ann-pop-content { color:#ccc !important; }
html.dark .ann-pop-time { color:#777 !important; }
html.dark .ann-pop-empty { color:#555 !important; }
.announce-float { display:flex; align-items:center; padding:10px 24px; background:linear-gradient(135deg, #fffbe6, #fff1cc); border-bottom:1px solid #ffd591; color:#8c6a00; font-size:14px; z-index:2000; }
.announce-float-icon { margin-right:10px; font-size:16px; flex-shrink:0; }
.announce-float-text { flex:1; overflow:hidden; white-space:nowrap; }
.announce-float-close { cursor:pointer; flex-shrink:0; margin-left:12px; color:#8c6a00; }
.slide-down-enter-active, .slide-down-leave-active { transition:all .3s ease; }
.slide-down-enter-from, .slide-down-leave-to { opacity:0; transform:translateY(-100%); }
html.dark .announce-float { background:linear-gradient(135deg, #3d3520, #4a3a18); border-color:#5a4a20; color:#e6c85a; }
html.dark .announce-float-close { color:#e6c85a; }
</style>
