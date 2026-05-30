<template>
  <div class="square-wrapper">
    <!-- 头部栏 -->
    <div class="square-header">
      <div class="header-left">
        <!-- 用户模式：返回按钮 -->
        <div v-if="viewUserId" class="icon-wrapper" @click="backToAll">
          <el-icon class="header-icon"><ArrowLeft /></el-icon>
        </div>
        <template v-else>
          <!-- 通知铃铛 -->
          <el-popover placement="bottom" :width="320" trigger="hover" popper-class="moment-notice-popper" :offset="15" :show-after="100" @show="fetchNotices">
            <template #reference>
              <div class="icon-wrapper">
                <el-badge v-if="squareNoticeCount" :value="squareNoticeCount" :offset="[10, -5]">
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
                      {{ notice.type === 'like' ? '赞了你的帖子' : notice.content }}
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="notice-empty">暂无消息记录</div>
            </div>
          </el-popover>
          <div class="icon-wrapper" @click="refreshPosts">
            <el-icon class="header-icon"><Refresh /></el-icon>
          </div>
        </template>
      </div>
      <div class="title">{{ viewUserName ? viewUserName + ' 的帖子' : isSingleMode ? '详情' : '广场' }}</div>
      <div class="header-right">
        <div class="icon-wrapper" @click="emit('close')">
          <el-icon class="header-icon"><Close /></el-icon>
        </div>
      </div>
    </div>

    <!-- 主体：帖子列表 + 排行榜 -->
    <div class="square-body">
      <div class="post-list" ref="listRef" @scroll="onScroll">
        <div v-for="p in posts" :key="p.id" class="post-item">
          <div class="post-header-row">
            <el-avatar :src="resolveAvatar(p.userId, p.avatar)" :size="40" shape="square" @click.stop="showProfile(p.userId)" style="cursor:pointer" />
            <div class="post-nick" @click.stop="viewUserPosts(p)">{{ resolveNickname(p.userId, p.nickname) }}</div>
            <el-icon v-if="p.userId === myUserId" class="post-delete" @click="deletePost(p)"><Delete /></el-icon>
          </div>

          <div @click="openSinglePost(p)" style="cursor:pointer">
            <div class="post-text" v-if="p.content">{{ p.content }}</div>

            <div v-if="p.mediaUrls" class="post-images">
            <div v-for="(url, i) in parseMedia(p.mediaUrls)" :key="i" class="post-img-wrap" :class="{ single: parseMedia(p.mediaUrls).length === 1 }">
              <video v-if="isVideo(url)" :src="resolveUrl(url)" class="post-video" controls preload="metadata" />
              <el-image v-else :src="resolveUrl(url)" :preview-src-list="parseMedia(p.mediaUrls).map(u => resolveUrl(u))" fit="cover" />
            </div>
          </div>
          </div>

          <div class="post-actions">
            <span class="post-time">{{ formatTime(p.createdAt) }}</span>
            <div class="post-btns">
              <span class="post-btn" @click="toggleLike(p)">
                {{ p.isLiked ? '❤️' : '🤍' }} <span v-if="p.likes?.length">{{ p.likes.length }}</span>
              </span>
              <span class="post-btn" @click="openComment(p)">💬 {{ p.comments?.length || 0 }}</span>
            </div>
          </div>

          <div v-if="p.likes?.length" class="post-likes">
            ❤️ <span v-for="(l, i) in p.likes" :key="l.userId">{{ resolveNickname(l.userId, l.nickname) }}{{ i < p.likes.length - 1 ? '、' : '' }}</span>
          </div>

          <div v-if="p.comments?.length" class="post-comments">
            <div v-for="(c, ci) in p.comments" :key="ci" class="post-comment">
              <span class="comment-name" @click="replyTo(p, c)">{{ resolveNickname(c.userId, c.nickname) }}</span>
              <span v-if="c.replyToUserId" class="comment-reply"> 回复 {{ resolveNickname(c.replyToUserId, c.replyToNickname) }}</span>
              ：{{ c.content }}
              <el-icon v-if="c.userId === myUserId" class="comment-del" @click="deleteComment(p, ci)"><Close /></el-icon>
            </div>
          </div>

          <div v-if="p._showComment" class="post-comment-input">
            <el-input v-model="p._commentText" :placeholder="p._replyPlaceholder || '评论...'" size="small" @keyup.enter="submitComment(p)" />
          </div>
        </div>

        <div v-if="loading" class="loading-text">加载中...</div>
        <div v-else-if="!hasMore && posts.length" class="loading-text">没有更多了</div>
      </div>

      <!-- 右侧排行榜（全局模式下显示） -->
      <div v-if="!viewUserId && !isSingleMode" class="leaderboard">
        <div class="lb-header">🏆 今日活跃</div>
        <div class="lb-list">
          <div v-for="(u, i) in leaderboard" :key="u.userId" class="lb-item">
            <span class="lb-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
            <el-avatar :src="resolveAvatar(u.userId, u.avatar)" :size="28" shape="square" style="cursor:pointer" @click.stop="showProfile(u.userId)">{{ resolveNickname(u.userId, u.nickname)?.charAt(0) }}</el-avatar>
            <span class="lb-name" style="cursor:pointer" @click.stop="viewUserPostsById(u)">{{ resolveNickname(u.userId, u.nickname) }}</span>
            <span class="lb-score">{{ u.score }}分</span>
          </div>
          <div v-if="!leaderboard.length" class="lb-empty">暂无数据</div>
        </div>
      </div>
    </div>

    <!-- 发布按钮 -->
    <div v-if="!isSingleMode" class="publish-btn" @click="showPublish = true"><el-icon :size="24"><Plus /></el-icon></div>

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

const posts = ref([]), loading = ref(false), listRef = ref(null)
const offset = ref(0), hasMore = ref(true)
const myUserId = ref(0), userInfo = ref({})
const showPublish = ref(false), publishContent = ref(''), publishImages = ref([]), publishImageInput = ref(null)
const leaderboard = ref([])

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

// ===== 模式 =====
const isSingleMode = ref(false)
const viewUserId = ref(null)
const viewUserName = ref('')

const backToAll = () => {
  isSingleMode.value = false
  viewUserId.value = null
  viewUserName.value = ''
  refreshPosts()
}

const openSinglePost = (p) => { isSingleMode.value = true; posts.value = [p] }
const viewUserPosts = (p) => {
  viewUserId.value = p.userId
  viewUserName.value = resolveNickname(p.userId, p.nickname)
  offset.value = 0; posts.value = []; hasMore.value = true
  fetchPosts()
}

const viewUserPostsById = (u) => {
  viewUserId.value = u.userId
  viewUserName.value = resolveNickname(u.userId, u.nickname)
  offset.value = 0; posts.value = []; hasMore.value = true
  fetchPosts()
}
const profileData = ref(null), profileVisible = ref(false)
const showProfile = async (uid) => {
  profileData.value = userCache.get(uid) || { userId: uid, nickname: '', avatarUrl: '' }; profileVisible.value = true
  try { const res = await request.get(`/users/${uid}/profile`); if (res.code === 0) { profileData.value = res.data; userCache.set(uid, res.data) } } catch(e) {}
}

// ===== 通知 =====
const notices = ref([])
const squareNoticeCount = inject('squareNoticeCount', ref(0))
const fetchSquareNotices = inject('fetchSquareNotices', () => {})

const fetchNotices = async () => {
  try {
    const res = await request.get('/square/notifications')
    if (res.code === 0) {
      notices.value = res.data
      const fromIds = [...new Set(res.data.map(n => n.fromUserId).filter(Boolean))]
      if (fromIds.length) userCache.batchFetch(fromIds)
      if (squareNoticeCount.value > 0) markNoticesRead()
    }
  } catch (e) {}
  fetchSquareNotices()
}

const markNoticesRead = async () => {
  try { await request.put('/square/notifications/read'); squareNoticeCount.value = 0 } catch (e) {}
}

const clearAllNotices = async () => {
  try { await request.delete('/square/notifications'); squareNoticeCount.value = 0; notices.value = []; ElMessage.success('消息已清空') } catch (e) {}
}

const handleClickNotice = async (notice) => {
  try {
    const res = await request.get(`/square/posts/${notice.postId}`)
    if (res.code === 0) {
      posts.value = [res.data]; isSingleMode.value = true; markNoticesRead()
      userCache.batchFetch(collectPostUserIds([res.data]))
    }
    else ElMessage.warning('该帖子已被删除')
  } catch (e) {}
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

// ===== 帖子列表 =====
const collectPostUserIds = (list) => {
  const ids = new Set()
  list.forEach(p => {
    if (p.userId) ids.add(p.userId)
    if (p.likes) p.likes.forEach(l => { if (l.userId) ids.add(l.userId) })
    if (p.comments) p.comments.forEach(c => {
      if (c.userId) ids.add(c.userId)
      if (c.replyToUserId) ids.add(c.replyToUserId)
    })
  })
  return [...ids]
}

const fetchPosts = async () => {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const params = { offset: offset.value, limit: 10 }
    if (viewUserId.value) params.filterUserId = viewUserId.value
    const res = await request.get('/square/posts', { params })
    if (res.code === 0 && res.data.length) {
      posts.value.push(...res.data); offset.value += res.data.length
      userCache.batchFetch(collectPostUserIds(res.data))
    }
    else hasMore.value = false
  } catch(e) {} finally { loading.value = false }
}
const isVideo = (url) => /\.(mp4|webm|mov|ogg|avi|mkv)(\?|$)/i.test(url)
const isVideoPreview = (src) => src.startsWith('blob:')
const refreshPosts = () => { offset.value = 0; posts.value = []; hasMore.value = true; fetchPosts(); fetchLeaderboard() }
const onScroll = () => {
  const el = listRef.value
  if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 100 && hasMore.value && !loading.value) fetchPosts()
}

// ===== 排行榜 =====
const fetchLeaderboard = async () => {
  try {
    const res = await request.get('/square/leaderboard')
    if (res.code === 0) {
      leaderboard.value = res.data
      const ids = [...new Set(res.data.map(u => u.userId).filter(Boolean))]
      if (ids.length) userCache.batchFetch(ids)
    }
  } catch(e) {}
}

// ===== 点赞 =====
const toggleLike = async (p) => {
  try { if (p.isLiked) { await request.delete(`/square/posts/${p.id}/like`); p.isLiked = false; p.likes = p.likes.filter(l => l.userId !== myUserId.value) } else { await request.post(`/square/posts/${p.id}/like`); p.isLiked = true; p.likes.push({ userId: myUserId.value, nickname: userInfo.value.nickname }) } } catch(e) {}
}

// ===== 评论 =====
const openComment = (p) => { p._showComment = !p._showComment; p._replyPlaceholder = '评论...'; p._replyToUserId = null; p._replyToNickname = null }
const replyTo = (p, c) => { p._showComment = true; p._replyPlaceholder = `回复 ${c.nickname}`; p._replyToUserId = c.userId; p._replyToNickname = c.nickname }
const submitComment = async (p) => {
  if (!p._commentText) return
  try { await request.post(`/square/posts/${p.id}/comments`, { content: p._commentText, replyToUserId: p._replyToUserId }); p.comments.push({ userId: myUserId.value, nickname: userInfo.value.nickname, content: p._commentText, replyToUserId: p._replyToUserId, replyToNickname: p._replyToNickname }); p._commentText = ''; p._showComment = false } catch(e) { ElMessage.error('评论失败') }
}
const deleteComment = async (p, ci) => {
  try { await request.delete(`/square/posts/${p.id}/comments/${ci}`); p.comments.splice(ci, 1) } catch(e) {}
}
const deletePost = async (p) => {
  try { await request.delete(`/square/posts/${p.id}`); posts.value = posts.value.filter(x => x.id !== p.id) } catch(e) {}
}

// ===== 发布 =====
const triggerPublishImage = () => publishImageInput.value?.click()
const publishFiles = ref([])
const onPublishImages = (e) => {
  Array.from(e.target.files||[]).forEach(f => {
    const isVideo = f.type.startsWith('video/')
    publishFiles.value.push(f)
    if (isVideo) {
      publishImages.value.push(URL.createObjectURL(f))
    } else {
      const reader = new FileReader(); reader.onload = ev => publishImages.value.push(ev.target.result); reader.readAsDataURL(f)
    }
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
    const res = await request.post('/square/posts', { content: publishContent.value, mediaUrls })
    if (res.code === 0) { showPublish.value = false; publishContent.value = ''; publishImages.value = []; publishFiles.value = []; refreshPosts(); fetchLeaderboard() }
  } catch(e) {}
}

// ===== 初始化 =====
const { onMessage: onWsMessage } = useWebSocket()
onWsMessage((msg) => {
  if (msg.action === 'event' && msg.data?.type === 'square_notify') fetchSquareNotices()
})

onMounted(() => {
  const info = JSON.parse(localStorage.getItem('user_info')||'{}')
  userInfo.value = info; myUserId.value = info.userId || 0
  fetchPosts(); fetchLeaderboard(); fetchSquareNotices()
})
</script>

<style scoped>
.square-wrapper { flex:1; display:flex; flex-direction:column; overflow:hidden; background:#fff; position:relative; }

.square-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px; height: 44px; background: #ededed;
  border-bottom: 1px solid #ddd; flex-shrink: 0;
}
.header-left, .header-right { display: flex; align-items: center; gap: 8px; width: 80px; flex-shrink: 0; }
.header-right { justify-content: flex-end; }
.title { font-size: 16px; font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.icon-wrapper { display: flex; align-items: center; justify-content: center; width: 34px; height: 34px; cursor: pointer; border-radius: 6px; transition: background 0.2s; }
.icon-wrapper:hover { background-color: rgba(0,0,0,0.08); }
.header-icon { font-size: 17px; color: #333; }

.square-body { flex:1; display:flex; overflow:hidden; }
.post-list { flex:1; overflow-y:auto; padding:0 16px; }

.post-item { padding:16px 0; border-bottom:1px solid #f0f0f0; }
.post-header-row { display:flex; align-items:center; gap:10px; }
.post-nick { font-size:15px; color:#576b95; font-weight:500; flex:1; cursor:pointer; }
.post-nick:hover { text-decoration:underline; }
.post-delete { cursor:pointer; color:#999; font-size:16px; }
.post-text { margin:8px 0; font-size:15px; line-height:1.5; }
.post-images { display:flex; flex-wrap:wrap; gap:4px; margin:8px 0; }
.post-img-wrap { width:80px; height:80px; overflow:hidden; border-radius:4px; }
.post-img-wrap.single { width:200px; height:auto; }
.post-video { width:100%; max-width:280px; border-radius:6px; }
.post-actions { display:flex; justify-content:space-between; align-items:center; margin-top:8px; }
.post-time { font-size:11px; color:#bbb; }
.post-btns { display:flex; gap:16px; }
.post-btn { cursor:pointer; font-size:13px; color:#576b95; }
.post-likes { font-size:13px; color:#576b95; margin-top:4px; padding:4px 0; }
.post-comments { background:#f7f7f7; border-radius:4px; padding:6px 10px; margin-top:4px; }
.post-comment { font-size:13px; line-height:1.6; }
.comment-name { color:#576b95; cursor:pointer; }
.comment-reply { color:#999; }
.comment-del { cursor:pointer; color:#ccc; margin-left:4px; font-size:12px; }
.post-comment-input { margin-top:6px; }

.leaderboard {
  width: 180px; min-width: 180px; border-left: 1px solid #eee;
  display: flex; flex-direction: column; background: #fafafa;
}
.lb-header { padding: 12px 14px; font-size: 13px; font-weight: 600; color: #333; border-bottom: 1px solid #eee; }
.lb-list { flex:1; overflow-y:auto; padding: 6px 0; }
.lb-item { display:flex; align-items:center; gap:8px; padding:8px 14px; }
.lb-item:hover { background:#f0f0f0; }
.lb-rank { width:20px; text-align:center; font-size:13px; font-weight:600; color:#999; }
.lb-rank.top { color:#ff6b00; }
.lb-name { flex:1; font-size:12px; color:#333; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.lb-score { font-size:11px; color:#999; }
.lb-empty { padding:20px; text-align:center; color:#ccc; font-size:12px; }

.loading-text { text-align:center; padding:20px; color:#999; font-size:13px; }

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

.profile-card-pop { display:flex; flex-direction:column; align-items:center; }
.profile-pop-name { font-size:16px; font-weight:600; color:#333; margin-top:10px; }
.profile-pop-gender { font-size:13px; color:#999; margin-top:2px; }
.profile-pop-sig { font-size:13px; color:#666; margin-top:6px; padding:6px 12px; background:#f5f5f5; border-radius:6px; }
.profile-pop-section { width:100%; margin-top:14px; text-align:left; }
.profile-pop-label { font-size:12px; color:#999; margin-bottom:6px; padding-bottom:4px; border-bottom:1px solid #f0f0f0; }
.profile-pop-row { display:flex; gap:8px; font-size:13px; color:#666; padding:3px 0; }
.profile-pop-desc { font-size:13px; color:#666; line-height:1.6; }
</style>
