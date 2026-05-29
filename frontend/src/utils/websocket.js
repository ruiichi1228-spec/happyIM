import { ref } from 'vue'

let socket = null
let pingTimer = null
const handlers = []
const connected = ref(false)

export function useWebSocket() {
  const connect = () => {
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return
    const token = localStorage.getItem('access_token')
    if (!token) return

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    socket = new WebSocket(`${protocol}//${location.hostname}:8080/ws?token=${token}`)

    socket.onopen = () => {
      connected.value = true
      pingTimer = setInterval(() => {
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ action: 'ping' }))
        }
      }, 30000)
    }

    socket.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        handlers.forEach(h => h(msg))
      } catch (_) {}
    }

    socket.onclose = () => {
      connected.value = false
      clearInterval(pingTimer)
      setTimeout(connect, 3000)
    }
  }

  const send = (data) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(data))
    }
  }

  const onMessage = (handler) => {
    handlers.push(handler)
    return () => {
      const idx = handlers.indexOf(handler)
      if (idx >= 0) handlers.splice(idx, 1)
    }
  }

  return { connect, send, onMessage, connected }
}
