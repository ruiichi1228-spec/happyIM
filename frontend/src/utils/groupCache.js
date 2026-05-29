import { reactive } from 'vue'
import request from '@/utils/request'

const groups = reactive({})

let pending = []
let timer = null
let resolvePending = null

function flush() {
  const ids = [...new Set(pending.filter(id => !groups[id]))]
  pending = []
  timer = null
  const resolve = resolvePending
  resolvePending = null
  if (ids.length === 0) { resolve && resolve(); return }

  request.post('/groups/batch', ids).then(res => {
    if (res.code === 0 && res.data) {
      res.data.forEach(g => set(g.groupId || g.id, g))
    }
    resolve && resolve()
  }).catch(() => {
    resolve && resolve()
  })
}

export function useGroupCache() {
  const get = (gid) => {
    if (!gid) return null
    return groups[gid] || null
  }

  const set = (gid, data) => {
    if (!gid || !data) return
    groups[gid] = { ...groups[gid], groupId: +gid, ...data }
  }

  const batchFetch = (gids) => {
    if (!gids || !gids.length) return Promise.resolve()
    const missing = [...new Set(gids)].filter(id => id && !groups[id])
    if (missing.length === 0) return Promise.resolve()

    pending.push(...missing)
    if (!timer) {
      return new Promise(resolve => {
        resolvePending = resolve
        timer = setTimeout(flush, 300)
      })
    } else {
      return new Promise(resolve => {
        const orig = resolvePending
        resolvePending = () => { orig && orig(); resolve() }
      })
    }
  }

  return { get, set, batchFetch }
}
