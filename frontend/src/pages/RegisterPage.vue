<template>
  <div class="auth-wrapper">
    <div class="rain-container">
      <div v-for="i in 80" :key="i" class="raindrop" :style="{
        left: Math.random()*100+'%',
        width: (2+Math.random()*5)+'px', height: (3+Math.random()*6)+'px',
        animationDuration: (6+Math.random()*10)+'s',
        animationDelay: Math.random()*12+'s',
        opacity: 0.3+Math.random()*0.4
      }" />
    </div>
    <div class="auth-card">
      <div class="logo-area">
        <div class="logo">H</div>
        <h1 class="title">创建账号</h1>
        <p class="subtitle">加入 HappyIM，开启即时通讯新体验</p>
      </div>

      <div class="form-body">
        <div class="input-group">
          <input
            type="text"
            v-model.trim="username"
            placeholder="用户名（5位以上字母数字下划线）"
          />
        </div>

        <div class="input-group">
          <input
            type="text"
            v-model.trim="nickname"
            placeholder="昵称"
          />
        </div>

        <div class="input-group">
          <input
            type="email"
            v-model.trim="email"
            placeholder="邮箱地址"
          />
        </div>

        <div class="input-group captcha-row">
          <input
            type="text"
            v-model.trim="code"
            placeholder="邮箱验证码"
            maxlength="6"
          />
          <button
            class="get-code-btn"
            :disabled="countdown > 0"
            @click="handleSendCode"
          >
            {{ countdown > 0 ? `${countdown}s` : "获取验证码" }}
          </button>
        </div>

        <div class="input-group">
          <input
            type="password"
            v-model="password"
            placeholder="设置密码（至少8位）"
          />
        </div>

        <div class="input-group">
          <input
            type="password"
            v-model="confirmPassword"
            placeholder="确认密码"
            @keyup.enter="handleRegister"
          />
        </div>

        <div class="avatar-section">
          <p class="avatar-label">选择头像</p>
          <div class="avatar-grid">
            <div
              v-for="(url, name) in avatarMap"
              :key="name"
              class="avatar-item"
              :class="{ selected: selectedAvatar === name }"
              @click="selectedAvatar = name"
            >
              <img :src="url" :alt="name" />
            </div>
          </div>
        </div>

        <button class="submit-btn" :disabled="loading" @click="handleRegister">
          {{ loading ? "注册中..." : "注 册" }}
        </button>

        <transition name="fade">
          <p class="error-msg" v-if="error">{{ error }}</p>
        </transition>
        <transition name="fade">
          <p class="success-msg" v-if="success">{{ success }}</p>
        </transition>
      </div>

      <div class="footer-links">
        <span @click="$router.push('/login')">已有账号？返回登录</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()

const avatarModules = import.meta.glob('@/assets/default_avatar/*.png', { eager: true })
const avatarMap = computed(() => {
  const map = {}
  for (const [path, mod] of Object.entries(avatarModules)) {
    const name = path.split('/').pop()
    map[name] = mod.default
  }
  return map
})
const avatarNames = computed(() => Object.keys(avatarMap.value))
const selectedAvatar = ref(avatarNames.value[0] || '')

const username = ref('')
const nickname = ref('')
const email = ref('')
const code = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const success = ref('')
const loading = ref(false)
const countdown = ref(0)

const handleSendCode = async () => {
  error.value = ''
  success.value = ''

  if (!email.value) {
    error.value = '请输入邮箱地址'
    return
  }

  try {
    const res = await request.post('/auth/send-code', {
      email: email.value,
      type: 'register'
    })
    if (res.code === 0) {
      success.value = '验证码已发送至邮箱'
      ElMessage.success('验证码已发送')
      startCountdown()
    } else {
      error.value = res.message
    }
  } catch (err) {
    error.value = err.response?.data?.message || '发送失败，请稍后再试'
  }
}

const handleRegister = async () => {
  error.value = ''
  success.value = ''

  if (!username.value || !nickname.value || !email.value || !code.value || !password.value) {
    error.value = '请填写完整的注册信息'
    return
  }

  if (!/^[a-zA-Z0-9_]{5,64}$/.test(username.value)) {
    error.value = '用户名需5位以上字母数字下划线'
    return
  }

  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  if (password.value.length < 8) {
    error.value = '密码长度不能少于8位'
    return
  }

  loading.value = true
  try {
    const res = await request.post('/auth/register', {
      username: username.value,
      nickname: nickname.value,
      email: email.value,
      password: password.value,
      code: code.value,
      avatarUrl: selectedAvatar.value
    })

    if (res.code === 0) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      error.value = res.message
    }
  } catch (err) {
    error.value = err.response?.data?.message || '连接服务器失败'
  } finally {
    loading.value = false
  }
}

const startCountdown = () => {
  countdown.value = 60
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}
</script>

<style scoped>
.auth-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: url('https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1920&q=80') center/cover;
  position: relative;
  overflow: hidden;
}
.auth-wrapper::before {
  content:''; position:absolute; inset:0; pointer-events:none;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  background: rgba(20,25,40,0.5);
}
.rain-container { position:absolute; inset:0; pointer-events:none; overflow:hidden; }
.raindrop {
  position:absolute; top:-10%; border-radius:50% 50% 50% 50% / 60% 60% 30% 30%;
  background: url('https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1920&q=80') center/cover fixed;
  transform:rotate(180deg) scaleX(-1);
  box-shadow: inset 0 0 4px rgba(255,255,255,0.25), 0 0 3px rgba(0,0,0,0.15);
  animation: rainDrop linear infinite;
  pointer-events:none;
}
@keyframes rainDrop {
  0% { top:-10%; opacity:0; }
  3% { opacity:0.7; }
  95% { opacity:0.6; }
  100% { top:110%; opacity:0; }
}

.auth-card {
  position:relative; z-index:1;
  width: 400px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 48px 44px;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15), 0 0 0 1px rgba(255,255,255,0.3);
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}

.logo-area {
  margin-bottom: 28px;
  text-align: center;
}

.logo {
  width: 52px;
  height: 52px;
  background: linear-gradient(135deg, #4a9e6e, #2d7a4a);
  border-radius: 14px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  box-shadow: 0 4px 12px rgba(45,122,74,0.25);
}
.subtitle { font-size:13px; color:#999; margin-top:10px; margin-bottom:0; text-align:center; }

.title {
  font-size: 20px;
  color: #1a1a1a;
  font-weight: 500;
}

.input-group {
  margin-bottom: 12px;
}

.input-group input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  background: #f7f7f7;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s;
}

.input-group input:focus {
  background: #fff;
  border-color: #07c160;
  outline: none;
  box-shadow: 0 0 0 3px rgba(7, 193, 96, 0.08);
}

.captcha-row {
  display: flex;
  gap: 10px;
}

.captcha-row input { flex: 1; }

.get-code-btn {
  width: 110px;
  background: #f7f7f7;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 13px;
  color: #576b95;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.3s;
}

.get-code-btn:hover:not(:disabled) {
  background: #eee;
  border-color: #07c160;
  color: #07c160;
}

.get-code-btn:disabled {
  color: #ccc;
  cursor: not-allowed;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  margin-top: 16px;
  background-color: #07c160;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.3s;
}

.submit-btn:hover { background-color: #06ad56; }

.submit-btn:disabled {
  background-color: #a5e5c5;
  cursor: not-allowed;
}

.avatar-section {
  margin: 16px 0;
}

.avatar-label {
  font-size: 13px;
  color: #888;
  margin-bottom: 10px;
}

.avatar-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}

.avatar-item {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 3px solid transparent;
  transition: border-color 0.2s, transform 0.2s;
  box-sizing: border-box;
}

.avatar-item:hover {
  transform: scale(1.1);
}

.avatar-item.selected {
  border-color: #07c160;
}

.avatar-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.error-msg {
  color: #fa5151;
  font-size: 13px;
  margin-top: 12px;
  text-align: center;
}

.success-msg {
  color: #07c160;
  font-size: 13px;
  margin-top: 12px;
  text-align: center;
}

.footer-links {
  margin-top: 22px;
  text-align: center;
  font-size: 13px;
  color: #576b95;
}

.footer-links span { cursor: pointer; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
