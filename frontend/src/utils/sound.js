let muted = false

// 预加载音频
const sounds = {
  msg: new Audio('/sounds/msg.wav'),
  friend: new Audio('/sounds/friend.wav'),
  moment: new Audio('/sounds/moment.wav'),
  square: new Audio('/sounds/square.wav'),
}

function play(name) {
  if (muted) return
  try {
    const a = sounds[name]
    if (a) {
      a.currentTime = 0
      a.play().catch(() => {})
    }
  } catch(e) {}
}

export function playMsgSound() { play('msg') }
export function playFriendSound() { play('friend') }
export function playMomentSound() { play('moment') }
export function playSquareSound() { play('square') }

export function isMuted() { return muted }

export function toggleMute() {
  muted = !muted
  return muted
}
