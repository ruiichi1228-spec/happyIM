<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <div class="session-list">
      <div class="search-bar">
        <el-input v-model="searchText" placeholder="搜索" size="small" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-icon class="plus" @click="openChatCreator"><Plus /></el-icon>
      </div>
      <div class="sessions">
        <div v-for="s in filteredSessions" :key="s.conversationId"
          class="session-item" :class="{ active: activeSession?.conversationId === s.conversationId }"
          @click="selectSession(s)" @contextmenu.prevent="openSessionMenu($event, s)">
          <el-badge v-if="s.unreadCount" :value="s.unreadCount" :max="99">
            <el-avatar shape="square" :src="s.peerAvatar" :size="40">{{ s.peerName?.charAt(0) }}</el-avatar>
          </el-badge>
          <el-avatar v-else shape="square" :src="s.peerAvatar" :size="40">{{ s.peerName?.charAt(0) }}</el-avatar>
          <div class="session-info">
            <div class="session-top">
              <div class="session-name-wrap">
                <span v-if="s.type == 1" class="group-badge">群</span>
                <span class="session-name">
                  <span v-if="s.type == 0 && userCache.get(s.peerId)?.isStarred" class="star-icon">⭐</span>
                  {{ s.type == 0 ? (userCache.get(s.peerId)?.remark || s.peerName) : s.peerName }}
                </span>
              </div>
              <span class="session-time">{{ formatTime(s.lastMsgTime) }}</span>
            </div>
            <div class="session-preview">
              <span class="msg-type-tag" :class="'tag-'+tagType(s.lastMsgType)">{{ tagLabel(s.lastMsgType) }}</span>
              {{ s.lastMsgContent || '暂无消息' }}
            </div>
          </div>
        </div>
        <div v-if="sessions.length === 0" class="empty-state">
          <el-empty :image-size="60" description="暂无会话" />
        </div>
      </div>
    </div>

    <!-- 聊天窗口 -->
    <div class="chat-main">
      <template v-if="activeSession">
        <div class="chat-header">
          <span class="chat-name">{{ activeSession.peerName }}<span v-if="activeSession.type == 1" class="chat-group-tag">群聊 ({{ activeSession.memberCount || 0 }}人)</span></span>
          <div class="header-actions">
            <el-icon class="more-icon" @click="historyVisible = true"><Clock /></el-icon>
            <el-icon class="more-icon" @click="drawerOpen = !drawerOpen"><MoreFilled /></el-icon>
          </div>
        </div>
        <div class="chat-messages" ref="msgListRef" @scroll="handleScroll">
          <div v-if="loadingHistory" class="loading-tip">加载中...</div>
          <div v-else-if="!hasMore" class="loading-tip">没有更多消息了</div>

          <template v-for="(msg, idx) in messages" :key="msg.messageId || idx">
            <div v-if="showTimeDivider(idx)" class="time-divider">
              <span>{{ formatMsgTime(messages[idx].createdAt) }}</span>
            </div>
            <div v-if="msg.messageType === 'system' || msg.messageType === 'announcement'" class="system-msg">
              <template v-if="msg.messageType === 'announcement'">
                <div class="announcement-card">
                  <el-icon :size="16"><Bell /></el-icon>
                  <span style="font-size:13px;color:#333;font-weight:500">群公告</span>
                  <div class="announcement-text">{{ msg.content }}</div>
                </div>
              </template>
              <span v-else>{{ msg.content }}</span>
            </div>
            <div v-else class="msg-row" :id="'msg-'+msg.messageId" :class="{ 'msg-self': msg.fromUserId === myUserId }"
              @contextmenu.prevent="openContextMenu($event, msg)">
              <el-popover v-if="msg.fromUserId !== myUserId" placement="right-start" :width="320" trigger="click" @show="showUserInfo(msg.fromUserId)">
                <template #reference>
                  <el-avatar :src="activeSession.type == 0 ? peerAvatar : senderAvatar(msg.fromUserId)" :size="34" shape="square" style="cursor:pointer">
                    {{ senderName(msg.fromUserId)?.charAt(0) || '?' }}
                  </el-avatar>
                </template>
                <div class="profile-card" v-if="userInfoData">
                  <el-avatar :src="userInfoData.avatarUrl" :size="60" shape="square" />
                  <div class="profile-name">{{ userInfoData.nickname }}</div>
                  <div class="profile-gender">{{ userInfoData.gender == 1 ? '♂' : userInfoData.gender == 2 ? '♀' : '' }}</div>
                  <div class="profile-signature" v-if="userInfoData.signature">{{ userInfoData.signature }}</div>
                  <div class="profile-section">
                    <div class="profile-label">基本信息</div>
                    <div class="profile-row"><span>ID号：</span><span>{{ userInfoData.id }}</span></div>
                    <div class="profile-row"><span>邮箱：</span><span>{{ userInfoData.email }}</span></div>
                    <div class="profile-row"><span>注册时间：</span><span>{{ userInfoData.createdTime }}</span></div>
                    <div class="profile-row"><span>最近登陆：</span><span>{{ userInfoData.lastLoginTime }}</span></div>
                    <div class="profile-row"><span>最近IP：</span><span>{{ userInfoData.lastLoginIp }}</span></div>
                  </div>
                  <div class="profile-section" v-if="userInfoData.description">
                    <div class="profile-label">其它说明</div>
                    <div class="profile-desc">{{ userInfoData.description }}</div>
                  </div>
                  <!-- <div class="profile-actions">
                    <el-button v-if="userInfoData.isFriend" size="small" type="primary" @click="startChat(userInfoData.id)">发起会话</el-button>
                    <template v-else>
                      <el-button size="small" @click="startTempChat(userInfoData.id)">试试临时聊天</el-button>
                      <el-button size="small" type="primary" @click="addFriend(userInfoData.id)">加为好友</el-button>
                    </template>
                  </div> -->
                </div>
              </el-popover>
              <div class="msg-body" :class="{ 'msg-right': msg.fromUserId === myUserId }">
                <div v-if="activeSession.type == 1 && msg.fromUserId !== myUserId" class="sender-name-text">
                  <span v-if="senderRole(msg.fromUserId) === 1" class="role-badge owner">群主</span>
                  <span v-else-if="senderRole(msg.fromUserId) === 2" class="role-badge admin">管理</span>
                  {{ senderName(msg.fromUserId) }}
                </div>
                <!-- 引用消息条 -->
                <div v-if="msg.quoteMessageId" class="quote-banner" @click="scrollToMsg(msg.quoteMessageId)"><span class="quote-inline-name">{{ senderName(msg.quoteSenderId) }}</span> {{ quotePreview(msg) }}</div>
                <!-- 文字 -->
                <div v-if="msg.messageType === 'text'" class="bubble text-bubble">
                  <span v-html="highlightMentions(msg.content)"></span>
                  <span v-if="msg.status === 'sending'" class="msg-icon sending"><el-icon class="is-loading" :size="14"><Loading /></el-icon></span>
                  <span v-if="msg.status === 'failed'" class="msg-icon failed" @click="retrySend(msg)"><el-icon :size="14"><WarningFilled /></el-icon></span>
                </div>
                <!-- 图片 -->
                <div v-else-if="msg.messageType === 'image'" class="bubble media-card" style="width:240px;height:180px">
                  <el-image :src="resolveUrl(msg.content)" :preview-src-list="[resolveUrl(msg.content)]" fit="cover" style="width:100%;height:100%;border-radius:6px" />
                </div>
                <!-- 视频 -->
                <div v-else-if="msg.messageType === 'video'" class="bubble media-card">
                  <video :src="resolveUrl(msg.content)" controls preload="metadata" style="max-width:280px;max-height:320px;border-radius:6px" />
                </div>
                <!-- 文件 -->
                <div v-else-if="msg.messageType === 'file'" class="bubble file-card" @click="downloadFile(msg)">
                  <div class="file-card-icon" :style="{background: fileColor(msg.fileName)}">{{ fileExt(msg.fileName) }}</div>
                  <div class="file-card-body">
                    <div class="file-card-name">{{ msg.fileName || '文件' }}</div>
                    <div class="file-card-size">{{ formatSize(msg.fileSize) }}</div>
                  </div>
                  <el-icon :size="18" color="#999"><Download /></el-icon>
                </div>
                <!-- 语音 -->
                <div v-else-if="msg.messageType === 'audio'" class="bubble audio-card">
                  <span @click="playAudio(msg)" class="audio-btn">{{ msg.playing ? '⏸' : '▶' }}</span>
                  <div class="audio-bars"><span v-for="i in 5" :key="i" class="audio-bar" :class="{ active: msg.playing && msg.audioProgress/20 >= i-1 }" /></div>
                  <span class="audio-sec">{{ msg.duration || 0 }}"</span>
                  <audio :ref="el => setAudioRef(msg, el)" :src="resolveUrl(msg.content)" @timeupdate="onAudioTime(msg)" @ended="onAudioEnd(msg)" style="display:none" />
                </div>
                <!-- 名片 -->
                <div v-else-if="msg.messageType === 'card'" class="bubble card-msg" @click="openCard(msg)">
                  <el-avatar :src="msg.cardAvatar || userCache.avatarUrl(msg.content)" :size="40" shape="square">{{ (msg.cardName || String(msg.content))?.charAt(0) }}</el-avatar>
                  <div class="card-msg-info">
                    <div class="card-msg-name">{{ msg.cardName || userCache.displayName(msg.content) || msg.content }}</div>
                    <div class="card-msg-tag">个人名片</div>
                    <div class="card-msg-sub" v-if="userCache.get(msg.content)?.signature">{{ userCache.get(msg.content).signature }}</div>
                  </div>
                </div>
                <!-- 位置 -->
                <div v-else-if="msg.messageType === 'location'" class="bubble loc-card" @click="openLocation(msg)">
                  <div class="loc-icon">📍</div>
                  <div class="loc-info">
                    <div class="loc-addr">{{ msg.locationAddress || msg.content }}</div>
                    <div class="loc-sub">查看地图</div>
                  </div>
                </div>
                <!-- 贴纸 -->
                <div v-else-if="msg.messageType === 'sticker'" class="bubble sticker-card">
                  <img :src="resolveUrl(msg.content)" style="width:120px;height:120px;object-fit:contain" />
                </div>
                <!-- 撤回 -->
                <div v-else-if="msg.messageType === 'recall'" class="bubble recall-bubble">消息已被撤回</div>
              </div>
              <el-avatar v-if="msg.fromUserId === myUserId" :src="myAvatar" :size="34" shape="square">
                {{ myName?.charAt(0) }}
              </el-avatar>
            </div>
          </template>
        </div>

        <div class="chat-input-area">
          <div v-if="quoting" class="quote-bar">
            <span class="quote-bar-user">{{ quotingName }}</span>
            <span class="quote-bar-text">{{ quoting.content?.substring(0,60) || '[非文字消息]' }}</span>
            <el-icon class="quote-bar-close" @click="quoting = null"><Close /></el-icon>
          </div>
          <div class="toolbar">
            <span class="tool-icon" @click="showEmoji = !showEmoji" style="position:relative">
              <el-icon :size="20"><IconEmoticon /></el-icon>
              <WechatEmojiPicker v-if="showEmoji" @select="addEmoji" @send-sticker="onSendSticker" class="emoji-picker-dropdown" />
            </span>
            <el-icon class="tool-icon" @click="triggerImage"><Picture /></el-icon>
            <el-icon class="tool-icon" @click="fileModalVisible = true"><Folder /></el-icon>
            <el-icon class="tool-icon" @click="cardPickerVisible = true"><User /></el-icon>
            <el-icon class="tool-icon" @click="mediaRecorder?.state==='recording' ? stopRecording() : startRecording()"><Microphone /></el-icon>
            <el-icon class="tool-icon" @click="locationPickerVisible = true"><Location /></el-icon>
            <input ref="imageInput" type="file" accept="image/*" hidden @change="onImageSelected" />
          </div>
          <div v-if="activeSession?.type === 1 && mentionChips.length" class="mention-chips">
            <el-tag v-for="uid in mentionChips" :key="uid" size="small" closable @close="removeMention(uid)" type="info">{{ getMemberName(uid) }}</el-tag>
          </div>
          <div v-if="showMentionPopup" class="mention-popup">
            <div v-for="m in mentionFiltered" :key="m.userId" class="mention-item" @click="selectMention(m)">
              {{ m.nickname || m.username }}
            </div>
            <div v-if="!mentionFiltered.length" class="mention-empty">{{ groupMembers.length ? '无匹配成员' : '加载中...' }}</div>
          </div>
          <el-input v-model="msgText" type="textarea" :rows="4" placeholder="" resize="none"
            @keydown.enter.exact.prevent="sendText" />
          <div class="send-row">
            <el-button type="primary" size="small" @click="sendText">发 送</el-button>
          </div>
        </div>
      </template>
      <div v-else class="empty-chat">
        <el-icon style="font-size:80px;color:#bbb"><ChatLineSquare /></el-icon>
        <div style="margin-top:20px;color:#999;font-size:16px">选择会话开始聊天</div>
      </div>
    </div>

    <!-- 创建聊天弹窗（简化，保留） -->
    <el-dialog v-model="chatCreatorVisible" title="选择联系人" width="560px">
      <div class="invite-layout">
        <div class="invite-left">
          <el-input v-model="creatorSearch" placeholder="搜索好友" size="small" />
          <div class="invite-list">
            <div v-for="f in creatorFriends" :key="f.userId" class="invite-friend"
              :class="{ picked: selectedFriendIds.includes(f.userId) }"
              @click="toggleSelect(f.userId)">
              <el-avatar :src="f.avatarUrl" :size="32" shape="square" /> {{ f.remark || f.nickname }}
              <el-icon v-if="selectedFriendIds.includes(f.userId)" color="#07c160"><Check /></el-icon>
            </div>
            <div v-if="creatorFriends.length === 0" class="invite-empty">暂无好友</div>
          </div>
        </div>
        <div class="invite-right">
          <div class="invite-right-title">已选择 ({{ selectedFriendIds.length }})</div>
          <div class="invite-list">
            <div v-for="f in creatorPicked" :key="f.userId" class="invite-picked">
              <el-avatar :src="f.avatarUrl" :size="28" shape="square" /> {{ f.remark || f.nickname }}
              <el-icon class="invite-remove" @click="selectedFriendIds = selectedFriendIds.filter(id => id !== f.userId)"><Close /></el-icon>
            </div>
            <div v-if="selectedFriendIds.length === 0" class="invite-empty">请从左侧选择联系人</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="chatCreatorVisible = false">取消</el-button>
        <el-button type="primary" :disabled="selectedFriendIds.length === 0" @click="handleCreateChat">{{ selectedFriendIds.length > 1 ? '创建群聊' : '开始聊天' }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="groupCreatorVisible" title="创建群聊" width="400px">
      <el-form label-width="80px"><el-form-item label="群名称"><el-input v-model="groupForm.name" placeholder="请输入群名称" /></el-form-item></el-form>
      <template #footer><el-button @click="groupCreatorVisible = false">取消</el-button><el-button type="primary" @click="createGroup">确认创建</el-button></template>
    </el-dialog>
    <el-dialog v-model="inviteVisible" title="邀请成员" width="560px">
      <div class="invite-layout">
        <!-- 左侧可选列表 -->
        <div class="invite-left">
          <el-input v-model="inviteSearch" placeholder="搜索好友" size="small" />
          <div class="invite-list">
            <div v-for="f in inviteCandidates" :key="f.userId" class="invite-friend"
              :class="{ picked: inviteIds.includes(f.userId) }"
              @click="toggleInvite(f.userId)">
              <el-avatar :src="f.avatarUrl" :size="32" shape="square" /> {{ f.remark || f.nickname }}
              <el-icon v-if="inviteIds.includes(f.userId)" color="#07c160"><Check /></el-icon>
            </div>
            <div v-if="inviteCandidates.length === 0" class="invite-empty">暂无好友可邀请</div>
          </div>
        </div>
        <!-- 右侧已选列表 -->
        <div class="invite-right">
          <div class="invite-right-title">已选择 ({{ inviteIds.length }})</div>
          <div class="invite-list">
            <div v-for="f in pickedFriends" :key="f.userId" class="invite-picked">
              <el-avatar :src="f.avatarUrl" :size="28" shape="square" /> {{ f.remark || f.nickname }}
              <el-icon class="invite-remove" @click="inviteIds = inviteIds.filter(id => id !== f.userId)"><Close /></el-icon>
            </div>
            <div v-if="inviteIds.length === 0" class="invite-empty">请从左侧选择成员</div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button type="primary" :disabled="inviteIds.length === 0" @click="doInviteMembers">确定 ({{ inviteIds.length }})</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="cardPickerVisible" title="选择联系人" width="420px">
      <el-input v-model="cardSearch" placeholder="搜索好友" size="small" style="margin-bottom:8px" />
      <div class="invite-list" style="max-height:360px; overflow-y:auto">
        <div v-for="f in cardFiltered" :key="f.userId" class="card-pick-item" @click="sendCard(f)">
          <el-avatar :src="f.avatarUrl" :size="36" shape="square">{{ f.nickname?.charAt(0) }}</el-avatar>
          <div class="card-pick-info"><div class="card-pick-name">{{ f.remark || f.nickname }}</div><div class="card-pick-sub">账号：{{ f.username }}</div></div>
        </div>
        <div v-if="cardFiltered.length === 0" class="invite-empty">暂无好友</div>
      </div>
    </el-dialog>
    <el-dialog v-model="locationPickerVisible" title="发送位置" width="380px" align-center>
      <div style="text-align:center;padding:20px">
        <el-icon :size="48" color="#07c160" v-if="!locating"><LocationFilled /></el-icon>
        <el-icon :size="48" class="is-loading" color="#07c160" v-else><Loading /></el-icon>
        <div style="margin-top:12px;font-size:14px;color:#333">{{ locating ? '正在获取位置...' : locResult || '点击获取当前位置' }}</div>
        <div style="margin-top:4px;font-size:12px;color:#999">{{ locError }}</div>
      </div>
      <template #footer>
        <el-button @click="locationPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="getCurrentLocation" :loading="locating">获取位置</el-button>
        <el-button type="primary" @click="sendCurrentLocation" :disabled="!locCoords">发送</el-button>
      </template>
    </el-dialog>

    <!-- 名片详情弹窗 -->
    <el-dialog v-model="cardDetailVisible" :title="cardDetailData?.nickname || '名片'" width="400px" align-center append-to-body destroy-on-close>
      <div class="profile-card" v-if="cardDetailData">
        <el-avatar :src="cardDetailData.avatarUrl" :size="64" shape="square">{{ cardDetailData.nickname?.charAt(0) }}</el-avatar>
        <div class="profile-name">{{ cardDetailData.nickname }}</div>
        <div class="profile-gender">{{ cardDetailData.gender == 1 ? '♂' : cardDetailData.gender == 2 ? '♀' : '' }}</div>
        <div class="profile-signature" v-if="cardDetailData.signature">{{ cardDetailData.signature }}</div>
        <div class="profile-section">
          <div class="profile-label">基本信息</div>
          <div class="profile-row"><span>ID号：</span><span>{{ cardDetailData.id || cardDetailData.userId }}</span></div>
          <div class="profile-row" v-if="cardDetailData.email"><span>邮箱：</span><span>{{ cardDetailData.email }}</span></div>
          <div class="profile-row" v-if="cardDetailData.createdTime"><span>注册时间：</span><span>{{ cardDetailData.createdTime }}</span></div>
          <div class="profile-row" v-if="cardDetailData.lastLoginTime"><span>最近登陆：</span><span>{{ cardDetailData.lastLoginTime }}</span></div>
        </div>
        <div class="profile-section" v-if="cardDetailData.description">
          <div class="profile-label">其它说明</div>
          <div class="profile-desc">{{ cardDetailData.description }}</div>
        </div>
        <div class="profile-actions" v-if="cardDetailData.userId">
          <el-button v-if="cardDetailData.isFriend" size="small" type="primary" @click="cardDetailVisible=false; startChat(cardDetailData.userId||cardDetailData.id)">发起会话</el-button>
          <template v-else>
            <el-button size="small" type="primary" @click="cardDetailVisible=false; addFriend(cardDetailData.userId||cardDetailData.id)">加为好友</el-button>
          </template>
        </div>
      </div>
    </el-dialog>

    <!-- 录音遮罩 -->
    <div v-if="recordingVisible" class="recording-overlay">
      <div class="recording-modal">
        <div class="rec-wave"><span v-for="i in 5" :key="i" class="rec-bar" :style="{ animationDelay: i*0.15+'s' }" /></div>
        <div class="rec-icon"><el-icon :size="40" color="#fa5151"><Microphone /></el-icon></div>
        <div class="rec-timer">{{ 60 - recordingSeconds }}s</div>
        <div class="rec-hint">最长可录制60秒</div>
        <div class="rec-actions">
          <el-button round @click="cancelRecording">取消</el-button>
          <el-button round type="danger" @click="stopRecording">停止录音</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="historyVisible" title="聊天记录" width="600px" @closed="historyList=[];historyOffset=0;historyTab='text'">
      <div class="history-tabs">
        <el-radio-group v-model="historyTab" size="small" @change="loadHistory">
          <el-radio-button value="text">文本</el-radio-button>
          <el-radio-button value="media">图片/视频</el-radio-button>
          <el-radio-button value="file">文件</el-radio-button>
        </el-radio-group>
        <div class="history-filter">
          <el-date-picker v-model="historyDate" type="date" placeholder="选择日期" size="small" style="width:130px" value-format="x" @change="jumpToDate" />
          <el-input v-model="historyKeyword" size="small" placeholder="搜索..." style="width:140px" clearable @change="loadHistory" />
        </div>
      </div>
      <div class="history-list" ref="historyListRef" @scroll="onHistoryScroll">
        <!-- 文本 -->
        <template v-if="historyTab === 'text'">
          <div v-if="historyList.length === 0 && !historyLoading" class="history-empty"><el-empty :image-size="50" description="暂无文本消息" /></div>
          <div v-for="msg in historyList" :key="msg.messageId" class="history-text-item">
            <el-avatar :src="senderAvatar(msg.fromUserId)" :size="30" shape="square">{{ senderName(msg.fromUserId)?.charAt(0) }}</el-avatar>
            <div class="history-text-info">
              <div class="history-text-name">{{ senderName(msg.fromUserId) }} <span class="history-text-time">{{ formatMsgTime(msg.createdAt) }}</span></div>
              <div class="history-text-content">{{ msg.content }}</div>
            </div>
          </div>
        </template>
        <template v-if="historyTab === 'media'">
          <div v-if="historyList.length === 0 && !historyLoading" class="history-empty"><el-empty :image-size="50" description="暂无图片/视频" /></div>
          <div class="media-grid">
            <div v-for="msg in historyList" :key="msg.messageId" class="media-grid-item">
              <video v-if="msg.messageType === 'video'" :src="resolveUrl(msg.content)" controls preload="metadata" style="width:120px;height:90px;object-fit:cover;border-radius:4px" />
              <el-image v-else :src="resolveUrl(msg.content)" :preview-src-list="[resolveUrl(msg.content)]" fit="cover" style="width:120px;height:90px;border-radius:4px" />
            </div>
          </div>
        </template>
        <template v-if="historyTab === 'file'">
          <div v-if="historyList.length === 0 && !historyLoading" class="history-empty"><el-empty :image-size="50" description="暂无文件" /></div>
          <div v-for="msg in historyList" :key="msg.messageId" class="history-file-item" @click="downloadFile(msg)">
            <img :src="fileTypeIcon(msg.fileName)" class="history-file-icon" />
            <div class="history-file-info">
              <div class="history-file-name">{{ msg.fileName || '文件' }}</div>
              <div class="history-file-size">{{ formatSize(msg.fileSize) }}</div>
            </div>
          </div>
        </template>
        <div v-if="historyLoading" class="loading-tip">加载中...</div>
      </div>
    </el-dialog>

    <el-dialog v-model="fileModalVisible" title="发送文件" width="480px" @closed="fileList = []">
      <div class="file-modal-body">
        <div class="file-add-area" @click="triggerFileInput">
          <el-icon :size="32"><Plus /></el-icon>
          <div class="file-add-text">点击添加文件</div>
        </div>
        <input ref="fileInputRef" type="file" multiple hidden @change="onFilesSelected" />
        <div v-if="fileList.length > 0" class="file-modal-list">
          <div v-for="(f, idx) in fileList" :key="f.id" class="file-modal-item">
            <div class="file-modal-icon">
              <img v-if="f.preview" :src="f.preview" class="file-thumb" />
              <el-icon v-else :size="32"><Document /></el-icon>
            </div>
            <div class="file-modal-info">
              <div class="file-modal-name">{{ f.name }}</div>
              <div class="file-modal-size">{{ formatSize(f.size) }}</div>
              <div v-if="f.uploading" class="file-progress">
                <el-progress :percentage="f.progress" :stroke-width="4" />
              </div>
            </div>
            <div class="file-modal-action">
              <el-icon v-if="f.uploading" class="is-loading" :size="16"><Loading /></el-icon>
              <el-button v-else link type="danger" size="small" @click="fileList.splice(idx,1)">删除</el-button>
            </div>
          </div>
        </div>
        <div v-if="fileList.length === 0" class="file-empty-tip">还没有添加文件</div>
      </div>
      <template #footer>
        <el-button @click="fileModalVisible = false">取消</el-button>
        <el-button type="primary" :disabled="fileList.length === 0" @click="sendFiles">发送 ({{ fileList.length }})</el-button>
      </template>
    </el-dialog>

    <transition name="drawer-slide">
      <div v-if="drawerOpen" class="chat-drawer">
        <div class="drawer-body" v-if="activeSession">
          <template v-if="activeSession.type == 1">
            <div class="drawer-section">
              <div class="drawer-header">
                <el-upload v-if="groupDetail.myRole <= 2" class="group-avatar-upload" :show-file-list="false" :http-request="uploadGroupAvatar" :before-upload="beforeAvatarUpload">
                  <el-avatar :src="groupDetail.avatarUrl" :size="56" shape="square" style="cursor:pointer">{{ groupDetail.name?.charAt(0) }}</el-avatar>
                </el-upload>
                <el-avatar v-else :src="groupDetail.avatarUrl" :size="56" shape="square">{{ groupDetail.name?.charAt(0) }}</el-avatar>
                <div class="drawer-header-info">
                  <div class="drawer-title">{{ groupDetail.name }}</div>
                  <div class="drawer-sub">{{ groupDetail.members?.length || 0 }} 名成员</div>
                </div>
              </div>
              <div class="drawer-divider" />
              <!-- 管理员编辑区 -->
              <template v-if="groupDetail.myRole <= 2">
                <div class="edit-item"><span class="edit-label">群名称</span><el-input v-model="editGroup.name" size="small" @keyup.enter="saveGroupInfo" /></div>
                <div class="edit-item"><span class="edit-label">群公告</span><el-input v-model="editGroup.notice" size="small" type="textarea" :rows="3" placeholder="编辑群公告" @keyup.enter="publishNotice" /></div>
                <div class="edit-item" style="text-align:right"><el-button size="small" type="primary" @click="publishNotice">发布公告</el-button></div>
                <div class="edit-item"><span class="edit-label">群简介</span><el-input v-model="editGroup.description" size="small" type="textarea" :rows="3" placeholder="编辑群简介" @keyup.enter="saveGroupInfo" /></div>
                <div class="edit-item" style="text-align:right"><el-button size="small" type="primary" @click="saveGroupInfo">保存简介</el-button></div>
                <div class="edit-item switch-item"><span class="edit-label">允许成员邀请</span><el-switch v-model="editGroup.allowInvite" size="small" @change="saveGroupInfo" /></div>
              </template>
              <template v-else>
                <div class="read-item"><span class="edit-label">群公告</span><span class="read-value">{{ groupDetail.notice || '暂无' }}</span></div>
                <div class="read-item"><span class="edit-label">群简介</span><span class="read-value">{{ groupDetail.description || '暂无' }}</span></div>
              </template>
              <div class="drawer-divider" />
              <div class="edit-item" style="padding:0 4px;margin-bottom:8px">
                <span class="edit-label">我的群昵称</span>
                <el-input v-model="myGroupNickname" size="small" placeholder="设置群昵称" @keyup.enter="saveGroupNickname" />
              </div>
              <div class="drawer-divider" />
              <el-input v-model="memberSearch" size="small" placeholder="搜索群成员" clearable />
              <div class="member-grid">
                <div v-for="m in filteredMembers" :key="m.userId" class="member-item" @click="showMemberInfo(m.userId); drawerMemberVisible = true">
                  <el-avatar :src="m.avatarUrl" :size="36" shape="square" style="cursor:pointer">{{ m.nickname?.charAt(0) }}</el-avatar>
                  <div class="member-name">
                    <span v-if="m.role === 1" class="role-prefix owner">群主</span>
                    <span v-else-if="m.role === 2" class="role-prefix admin">管理</span>
                    {{ m.groupNickname || m.nickname }}
                  </div>
                </div>
                <div class="member-item" @click="inviteVisible = true"><div class="add-member-btn"><Plus /></div><div class="member-name">邀请</div></div>
              </div>
              <div class="drawer-divider" />
              <div class="drawer-actions">
                <div class="drawer-action-item" @click="inviteVisible = true"><el-icon><Plus /></el-icon> 邀请成员</div>
                <div v-if="groupDetail.myRole === 1" class="drawer-action-item" @click="openTransferFromDrawer"><el-icon><Switch /></el-icon> 转让群主</div>
                <div class="drawer-action-item" @click="clearHistory"><el-icon><Delete /></el-icon> 清除聊天记录</div>
                <div class="drawer-action-item danger" @click="leaveOrDissolve"><el-icon><SwitchButton /></el-icon> {{ groupDetail.myRole === 1 ? '解散群聊' : '退出群聊' }}</div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="drawer-section">
              <div class="drawer-header">
                <el-avatar :src="drawerFriend?.avatarUrl || activeSession.peerAvatar" :size="56" shape="square">{{ activeSession.peerName?.charAt(0) }}</el-avatar>
                <div class="drawer-header-info">
                  <div class="drawer-title">{{ drawerFriend?.nickname || activeSession.peerName }}</div>
                  <div class="drawer-sub" v-if="drawerFriend?.remark">备注: {{ drawerFriend.remark }}</div>
                  <div class="drawer-sub">账号: {{ drawerFriend?.username || activeSession.peerId }}</div>
                </div>
              </div>
              <div class="drawer-divider" />
              <div class="drawer-actions">
                <div class="drawer-action-item" @click="showRemarkEdit = true"><el-icon><EditPen /></el-icon> 修改备注</div>
                <div class="drawer-action-item" @click="toggleStar"><el-icon><StarFilled v-if="drawerFriend?.isStarred" /><Star v-else /></el-icon> {{ drawerFriend?.isStarred ? '取消星标' : '设为星标' }}</div>
                <div class="drawer-action-item" @click="toggleBlock"><el-icon><WarningFilled /></el-icon> {{ drawerFriend?.isBlocked ? '取消拉黑' : '加入黑名单' }}</div>
                <div class="drawer-action-item danger" @click="confirmDeleteFriend"><el-icon><Delete /></el-icon> 删除好友</div>
                <div class="drawer-action-item" @click="clearHistory"><el-icon><Delete /></el-icon> 清除聊天记录</div>
              </div>
              <el-dialog v-model="showRemarkEdit" title="修改备注" width="300px" append-to-body>
                <el-input v-model="remarkText" placeholder="输入备注名" @keyup.enter="saveRemark" />
                <template #footer><el-button @click="showRemarkEdit = false">取消</el-button><el-button type="primary" @click="saveRemark">确定</el-button></template>
              </el-dialog>
            </div>
          </template>
        </div>
      </div>
    </transition>

    <!-- 抽屉成员名片弹窗 -->
    <el-dialog v-model="drawerMemberVisible" :title="memberDetail?.nickname || '名片'" width="400px" align-center append-to-body destroy-on-close>
      <div class="profile-card" v-if="memberDetail || memberInfo">
        <el-avatar :src="(memberDetail || memberInfo).avatarUrl" :size="64" shape="square">{{ (memberDetail || memberInfo).nickname?.charAt(0) }}</el-avatar>
        <div class="profile-name">{{ memberInfo?.groupNickname || (memberDetail || memberInfo).nickname }}</div>
        <div class="profile-gender">{{ (memberDetail || memberInfo).gender == 1 ? '♂' : (memberDetail || memberInfo).gender == 2 ? '♀' : '' }}</div>
        <div class="profile-signature" v-if="memberDetail?.signature">{{ memberDetail.signature }}</div>
        <div class="profile-section">
          <div class="profile-label">基本信息</div>
          <div class="profile-row"><span>ID号：</span><span>{{ memberDetail?.id || memberInfo?.userId }}</span></div>
          <div class="profile-row" v-if="memberDetail?.email"><span>邮箱：</span><span>{{ memberDetail.email }}</span></div>
          <div class="profile-row" v-if="memberDetail?.createdTime"><span>注册时间：</span><span>{{ memberDetail.createdTime }}</span></div>
          <div class="profile-row" v-if="memberDetail?.lastLoginTime"><span>最近登陆：</span><span>{{ memberDetail.lastLoginTime }}</span></div>
          <div class="profile-row" v-if="memberDetail?.lastLoginIp"><span>最近IP：</span><span>{{ memberDetail.lastLoginIp }}</span></div>
          <div class="profile-row" v-if="memberInfo?.groupNickname"><span>群昵称：</span><span>{{ memberInfo.groupNickname }}</span></div>
          <div class="profile-row" v-if="memberInfo?.role"><span>角色：</span><span>{{ memberInfo.role === 1 ? '群主' : memberInfo.role === 2 ? '管理员' : '成员' }}</span></div>
        </div>
        <div class="profile-section" v-if="memberDetail?.description">
          <div class="profile-label">其它说明</div>
          <div class="profile-desc">{{ memberDetail.description }}</div>
        </div>
      </div>
    </el-dialog>

    <!-- 抽屉转让弹窗 -->
    <el-dialog v-model="drawerTransferVisible" title="转让群主" width="380px" align-center append-to-body destroy-on-close>
      <div class="transfer-section">
        <div class="transfer-warn-text">选择一名成员接收群主身份，转让后你将失去群主权限</div>
        <div v-for="m in groupMembers" :key="m.userId" class="transfer-member" v-show="m.userId !== myUserId && m.role !== 1"
          :class="{ selected: drawerTransferForm.userId === m.userId }"
          @click="drawerTransferForm = { userId: m.userId, nickname: m.groupNickname || m.nickname, reason: '' }">
          <el-avatar :src="m.avatarUrl" :size="32" shape="square" /> {{ m.groupNickname || m.nickname }}
          <el-icon v-if="drawerTransferForm.userId === m.userId" color="#07c160"><Check /></el-icon>
        </div>
      </div>
      <template #footer>
        <el-button @click="drawerTransferVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmTransferFromDrawer" :disabled="!drawerTransferForm.userId">确认转让</el-button>
      </template>
    </el-dialog>

    <div v-if="drawerOpen" class="drawer-overlay" @click="drawerOpen = false" />
    <teleport to="body">
      <div v-if="sessionMenu.visible" class="context-menu" :style="{ left: sessionMenu.x+'px', top: sessionMenu.y+'px' }" @click.stop>
        <div class="ctx-item" @click="pinSession"><el-icon><Top /></el-icon> 置顶</div>
        <div class="ctx-item danger" @click="deleteSession"><el-icon><Delete /></el-icon> 删除会话</div>
      </div>
    </teleport>
    <div v-if="sessionMenu.visible" class="ctx-overlay" @click="closeSessionMenu" />
    <teleport to="body">
      <div v-if="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x+'px', top: contextMenu.y+'px' }" @click.stop>
        <div class="ctx-item" @click="quoteMsg(contextMenu.msg); closeContextMenu()"><el-icon><ChatLineSquare /></el-icon> 引用</div>
        <div class="ctx-item" @click="copyMsg(contextMenu.msg)"><el-icon><CopyDocument /></el-icon> 复制</div>
        <div v-if="contextMenu.msg.fromUserId === myUserId" class="ctx-item" @click="recallMsg(contextMenu.msg)"><el-icon><RefreshLeft /></el-icon> 撤回</div>
        <div class="ctx-item" @click="deleteMsg(contextMenu.msg)"><el-icon><Delete /></el-icon> 删除</div>
      </div>
    </teleport>
    <div v-if="contextMenu.visible" class="ctx-overlay" @click="closeContextMenu" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, inject, watch } from 'vue'
import { useWebSocket } from '@/utils/websocket'
import { useUserCache } from '@/utils/userCache'
import { useGroupCache } from '@/utils/groupCache'
import IconEmoticon from '~icons/mdi/emoticon-happy-outline'
import request from '@/utils/request'
import { Search, Plus, MoreFilled, ChatLineSquare, Picture, Folder, Document, CopyDocument, RefreshLeft, Delete, Top, Loading, Clock, WarningFilled, EditPen, Star, StarFilled, SwitchButton, Switch, Check, Close, UserFilled, Microphone, Location, LocationFilled, Download } from '@element-plus/icons-vue'
import WechatEmojiPicker from '@/components/WechatEmojiPicker.vue'
import { ElMessage } from 'element-plus'

const userCache = useUserCache()
const groupCache = useGroupCache()
const myUserId = ref(0), myName = ref(''), myAvatar = ref('')
const searchText = ref(''), sessions = ref([]), activeSession = ref(null)
const msgText = ref(''), messages = ref([]), msgListRef = ref(null)
const showEmoji = ref(false), imageInput = ref(null), fileInputRef = ref(null)
const cardPickerVisible = ref(false), cardSearch = ref(''), locationPickerVisible = ref(false)
// @ 提及功能
const showMentionPopup = ref(false), mentionSearch = ref(''), mentionChips = ref([])
const mentionFiltered = computed(() => {
  const kw = mentionSearch.value.toLowerCase()
  const members = groupMembers.value || []
  return members.filter(m => m.userId !== myUserId.value && ((m.nickname||'').toLowerCase().includes(kw) || (m.username||'').toLowerCase().includes(kw)))
})
const getMemberName = (uid) => { const m = (groupMembers.value||[]).find(x => x.userId === uid); return m ? (m.groupNickname || m.nickname || m.username) : '' }
const selectMention = (m) => {
  const v = msgText.value
  const idx = v.lastIndexOf('@')
  const name = m.groupNickname || m.nickname || m.username
  msgText.value = v.substring(0, idx) + '@' + name + ' '
  if (!mentionChips.value.includes(m.userId)) mentionChips.value.push(m.userId)
  showMentionPopup.value = false
}
const removeMention = (uid) => { mentionChips.value = mentionChips.value.filter(id => id !== uid) }
// 监听输入检测 @
watch(msgText, (val) => {
  if (!activeSession.value || activeSession.value.type != 1) { showMentionPopup.value = false; return }
  const idx = (val || '').lastIndexOf('@')
  if (idx >= 0) {
    const after = (val || '').substring(idx + 1)
    if (!after.includes(' ') && !after.includes('\n')) {
      mentionSearch.value = after
      showMentionPopup.value = true
      return
    }
  }
  showMentionPopup.value = false
})
const highlightMentions = (content) => {
  if (!content) return ''
  // 转义 HTML 然后高亮 @xxx
  return content.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
    .replace(/@(\S+)/g, '<span class="mention-highlight">@$1</span>')
}
const cardFiltered = computed(() => {
  if (!cardSearch.value) return friends.value
  const kw = cardSearch.value.toLowerCase()
  return friends.value.filter(f => (f.nickname||'').toLowerCase().includes(kw) || (f.remark||'').toLowerCase().includes(kw) || (f.username||'').toLowerCase().includes(kw))
})
let mediaRecorder = null, audioChunks = [], recordingTimer = null
const hasMore = ref(true), loadingHistory = ref(false)
const recordingVisible = ref(false), recordingSeconds = ref(0)
const fileModalVisible = ref(false), fileList = ref([])
const historyVisible = ref(false), historyTab = ref('text'), historyKeyword = ref('')
const historyList = ref([]), historyOffset = ref(0), historyLoading = ref(false), historyRef = ref(null)
const historyDate = ref(null)
let historyHasMore = true
const drawerOpen = ref(false)
const groupDetail = ref({}), groupMembers = ref([]), editGroup = ref({}), memberSearch = ref(''), savingGroup = ref(false), myGroupNickname = ref('')
const drawerFriend = ref(null), showRemarkEdit = ref(false), remarkText = ref('')
const friends = ref([]), chatCreatorVisible = ref(false), groupCreatorVisible = ref(false)
const creatorSearch = ref(''), selectedFriendIds = ref([]), groupForm = ref({name:''})
const toggleSelect = (uid) => { const idx = selectedFriendIds.value.indexOf(uid); if (idx >= 0) selectedFriendIds.value.splice(idx, 1); else selectedFriendIds.value.push(uid) }
const creatorPicked = computed(() => friends.value.filter(f => selectedFriendIds.value.includes(f.userId)))
const updateUnread = inject('updateUnread', () => {})
const inviteVisible = ref(false), inviteSearch = ref(''), inviteIds = ref([])
const toggleInvite = (uid) => {
  const idx = inviteIds.value.indexOf(uid)
  if (idx >= 0) inviteIds.value.splice(idx, 1)
  else inviteIds.value.push(uid)
}
const pickedFriends = computed(() => friends.value.filter(f => inviteIds.value.includes(f.userId)))

const filteredSessions = computed(() => {
  if (!searchText.value) return sessions.value
  const kw = searchText.value.toLowerCase()
  return sessions.value.filter(s => (s.peerName||'').toLowerCase().includes(kw))
})
const filteredMembers = computed(() => {
  if (!memberSearch.value) return groupMembers.value
  const kw = memberSearch.value.toLowerCase()
  return groupMembers.value.filter(m => (m.nickname||'').toLowerCase().includes(kw) || (m.username||'').toLowerCase().includes(kw))
})
const creatorFriends = computed(() => {
  if (!creatorSearch.value) return friends.value
  const kw = creatorSearch.value.toLowerCase()
  return friends.value.filter(f => (f.nickname||'').toLowerCase().includes(kw) || (f.username||'').toLowerCase().includes(kw))
})
const peerAvatar = computed(() => drawerFriend.value?.avatarUrl || '')
const senderAvatar = (uid) => userCache.avatarUrl(uid)
const senderName = (uid) => userCache.displayName(uid, activeSession.value?.type === 1 ? Number(activeSession.value.peerId) : null)
const senderRole = (uid) => {
  if (activeSession.value?.type !== 1) return null
  const gm = groupMembers.value.find(m => m.userId === uid)
  return gm?.role || null
}
const tagLabel = (type) => {
  const t = (type || 'text').toLowerCase()
  return { text:'[文字]', image:'[图片]', video:'[视频]', file:'[文件]', audio:'[语音]', system:'[系统]', announcement:'[公告]', recall:'[撤回]' }[t] || ''
}
const tagType = (type) => (type || 'text').toLowerCase()

const formatTime = (ts) => {
  if (!ts || ts === 0 || ts === '0') return ''
  const d = new Date(Number(ts))
  return d.toDateString() === new Date().toDateString() ? d.toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'}) : d.toLocaleDateString('zh-CN', {month:'short',day:'numeric'})
}
const formatMsgTime = (ts) => {
  if (!ts) return ''; const d = new Date(Number(ts)), diff = Date.now() - d
  if (diff < 60000) return '刚刚'; if (diff < 3600000) return Math.floor(diff/60000)+'分钟前'
  return d.toLocaleDateString('zh-CN', {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'})
}
const showTimeDivider = (idx) => {
  if (idx === 0) return true
  return messages.value[idx].createdAt - messages.value[idx-1].createdAt > 300000
}
const formatSize = (bytes) => {
  if (!bytes) return ''; if (bytes < 1024) return bytes+'B'; if (bytes < 1048576) return (bytes/1024).toFixed(1)+'KB'
  return (bytes/1048576).toFixed(1)+'MB'
}
const fileExt = (name) => (name || '').split('.').pop()?.toUpperCase()?.slice(0,4) || 'FILE'
const fileColor = (name) => {
  const ext = (name || '').split('.').pop()?.toLowerCase()
  const map = { pdf:'#e74c3c', doc:'#2b7bd6', docx:'#2b7bd6', xls:'#27ae60', xlsx:'#27ae60', ppt:'#e67e22', pptx:'#e67e22', zip:'#f39c12', rar:'#f39c12', mp3:'#9b59b6', mp4:'#3498db', jpg:'#e91e63', png:'#e91e63', gif:'#e91e63', txt:'#607d8b' }
  return map[ext] || '#607d8b'
}
const fileTypeIcon = (name) => {
  const ext = (name || '').split('.').pop()?.toLowerCase()
  const map = {
    pdf: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#e74c3c"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="16" font-weight="bold">PDF</text></svg>'),
    doc: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#2980b9"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">DOC</text></svg>'),
    docx: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#2980b9"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">DOC</text></svg>'),
    xls: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#27ae60"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">XLS</text></svg>'),
    xlsx: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#27ae60"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">XLS</text></svg>'),
    ppt: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#e67e22"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">PPT</text></svg>'),
    zip: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#f39c12"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">ZIP</text></svg>'),
    rar: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#f39c12"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">RAR</text></svg>'),
    mp4: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#9b59b6"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">VID</text></svg>'),
    mp3: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#1abc9c"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">AUD</text></svg>'),
  }
  return map[ext] || 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" rx="6" fill="#95a5a6"/><text x="24" y="32" text-anchor="middle" fill="white" font-size="14" font-weight="bold">FILE</text></svg>')
}

const triggerImage = () => imageInput.value?.click()
const triggerFileInput = () => fileInputRef.value?.click()

// ===== 会话 =====
const selectSession = async (s) => {
  if (activeSession.value?.conversationId === s.conversationId) return
  send({action:'leave_conversation',data:{conversationId:activeSession.value?.conversationId||''}})
  activeSession.value = s; drawerOpen.value = false; messages.value = []; hasMore.value = true
  if (s.type == 1) {
    try { const res = await request.get(`/groups/${s.peerId}`); if (res.code===0) { groupDetail.value=res.data; groupMembers.value=res.data.members||[]; editGroup.value={name:res.data.name||'',notice:res.data.notice||'',description:res.data.description||'',allowInvite:res.data.allowInvite!==false}; userCache.setAll(res.data.members); const me = res.data.members?.find(m => m.userId === myUserId.value); myGroupNickname.value = me?.groupNickname || '' } } catch(e){}
  } else {
    try { const res = await request.get('/friends'); if (res.code===0) drawerFriend.value = res.data.find(f=>f.userId==s.peerId) } catch(e){}
  }
  await loadMessages()
  markRead(s.conversationId)
  send({action:'enter_conversation',data:{conversationId:s.conversationId}})
}

const loadMessages = async () => {
  if (!activeSession.value || loadingHistory.value) return
  loadingHistory.value = true
  const firstLoad = messages.value.length === 0
  const offset = messages.value.length
  try {
    const res = await request.get(`/conversations/${activeSession.value.conversationId}/messages`, { params: { offset, limit: 20 } })
    if (res.code === 0) {
      const list = res.data.list || []
      // 后端 $in 查询不保证顺序，前端强制按 createdAt 升序排列
      const sorted = [...list].sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
      // 保存滚动位置，防止历史消息加载导致跳动
      const el = msgListRef.value
      const prevHeight = el?.scrollHeight || 0
      const prevTop = el?.scrollTop || 0
      messages.value = [...sorted, ...messages.value]
      hasMore.value = res.data.hasMore
      if (!firstLoad && el) {
        // requestAnimationFrame 等待浏览器完成布局后再设置 scrollTop，确保图片 placeholder 高度已被计入
        requestAnimationFrame(() => {
          el.scrollTop = el.scrollHeight - prevHeight + prevTop
        })
      }
      // 批量加载发送者信息到缓存
      const uids = [...new Set(sorted.map(m => m.fromUserId).filter(Boolean))]
      userCache.batchFetch(uids)
      // 群聊时拉取发送者的群昵称
      if (activeSession.value?.type === 1) {
        const gid = Number(activeSession.value.peerId)
        uids.forEach(uid => userCache.fetchGroupMember(gid, uid))
      }
      if (firstLoad) {
        nextTick(() => { if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight })
      }
    }
  } catch(e) {} finally { loadingHistory.value = false }
}

const handleScroll = () => { const el = msgListRef.value; if (el && hasMore.value && !loadingHistory.value && el.scrollTop < 40) loadMessages() }

// ===== 发送 =====
const sendText = async () => {
  if (!msgText.value.trim() || !activeSession.value) return
  const content = msgText.value.trim(); msgText.value = ''
  const mentions = [...mentionChips.value]; mentionChips.value = []
  const extra = {}
  if (mentions.length) extra.mentions = mentions
  const tempId = 'temp_' + Date.now()
  const quotedMsg = quoting.value
  const tempMsg = { messageId: tempId, fromUserId: myUserId.value, content, messageType: 'text', createdAt: Date.now(), status: 'sending', mentions, quoteMessageId: quotedMsg?.messageId || null, quoteSenderId: quotedMsg?.fromUserId || null, quoteMessageType: quotedMsg?.messageType || 'text', quoteContent: quotedMsg?.content?.substring(0,60) || '' }
  messages.value.push(tempMsg)
  quoting.value = null
  nextTick(() => { if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight })
  try {
    const res = await request.post(`/conversations/${activeSession.value.conversationId}/messages`, { content, messageType: 'text', quoteMessageId: quotedMsg?.messageId || null, mentions })
    const idx = messages.value.findIndex(m => m.messageId === tempId)
    if (res.code === 0 && idx >= 0) {
      messages.value[idx] = { ...tempMsg, messageId: res.data.messageId, createdAt: res.data.createdAt, status: 'sent', quoteMessageId: tempMsg.quoteMessageId, quoteSenderId: tempMsg.quoteSenderId, quoteMessageType: tempMsg.quoteMessageType, quoteContent: tempMsg.quoteContent }
      // 本地更新会话列表的最后一条消息
      updateSessionLastMsg(activeSession.value.conversationId, content, res.data.createdAt, 'text')
    } else {
      if (idx >= 0) messages.value[idx].status = 'failed'
      messages.value.push({ messageId: 'sys_'+Date.now(), fromUserId: 0, messageType: 'system', content: (res && res.message) || '发送失败', createdAt: Date.now() })
      nextTick(() => { if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight })
    }
  } catch(e) {
    const idx = messages.value.findIndex(m => m.messageId === tempId)
    if (idx >= 0) messages.value[idx].status = 'failed'
    messages.value.push({ messageId: 'sys_'+Date.now(), fromUserId: 0, messageType: 'system', content: e?.response?.data?.message || '发送失败', createdAt: Date.now() })
    nextTick(() => { if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight })
  }
}
// ===== 名片 =====
const sendCard = async (f) => {
  cardPickerVisible.value = false
  await sendMessage(f.userId + '', 'card', { cardName: f.remark || f.nickname, cardAvatar: f.avatarUrl })
}
const cardDetailVisible = ref(false), cardDetailData = ref(null)
const openCard = async (msg) => {
  const uid = Number(msg.content)
  cardDetailData.value = { nickname: msg.cardName || userCache.displayName(uid) || '', avatarUrl: msg.cardAvatar || userCache.avatarUrl(uid) || '', userId: uid }
  cardDetailVisible.value = true
  try { const res = await request.get(`/users/${uid}/profile`); if (res.code === 0) { cardDetailData.value = res.data; userCache.set(uid, res.data) } } catch(e) {}
}

// ===== 位置 =====
const locCoords = ref(null), locResult = ref(''), locError = ref(''), locating = ref(false)
const getCurrentLocation = () => {
  locating.value = true; locResult.value = ''; locError.value = ''
  if (!navigator.geolocation) { locError.value = '浏览器不支持定位'; locating.value = false; return }
  navigator.geolocation.getCurrentPosition(pos => {
    locCoords.value = { lat: pos.coords.latitude.toFixed(4), lng: pos.coords.longitude.toFixed(4) }
    locResult.value = `${locCoords.value.lat}, ${locCoords.value.lng}`
    locating.value = false
  }, err => { locError.value = '定位失败: ' + err.message; locating.value = false }, { timeout: 10000 })
}
const sendCurrentLocation = () => {
  if (!locCoords.value) return
  const addr = `${locCoords.value.lat},${locCoords.value.lng}`
  locationPickerVisible.value = false; locCoords.value = null; locResult.value = ''
  sendMessage(addr, 'location', { locationAddress: addr })
}
const openLocation = (msg) => {
  const addr = msg.locationAddress || msg.content || ''
  const match = addr.match(/([\d.]+)\s*,?\s*([\d.]+)/)
  if (match) window.open(`https://map.baidu.com/search/${match[2]},${match[1]}`, '_blank')
  else window.open(`https://map.baidu.com/search/${encodeURIComponent(addr)}`, '_blank')
}

// ===== 语音 =====
const startRecording = async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder = new MediaRecorder(stream)
    audioChunks = []; recordingSeconds.value = 0; recordingVisible.value = true
    recordingTimer = setInterval(() => { recordingSeconds.value++; if (recordingSeconds.value >= 60) stopRecording() }, 1000)
    mediaRecorder.ondataavailable = (e) => audioChunks.push(e.data)
    mediaRecorder.onstop = async () => {
      clearInterval(recordingTimer); recordingVisible.value = false; stream.getTracks().forEach(t => t.stop())
      if (recordingCancelled) { recordingCancelled = false; audioChunks = []; return }
      if (audioChunks.length === 0) return
      const blob = new Blob(audioChunks, { type: 'audio/webm' })
      const fd = new FormData(); fd.append('file', blob, 'recording.webm')
      const { default: axios } = await import('axios')
      const res = await axios.post('/api/files/upload', fd, { headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` } })
      if (res.data?.code === 0) await sendMessage(res.data.data.url, 'audio', { duration: recordingSeconds.value })
    }
    mediaRecorder.start()
  } catch(e) { ElMessage.error('无法访问麦克风') }
}
let recordingCancelled = false
const cancelRecording = () => { recordingCancelled = true; stopRecording() }
const stopRecording = () => {
  if (mediaRecorder && mediaRecorder.state === 'recording') { mediaRecorder.stop() }
}
const quoting = ref(null)
const quotingName = computed(() => quoting.value ? (userCache.displayName(quoting.value.fromUserId, activeSession.value?.type === 1 ? Number(activeSession.value.peerId) : null)) : '')
const quoteMsg = (msg) => { quoting.value = msg }
let scrollTarget = null
const scrollToMsg = async (messageId) => {
  if (!messageId) return
  const doFind = () => {
    const el = document.getElementById('msg-' + messageId)
    if (el) {
      el.scrollIntoView({ behavior:'smooth', block:'center' })
      el.style.background = 'rgba(7,193,96,0.15)'
      setTimeout(() => el.style.background = '', 1500)
      return true
    }
    return false
  }
  if (doFind()) return
  scrollTarget = messageId
  while (hasMore.value) {
    if (loadingHistory.value) { await new Promise(r => setTimeout(r, 200)); continue }
    msgListRef.value.scrollTop = 0
    loadMessages()
    // 等 loadingHistory 变 false
    while (loadingHistory.value) await new Promise(r => setTimeout(r, 100))
    await nextTick()
    if (doFind()) { scrollTarget = null; return }
    if (messages.value.length > 0 && messages.value[0].messageId > messageId) break
  }
  scrollTarget = null
}
const quotePreview = (msg) => {
  const t = (msg.quoteMessageType || 'text').toLowerCase()
  if (t === 'image') return '[图片]'
  if (t === 'video') return '[视频]'
  if (t === 'file') return '[文件]'
  if (t === 'audio') return '[语音]'
  return msg.quoteContent || ''
}
const setAudioRef = (msg, el) => { if (el) msg._audioEl = el }
const playAudio = (msg) => {
  const el = msg._audioEl; if (!el) return
  if (el.paused) { el.play(); msg.playing = true } else { el.pause(); msg.playing = false }
}
const onAudioTime = (msg) => { if (msg._audioEl) msg.audioProgress = (msg._audioEl.currentTime / msg._audioEl.duration) * 100 }
const onAudioEnd = (msg) => { msg.playing = false; msg.audioProgress = 0 }

const retrySend = async (msg) => {
  msg.status = 'sending'
  try {
    const res = await request.post(`/conversations/${activeSession.value.conversationId}/messages`, { content: msg.content, messageType: 'text' })
    if (res.code === 0) { msg.messageId = res.data.messageId; msg.createdAt = res.data.createdAt; msg.status = 'sent'; updateSessionLastMsg(activeSession.value.conversationId, msg.content, res.data.createdAt, msg.messageType) }
    else { msg.status = 'failed' }
  } catch(e) { msg.status = 'failed' }
}
const onImageSelected = async (e) => {
  const file = e.target.files?.[0]; if (!file) return; e.target.value = ''
  try { const fd = new FormData(); fd.append('file', file); const { default: axios } = await import('axios'); const res = await axios.post('/api/files/upload', fd, { headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}`, 'Content-Type':'multipart/form-data' } }); if (res.data?.code === 0) await sendMessage(res.data.data.url, 'image') } catch(e) { ElMessage.error('上传失败') }
}
const onFilesSelected = (e) => {
  const files = Array.from(e.target.files || [])
  files.forEach(file => {
    const preview = file.type.startsWith('image/') ? URL.createObjectURL(file) : null
    fileList.value.push({ id: Date.now() + Math.random(), file, name: file.name, size: file.size, preview })
  })
  e.target.value = ''
}
const isVideo = (name) => { const ext = (name||'').split('.').pop()?.toLowerCase(); return ['mp4','avi','mov','wmv','flv','mkv','webm'].includes(ext) }
const isImageExt = (name) => { const ext = (name||'').split('.').pop()?.toLowerCase(); return ['jpg','jpeg','png','gif','webp','bmp','svg'].includes(ext) }
const fileType = (name) => isVideo(name) ? 'video' : isImageExt(name) ? 'image' : 'file'
const sendFiles = async () => {
  for (const f of fileList.value) {
    f.uploading = true; f.progress = 0
    const fd = new FormData(); fd.append('file', f.file)
    try {
      const { default: axios } = await import('axios')
      const token = localStorage.getItem('access_token')
      const res = await axios.post('/api/files/upload', fd, {
        headers: { Authorization: `Bearer ${token}` },
        onUploadProgress: (e) => { if (e?.total) f.progress = Math.round(e.loaded / e.total * 100) }
      })
      const body = res.data
      if (body && body.code === 0) {
        f.progress = 100
        const type = fileType(f.name)
        try {
          await sendMessage(body.data.url, type, { fileName: f.name, fileSize: f.size })
        } catch(e) { ElMessage.error(`${f.name} 发送失败`) }
      } else {
        ElMessage.error((body && body.message) || `${f.name} 上传失败`)
      }
    } catch(e) { console.error(e); ElMessage.error(`${f.name} 上传失败: ${e.message}`) }
    f.uploading = false
  }
  fileModalVisible.value = false
}
const sendMessage = async (content, type, extra = {}) => {
  try {
    const res = await request.post(`/conversations/${activeSession.value.conversationId}/messages`, { content, messageType: type, ...extra })
    if (res.code === 0) { messages.value.push({ messageId: res.data.messageId, fromUserId: myUserId.value, content, messageType: type, createdAt: res.data.createdAt, ...extra }); nextTick(() => { if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight }); updateSessionLastMsg(activeSession.value.conversationId, content, res.data.createdAt, type) }
  } catch(e) { ElMessage.error('发送失败') }
}
const resolveUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return '/api/files/download/' + (url.includes('/') ? url.substring(url.indexOf('/')+1) : url)
}
const addEmoji = (emoji) => { msgText.value += (emoji?.native || emoji); showEmoji.value = false }
const onSendSticker = async (sticker) => {
  showEmoji.value = false
  if (!activeSession.value) return
  // 如果已经是URL（http开头），直接用；否则上传到MinIO
  let url = sticker.url
  if (!url.startsWith('http') && sticker.file) {
    const fd = new FormData(); fd.append('file', sticker.file)
    try { const { default: axios } = await import('axios'); const res = await axios.post('/api/files/upload', fd, { headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}`, 'Content-Type':'multipart/form-data' } }); if (res.data?.code === 0) url = res.data.data.url } catch(e) { ElMessage.error('上传失败'); return }
  }
  await sendMessage(url, 'sticker')
}
const downloadFile = (msg) => { if (msg.content) window.open(resolveUrl(msg.content), '_blank') }
const userInfoData = ref(null)
const memberInfo = ref(null), memberDetail = ref(null)
const drawerMemberVisible = ref(false)
const drawerTransferVisible = ref(false), drawerTransferForm = ref({ userId: null, nickname: '', reason: '' })
const showMemberInfo = async (uid) => {
  memberInfo.value = groupMembers.value.find(x => x.userId === uid) || null; memberDetail.value = null
  const cached = userCache.get(uid)
  if (cached) memberDetail.value = { ...cached, id: cached.userId || uid }
  try { const res = await request.get(`/users/${uid}/profile`); if (res.code === 0) { memberDetail.value = res.data; userCache.set(uid, res.data) } } catch(e) {}
}
const showUserInfo = async (uid) => {
  userInfoData.value = null
  const cached = userCache.get(uid)
  // 用缓存展示基础信息，同时异步拉取详细资料
  if (cached) userInfoData.value = { ...cached, id: cached.userId }
  try { const res = await request.get(`/users/${uid}/profile`); if (res.code===0) { userInfoData.value = res.data; userCache.set(uid, res.data) } } catch(e) {}
}
const startChat = async (uid) => {
  try {
    const res = await request.post('/conversations/private', { peerId: uid })
    if (res.code === 0) {
      activeSession.value = { conversationId: res.data.conversationId, type: 0, peerId: uid, peerName: userCache.get(uid)?.displayName || '', peerAvatar: userCache.get(uid)?.avatarUrl || '' }
      messages.value = []; hasMore.value = true; loadMessages(); fetchSessions()
    }
  } catch(e) { ElMessage.error('发起会话失败') }
}
const startTempChat = (uid) => { /* TODO */ ElMessage.info('临时聊天功能开发中') }
const addFriend = async (uid) => {
  try { await request.post('/friends/requests', { toUserId: uid, message: '' }); ElMessage.success('好友申请已发送') } catch(e) { ElMessage.error(e?.response?.data?.message || '发送失败') }
}
const beforeAvatarUpload = (file) => { const isImage = file.type.startsWith('image/'); if (!isImage) ElMessage.error('只能上传图片'); return isImage }
const uploadGroupAvatar = async (options) => {
  const fd = new FormData(); fd.append('file', options.file)
  try { const { default: axios } = await import('axios'); const res = await axios.post('/api/files/upload', fd, { headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}`, 'Content-Type':'multipart/form-data' } }); if (res.data?.code === 0) { editGroup.value.avatarUrl = res.data.data.url; groupDetail.value.avatarUrl = res.data.data.url; saveGroupInfo(); ElMessage.success('头像已更新') } } catch(e) { ElMessage.error('上传失败') }
}
const contextMenu = ref({ visible: false, x: 0, y: 0, msg: null })
const openContextMenu = (e, msg) => { contextMenu.value = { visible: true, x: e.clientX, y: e.clientY, msg } }
const closeContextMenu = () => { contextMenu.value.visible = false }
const copyMsg = (msg) => { navigator.clipboard.writeText(msg.content).then(() => ElMessage.success('已复制')); closeContextMenu() }
const recallMsg = async (msg) => { try { await request.put(`/conversations/${activeSession.value.conversationId}/messages/${msg.messageId}/recall`); msg.messageType = 'recall'; msg.content = '消息已被撤回' } catch(e) { ElMessage.error('撤回失败') }; closeContextMenu() }
const deleteMsg = async (msg) => { try { await request.delete(`/conversations/${activeSession.value.conversationId}/messages/${msg.messageId}`); messages.value = messages.value.filter(m => m.messageId !== msg.messageId) } catch(e) { ElMessage.error(e?.response?.data?.message||'删除失败') }; closeContextMenu() }

// session context menu
const sessionMenu = ref({ visible: false, x: 0, y: 0, session: null })
const openSessionMenu = (e, s) => { sessionMenu.value = { visible: true, x: e.clientX, y: e.clientY, session: s } }
const closeSessionMenu = () => { sessionMenu.value.visible = false }
const deleteSession = async () => {
  const convId = sessionMenu.value.session.conversationId
  try {
    await request.delete(`/conversations/${convId}`)
    sessions.value = sessions.value.filter(s => s.conversationId !== convId)
    if (activeSession.value?.conversationId === convId) activeSession.value = null
  } catch(e) { ElMessage.error('删除失败') }
  closeSessionMenu()
}
const pinSession = () => { ElMessage.info('置顶功能开发中'); closeSessionMenu() }

// ===== WS =====
const { connect, send, onMessage } = useWebSocket()
onMessage((msg) => {
  if (msg.action === 'message') {
    const data = msg.data
    if (data.conversationId === activeSession.value?.conversationId) {
      if (data.messageType === 'recall') {
        const existing = messages.value.find(m => m.messageId === data.messageId)
        if (existing) { existing.messageType = 'recall'; existing.content = data.content }
      } else if (!messages.value.some(m => m.messageId === data.messageId)) {
        messages.value.push({ messageId: data.messageId, fromUserId: data.fromUserId, content: data.content, messageType: data.messageType||'text', createdAt: data.createdAt })
        nextTick(() => { if (msgListRef.value) msgListRef.value.scrollTop = msgListRef.value.scrollHeight })
        userCache.batchFetch([data.fromUserId].filter(Boolean))
        // 群聊时拉取群昵称
        if (data.conversationType === 1) {
          const gid = Number(data.conversationId?.substring(2))
          if (gid) userCache.fetchGroupMember(gid, data.fromUserId)
        }
      }
    }
    const sidx = sessions.value.findIndex(s => s.conversationId === data.conversationId)
    if (sidx >= 0) {
      sessions.value[sidx].unreadCount = 0
      const sender = userCache.get(data.fromUserId)
      sessions.value[sidx].lastMsgContent = (sender?.displayName ? sender.displayName + ': ' : '') + (data.content || '')
      sessions.value[sidx].lastMsgTime = data.createdAt || Date.now()
      sessions.value[sidx].lastMsgType = data.messageType || 'text'
      sessions.value.sort((a, b) => (b.lastMsgTime || 0) - (a.lastMsgTime || 0))
    }
  } else if (msg.action === 'new_message') {
    const d = msg.data
    const sidx = sessions.value.findIndex(s => s.conversationId === d.conversationId)
    if (sidx >= 0) {
      const mentionPrefix = d.mention ? '[有人@你] ' : ''
	      sessions.value[sidx].lastMsgContent = mentionPrefix + (d.senderName ? d.senderName + ': ' : '') + (d.preview || '')
      sessions.value[sidx].lastMsgTime = Date.now()
      sessions.value[sidx].lastMsgType = d.messageType || 'text'
      sessions.value[sidx].unreadCount = (sessions.value[sidx].unreadCount || 0) + 1
      // 重新排序：最新消息的会话排到最前面
      sessions.value.sort((a, b) => (b.lastMsgTime || 0) - (a.lastMsgTime || 0))
      updateUnread(sessions.value.reduce((s, x) => s + (x.unreadCount||0), 0))
    }
  }
})

const updateSessionLastMsg = (convId, content, time, msgType) => {
  const idx = sessions.value.findIndex(s => s.conversationId === convId)
  if (idx >= 0) {
    sessions.value[idx].lastMsgContent = content
    sessions.value[idx].lastMsgTime = time
    if (msgType) sessions.value[idx].lastMsgType = msgType
    sessions.value.sort((a, b) => (b.lastMsgTime || 0) - (a.lastMsgTime || 0))
  }
}

const markRead = async (convId) => {
  try { await request.put(`/conversations/${convId}/read`); const idx = sessions.value.findIndex(s => s.conversationId === convId); if (idx >= 0) sessions.value[idx].unreadCount = 0; updateUnread(sessions.value.reduce((s, x) => s + (x.unreadCount||0), 0)) } catch(e) {}
}
const saveRemark = async () => {
  if (!drawerFriend.value) return
  try { await request.put(`/friends/${drawerFriend.value.userId}/remark`, { remark: remarkText.value }); drawerFriend.value.remark = remarkText.value; showRemarkEdit.value = false; ElMessage.success('备注已更新') } catch(e) { ElMessage.error('更新失败') }
}
const toggleStar = async () => {
  if (!drawerFriend.value) return
  const v = !drawerFriend.value.isStarred
  try { await request.put(`/friends/${drawerFriend.value.userId}/star`, { starred: v }); drawerFriend.value.isStarred = v; ElMessage.success(v ? '已设置星标' : '已取消星标') } catch(e) { ElMessage.error('操作失败') }
}
const toggleBlock = async () => {
  if (!drawerFriend.value) return
  const blocked = drawerFriend.value.isBlocked
  try {
    await request.post(`/friends/${drawerFriend.value.userId}/${blocked ? 'unblock' : 'block'}`)
    drawerFriend.value.isBlocked = !blocked
    ElMessage.success(blocked ? '已取消拉黑' : '已加入黑名单')
  } catch(e) { ElMessage.error('操作失败') }
}
const confirmDeleteFriend = async () => {
  if (!drawerFriend.value) return
  try { await request.delete(`/friends/${drawerFriend.value.userId}`); drawerFriend.value = null; drawerOpen.value = false; ElMessage.success('已删除好友') } catch(e) { ElMessage.error('删除失败') }
}
const leaveOrDissolve = () => {
  const isOwner = groupDetail.value?.myRole === 1
  ElMessageBox.confirm(isOwner ? '确定解散该群？' : '确定退出群聊？', '提示', { type: 'warning' }).then(async () => {
    try {
      await request.post(`/groups/${activeSession.value.peerId}/${isOwner ? 'dissolve' : 'leave'}`)
      ElMessage.success(isOwner ? '群已解散' : '已退出'); drawerOpen.value = false; fetchSessions()
    } catch(e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}
const openTransferFromDrawer = () => {
  drawerTransferVisible.value = true
}
const confirmTransferFromDrawer = async () => {
  if (!drawerTransferForm.userId) { ElMessage.warning('请选择转让对象'); return }
  try {
    await request.put(`/groups/${activeSession.value.peerId}/members/${drawerTransferForm.userId}/role`, { role: 1 })
    ElMessage.success('群主已转让'); drawerTransferVisible.value = false; selectSession(activeSession.value)
  } catch(e) { ElMessage.error('操作失败') }
}

const clearHistory = async () => {
  if (!activeSession.value) return
  try { await request.delete(`/conversations/${activeSession.value.conversationId}/messages`); messages.value = []; ElMessage.success('聊天记录已清除') } catch(e) { ElMessage.error('清除失败') }
}
const fetchSessions = async () => { try { const res = await request.get('/conversations'); if (res.code===0) { sessions.value = res.data; updateUnread(res.data.reduce((s, x) => s + (x.unreadCount||0), 0)); const userIds = res.data.filter(s => s.type === 0).map(s => Number(s.peerId)).filter(Boolean); const groupIds = res.data.filter(s => s.type === 1).map(s => Number(s.peerId)).filter(Boolean); userCache.batchFetch(userIds); groupCache.batchFetch(groupIds) } } catch(e) {} }
const fetchFriends = async () => { try { const res = await request.get('/friends'); if (res.code===0) { friends.value = res.data; userCache.setAll(res.data) } } catch(e) {} }

// ===== 创建聊天 =====
const openChatCreator = () => { selectedFriendIds.value = []; creatorSearch.value = ''; chatCreatorVisible.value = true }
const handleCreateChat = async () => {
  if (selectedFriendIds.value.length === 0) return ElMessage.warning('请选择联系人')
  if (selectedFriendIds.value.length === 1) {
    try { await request.post('/conversations/private', { peerId: selectedFriendIds.value[0] }); chatCreatorVisible.value = false; fetchSessions() } catch(e) { ElMessage.error('创建失败') }
  } else { groupForm.value.name = ''; groupCreatorVisible.value = true }
}
const createGroup = async () => {
  if (!groupForm.value.name) return ElMessage.warning('请输入群名称')
  try { await request.post('/groups', { name: groupForm.value.name, memberIds: selectedFriendIds.value }); groupCreatorVisible.value = false; chatCreatorVisible.value = false; fetchSessions() } catch(e) { ElMessage.error(e.response?.data?.message||'创建失败') }
}

// ===== 群相关 =====
const publishNotice = async () => {
  if (!editGroup.value.notice) return
  try {
    await request.put(`/groups/${activeSession.value.peerId}`, { notice: editGroup.value.notice })
    groupDetail.value.notice = editGroup.value.notice
    ElMessage.success('公告已发布')
  } catch(e) { ElMessage.error('发布失败') }
}
const saveGroupInfo = async () => {
  savingGroup.value = true
  try {
    await request.put(`/groups/${activeSession.value.peerId}`, { name: editGroup.value.name, notice: editGroup.value.notice, description: editGroup.value.description, allowInvite: editGroup.value.allowInvite, avatarUrl: editGroup.value.avatarUrl })
    groupDetail.value = { ...groupDetail.value, ...editGroup.value }; activeSession.value.peerName = editGroup.value.name; activeSession.value.peerAvatar = editGroup.value.avatarUrl; fetchSessions()
    try { const res = await request.get(`/groups/${activeSession.value.peerId}`); if (res.code === 0) { groupDetail.value = res.data; groupMembers.value = res.data.members||[]; activeSession.value.peerAvatar = res.data.avatarUrl; userCache.setAll(res.data.members) } } catch(e) {}
    ElMessage.success('已保存')
  } catch(e) { ElMessage.error('保存失败') } finally { savingGroup.value = false }
}
const saveGroupNickname = async () => {
  try {
    await request.put(`/groups/${activeSession.value.peerId}/members/me/nickname`, { groupNickname: myGroupNickname.value })
    userCache.setGroupMember(activeSession.value.peerId, myUserId.value, { groupNickname: myGroupNickname.value })
    ElMessage.success('群昵称已更新')
  } catch(e) { ElMessage.error('更新失败') }
}
const inviteCandidates = computed(() => {
  const memberIds = (groupMembers.value || []).map(m => m.userId)
  const list = friends.value.filter(f => !memberIds.includes(f.userId))
  if (!inviteSearch.value) return list
  const kw = inviteSearch.value.toLowerCase()
  return list.filter(f => (f.nickname||'').toLowerCase().includes(kw) || (f.username||'').toLowerCase().includes(kw))
})
const doInviteMembers = async () => {
  if (inviteIds.value.length === 0) return
  try {
    await request.post(`/groups/${activeSession.value.peerId}/members`, { userIds: inviteIds.value })
    inviteVisible.value = false; inviteIds.value = []; inviteSearch.value = ''
    selectSession(activeSession.value)
    ElMessage.success('已邀请')
  } catch(e) { ElMessage.error('邀请失败') }
}

const loadHistory = async (reset = true) => {
  if (!activeSession.value || historyLoading.value) return
  if (reset) { historyOffset.value = 0; historyList.value = []; historyHasMore = true }
  historyLoading.value = true
  try {
    const params = { type: historyTab.value, keyword: historyKeyword.value, offset: historyOffset.value, limit: 30 }
    if (historyDate.value) params.since = historyDate.value
    const res = await request.get(`/conversations/${activeSession.value.conversationId}/messages/search`, { params })
    if (res.code === 0) {
      historyList.value = reset ? res.data.list : [...historyList.value, ...res.data.list]
      historyHasMore = res.data.hasMore
    }
  } catch(e) {} finally { historyLoading.value = false }
}
const onHistoryScroll = () => {
  const el = document.querySelector('.history-list')
  if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 40 && historyHasMore && !historyLoading.value) {
    historyOffset.value += 30; loadHistory(false)
  }
}
const jumpToDate = () => { if (historyDate.value) loadHistory() }
watch(drawerOpen, (v) => { if (v && activeSession.value?.type == 1) selectSession(activeSession.value) })
watch(historyVisible, (v) => { if (v) { historyTab.value = 'text'; historyKeyword.value = ''; historyDate.value = null; loadHistory() } })

onMounted(() => {
  const info = JSON.parse(localStorage.getItem('user_info')||'{}')
  myUserId.value = info.userId || 0; myName.value = info.nickname || info.username || ''; myAvatar.value = info.avatarUrl || ''
  fetchSessions(); fetchFriends(); connect()
})
</script>

<style scoped>
.chat-layout { flex:1; display:flex; overflow:hidden; position:relative; border-radius:0 14px 14px 0; }
.session-list { width:250px; min-width:250px; display:flex; flex-direction:column; border-right:1px solid #e2e2e2; background:#f5f5f5; }
.search-bar { height:60px; display:flex; align-items:center; padding:0 10px; gap:8px; }
.plus { cursor:pointer; font-size:20px; color:#333; }
.sessions { flex:1; overflow-y:auto; }
.session-item { display:flex; align-items:center; padding:12px 10px; cursor:pointer; gap:10px; position:relative; overflow:visible; margin:1px 8px; border-radius:8px; }
.session-item:hover { background:#ebebeb; } .session-item.active { background:#d6d6d6; }
.session-info { flex:1; min-width:0; position:relative; }
.session-top { display:flex; justify-content:space-between; align-items:center; }
.session-name-wrap { display:flex; align-items:center; gap:4px; min-width:0; overflow:hidden; }
.session-name { font-weight:500; font-size:14px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:140px; }
.session-time { font-size:11px; color:#999; white-space:nowrap; }
.group-badge { width:16px; height:16px; border-radius:3px; background:#409eff; color:#fff; font-size:10px; display:inline-flex; align-items:center; justify-content:center; flex-shrink:0; margin-right:4px; }
.star-icon { font-size:11px; margin-right:2px; }
.session-preview { font-size:12px; color:#999; margin-top:2px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:170px; }
.msg-type-tag { font-size:10px; padding:0 3px; border-radius:2px; margin-right:2px; }
.tag-text { color:#07c160; }
.tag-image { color:#1989fa; }
.tag-video { color:#9b59b6; }
.tag-file { color:#e67e22; }
.tag-audio { color:#9b59b6; }
.tag-system, .tag-announcement, .tag-recall { color:#999; }

.chat-main { flex:1; display:flex; flex-direction:column; background:#f5f6f8; border-radius:0 14px 14px 0; }
.empty-chat { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; }
.chat-header { height:56px; display:flex; align-items:center; justify-content:space-between; padding:0 20px; border-bottom:1px solid #e2e2e2; background:#fff; z-index:2; }
.chat-name { font-size:17px; font-weight:500; }
.more-icon { cursor:pointer; margin-right: 10px; font-size:20px; color:#666; }

.chat-messages { flex:1; overflow-y:auto; padding:16px 20px; display:flex; flex-direction:column; gap:2px; background:transparent; }
.loading-tip { text-align:center; font-size:12px; color:#999; padding:10px; }

.time-divider { text-align:center; padding:10px 0; }
.time-divider span { font-size:11px; color:#999; background:#e8e8e8; padding:2px 8px; border-radius:2px; }

.system-msg { text-align:center; font-size:12px; color:#999; padding:6px; }
.announcement-msg { display:flex; justify-content:center; padding:8px; }
.announcement-card { display:inline-flex; flex-direction:column; align-items:center; gap:6px; background:#fffbe6; border:1px solid #ffe58f; border-radius:8px; padding:12px 20px; max-width:300px; }
.announcement-text { font-size:14px; color:#333; text-align:center; }

.msg-row { display:flex; gap:10px; margin:4px 0; align-items:flex-start; }
.msg-self { justify-content:flex-end; }
.msg-body { max-width:60%; display:flex; flex-direction:column; }
.msg-right { align-items:flex-end; }
.sender-name-text { font-size:12px; color:#999; margin-bottom:2px; padding-left:4px; }
.role-badge { font-size:10px; padding:0 4px; border-radius:3px; margin-right:4px; color:#fff; }
.role-badge.owner { background:#FF5E00; }
.role-badge.admin { background:#07c160; }
.chat-group-tag { font-size:12px; color:#999; font-weight:400; margin-left:6px; }

.bubble { padding:9px 13px; border-radius:4px; font-size:14px; line-height:1.5; word-break:break-word; position:relative; }
.text-bubble { background:#fff; box-shadow:0 1px 3px rgba(0,0,0,0.06); position:relative; }
.msg-self .text-bubble { background:#95ec69; }
.msg-icon { position:absolute; left:-20px; top:50%; transform:translateY(-50%); }
.msg-icon.sending { color:#999; }
.msg-icon.failed { color:#fa5151; cursor:pointer; }
.recall-bubble { font-size:12px; color:#999; font-style:italic; background:#f0f0f0; }

/* 图片/视频 */
.media-card { padding:3px; background:#fff; border-radius:8px; box-shadow:0 1px 4px rgba(0,0,0,0.08); overflow:hidden; }
/* 文件 */
.file-card { display:flex; align-items:center; gap:12px; padding:12px 14px; cursor:pointer; background:#fff; border-radius:10px; box-shadow:0 1px 4px rgba(0,0,0,0.06); width:240px; }
.file-card:hover { box-shadow:0 2px 8px rgba(0,0,0,0.1); }
.file-card-icon { width:40px; height:40px; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#fff; font-size:10px; font-weight:700; flex-shrink:0; }
.file-card-body { flex:1; min-width:0; }
.file-card-name { font-size:13px; font-weight:500; color:#333; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:140px; }
.file-card-size { font-size:11px; color:#999; margin-top:1px; }
/* 语音 */
.audio-card { display:flex; align-items:center; gap:10px; padding:10px 14px; background:#fff; border-radius:20px; box-shadow:0 1px 3px rgba(0,0,0,0.06); width:120px; }
.audio-btn { cursor:pointer; font-size:18px; width:28px; text-align:center; flex-shrink:0; }
.audio-bars { display:flex; align-items:flex-end; gap:2px; flex:1; height:28px; }
.audio-bar { width:3px; border-radius:1px; background:#ddd; transition:background 0.2s; }
.audio-bar:nth-child(1) { height:12px; } .audio-bar:nth-child(2) { height:20px; }
.audio-bar:nth-child(3) { height:28px; } .audio-bar:nth-child(4) { height:20px; }
.audio-bar:nth-child(5) { height:12px; }
.audio-bar.active { background:#07c160; }
.audio-sec { font-size:12px; color:#888; flex-shrink:0; }
/* 名片 */
.card-msg { display:flex; align-items:center; gap:10px; padding:12px 14px; cursor:pointer; background:#fff; border-radius:10px; box-shadow:0 1px 4px rgba(0,0,0,0.06); width:220px; border-left:3px solid #07c160; }
.card-msg-info { flex:1; min-width:0; }
.card-msg-name { font-size:14px; font-weight:500; color:#333; }
.card-msg-tag { font-size:11px; color:#07c160; margin-top:2px; }
.card-msg-sub { font-size:11px; color:#888; margin-top:1px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:160px; }
/* 位置 */
.loc-card { display:flex; align-items:center; gap:10px; padding:12px 14px; cursor:pointer; background:#fff; border-radius:10px; box-shadow:0 1px 4px rgba(0,0,0,0.06); width:240px; }
.loc-icon { font-size:28px; flex-shrink:0; }
.loc-info { flex:1; min-width:0; }
.loc-addr { font-size:13px; color:#333; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.loc-sub { font-size:11px; color:#07c160; margin-top:2px; }
/* 贴纸 */
.sticker-card { padding:0; background:transparent; box-shadow:none; }

.card-pick-item { display:flex; align-items:center; gap:10px; padding:10px 12px; border-radius:8px; margin:2px 0; cursor:pointer; transition:background 0.15s; }
.card-pick-item:hover { background:#f5f5f5; }
.card-pick-info { flex:1; }
.card-pick-name { font-size:14px; font-weight:500; }
.card-pick-sub { font-size:12px; color:#999; }
.location-input-row { display:flex; align-items:center; }

.chat-input-area { border-top:1px solid #e2e2e2; background:#fff; border-radius:0 0 14px 0; position:relative; }
.chat-input-area :deep(.el-textarea__inner) { border:none; box-shadow:none; background:transparent; resize:none; }
.chat-input-area :deep(.el-textarea__inner:focus) { border:none; box-shadow:none; }
.toolbar { display:flex; gap:12px; padding:8px 20px 0; }
.tool-icon { cursor:pointer; font-size:18px; color:#666; }
.emoji-picker-dropdown { position:absolute; bottom:50px; left:50%; transform:translateX(-50%); z-index:100; }
.send-row { display:flex; justify-content:flex-end; padding:8px 20px 12px; }

.creator-list { max-height:300px; overflow-y:auto; }
.creator-item { padding:4px 0; }
.creator-item :deep(.el-checkbox__label) { display:flex; align-items:center; gap:6px; }
.empty-state { display:flex; justify-content:center; padding:30px; }

/* drawer */
.drawer-body { padding:16px; }
.drawer-section { margin-bottom:16px; }
.member-grid { display:flex; flex-wrap:wrap; gap:12px; margin-top:12px; }
.member-item { display:flex; flex-direction:column; align-items:center; width:60px; cursor:pointer; }
.add-member-btn { width:35px; height:35px; border:1px dashed #ccc; border-radius:4px; display:flex; align-items:center; justify-content:center; font-size:16px; color:#999; }
.member-name { font-size:11px; color:#333; margin-top:3px; text-align:center; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:60px; }
.member-popover { display:flex; flex-direction:column; align-items:center; gap:6px; padding:8px 0; }
.member-popover-name { font-size:16px; font-weight:500; }
.member-popover-sub { font-size:12px; color:#888; }
/* custom drawer */
.chat-drawer { position:absolute; top:0; right:0; bottom:0; width:280px; background:#f5f5f5; z-index:20; overflow-y:auto; box-shadow:-2px 0 8px rgba(0,0,0,0.1); }
.drawer-overlay { position:absolute; inset:0; z-index:15; }
.drawer-slide-enter-active, .drawer-slide-leave-active { transition:transform 0.25s ease; }
.drawer-slide-enter-from, .drawer-slide-leave-to { transform:translateX(100%); }
.context-menu { position:fixed; background:#fff; border-radius:6px; box-shadow:0 4px 16px rgba(0,0,0,0.12); padding:4px 0; min-width:120px; z-index:9999; }
.ctx-item { padding:8px 16px; font-size:13px; cursor:pointer; display:flex; align-items:center; gap:8px; color:#333; }
.ctx-item:hover { background:#f5f5f5; }
.ctx-overlay { position:fixed; inset:0; z-index:9998; }
.user-info-card { display:flex; flex-direction:column; align-items:center; gap:8px; padding:16px 0; }
.user-info-name { font-size:18px; font-weight:500; }
.user-info-sub { font-size:13px; color:#888; }
.svg-icon svg { vertical-align:middle; }
.file-modal-body { min-height:120px; }
.file-add-area { border:1px dashed #ddd; border-radius:8px; padding:20px; text-align:center; cursor:pointer; margin-bottom:12px; }
.file-add-area:hover { border-color:#409eff; color:#409eff; }
.file-add-text { font-size:12px; color:#999; margin-top:6px; }
.file-modal-list { max-height:260px; overflow-y:auto; }
.file-modal-item { display:flex; align-items:center; gap:10px; padding:8px 0; border-bottom:1px solid #f0f0f0; }
.file-modal-icon { width:44px; height:44px; display:flex; align-items:center; justify-content:center; overflow:hidden; }
.file-thumb { width:44px; height:44px; object-fit:cover; border-radius:4px; }
.file-modal-info { flex:1; min-width:0; }
.file-modal-name { font-size:13px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.file-modal-size { font-size:11px; color:#999; }
.file-progress { margin-top:4px; width:120px; }
.file-modal-action { display:flex; align-items:center; }
.file-empty-tip { text-align:center; color:#ccc; font-size:12px; padding:20px 0; }
.history-tabs { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; flex-wrap:wrap; gap:8px; }
.history-filter { display:flex; gap:8px; align-items:center; }
.history-list { height:420px; overflow-y:auto; }
.history-text-item { display:flex; gap:10px; padding:8px 0; border-bottom:1px solid #f5f5f5; }
.history-text-info { flex:1; }
.history-text-name { font-size:13px; color:#999; }
.history-text-time { font-size:11px; color:#bbb; margin-left:8px; }
.history-text-content { font-size:14px; margin-top:4px; }
.media-grid { display:flex; flex-wrap:wrap; gap:8px; }
.media-grid-item { width:120px; height:90px; overflow:hidden; border-radius:4px; cursor:pointer; }
.history-file-item { display:flex; gap:12px; padding:10px 0; border-bottom:1px solid #f5f5f5; cursor:pointer; align-items:center; }
.history-file-icon { width:36px; height:36px; }
.history-file-info { flex:1; }
.history-file-name { font-size:14px; }
.history-file-size { font-size:11px; color:#999; }
.header-actions { display:flex; gap:8px; align-items:center; }
.history-empty { display:flex; justify-content:center; padding-top:80px; }
.profile-card { display:flex; flex-direction:column; align-items:center; padding:8px 0; }
.profile-name { font-size:18px; font-weight:600; margin-top:8px; }
.profile-gender { font-size:14px; color:#666; margin-top:2px; }
.profile-signature { font-size:12px; color:#999; margin-top:6px; text-align:center; padding:0 8px; }
.profile-section { width:100%; margin-top:12px; padding-top:10px; border-top:1px solid #f0f0f0; }
.profile-label { font-size:12px; color:#999; margin-bottom:4px; }
.profile-row { display:flex; font-size:12px; line-height:1.8; }
.profile-row span:first-child { color:#999; width:70px; flex-shrink:0; }
.profile-row span:last-child { color:#333; word-break:break-all; }
.profile-desc { font-size:12px; color:#555; line-height:1.6; }
.profile-actions { display:flex; gap:8px; margin-top:14px; width:100%; justify-content:center; }
.private-info { display:flex; flex-direction:column; align-items:center; gap:8px; padding-top:20px; }
.private-name { font-size:18px; font-weight:500; }
.private-id { font-size:13px; color:#888; }
.drawer-actions { margin-top:16px; display:flex; flex-direction:column; gap:4px; }
.drawer-action-item { display:flex; align-items:center; gap:8px; padding:10px 0; font-size:14px; cursor:pointer; border-bottom:1px solid #f0f0f0; }
.drawer-action-item:hover { color:#07c160; }
.drawer-action-item.danger { color:#fa5151; }
.drawer-action-item.danger:hover { opacity:0.8; }
.drawer-body { padding:16px; }
.drawer-title { font-size:16px; font-weight:600; }
.drawer-header { display:flex; align-items:center; gap:12px; margin-bottom:12px; }
.drawer-header-info { flex:1; }
.drawer-sub { font-size:12px; color:#888; margin-top:2px; }
.role-prefix { font-size:10px; padding:0 4px; border-radius:3px; margin-right:3px; }
.role-prefix.owner { background:#FF5E00; color:#fff; }
.role-prefix.admin { background:#07c160; color:#fff; }
.drawer-member-card { display:flex; flex-direction:column; align-items:center; gap:4px; padding:8px 0; }
.dm-name { font-size:18px; font-weight:600; }
.dm-sub { font-size:13px; color:#888; }
.transfer-warn-text { font-size:13px; color:#fa5151; margin-bottom:12px; }
.transfer-member { display:flex; align-items:center; gap:8px; padding:8px 10px; border-radius:6px; cursor:pointer; font-size:14px; }
.transfer-member:hover { background:#f5f5f5; }
.transfer-member.selected { background:#e8f4e8; font-weight:500; }
.invite-layout { display:flex; gap:16px; height:380px; margin:8px 0; }
.invite-left, .invite-right { flex:1; display:flex; flex-direction:column; }
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
.recording-overlay { position:fixed; inset:0; background:rgba(0,0,0,0.6); z-index:3000; display:flex; align-items:center; justify-content:center; }
.recording-modal { background:#fff; border-radius:16px; padding:40px 48px; text-align:center; min-width:280px; }
.rec-wave { display:flex; justify-content:center; gap:3px; margin-bottom:16px; height:40px; align-items:flex-end; }
.rec-bar { width:4px; border-radius:2px; background:#fa5151; animation:recPulse 0.6s ease-in-out infinite alternate; }
.rec-bar:nth-child(1) { height:16px; }
.rec-bar:nth-child(2) { height:28px; }
.rec-bar:nth-child(3) { height:40px; }
.rec-bar:nth-child(4) { height:28px; }
.rec-bar:nth-child(5) { height:16px; }
@keyframes recPulse { to { opacity:0.3; transform:scaleY(0.5); } }
.rec-timer { font-size:36px; font-weight:700; color:#333; margin:8px 0; }
.rec-hint { font-size:12px; color:#999; margin-bottom:20px; }
.rec-actions { display:flex; gap:12px; justify-content:center; }
.quote-bar { display:flex; align-items:center; gap:8px; padding:8px 16px; background:#f0f0f0; border-top:1px solid #e0e0e0; font-size:12px; }
.quote-bar-user { color:#576b95; font-weight:500; white-space:nowrap; }
.quote-bar-text { flex:1; color:#888; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.quote-bar-close { cursor:pointer; color:#999; flex-shrink:0; }
.quote-inline { font-size:12px; color:#666; margin-bottom:6px; padding:6px 8px; background:rgba(0,0,0,0.04); border-radius:4px; border-left:3px solid rgba(0,0,0,0.1); }
.quote-inline-name { color:#576b95; font-weight:500; }
.quote-banner { font-size:12px; color:#888; padding:6px 10px; margin-bottom:4px; background:#f5f5f5; border-left:3px solid #c0c0c0; border-radius:3px; max-width:320px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; cursor:pointer; }
.quote-banner:hover { background:#eee; }
.drawer-section { }
.drawer-divider { height:1px; background:#e0e0e0; margin:16px 0; }
.group-avatar-upload { cursor:pointer; }
.group-avatar-hover:hover { opacity:0.75; }
.group-editable { margin-bottom:8px; }
.edit-item { margin-bottom:8px; }
.edit-item.switch-item { display:flex; justify-content:space-between; align-items:center; }
.edit-label { font-size:12px; color:#666; display:block; margin-bottom:2px; }
.edit-item.switch-item .edit-label { margin-bottom:0; }
.group-readonly { font-size:13px; }
.read-item { margin-bottom:6px; }
.read-value { color:#333; word-break:break-all; }
.member-grid { display:flex; flex-wrap:wrap; gap:12px; margin-top:12px; }
.member-item { display:flex; flex-direction:column; align-items:center; width:60px; cursor:pointer; }
.add-member-btn { width:35px; height:35px; border:1px dashed #ccc; border-radius:4px; display:flex; align-items:center; justify-content:center; font-size:16px; color:#999; }
.member-name { font-size:11px; color:#333; margin-top:3px; text-align:center; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:60px; }
.member-popover { display:flex; flex-direction:column; align-items:center; gap:6px; padding:8px 0; }
.member-popover-name { font-size:16px; font-weight:500; }
.member-popover-sub { font-size:12px; color:#888; }
/* custom drawer */
.chat-drawer { position:absolute; top:0; right:0; bottom:0; width:280px; background:#f5f5f5; z-index:20; overflow-y:auto; box-shadow:-2px 0 8px rgba(0,0,0,0.1); }
.drawer-overlay { position:absolute; inset:0; z-index:15; }
.drawer-slide-enter-active, .drawer-slide-leave-active { transition:transform 0.25s ease; }
.drawer-slide-enter-from, .drawer-slide-leave-to { transform:translateX(100%); }
.context-menu { position:fixed; background:#fff; border-radius:6px; box-shadow:0 4px 16px rgba(0,0,0,0.12); padding:4px 0; min-width:120px; z-index:9999; }
.ctx-item { padding:8px 16px; font-size:13px; cursor:pointer; display:flex; align-items:center; gap:8px; color:#333; }
.ctx-item:hover { background:#f5f5f5; }
.ctx-overlay { position:fixed; inset:0; z-index:9998; }
.user-info-card { display:flex; flex-direction:column; align-items:center; gap:8px; padding:16px 0; }
.user-info-name { font-size:18px; font-weight:500; }
.user-info-sub { font-size:13px; color:#888; }
.svg-icon svg { vertical-align:middle; }
.file-modal-body { min-height:120px; }
.file-add-area { border:1px dashed #ddd; border-radius:8px; padding:20px; text-align:center; cursor:pointer; margin-bottom:12px; }
.file-add-area:hover { border-color:#409eff; color:#409eff; }
.file-add-text { font-size:12px; color:#999; margin-top:6px; }
.file-modal-list { max-height:260px; overflow-y:auto; }
.file-modal-item { display:flex; align-items:center; gap:10px; padding:8px 0; border-bottom:1px solid #f0f0f0; }
.file-modal-icon { width:44px; height:44px; display:flex; align-items:center; justify-content:center; overflow:hidden; }
.file-thumb { width:44px; height:44px; object-fit:cover; border-radius:4px; }
.file-modal-info { flex:1; min-width:0; }
.file-modal-name { font-size:13px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.file-modal-size { font-size:11px; color:#999; }
.file-progress { margin-top:4px; width:120px; }
.file-modal-action { display:flex; align-items:center; }
.file-empty-tip { text-align:center; color:#ccc; font-size:12px; padding:20px 0; }
.history-tabs { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; flex-wrap:wrap; gap:8px; }
.history-filter { display:flex; gap:8px; align-items:center; }
.history-list { height:420px; overflow-y:auto; }
.history-text-item { display:flex; gap:10px; padding:8px 0; border-bottom:1px solid #f5f5f5; }
.history-text-info { flex:1; }
.history-text-name { font-size:13px; color:#999; }
.history-text-time { font-size:11px; color:#bbb; margin-left:8px; }
.history-text-content { font-size:14px; margin-top:4px; }
.media-grid { display:flex; flex-wrap:wrap; gap:8px; }
.media-grid-item { width:120px; height:90px; overflow:hidden; border-radius:4px; cursor:pointer; }
.history-file-item { display:flex; gap:12px; padding:10px 0; border-bottom:1px solid #f5f5f5; cursor:pointer; align-items:center; }
.history-file-icon { width:36px; height:36px; }
.history-file-info { flex:1; }
.history-file-name { font-size:14px; }
.history-file-size { font-size:11px; color:#999; }
.header-actions { display:flex; gap:8px; align-items:center; }
.history-empty { display:flex; justify-content:center; padding-top:80px; }
.profile-card { display:flex; flex-direction:column; align-items:center; padding:8px 0; }
.profile-name { font-size:18px; font-weight:600; margin-top:8px; }
.profile-gender { font-size:14px; color:#666; margin-top:2px; }
.profile-signature { font-size:12px; color:#999; margin-top:6px; text-align:center; padding:0 8px; }
.profile-section { width:100%; margin-top:12px; padding-top:10px; border-top:1px solid #f0f0f0; }
.profile-label { font-size:12px; color:#999; margin-bottom:4px; }
.profile-row { display:flex; font-size:12px; line-height:1.8; }
.profile-row span:first-child { color:#999; width:70px; flex-shrink:0; }
.profile-row span:last-child { color:#333; word-break:break-all; }
.profile-desc { font-size:12px; color:#555; line-height:1.6; }
.profile-actions { display:flex; gap:8px; margin-top:14px; width:100%; justify-content:center; }
.group-info-header { display:flex; gap:12px; align-items:center; margin-bottom:12px; }
.group-info-text { flex:1; } .group-edit-name { max-width:180px; }
.group-name-static { font-size:16px; font-weight:500; }
.group-member-count { font-size:12px; color:#999; margin-top:2px; }
.group-editable { margin-bottom:8px; }
.edit-item { margin-bottom:8px; }
.edit-item.switch-item { display:flex; justify-content:space-between; align-items:center; }
.edit-label { font-size:12px; color:#666; display:block; margin-bottom:2px; }
.edit-item.switch-item .edit-label { margin-bottom:0; }
.group-readonly { font-size:13px; }
.read-item { margin-bottom:6px; }
.read-value { color:#333; word-break:break-all; }

/* @ 提及 */
.mention-highlight { color:#1890ff; font-weight:500; }
.mention-chips { display:flex; gap:4px; flex-wrap:wrap; padding:4px 8px; }
.mention-popup { position:absolute; bottom:100%; left:8px; background:#fff; border:1px solid #e2e2e2; border-radius:8px; box-shadow:0 4px 12px rgba(0,0,0,0.1); max-height:200px; overflow-y:auto; width:200px; z-index:100; }
.mention-item { padding:8px 12px; cursor:pointer; font-size:13px; }
.mention-item:hover { background:#f0f0f0; }
.mention-empty { padding:8px 12px; color:#ccc; font-size:12px; text-align:center; }
</style>
