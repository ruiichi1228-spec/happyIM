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
        <h1 class="title">重置密码</h1>
      </div>

      <div class="form-body">
        <div class="input-group">
          <input
            type="email"
            v-model.trim="email"
            placeholder="注册邮箱"
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
            v-model="newPassword"
            placeholder="新密码（至少8位）"
          />
        </div>

        <div class="input-group">
          <input
            type="password"
            v-model="confirmPassword"
            placeholder="确认新密码"
            @keyup.enter="handleReset"
          />
        </div>

        <button class="submit-btn" :disabled="loading" @click="handleReset">
          {{ loading ? "提交中..." : "确认修改" }}
        </button>

        <transition name="fade">
          <p class="error-msg" v-if="error">{{ error }}</p>
        </transition>
        <transition name="fade">
          <p class="success-msg" v-if="success">{{ success }}</p>
        </transition>
      </div>

      <div class="footer-links">
        <span @click="$router.push('/login')">返回登录</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()

const email = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const success = ref('')
const loading = ref(false)
const countdown = ref(0)

const handleSendCode = async () => {
  error.value = ''
  success.value = ''

  if (!email.value) {
    error.value = '请输入注册邮箱'
    return
  }

  try {
    const res = await request.post('/auth/send-code', {
      email: email.value,
      type: 'reset-password'
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

const handleReset = async () => {
  error.value = ''
  success.value = ''

  if (!email.value || !code.value || !newPassword.value) {
    error.value = '请填写邮箱、验证码和新密码'
    return
  }

  if (newPassword.value.length < 8) {
    error.value = '密码长度不能少于8位'
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    const res = await request.post('/auth/reset-password', {
      email: email.value,
      newPassword: newPassword.value,
      code: code.value
    })

    if (res.code === 0) {
      ElMessage.success('密码重置成功，请重新登录')
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
}

.logo-area {
  margin-bottom: 36px;
  text-align: center;
}

.logo {
  width: 56px;
  height: 56px;
  background: #07c160;
  border-radius: 12px;
  margin: 0 auto 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: bold;
  color: #fff;
}

.title {
  font-size: 22px;
  color: #1a1a1a;
  letter-spacing: 1px;
  font-weight: 500;
}

.input-group {
  margin-bottom: 14px;
}

.input-group input {
  width: 100%;
  box-sizing: border-box;
  padding: 13px 16px;
  background: #f7f7f7;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 15px;
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
  padding: 14px;
  margin-top: 18px;
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

.error-msg {
  color: #fa5151;
  font-size: 13px;
  margin-top: 14px;
  text-align: center;
}

.success-msg {
  color: #07c160;
  font-size: 13px;
  margin-top: 14px;
  text-align: center;
}

.footer-links {
  margin-top: 28px;
  text-align: center;
  font-size: 13px;
  color: #576b95;
}

.footer-links span { cursor: pointer; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
