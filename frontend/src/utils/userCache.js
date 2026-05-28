import { reactive } from 'vue'
import request from '@/utils/request'

const users = reactive({})      // uid → { nickname, avatarUrl, gender, remark, isFriend }
const groupMembers = reactive({}) // groupId → { uid → { groupNickname, role } }

let pendingUids = []
let batchTimer = null
let batchResolve = null

function flush() {
  const uids = [...new Set(pendingUids.filter(uid => !users[uid]))]
  pendingUids = []
  batchTimer = null
  const resolve = batchResolve
  batchResolve = null
  if (uids.length === 0) { resolve && resolve(); return }

  console.log('[userCache] batch fetch:', uids)
  request.post('/users/batch', uids).then(res => {
    if (res.code === 0 && res.data) {
      res.data.forEach(u => set(u.userId, u))
      console.log('[userCache] batch fetched', res.data.length, 'users, cache size:', Object.keys(users).length)
    }
    resolve && resolve()
  }).catch(() => {
    resolve && resolve()
  })
}

export function useUserCache() {
  const get = (uid) => {
    if (!uid) return null
    return users[uid] || null
  }

  const set = (uid, data) => {
    if (!uid || !data) return
    if (!users[uid]) {
      console.log('[userCache] set', uid, data.nickname || data.displayName)
    }
    const existing = users[uid] || {}
    users[uid] = { ...existing, userId: +uid, ...data }
  }

  const setAll = (list) => {
    if (!list || !list.length) return
    console.log('[userCache] setAll', list.length, 'entries')
    list.forEach(u => {
      const id = u.userId || u.id
      if (id) set(id, u)
    })
  }

  // 批量请求：收集 uid，300ms 内合并成一次 POST
  const batchFetch = (uids) => {
    if (!uids || !uids.length) return Promise.resolve()
    const missing = [...new Set(uids)].filter(uid => uid && !users[uid])
    if (missing.length === 0) return Promise.resolve()

    pendingUids.push(...missing)
    if (!batchTimer) {
      return new Promise(resolve => {
        batchResolve = resolve
        batchTimer = setTimeout(flush, 300)
      })
    } else {
      return new Promise(resolve => {
        const orig = batchResolve
        batchResolve = () => { orig && orig(); resolve() }
      })
    }
  }

  // 群里某个成员的昵称，按需加载
  const setGroupMember = (gid, uid, data) => {
    if (!groupMembers[gid]) groupMembers[gid] = reactive({})
    groupMembers[gid][uid] = { ...groupMembers[gid][uid], ...data }
    console.log('[userCache] setGroupMember', gid, uid, data.groupNickname)
  }

  const getGroupMember = (gid, uid) => {
    return groupMembers[gid]?.[uid] || null
  }

  const fetchGroupMember = async (gid, uid) => {
    const gm = getGroupMember(gid, uid)
    if (gm && gm.groupNickname !== undefined) return gm
    try {
      const res = await request.get(`/groups/${gid}/members/${uid}`)
      if (res.code === 0 && res.data) {
        setGroupMember(gid, uid, res.data)
        return res.data
      }
    } catch (e) { /* ignore */ }
    return gm
  }

  // 展示名：remark > groupNickname > nickname
  const displayName = (uid, gid) => {
    const u = users[uid]
    if (!u) return String(uid)
    if (u.remark) return u.remark
    if (gid && groupMembers[gid]?.[uid]?.groupNickname) return groupMembers[gid][uid].groupNickname
    return u.nickname || String(uid)
  }

  const avatarUrl = (uid) => {
    return users[uid]?.avatarUrl || ''
  }

  return { get, set, setAll, batchFetch, setGroupMember, getGroupMember, fetchGroupMember, displayName, avatarUrl }
}
