<template>
  <div class="moments-wrapper">
    <!-- 头部栏 -->
    <div class="moment-header">
      <div class="header-left">
        <!-- 单条/用户模式：返回按钮 -->
        <div v-if="isSingleMode || viewUserId" class="icon-wrapper" @click="backToAll">
          <el-icon class="header-icon"><ArrowLeft /></el-icon>
        </div>

        <!-- 列表模式：通知铃铛 + 刷新 -->
        <template v-else>
          <el-popover
            placement="bottom"
            :width="320"
            trigger="hover"
            popper-class="moment-notice-popper"
            :offset="15"
            :show-after="100"
            @show="fetchNotices"
          >
            <template #reference>
              <div class="icon-wrapper">
                <el-badge v-if="momentNoticeCount" :value="momentNoticeCount" :offset="[10, -5]" class="notice-badge">
                  <el-icon class="header-icon"><Message /></el-icon>
                </el-badge>
                <el-icon v-else class="header-icon"><Message /></el-icon>
              </div>
            </template>

            <div class="notice-container">
              <div class="notice-header">
                <span class="notice-title">消息</span>
                <span class="notice-clear" @click="clearAllNotices">清空</span>
              </div>
              <div class="notice-list" v-if="notices.length > 0">
                <div v-for="notice in notices" :key="notice.id" class="notice-item" @click="handleClickNotice(notice)">
                  <el-avatar :size="40" :src="resolveFromAvatar(notice.fromUserId)" />
                  <div class="notice-info">
                    <div class="info-top">
                      <span class="sender-name">{{ resolveFromNickname(notice.fromUserId) }}</span>
                      <span class="notice-time">{{ formatTime(notice.createdAt) }}</span>
                    </div>
                    <div class="info-bottom">
                      {{ notice.type === 'like' ? '赞了你的动态' : notice.content }}
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="notice-empty">暂无消息记录</div>
            </div>
          </el-popover>

          <div class="icon-wrapper" @click="refreshMoments">
            <el-icon class="header-icon"><Refresh /></el-icon>
          </div>
        </template>
      </div>

      <div class="title">{{ isSingleMode ? '详情' : viewUserName ? viewUserName + ' 的动态' : '朋友圈' }}</div>

      <div class="header-right">
        <div class="icon-wrapper" @click="emit('close')">
          <el-icon class="header-icon"><Close /></el-icon>
        </div>
      </div>
    </div>

    <!-- 可滚动内容区 -->
    <div class="moment-body" ref="listRef" @scroll="onScroll">
      <!-- 封面（仅列表模式） -->
      <div v-if="!isSingleMode && !viewUserId" class="moment-cover">
        <img class="cover-img" src="https://pic.616pic.com/bg_w1180/00/00/32/kpmg3R46Q2.jpg?x-oss-process=image/resize,w_1120" />
        <div class="moment-nickname">{{ userInfo?.nickname }}</div>
        <el-avatar class="moment-avatar" :src="userInfo?.avatarUrl" :size="60" shape="square">{{ userInfo?.nickname?.charAt(0) }}</el-avatar>
      </div>

      <div class="moment-content" :style="{ marginTop: isSingleMode ? '20px' : '0' }">
        <div v-for="m in moments" :key="m.id" class="moment-item">
          <div class="moment-header-row">
            <el-avatar :src="resolveAvatar(m.userId, m.avatar)" :size="40" shape="square" @click.stop="showProfile(m.userId)" style="cursor:pointer">{{ resolveNickname(m.userId, m.nickname)?.charAt(0) }}</el-avatar>
            <div class="moment-nick" @click.stop="viewUserPosts(m)">{{ resolveNickname(m.userId, m.nickname) }}</div>
            <el-icon v-if="m.userId === myUserId && !isSingleMode && !viewUserId" class="moment-delete" @click="deleteMoment(m)"><Delete /></el-icon>
          </div>

          <div @click="openSingleMoment(m)" style="cursor:pointer">
            <div class="moment-text" v-if="m.content">{{ m.content }}</div>

            <div v-if="m.mediaUrls" class="moment-images">
              <div v-for="(url, i) in parseMedia(m.mediaUrls)" :key="i" class="moment-img-wrap" :class="{ single: parseMedia(m.mediaUrls).length === 1 }">
                <video v-if="isVideo(url)" :src="resolveUrl(url)" class="moment-video" controls preload="metadata" />
                <el-image v-else :src="resolveUrl(url)" :preview-src-list="parseMedia(m.mediaUrls).map(u => resolveUrl(u))" fit="cover" />
              </div>
            </div>
          </div>

          <div class="moment-actions">
            <span class="moment-time-text">{{ formatTime(m.createdAt) }}</span>
            <div class="moment-btns">
              <span class="moment-btn" @click="toggleLike(m)">
                {{ m.isLiked ? '❤️' : '🤍' }} <span v-if="m.likes?.length">{{ m.likes.length }}</span>
              </span>
              <span class="moment-btn" @click="openComment(m)">💬 {{ m.comments?.length || 0 }}</span>
            </div>
          </div>

          <div v-if="m.likes?.length" class="moment-likes">
            ❤️ <span v-for="(l, i) in m.likes" :key="l.userId">{{ resolveNickname(l.userId, l.nickname) }}{{ i < m.likes.length - 1 ? '、' : '' }}</span>
          </div>

          <div v-if="m.comments?.length" class="moment-comments">
            <div v-for="(c, ci) in m.comments" :key="ci" class="moment-comment">
              <span class="comment-name" @click="replyTo(m, c)">{{ resolveNickname(c.userId, c.nickname) }}</span>
              <span v-if="c.replyToUserId" class="comment-reply"> 回复 {{ resolveNickname(c.replyToUserId, c.replyToNickname) }}</span>
              ：{{ c.content }}
              <el-icon v-if="c.userId === myUserId" class="comment-del" @click="deleteComment(m, ci)"><Close /></el-icon>
            </div>
          </div>

          <div v-if="m._showComment" class="moment-comment-input">
            <el-input v-model="m._commentText" :placeholder="m._replyPlaceholder || '评论...'" size="small" @keyup.enter="submitComment(m)" />
          </div>
        </div>

        <!-- 加载指示器（仅列表模式） -->
        <div v-if="!isSingleMode" class="loading-wrapper">
          <div v-if="loading" class="loading-text">加载中...</div>
          <div v-else-if="!hasMore" class="loading-text">没有更多了</div>
        </div>
      </div>
    </div>

    <!-- 发布按钮（仅列表模式） -->
    <div v-if="!isSingleMode && !viewUserId" class="publish-btn" @click="showPublish = true"><el-icon :size="24"><Plus /></el-icon></div>

    <!-- 发布弹窗 -->
    <el-dialog v-model="showPublish" width="480px" align-center :close-on-click-modal="false" destroy-on-close>
      <template #header>
        <div class="publish-header">
          <el-avatar :src="userInfo?.avatarUrl" :size="40" shape="square">{{ userInfo?.nickname?.charAt(0) }}</el-avatar>
          <span class="publish-header-name">{{ userInfo?.nickname }}</span>
        </div>
      </template>
      <div class="publish-body">
        <el-input v-model="publishContent" type="textarea" :rows="6" placeholder="分享新鲜事..." resize="none" class="publish-textarea" maxlength="2000" show-word-limit />
        <div class="publish-toolbar">
          <div class="publish-tool-btn" @click="triggerPublishImage" title="添加图片或视频">
            <el-icon :size="20" color="#576b95"><PictureFilled /></el-icon>
            <span>图片/视频</span>
          </div>
          <span class="publish-tip">支持 jpg/png/mp4 格式，最多9个</span>
        </div>
        <div class="publish-media" v-if="publishImages.length">
          <div v-for="(img, i) in publishImages" :key="i" class="publish-img-item">
            <video v-if="isVideoPreview(img)" :src="img" class="publish-thumb" />
            <img v-else :src="img" class="publish-thumb" />
            <span class="publish-img-del" @click="publishImages.splice(i,1); publishFiles.splice(i,1)">×</span>
          </div>
        </div>
      </div>
      <input ref="publishImageInput" type="file" accept="image/*,video/*" multiple hidden @change="onPublishImages" />
      <template #footer>
        <div class="publish-footer">
          <span class="publish-count">{{ publishContent.length }}/2000</span>
          <div class="publish-footer-btns">
            <el-button @click="showPublish = false; publishFiles = []">取消</el-button>
            <el-button type="primary" :disabled="!publishContent && !publishImages.length" @click="submitPublish" round>发布</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 名片弹窗 -->
    <el-dialog v-model="profileVisible" :title="profileData?.nickname || '名片'" width="400px" align-center destroy-on-close>
      <div class="profile-card-pop" v-if="profileData">
        <el-avatar :src="profileData.avatarUrl" :size="64" shape="square">{{ profileData.nickname?.charAt(0) }}</el-avatar>
        <div class="profile-pop-name">{{ profileData.nickname }}</div>
        <div class="profile-pop-gender">{{ profileData.gender == 1 ? '♂' : profileData.gender == 2 ? '♀' : '' }}</div>
        <div class="profile-pop-sig" v-if="profileData.signature">{{ profileData.signature }}</div>
        <div class="profile-pop-section">
          <div class="profile-pop-label">基本信息</div>
          <div class="profile-pop-row"><span>ID号：</span><span>{{ profileData.id || profileData.userId }}</span></div>
          <div class="profile-pop-row" v-if="profileData.email"><span>邮箱：</span><span>{{ profileData.email }}</span></div>
        </div>
        <div class="profile-pop-section" v-if="profileData.description">
          <div class="profile-pop-label">其它说明</div>
          <div class="profile-pop-desc">{{ profileData.description }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, inject } from 'vue'
import request from '@/utils/request'
import { useWebSocket } from '@/utils/websocket'
import { useUserCache } from '@/utils/userCache'
const userCache = useUserCache()
import { Plus, Delete, Close, Message, Refresh, ArrowLeft, PictureFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['close'])

// 共享状态：从 MainLayout 注入
const momentNoticeCount = inject('momentNoticeCount', ref(0))
const fetchMomentNotices = inject('fetchMomentNotices', () => {})

const moments = ref([]), loading = ref(false), listRef = ref(null)
const offset = ref(0), hasMore = ref(true)
const myUserId = ref(0), userInfo = ref({})
const showPublish = ref(false), publishContent = ref(''), publishImages = ref([]), publishImageInput = ref(null)

// ===== 单条/用户模式 =====
const isSingleMode = ref(false)
const viewUserId = ref(null)
const viewUserName = ref('')

// ===== 通过 userCache 解析昵称和头像（优先缓存，API 数据作为 fallback） =====
const resolveNickname = (uid, fallback) => {
  const u = userCache.get(uid)
  return u?.nickname || fallback || ''
}
const resolveAvatar = (uid, fallback) => {
  const u = userCache.get(uid)
  return u?.avatarUrl || fallback || ''
}
const resolveFromNickname = (uid) => {
  const u = userCache.get(uid)
  return u?.nickname || ''
}
const resolveFromAvatar = (uid) => {
  const u = userCache.get(uid)
  return u?.avatarUrl || ''
}

const backToAll = () => {
  isSingleMode.value = false
  viewUserId.value = null
  viewUserName.value = ''
  refreshMoments()
}

const openSingleMoment = (m) => { isSingleMode.value = true; moments.value = [m] }
const viewUserPosts = (m) => {
  if (isSingleMode.value) return
  viewUserId.value = m.userId
  viewUserName.value = resolveNickname(m.userId, m.nickname)
  offset.value = 0; moments.value = []; hasMore.value = true
  fetchMoments()
}
const profileData = ref(null), profileVisible = ref(false)
const showProfile = async (uid) => {
  profileData.value = userCache.get(uid) || { userId: uid, nickname: '', avatarUrl: '' }; profileVisible.value = true
  try { const res = await request.get(`/users/${uid}/profile`); if (res.code === 0) { profileData.value = res.data; userCache.set(uid, res.data) } } catch(e) {}
}

// ===== 通知 =====
const notices = ref([])

const fetchNotices = async () => {
  try {
    const res = await request.get('/moments/notifications')
    if (res.code === 0) {
      notices.value = res.data
      const fromIds = [...new Set(res.data.map(n => n.fromUserId).filter(Boolean))]
      if (fromIds.length) userCache.batchFetch(fromIds)
      if (momentNoticeCount.value > 0) markNoticesRead()
    }
  } catch (e) { console.error(e) }
  fetchMomentNotices() // 同步刷新摘要计数
}

const markNoticesRead = async () => {
  try {
    await request.put('/moments/notifications/read')
    momentNoticeCount.value = 0
  } catch (e) { console.error(e) }
}

const clearAllNotices = async () => {
  try {
    await request.delete('/moments/notifications')
    momentNoticeCount.value = 0
    notices.value = []
    ElMessage.success('消息已清空')
  } catch (e) { console.error(e) }
}

const handleClickNotice = async (notice) => {
  try {
    const res = await request.get(`/moments/${notice.momentId}`)
    if (res.code === 0) {
      moments.value = [res.data]
      userCache.batchFetch(collectMomentUserIds([res.data]))
      isSingleMode.value = true
      markNoticesRead()
    } else {
      ElMessage.warning('该动态已被删除')
    }
  } catch (e) { console.error(e) }
}

// ===== 工具函数 =====
const formatTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts), now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff/60000)+'分钟前'
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})
  return d.toLocaleDateString('zh-CN',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'})
}
const parseMedia = (s) => { try { return JSON.parse(s) } catch(e) { return [] } }
const resolveUrl = (url) => {
  if (!url) return ''; if (url.startsWith('http')) return url
  return '/api/files/download/' + (url.includes('/') ? url.substring(url.indexOf('/')+1) : url)
}

// ===== 时间线 =====
const collectMomentUserIds = (list) => {
  const ids = new Set()
  list.forEach(m => {
    if (m.userId) ids.add(m.userId)
    if (m.likes) m.likes.forEach(l => { if (l.userId) ids.add(l.userId) })
    if (m.comments) m.comments.forEach(c => {
      if (c.userId) ids.add(c.userId)
      if (c.replyToUserId) ids.add(c.replyToUserId)
    })
  })
  return [...ids]
}

const fetchMoments = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const params = { offset: offset.value, limit: 10 }
    if (viewUserId.value) params.filterUserId = viewUserId.value
    const res = await request.get('/moments', { params })
    if (res.code === 0 && res.data.length) {
      moments.value.push(...res.data); offset.value += res.data.length
      userCache.batchFetch(collectMomentUserIds(res.data))
    }
    else hasMore.value = false
  } catch(e) {} finally { loading.value = false }
}
const refreshMoments = () => { offset.value = 0; moments.value = []; hasMore.value = true; fetchMoments() }
const onScroll = () => {
  if (isSingleMode.value) return
  const el = listRef.value
  if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 100 && hasMore.value && !loading.value) fetchMoments()
}

// ===== 点赞 =====
const toggleLike = async (m) => {
  try { if (m.isLiked) { await request.delete(`/moments/${m.id}/like`); m.isLiked = false; m.likes = m.likes.filter(l => l.userId !== myUserId.value) } else { await request.post(`/moments/${m.id}/like`); m.isLiked = true; m.likes.push({ userId: myUserId.value, nickname: userInfo.value.nickname }) } } catch(e) {}
}

// ===== 评论 =====
const openComment = (m) => { m._showComment = !m._showComment; m._replyPlaceholder = '评论...'; m._replyToUserId = null; m._replyToNickname = null }
const replyTo = (m, c) => { m._showComment = true; m._replyPlaceholder = `回复 ${c.nickname}`; m._replyToUserId = c.userId; m._replyToNickname = c.nickname }
const submitComment = async (m) => {
  if (!m._commentText) return
  try { await request.post(`/moments/${m.id}/comments`, { content: m._commentText, replyToUserId: m._replyToUserId }); m.comments.push({ userId: myUserId.value, nickname: userInfo.value.nickname, content: m._commentText, replyToUserId: m._replyToUserId, replyToNickname: m._replyToNickname }); m._commentText = ''; m._showComment = false } catch(e) { ElMessage.error('评论失败') }
}
const deleteComment = async (m, ci) => {
  try { await request.delete(`/moments/${m.id}/comments/${ci}`); m.comments.splice(ci, 1) } catch(e) {}
}
const deleteMoment = async (m) => {
  try { await request.delete(`/moments/${m.id}`); moments.value = moments.value.filter(x => x.id !== m.id) } catch(e) {}
}

// ===== 发布 =====
const publishFiles = ref([])
const isVideo = (url) => /\.(mp4|webm|mov|ogg|avi|mkv)(\?|$)/i.test(url)
const isVideoPreview = (src) => src.startsWith('blob:')
const triggerPublishImage = () => publishImageInput.value?.click()
const onPublishImages = (e) => {
  Array.from(e.target.files||[]).forEach(f => {
    const isVid = f.type.startsWith('video/')
    publishFiles.value.push(f)
    if (isVid) { publishImages.value.push(URL.createObjectURL(f)) }
    else { const reader = new FileReader(); reader.onload = ev => publishImages.value.push(ev.target.result); reader.readAsDataURL(f) }
  }); e.target.value = ''
}
const submitPublish = async () => {
  let mediaUrls = null
  if (publishFiles.value.length) {
    const urls = []
    for (const f of publishFiles.value) {
      const fd = new FormData(); fd.append('file', f)
      try {
        const { default: axios } = await import('axios')
        const res = await axios.post('/api/files/upload', fd, { headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}`, 'Content-Type':'multipart/form-data' } })
        if (res.data?.code === 0) urls.push(res.data.data.url)
      } catch(e) {}
    }
    if (urls.length) mediaUrls = JSON.stringify(urls)
  }
  try {
    const res = await request.post('/moments', { content: publishContent.value, mediaUrls })
    if (res.code === 0) { showPublish.value = false; publishContent.value = ''; publishImages.value = []; publishFiles.value = []; refreshMoments() }
  } catch(e) {}
}

// ===== 初始化 =====
const { onMessage: onWsMessage } = useWebSocket()
onWsMessage((msg) => {
  if (msg.action === 'event' && msg.data?.type === 'moment_notify') {
    fetchMomentNotices()
  }
})

onMounted(() => {
  const info = JSON.parse(localStorage.getItem('user_info')||'{}')
  userInfo.value = info; myUserId.value = info.userId || 0
  fetchMoments()
  fetchMomentNotices()
})
</script>

<style scoped>
.moments-wrapper { flex:1; display:flex; flex-direction:column; overflow:hidden; background:#fff; position:relative; }

/* ===== 头部栏 ===== */
.moment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  height: 44px;
  background: #ededed;
  border-bottom: 1px solid #ddd;
  position: sticky;
  top: 0;
  z-index: 10;
  flex-shrink: 0;
}
.header-left, .header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 120px;
  flex-shrink: 0;
  z-index: 2;
}
.header-right { justify-content: flex-end; }
.title { font-size: 16px; font-weight: 600; color: #333; }
.icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.2s;
  position: relative;
}
.icon-wrapper:hover { background-color: rgba(0,0,0,0.08); }
.header-icon { font-size: 17px; color: #333; }
.notice-badge { display: flex; }

/* ===== 内容区 ===== */
.moment-body { flex:1; overflow-y:auto; }

/* ===== 封面 ===== */
.moment-cover { position:relative; height:240px; }
.cover-img { width:100%; height:100%; object-fit:cover; }
.moment-nickname { position:absolute; right:24px; bottom:30px; color:#fff; font-size:18px; font-weight:500; text-shadow:0 1px 3px rgba(0,0,0,0.5); }
.moment-avatar { position:absolute; right:20px; bottom:70px; border:2px solid #fff; }

/* ===== 内容 ===== */
.moment-content { padding:0 20px; }
.moment-item { padding:16px 0; border-bottom:1px solid #f0f0f0; }
.moment-header-row { display:flex; align-items:center; gap:10px; }
.moment-nick { font-size:15px; color:#576b95; font-weight:500; flex:1; }
.moment-nick:hover { text-decoration:underline; }
.moment-delete { cursor:pointer; color:#999; font-size:16px; }
.moment-text { margin:8px 0; font-size:15px; line-height:1.5; }
.moment-images { display:flex; flex-wrap:wrap; gap:4px; margin:8px 0; }
.moment-img-wrap { width:80px; height:80px; overflow:hidden; border-radius:4px; }
.moment-img-wrap.single { width:200px; height:auto; }
.moment-video { width:100%; max-width:280px; border-radius:6px; }
.moment-actions { display:flex; justify-content:space-between; align-items:center; margin-top:8px; }
.moment-time-text { font-size:11px; color:#bbb; }
.moment-btns { display:flex; gap:16px; }
.moment-btn { cursor:pointer; font-size:13px; color:#576b95; }
.moment-likes { font-size:13px; color:#576b95; margin-top:4px; padding:4px 0; }
.moment-comments { background:#f7f7f7; border-radius:4px; padding:6px 10px; margin-top:4px; }
.moment-comment { font-size:13px; line-height:1.6; }
.comment-name { color:#576b95; cursor:pointer; }
.comment-reply { color:#999; }
.comment-del { cursor:pointer; color:#ccc; margin-left:4px; font-size:12px; }
.moment-comment-input { margin-top:6px; }
.profile-card-pop { display:flex; flex-direction:column; align-items:center; padding:8px 0; }
.profile-pop-name { font-size:18px; font-weight:600; margin-top:8px; }
.profile-pop-gender { font-size:14px; color:#666; margin-top:2px; }
.profile-pop-sig { font-size:12px; color:#999; margin-top:4px; text-align:center; }
.profile-pop-section { width:100%; margin-top:12px; padding-top:10px; border-top:1px solid #f0f0f0; }
.profile-pop-label { font-size:12px; color:#999; margin-bottom:4px; }
.profile-pop-row { display:flex; font-size:12px; line-height:1.8; }
.profile-pop-row span:first-child { color:#999; width:60px; flex-shrink:0; }
.profile-pop-row span:last-child { color:#333; word-break:break-all; }
.profile-pop-desc { font-size:12px; color:#555; line-height:1.6; }

/* ===== 加载 ===== */
.loading-wrapper { text-align:center; padding:20px; }
.loading-text { color:#999; font-size:13px; }

/* ===== 发布按钮 ===== */
.publish-btn { position:absolute; bottom:24px; right:24px; width:48px; height:48px; background:#07c160; color:#fff; border-radius:50%; display:flex; align-items:center; justify-content:center; cursor:pointer; box-shadow:0 4px 12px rgba(7,193,96,0.4); z-index:50; }
.publish-header { display:flex; align-items:center; gap:10px; }
.publish-header-name { font-size:15px; font-weight:500; color:#333; }
.publish-body { margin-top:12px; }
.publish-textarea :deep(.el-textarea__inner) { border:none; box-shadow:none; font-size:15px; padding:0; }
.publish-toolbar { display:flex; align-items:center; justify-content:space-between; margin-top:10px; padding-top:10px; border-top:1px solid #f0f0f0; }
.publish-tool-btn { display:flex; align-items:center; gap:4px; cursor:pointer; font-size:12px; color:#576b95; padding:4px 8px; border-radius:4px; transition:background 0.2s; }
.publish-tool-btn:hover { background:#f0f0f0; }
.publish-tip { font-size:11px; color:#ccc; }
.publish-footer { display:flex; justify-content:space-between; align-items:center; width:100%; }
.publish-count { font-size:12px; color:#ccc; }
.publish-media { display:flex; flex-wrap:wrap; gap:6px; margin-top:10px; }
.publish-img-item { position:relative; width:80px; height:80px; }
.publish-thumb { width:100%; height:100%; object-fit:cover; border-radius:6px; }
.publish-img-del { position:absolute; top:-6px; right:-6px; width:20px; height:20px; background:#fa5151; color:#fff; border-radius:50%; font-size:12px; display:flex; align-items:center; justify-content:center; cursor:pointer; }
.publish-add { width:80px; height:80px; border:1px dashed #ccc; border-radius:6px; display:flex; align-items:center; justify-content:center; font-size:28px; color:#ccc; cursor:pointer; }
</style>

<style>
/* 通知弹窗全局样式（非 scoped，因为 el-popover 挂载在 body 上） */
.moment-notice-popper { padding:0 !important; border-radius:8px; }
.notice-container { display:flex; flex-direction:column; max-height:400px; }
.notice-header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #f0f0f0; background:#fafafa; border-radius:8px 8px 0 0; }
.notice-title { font-weight:bold; font-size:14px; color:#333; }
.notice-clear { font-size:12px; color:#576b95; cursor:pointer; }
.notice-clear:hover { opacity:0.8; }
.notice-list { overflow-y:auto; }
.notice-item { display:flex; padding:12px 16px; transition:background 0.2s; border-bottom:1px solid #f9f9f9; cursor:pointer; }
.notice-item:hover { background:#f5f5f5; }
.notice-item:last-child { border-bottom:none; }
.notice-info { flex:1; margin-left:10px; display:flex; flex-direction:column; min-width:0; }
.info-top { display:flex; justify-content:space-between; align-items:center; }
.sender-name { font-size:14px; font-weight:500; color:#333; }
.notice-time { font-size:11px; color:#b2b2b2; }
.info-bottom { font-size:13px; color:#666; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; margin-top:4px; }
.notice-empty { padding:40px; text-align:center; color:#999; font-size:13px; }
</style>
