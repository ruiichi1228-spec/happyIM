<template>
  <div class="contacts-layout">
    <!-- 左侧列表 -->
    <div class="sidebar">
      <div class="search-bar">
        <el-input v-if="!searchMore" v-model="searchText" placeholder="搜索" clearable size="small">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-if="searchMore" v-model="addSearch" placeholder="查找用户" clearable size="small" @keyup.enter="handleSearchUser">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div v-if="!searchMore" class="add-btn" @click="searchMore = true">+</div>
        <div v-if="searchMore" class="cancel-btn" @click="searchMore = false; activeType = null">取消</div>
      </div>

      <template v-if="searchMore">
        <div class="section-title">查找用户</div>
        <div class="search-result-item sidebar-item" @click="handleSearchUser">
          <el-icon :size="22" color="#07c160"><Search /></el-icon>
          <span>搜索用户</span>
        </div>
      </template>

      <template v-else>
        <div class="sidebar-section">
          <div class="friend-item" :class="{ active: activeType === 'newFriend' }" @click="selectNewFriend">
            <div class="friend-avatar">
              <el-badge :value="pendingCount" :hidden="!pendingCount" :max="99">
                <el-icon :size="36" color="#FF5E00"><svg viewBox="0 0 1024 1024" width="36" height="36"><path d="M804.57 0c121.18 0 219.43 98.25 219.43 219.43v585.14c0 121.18-98.25 219.43-219.43 219.43H219.43C98.25 1024 0 925.75 0 804.57V219.43C0 98.25 98.25 0 219.43 0h585.14z" fill="#FF5E00"/><path d="M493.71 182.86c-101 0-182.86 81.86-182.86 182.86 0 55.86 25.05 105.87 64.51 139.37-146.2 31.63-216.36 129.17-210.47 292.63 0 23.99 14.74 43.43 32.9 43.43h318.83A218.44 218.44 0 01475.43 713.14c0-94.83 60.16-175.62 144.4-206.26-2.56-.6-5.16-1.2-7.77-1.77A182.45 182.45 0 00676.57 365.71c0-100.99-81.86-182.85-182.86-182.85z" fill="#FFF"/></svg></el-icon>
              </el-badge>
            </div>
            <div class="friend-text"><div class="friend-name">新的朋友</div></div>
          </div>
        </div>

        <div class="sidebar-section">
          <div class="section-header">
            <span>我的好友</span>
            <span class="section-count">{{ friends.length }}</span>
          </div>
          <div v-for="f in filteredFriends" :key="f.userId" class="friend-item" @click="selectFriend(f)" :class="{ active: activeType === 'friend' && activeItem?.userId === f.userId }">
            <el-avatar :src="f.avatarUrl" :size="36" shape="square">{{ f.nickname?.charAt(0) }}</el-avatar>
            <div class="friend-text">
              <div class="friend-name">{{ displayName(f) }}</div>
              <div class="friend-sub" v-if="f.remark">备注: {{ f.remark }}</div>
            </div>
          </div>
          <div v-if="filteredFriends.length === 0" class="empty-hint">暂无好友</div>
        </div>

        <div class="sidebar-section">
          <div class="section-header">
            <span>我的群聊</span>
            <span class="section-count">{{ groups.length }}</span>
          </div>
          <div v-for="g in groups" :key="g.groupId" class="friend-item" @click="selectGroup(g)" :class="{ active: activeType === 'group' && activeItem?.groupId === g.groupId }">
            <el-avatar :src="g.avatarUrl" :size="36" shape="square">{{ g.name?.charAt(0) }}</el-avatar>
            <div class="friend-text">
              <div class="friend-name">{{ g.name }}</div>
              <div class="friend-sub">{{ g.memberCount }} 人</div>
            </div>
          </div>
          <div v-if="groups.length === 0" class="empty-hint">暂无群聊</div>
        </div>
      </template>
    </div>

    <!-- 右侧面板 -->
    <div class="main-panel">
      <div v-if="!activeType && !searchMore" class="placeholder">
        <el-icon :size="60" color="#ddd"><ChatDotRound /></el-icon>
        <span>选择一位好友或群聊查看详情</span>
      </div>

      <!-- 搜索用户 -->
      <div v-else-if="searchMore" class="detail-panel">
        <div class="detail-title">查找用户</div>
        <div v-for="u in searchResults" :key="u.userId" class="search-item">
          <el-avatar :src="u.avatarUrl" :size="44" shape="square">{{ u.nickname?.charAt(0) }}</el-avatar>
          <div class="search-info">
            <div class="search-name">{{ u.nickname || u.username }}</div>
            <div class="search-sub">账号: {{ u.username }}</div>
          </div>
          <el-button v-if="u.isFriend" type="info" size="small" disabled>已是好友</el-button>
          <el-button v-else type="primary" size="small" @click="openRequestDialog(u)">添加</el-button>
        </div>
        <div v-if="searchResults.length === 0 && addSearch" class="empty-hint">未找到用户</div>
      </div>

      <!-- 新的朋友 -->
      <div v-else-if="activeType === 'newFriend'" class="detail-panel">
        <div class="detail-title">新的朋友</div>
        <div v-for="req in friendRequests" :key="req.id" class="request-card">
          <el-avatar :src="req.fromAvatarUrl" :size="44" shape="square">{{ req.fromNickname?.charAt(0) }}</el-avatar>
          <div class="request-info">
            <div class="request-name">{{ req.fromNickname || req.fromUsername }}</div>
            <div class="request-sub" v-if="req.message">"{{ req.message }}"</div>
            <div class="request-sub">{{ req.createdTime }}</div>
          </div>
          <div class="request-actions" v-if="req.status === 0">
            <el-button type="primary" size="small" @click="openAcceptDialog(req)">接受</el-button>
            <el-button size="small" @click="friendRequests = friendRequests.filter(r => r.id !== req.id)">忽略</el-button>
          </div>
          <el-tag v-else type="success" size="small">已添加</el-tag>
        </div>
        <div v-if="friendRequests.length === 0" class="empty-hint">暂无好友请求</div>
      </div>

      <!-- 好友详情 -->
      <div v-else-if="activeType === 'friend' && activeItem" class="detail-panel">
        <div class="detail-card">
          <div class="detail-card-top">
            <el-avatar :src="activeItem.avatarUrl" :size="72" shape="square">{{ activeItem.nickname?.charAt(0) }}</el-avatar>
            <div class="detail-card-info">
              <div class="detail-card-name">{{ displayName(activeItem) }}</div>
              <div class="detail-card-sub" v-if="friendDetail?.gender">性别: {{ friendDetail.gender == 1 ? '男' : friendDetail.gender == 2 ? '女' : '' }}</div>
              <div class="detail-card-sub">昵称: {{ activeItem.nickname }}</div>
              <div class="detail-card-sub">账号: {{ activeItem.username }}</div>
              <div class="detail-card-sub" v-if="friendDetail?.signature">签名: {{ friendDetail.signature }}</div>
            </div>
          </div>
          <div class="detail-card-desc" v-if="friendDetail?.description">{{ friendDetail.description }}</div>
        </div>

        <div class="info-rows">
          <div class="info-row">
            <span class="info-label">备注名</span>
            <span class="info-val" v-if="!editingRemark">{{ activeItem.remark || '未设置' }}</span>
            <el-input v-else v-model="remarkText" size="small" style="width:160px" @blur="saveRemark" @keyup.enter="saveRemark" ref="remarkInput" />
            <el-button link type="primary" size="small" @click="startEditRemark">{{ editingRemark ? '' : '修改' }}</el-button>
          </div>
          <div class="info-row">
            <span class="info-label">星标好友</span>
            <el-switch :model-value="activeItem.isStarred" size="small" @change="toggleStar(activeItem)" />
          </div>
          <div v-if="friendDetail" class="info-row"><span class="info-label">ID</span><span class="info-val">{{ friendDetail.id }}</span></div>
          <div v-if="friendDetail" class="info-row"><span class="info-label">邮箱</span><span class="info-val">{{ friendDetail.email }}</span></div>
        </div>

        <div class="detail-actions">
          <div class="detail-action-btn" @click="createPrivateChat(activeItem)"><el-icon :size="22"><ChatDotRound /></el-icon><span>发消息</span></div>
          <div class="detail-action-btn danger" @click="confirmDeleteFriend(activeItem)"><el-icon :size="22"><Delete /></el-icon><span>删除好友</span></div>
        </div>
      </div>

      <!-- 群聊详情 -->
      <div v-else-if="activeType === 'group' && groupDetail" class="detail-panel">
        <div class="detail-card">
          <div class="detail-card-top">
            <el-avatar :src="groupDetail.avatarUrl" :size="72" shape="square">{{ groupDetail.name?.charAt(0) }}</el-avatar>
            <div class="detail-card-info">
              <div class="detail-card-name">{{ groupDetail.name }}</div>
              <div class="detail-card-sub">群主: {{ ownerName }}</div>
              <div class="detail-card-sub">{{ groupDetail.memberCount || groupDetail.members?.length }} 名成员</div>
            </div>
          </div>
        </div>

        <div class="info-rows">
          <div class="info-row">
            <span class="info-label">群简介</span>
            <span class="info-val">{{ groupDetail.description || '暂无' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">群公告</span>
            <span class="info-val">{{ groupDetail.notice || '暂无' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">邀请权限</span>
            <span class="info-val">{{ groupDetail.allowInvite ? '所有人可邀请' : '仅管理员' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">我的群昵称</span>
            <el-input v-model="myGroupNickname" size="small" style="width:160px" placeholder="设置群昵称" @blur="saveMyGroupNick" @keyup.enter="saveMyGroupNick" />
          </div>
        </div>

        <div class="member-section">
          <div class="section-header">群成员 ({{ groupDetail.members?.length || 0 }})</div>
          <div class="member-grid">
            <div v-for="m in groupDetail.members" :key="m.userId" class="member-card" @click="viewMemberProfile(m)" @contextmenu.prevent="openManageMenu($event, m)">
              <el-avatar :src="m.avatarUrl" :size="44" shape="square">{{ m.nickname?.charAt(0) }}</el-avatar>
              <div class="member-card-name">{{ m.groupNickname || m.nickname }}</div>
              <span class="member-role-tag" v-if="m.role === 1">群主</span>
              <span class="member-role-tag admin" v-else-if="m.role === 2">管理</span>
              <div class="member-card-sub" v-if="m.groupNickname && m.nickname">{{ m.nickname }}</div>
            </div>
          </div>
        </div>

        <div class="detail-actions">
          <div class="detail-action-btn" @click="gotoGroupChat(activeItem)"><el-icon :size="22"><ChatDotRound /></el-icon><span>发消息</span></div>
          <div class="detail-action-btn" @click="inviteMembersVisible = true"><el-icon :size="22"><Plus /></el-icon><span>邀请</span></div>
          <div v-if="groupDetail.myRole === 1" class="detail-action-btn" @click="transferVisible = true"><el-icon :size="22"><Switch /></el-icon><span>转让</span></div>
          <div class="detail-action-btn danger" @click="leaveOrDissolve"><el-icon :size="22"><Delete /></el-icon><span>{{ groupDetail.myRole === 1 ? '解散群' : '退出群' }}</span></div>
        </div>
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="inviteMembersVisible" title="邀请成员" width="560px">
      <div class="invite-layout">
        <div class="invite-left">
          <el-input v-model="inviteSearch" placeholder="搜索好友" size="small" />
          <div class="invite-list">
            <div v-for="f in inviteCandidates" :key="f.userId" class="invite-friend"
              :class="{ picked: inviteIds.includes(f.userId) }"
              @click="toggleInvite(f.userId)">
              <el-avatar :src="f.avatarUrl" :size="32" shape="square" /> {{ displayName(f) }}
              <el-icon v-if="inviteIds.includes(f.userId)" color="#07c160"><Check /></el-icon>
            </div>
            <div v-if="inviteCandidates.length === 0" class="invite-empty">暂无好友可邀请</div>
          </div>
        </div>
        <div class="invite-right">
          <div class="invite-right-title">已选择 ({{ inviteIds.length }})</div>
          <div class="invite-list">
            <div v-for="f in pickedForInvite" :key="f.userId" class="invite-picked">
              <el-avatar :src="f.avatarUrl" :size="28" shape="square" /> {{ displayName(f) }}
              <el-icon class="invite-remove" @click="inviteIds = inviteIds.filter(id => id !== f.userId)"><Close /></el-icon>
            </div>
            <div v-if="inviteIds.length === 0" class="invite-empty">请从左侧选择成员</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="inviteMembersVisible = false">取消</el-button>
        <el-button type="primary" :disabled="inviteIds.length === 0" @click="handleInviteMembers">确定 ({{ inviteIds.length }})</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="requestDialogVisible" title="添加好友" width="400px">
      <el-input v-model="requestForm.message" type="textarea" :rows="4" placeholder="你好，我想加你为好友" />
      <template #footer><el-button @click="requestDialogVisible = false">取消</el-button><el-button type="primary" @click="handleSendRequest">确定</el-button></template>
    </el-dialog>
    <el-dialog v-model="acceptDialogVisible" title="通过验证" width="400px">
      <el-form :model="acceptForm" label-width="60px"><el-form-item label="备注"><el-input v-model="acceptForm.remark" placeholder="设置备注名（可选）" /></el-form-item></el-form>
      <template #footer><el-button @click="acceptDialogVisible = false">取消</el-button><el-button type="primary" @click="handleAcceptRequest">确定</el-button></template>
    </el-dialog>

    <!-- 管理成员右键菜单 -->
    <div v-if="manageVisible" class="manage-popover" :style="{ top: manageY + 'px', left: manageX + 'px' }">
      <div class="manage-title">{{ manageMember?.groupNickname || manageMember?.nickname }}</div>
      <div v-if="groupDetail?.myRole <= 2 && manageMember?.role !== 1 && manageMember?.userId !== myUserId()" class="manage-item" @click="setGroupRole(manageMember, manageMember.role === 2 ? 3 : 2); manageVisible = false">
        {{ manageMember?.role === 2 ? '取消管理员' : '设为管理员' }}
      </div>
      <div v-if="groupDetail?.myRole <= 2 && manageMember?.role !== 1" class="manage-item danger" @click="removeGroupMember(manageMember); manageVisible = false">移出群聊</div>
      <div v-if="groupDetail?.myRole === 1 && manageMember?.role !== 1" class="manage-item" @click="openTransfer(manageMember); manageVisible = false">转让群主</div>
      <div class="manage-item" @click="manageVisible = false">取消</div>
    </div>
    <div v-if="manageVisible" class="manage-overlay" @click="manageVisible = false"></div>

    <!-- 转让弹窗 -->
    <el-dialog v-model="transferVisible" title="转让群主" width="380px" align-center>
      <div>是否将群「{{ groupDetail?.name }}」转让给 {{ transferForm.nickname }}？</div>
      <div class="transfer-warn">转让后你将失去群主权限</div>
      <el-input v-model="transferForm.reason" size="small" placeholder="转让说明（可选）" style="margin-top:12px" />
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmTransfer">确认转让</el-button>
      </template>
    </el-dialog>

    <!-- 成员名片弹窗（复用个人信息卡） -->
    <el-dialog v-model="memberProfileVisible" :title="memberDetail?.nickname || '名片'" width="400px" align-center destroy-on-close>
      <div class="profile-card" v-if="memberDetail">
        <el-avatar :src="memberDetail.avatarUrl" :size="64" shape="square">{{ memberDetail.nickname?.charAt(0) }}</el-avatar>
        <div class="profile-name">{{ memberDetail.groupNickname || memberDetail.nickname }}</div>
        <div class="profile-gender">{{ memberDetail.gender == 1 ? '♂' : memberDetail.gender == 2 ? '♀' : '' }}</div>
        <div class="profile-signature" v-if="memberDetail.signature">{{ memberDetail.signature }}</div>
        <div class="profile-section">
          <div class="profile-label">基本信息</div>
          <div class="profile-row"><span>ID号：</span><span>{{ memberDetail.id }}</span></div>
          <div class="profile-row"><span>邮箱：</span><span>{{ memberDetail.email }}</span></div>
          <div class="profile-row"><span>注册时间：</span><span>{{ memberDetail.createdTime }}</span></div>
          <div class="profile-row"><span>最近登陆：</span><span>{{ memberDetail.lastLoginTime }}</span></div>
          <div class="profile-row"><span>最近IP：</span><span>{{ memberDetail.lastLoginIp }}</span></div>
          <div class="profile-row" v-if="memberProfile?.groupNickname"><span>群昵称：</span><span>{{ memberProfile.groupNickname }}</span></div>
          <div class="profile-row" v-if="memberProfile?.nickname !== memberDetail.nickname"><span>原名：</span><span>{{ memberProfile.nickname }}</span></div>
        </div>
        <div class="profile-section" v-if="memberDetail.description">
          <div class="profile-label">其它说明</div>
          <div class="profile-desc">{{ memberDetail.description }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserCache } from '@/utils/userCache'
import { useGroupCache } from '@/utils/groupCache'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ChatDotRound, Delete, Plus, Switch, Check, Close } from '@element-plus/icons-vue'

const router = useRouter()
const userCache = useUserCache()
const groupCache = useGroupCache()
const groupInfo = groupCache.groups

const searchText = ref(''), searchMore = ref(false), addSearch = ref('')
const activeType = ref(null), activeItem = ref(null)
const friends = ref([]), groups = ref([]), friendRequests = ref([]), pendingCount = ref(0), searchResults = ref([])
const groupDetail = ref(null)
const inviteMembersVisible = ref(false), inviteSearch = ref(''), inviteIds = ref([])
const toggleInvite = (uid) => { const idx = inviteIds.value.indexOf(uid); if (idx >= 0) inviteIds.value.splice(idx, 1); else inviteIds.value.push(uid) }
const pickedForInvite = computed(() => friends.value.filter(f => inviteIds.value.includes(f.userId)))
const requestDialogVisible = ref(false), acceptDialogVisible = ref(false)
const requestForm = ref({ toUserId: null, message: '' }), acceptForm = ref({ requestId: null, remark: '' })
const myGroupNickname = ref('')
const editingRemark = ref(false), remarkText = ref(''), remarkInput = ref(null)
const memberProfileVisible = ref(false), memberProfile = ref(null), memberDetail = ref(null)
const friendDetail = ref(null)
const manageVisible = ref(false), manageMember = ref(null), manageX = ref(0), manageY = ref(0)
const transferVisible = ref(false), transferForm = ref({ userId: null, nickname: '', reason: '' })

const openManageMenu = (e, m) => {
  if (m.userId === myUserId() || (groupDetail.value?.myRole > 2 && m.role !== 1)) return
  manageMember.value = m; manageX.value = e.clientX; manageY.value = e.clientY; manageVisible.value = true
}
const setGroupRole = async (m, role) => {
  try { await request.put(`/groups/${activeItem.value.groupId}/members/${m.userId}/role`, { role }); ElMessage.success('角色已更新'); selectGroup(activeItem.value) } catch(e) { ElMessage.error('操作失败') }
}
const removeGroupMember = async (m) => {
  try { await request.delete(`/groups/${activeItem.value.groupId}/members/${m.userId}`); ElMessage.success('已移除'); selectGroup(activeItem.value) } catch(e) { ElMessage.error('操作失败') }
}
const openTransfer = (m) => { transferForm.value = { userId: m.userId, nickname: m.groupNickname || m.nickname, reason: '' }; transferVisible.value = true; manageVisible.value = false }
const confirmTransfer = async () => {
  try {
    await request.put(`/groups/${activeItem.value.groupId}/members/${transferForm.value.userId}/role`, { role: 1 })
    ElMessage.success('群主已转让'); transferVisible.value = false; selectGroup(activeItem.value)
  } catch(e) { ElMessage.error('操作失败') }
}

const filteredFriends = computed(() => {
  if (!searchText.value) return friends.value
  const kw = searchText.value.toLowerCase()
  return friends.value.filter(f => (f.nickname||'').toLowerCase().includes(kw) || (f.remark||'').toLowerCase().includes(kw) || (f.username||'').toLowerCase().includes(kw))
})
const inviteCandidates = computed(() => {
  const memberIds = groupDetail.value?.members?.map(m => m.userId) || []
  const list = friends.value.filter(f => !memberIds.includes(f.userId))
  if (!inviteSearch.value) return list
  const kw = inviteSearch.value.toLowerCase()
  return list.filter(f => (f.nickname||'').toLowerCase().includes(kw) || (f.username||'').toLowerCase().includes(kw))
})
const displayName = (f) => f.remark || f.nickname || f.username
const ownerName = computed(() => {
  if (!groupDetail.value) return ''
  const owner = groupDetail.value.members?.find(m => m.userId === groupDetail.value.ownerId || m.role === 1)
  return owner?.nickname || owner?.groupNickname || owner?.username || groupDetail.value.ownerId || ''
})
const friendGender = computed(() => {
  if (!activeItem.value) return ''
  const uid = activeItem.value.userId
  const g = userCache.get(uid)?.gender
  return g === 1 ? '男' : g === 2 ? '女' : ''
})
const friendSignature = computed(() => {
  return userCache.get(activeItem.value?.userId)?.signature || ''
})

// ===== 数据 =====
const fetchFriends = async () => { try { const res = await request.get('/friends'); if (res.code === 0) { friends.value = res.data; userCache.setAll(res.data) } } catch (e) {} }
const fetchRequests = async () => { try { const res = await request.get('/friends/requests'); if (res.code === 0) { friendRequests.value = res.data.list || []; pendingCount.value = res.data.pendingCount || 0 } } catch (e) {} }
const fetchGroups = async () => { try { const res = await request.get('/groups'); if (res.code === 0) { const list = res.data; const gids = list.map(g => g.groupId).filter(Boolean); await groupCache.batchFetch(gids); list.forEach(g => { const info = groupInfo[g.groupId]; if (info) { g.name = info.name; g.avatarUrl = info.avatarUrl; g.memberCount = info.memberCount } }); groups.value = list } } catch (e) {} }

const createPrivateChat = async (friend) => {
  try { const res = await request.post('/conversations/private', { peerId: friend.userId }); if (res.code === 0) router.push('/chat') } catch (e) { ElMessage.error('创建会话失败') }
}
const selectNewFriend = () => { activeType.value = 'newFriend'; activeItem.value = null; searchMore.value = false; fetchRequests() }
const selectFriend = async (f) => {
  activeType.value = 'friend'; activeItem.value = { ...f }; searchMore.value = false; editingRemark.value = false
  friendDetail.value = null
  userCache.batchFetch([f.userId])
  try { const res = await request.get(`/users/${f.userId}/profile`); if (res.code === 0) { friendDetail.value = res.data; userCache.set(f.userId, res.data) } } catch(e) {}
}
const selectGroup = async (g) => {
  activeType.value = 'group'; activeItem.value = g; searchMore.value = false
  try {
    const res = await request.get(`/groups/${g.groupId}`)
    if (res.code === 0) {
      groupDetail.value = res.data
      // 从 cache 补 name/avatar/memberCount
      const gi = groupInfo[g.groupId]
      if (gi) { groupDetail.value.name = gi.name; groupDetail.value.avatarUrl = gi.avatarUrl; groupDetail.value.memberCount = gi.memberCount }
      userCache.setAll(res.data.members)
      const me = res.data.members?.find(m => m.userId === myUserId())
      myGroupNickname.value = me?.groupNickname || ''
    }
  } catch (e) {}
}
const handleSearchUser = async () => {
  if (!addSearch.value) return
  try { const res = await request.get('/users/search', { params: { keyword: addSearch.value } }); if (res.code === 0) searchResults.value = res.data } catch (e) {}
}

// ===== 群操作 =====
const saveMyGroupNick = async () => {
  try { await request.put(`/groups/${groupDetail.value.groupId}/members/me/nickname`, { groupNickname: myGroupNickname.value }); ElMessage.success('已更新') } catch(e) { ElMessage.error('更新失败') }
}
const handleInviteMembers = async () => {
  if (!inviteIds.value.length) { ElMessage.warning('请选择好友'); return }
  try { await request.post(`/groups/${activeItem.value.groupId}/members`, { userIds: inviteIds.value }); inviteMembersVisible.value = false; inviteIds.value = []; selectGroup(activeItem.value); ElMessage.success('已邀请') } catch (e) { ElMessage.error('邀请失败') }
}
const leaveOrDissolve = () => {
  const isOwner = groupDetail.value?.myRole === 1
  ElMessageBox.confirm(isOwner ? '确定解散该群？' : '确定退出群聊？', '提示', { type: 'warning' }).then(async () => {
    try {
      await request.post(`/groups/${activeItem.value.groupId}/${isOwner ? 'dissolve' : 'leave'}`)
      ElMessage.success(isOwner ? '群已解散' : '已退出')
      activeType.value = null; activeItem.value = null; fetchGroups()
    } catch (e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}
const gotoGroupChat = async (g) => {
  try { await request.post(`/conversations/group/${g.groupId}`); router.push('/chat') } catch(e) { router.push('/chat') }
}
const viewMemberProfile = async (m) => {
  memberProfile.value = m; memberDetail.value = null; memberProfileVisible.value = true
  // 优先用缓存，异步拉完整资料
  const cached = userCache.get(m.userId)
  if (cached && cached.email) { memberDetail.value = { ...cached, id: cached.userId, groupNickname: m.groupNickname }; return }
  if (cached) memberDetail.value = { ...cached, id: cached.userId, groupNickname: m.groupNickname, nickname: m.nickname }
  try {
    const res = await request.get(`/users/${m.userId}/profile`)
    if (res.code === 0) { memberDetail.value = { ...res.data, groupNickname: m.groupNickname }; userCache.set(m.userId, res.data) }
  } catch(e) {}
}

// ===== 好友操作 =====
const confirmDeleteFriend = (friend) => {
  ElMessageBox.confirm(`确定删除好友"${displayName(friend)}"？`, '删除好友', { type: 'warning' }).then(async () => {
    try { await request.delete(`/friends/${friend.userId}`); friends.value = friends.value.filter(f => f.userId !== friend.userId); activeType.value = null; activeItem.value = null; ElMessage.success('已删除') } catch (e) { ElMessage.error('删除失败') }
  }).catch(() => {})
}
const toggleStar = async (friend) => {
  const v = !friend.isStarred
  try { await request.put(`/friends/${friend.userId}/star`, { starred: v }); friend.isStarred = v; activeItem.value.isStarred = v; ElMessage.success(v ? '已设置星标' : '已取消星标') } catch (e) { ElMessage.error('操作失败') }
}
const startEditRemark = () => { editingRemark.value = true; remarkText.value = activeItem.value?.remark || ''; nextTick(() => remarkInput.value?.focus()) }
const saveRemark = async () => {
  editingRemark.value = false
  const val = remarkText.value.trim()
  if (val === (activeItem.value?.remark || '')) return
  try { await request.put(`/friends/${activeItem.value.userId}/remark`, { remark: val }); activeItem.value.remark = val || null; const f = friends.value.find(x => x.userId === activeItem.value.userId); if (f) f.remark = val || null; userCache.set(activeItem.value.userId, { remark: val }); ElMessage.success('备注已更新') } catch(e) { ElMessage.error('更新失败') }
}
const openRequestDialog = (u) => { requestForm.value = { toUserId: u.userId, message: '' }; requestDialogVisible.value = true }
const handleSendRequest = async () => {
  if (!requestForm.value.message) { ElMessage.warning('请输入请求信息'); return }
  try { await request.post('/friends/request', { toUserId: requestForm.value.toUserId, message: requestForm.value.message }); ElMessage.success('已发送'); requestDialogVisible.value = false } catch (e) { ElMessage.error(e.response?.data?.message || '发送失败') }
}
const openAcceptDialog = (req) => { acceptForm.value = { requestId: req.id, remark: '' }; acceptDialogVisible.value = true }
const handleAcceptRequest = async () => {
  try { await request.post(`/friends/requests/${acceptForm.value.requestId}/accept`); ElMessage.success('已添加'); acceptDialogVisible.value = false; fetchRequests(); fetchFriends() } catch (e) { ElMessage.error('操作失败') }
}
const myUserId = () => { try { return JSON.parse(localStorage.getItem('user_info')||'{}').userId || 0 } catch(e) { return 0 } }

onMounted(() => { fetchFriends(); fetchRequests(); fetchGroups() })
</script>

<style scoped>
.contacts-layout { flex:1; display:flex; overflow:hidden; }

/* 左侧 */
.sidebar { width:260px; min-width:260px; display:flex; flex-direction:column; border-right:1px solid #e2e2e2; background:#f5f5f5; }
.search-bar { display:flex; align-items:center; height:56px; padding:0 10px; gap:8px; }
.add-btn, .cancel-btn { height:30px; min-width:30px; background:#e0e0e0; border-radius:6px; display:flex; align-items:center; justify-content:center; cursor:pointer; font-size:18px; color:#333; flex-shrink:0; }
.cancel-btn { min-width:auto; padding:0 8px; font-size:13px; }
.sidebar-section { padding-bottom:4px; }
.section-header { display:flex; justify-content:space-between; align-items:center; padding:14px 16px 8px; font-size:13px; color:#888; }
.section-count { font-size:11px; background:#ddd; padding:1px 8px; border-radius:10px; color:#666; }
.friend-item { display:flex; align-items:center; padding:8px 12px; margin:1px 8px; border-radius:8px; cursor:pointer; gap:10px; transition:all 0.15s; }
.friend-item:hover { background:rgba(0,0,0,0.04); }
.friend-item.active { background:rgba(7,193,96,0.08); }
.friend-text { flex:1; min-width:0; }
.friend-name { font-size:14px; color:#333; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.friend-sub { font-size:11px; color:#999; margin-top:1px; }
.friend-avatar { width:36px; height:36px; display:flex; align-items:center; justify-content:center; }
.empty-hint { text-align:center; padding:20px; color:#ccc; font-size:12px; }
.section-title { padding:12px 16px 4px; font-size:13px; color:#888; }
.sidebar-item { display:flex; align-items:center; gap:10px; padding:10px 16px; cursor:pointer; font-size:14px; color:#333; }
.sidebar-item:hover { background:#ebebeb; }

/* 右侧 */
.main-panel { flex:1; background:#f7f7f7; overflow-y:auto; }
.placeholder { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; color:#ccc; font-size:14px; gap:12px; }

.detail-panel { padding:24px; max-width:600px;margin:0 auto; }
.detail-title { font-size:20px; font-weight:600; margin-bottom:16px; }

/* 好友/群资料卡 */
.detail-card { background:#fff; border-radius:14px; padding:20px; margin-bottom:16px; box-shadow:0 1px 4px rgba(0,0,0,0.04); }
.detail-card-top { display:flex; align-items:center; gap:16px; }
.detail-card-info { flex:1; }
.detail-card-name { font-size:20px; font-weight:600; }
.detail-card-sub { font-size:13px; color:#888; margin-top:4px; }

/* 信息行 */
.info-rows { background:#fff; border-radius:14px; padding:4px 20px; margin-bottom:16px; box-shadow:0 1px 4px rgba(0,0,0,0.04); }
.info-row { display:flex; align-items:center; padding:12px 0; border-bottom:1px solid #f5f5f5; gap:12px; font-size:14px; }
.info-row:last-child { border-bottom:none; }
.info-label { color:#999; width:70px; flex-shrink:0; }
.info-val { color:#333; }

/* 操作按钮 */
.detail-actions { display:flex; justify-content:center; gap:32px; margin-top:8px; }
.detail-action-btn { display:flex; flex-direction:column; align-items:center; gap:4px; font-size:11px; color:#576b95; cursor:pointer; }
.detail-action-btn:hover { opacity:0.8; }
.detail-action-btn.danger { color:#fa5151; }

/* 成员网格 */
.member-section { background:#fff; border-radius:14px; padding:16px 20px; margin-bottom:16px; box-shadow:0 1px 4px rgba(0,0,0,0.04); }
.member-section .section-header { padding:0 0 12px; }
.member-grid { display:flex; flex-wrap:wrap; gap:12px; }
.member-card { display:flex; flex-direction:column; align-items:center; gap:4px; cursor:pointer; width:72px; padding:4px; border-radius:8px; transition:background 0.15s; }
.member-card:hover { background:#f5f5f5; }
.member-card-name { font-size:12px; color:#333; text-align:center; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.member-card-sub { font-size:10px; color:#aaa; text-align:center; }
.member-role-tag { font-size:10px; background:#07c160; color:#fff; padding:0 4px; border-radius:3px; }
.member-role-tag.admin { background:#1989fa; }

/* 请求 */
.request-card { display:flex; align-items:center; padding:12px 0; border-bottom:1px solid #f0f0f0; gap:12px; }
.request-info { flex:1; }
.request-name { font-size:15px; font-weight:500; }
.request-sub { font-size:12px; color:#999; margin-top:2px; }
.request-actions { display:flex; gap:6px; }

/* 搜索 */
.search-item { display:flex; align-items:center; padding:12px 0; border-bottom:1px solid #f0f0f0; gap:12px; }
.search-info { flex:1; }
.search-name { font-size:15px; font-weight:500; }
.search-sub { font-size:12px; color:#999; margin-top:2px; }

/* 弹窗 */
.invite-list { max-height:300px; overflow-y:auto; }
.invite-item { padding:3px 0; }
.invite-item :deep(.el-checkbox__label) { display:flex; align-items:center; gap:6px; }
.profile-card { display:flex; flex-direction:column; align-items:center; padding:8px 0; }
.profile-name { font-size:18px; font-weight:600; margin-top:8px; }
.profile-gender { font-size:14px; color:#666; margin-top:2px; }
.profile-signature { font-size:12px; color:#999; margin-top:6px; text-align:center; padding:0 8px; }
.profile-section { width:100%; margin-top:12px; padding-top:10px; border-top:1px solid #f0f0f0; }
.profile-label { font-size:12px; color:#999; margin-bottom:4px; }
.profile-row { display:flex; font-size:12px; line-height:1.8; }
.profile-row span:first-child { color:#999; width:70px; flex-shrink:0; }
.profile-row span:last-child { color:#333; word-break:break-all; }
.detail-card-desc { font-size:13px; color:#555; line-height:1.6; margin-top:12px; padding-top:12px; border-top:1px solid #f0f0f0; }

/* 管理菜单 */
.manage-overlay { position:fixed; inset:0; z-index:999; }
.manage-popover { position:fixed; z-index:1000; background:#fff; border-radius:8px; box-shadow:0 4px 20px rgba(0,0,0,0.15); min-width:150px; overflow:hidden; }
.manage-title { padding:10px 16px; font-size:13px; color:#999; border-bottom:1px solid #f0f0f0; }
.manage-item { padding:10px 16px; font-size:14px; color:#333; cursor:pointer; }
.manage-item:hover { background:#f5f5f5; }
.manage-item.danger { color:#fa5151; }
.transfer-warn { font-size:13px; color:#fa5151; margin-top:8px; }
.invite-layout { display:flex; gap:16px; height:380px; margin:8px 0; }
.invite-left, .invite-right { flex:1; display:flex; flex-direction:column; }
.invite-left { padding-right:8px; }
.invite-left .el-input { margin-bottom:8px; }
.invite-right-title { padding:0 0 10px; font-size:14px; font-weight:600; color:#333; }
.invite-list { flex:1; overflow-y:auto; }
.invite-friend { display:flex; align-items:center; gap:10px; padding:10px 12px; border-radius:8px; cursor:pointer; font-size:14px; transition:all 0.12s; }
.invite-friend:hover { background:#f5f5f5; }
.invite-friend.picked { background:#e8f4e8; color:#07c160; }
.invite-picked { display:flex; align-items:center; gap:10px; padding:10px 12px; border-radius:8px; font-size:14px; transition:background 0.12s; }
.invite-picked:hover { background:#fef0f0; }
.invite-remove { cursor:pointer; color:#bbb; margin-left:auto; }
.invite-remove:hover { color:#fa5151; }
.invite-empty { text-align:center; padding:40px; color:#ccc; font-size:13px; }
</style>
