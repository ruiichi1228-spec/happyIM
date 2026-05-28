let muted = false
const audioCtx = new (window.AudioContext || window.webkitAudioContext)()

function playTone(freq, duration, type = 'sine') {
  if (muted) return
  try {
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    osc.type = type
    osc.frequency.setValueAtTime(freq, audioCtx.currentTime)
    gain.gain.setValueAtTime(0.15, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration)
    osc.connect(gain)
    gain.connect(audioCtx.destination)
    osc.start(); osc.stop(audioCtx.currentTime + duration)
  } catch(e) {}
}

// 消息：清脆短音
export function playMsgSound() {
  playTone(800, 0.1, 'sine')
  setTimeout(() => playTone(1200, 0.1, 'sine'), 100)
}

// 好友请求：叮咚
export function playFriendSound() {
  playTone(600, 0.15, 'triangle')
  setTimeout(() => playTone(900, 0.2, 'triangle'), 150)
}

// 朋友圈：柔和升调
export function playMomentSound() {
  playTone(400, 0.12, 'sine')
  setTimeout(() => playTone(600, 0.12, 'sine'), 120)
  setTimeout(() => playTone(800, 0.15, 'sine'), 240)
}

// 广场：三连音
export function playSquareSound() {
  playTone(500, 0.08, 'square')
  setTimeout(() => playTone(700, 0.08, 'square'), 80)
  setTimeout(() => playTone(900, 0.1, 'square'), 160)
}

export function isMuted() { return muted }

export function toggleMute() {
  muted = !muted
  return muted
}
