<template>
  <el-dialog v-model="showTerms" title="用户协议与隐私政策" width="440px" top="10vh">
    <div class="terms-content">
      <h3>用户协议</h3>
      <p>欢迎使用 HappyIM（以下简称"本服务"）。</p>
      <p><b>1. 账号安全</b><br/>您有责任保护账号和密码的安全，不得将账号转借他人使用。如发现账号异常，请立即联系管理员。</p>
      <p><b>2. 隐私保护</b><br/>我们重视您的隐私。未经您的明确同意，我们不会向第三方泄露您的个人信息，法律法规另有规定的除外。</p>
      <p><b>3. 使用规范</b><br/>您承诺不会利用本服务发布、传播含有下列内容的信息：<br/>- 违反法律法规的；<br/>- 危害国家安全、泄露国家秘密的；<br/>- 侮辱、诽谤他人的；<br/>- 含有虚假、诈骗、骚扰信息的。</p>
      <p><b>4. 知识产权</b><br/>本服务的所有权利归开发者所有。用户发布的内容，视为授权本服务在合理范围内使用。</p>
      <p><b>5. 免责声明</b><br/>本服务按"现状"提供。开发者不对服务的可用性、准确性做任何明示或默示的保证。因不可抗力导致的服务中断，开发者不承担责任。</p>
      <p><b>6. 协议修改</b><br/>我们有权随时修改本协议。修改后的协议一经发布即生效。继续使用本服务即视为接受修改后的协议。</p>
      <p style="text-align:right;color:#999;margin-top:16px">HappyIM 团队</p>
    </div>
  </el-dialog>
  <div class="auth-wrapper">
    <div class="rain-container">
      <div v-for="d in raindrops" :key="d.id" class="raindrop" :style="d.style" />
    </div>
    <div class="auth-card">
      <div class="logo-area">
        <div class="logo">H</div>
        <h1 class="title">HappyIM</h1>
        <p class="subtitle">连接每一次心动 · 让沟通更简单</p>
      </div>

      <div class="form-body">
        <div class="input-group">
          <input
            type="email"
            v-model.trim="email"
            placeholder="邮箱地址"
            @keyup.enter="handleLogin"
          />
        </div>

        <div class="input-group">
          <input
            type="password"
            v-model="password"
            placeholder="登录密码"
            @keyup.enter="handleLogin"
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

        <p class="agreement-text">登录即表示同意 <a @click.stop="showTerms=true">《用户协议》</a> 和 <a @click.stop="showTerms=true">《隐私政策》</a></p>

        <button class="submit-btn" :disabled="loading" @click="handleLogin">
          {{ loading ? "登录中..." : "登 录" }}
        </button>

        <transition name="fade">
          <p class="error-msg" v-if="error">{{ error }}</p>
        </transition>
        <transition name="fade">
          <p class="success-msg" v-if="success">{{ success }}</p>
        </transition>
      </div>

      <div class="footer-links">
        <span @click="$router.push('/register')">注册账号</span>
        <span class="divider">|</span>
        <span @click="$router.push('/reset-password')">忘记密码</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request, { saveAuth } from '@/utils/request'
import { ElMessage } from 'element-plus'

const raindrops = Array.from({ length: 80 }, (_, i) => ({
  id: i,
  style: {
    left: Math.random()*100+'%',
    width: (2+Math.random()*5)+'px',
    height: (3+Math.random()*6)+'px',
    animationDuration: (6+Math.random()*10)+'s',
    animationDelay: Math.random()*12+'s',
    opacity: 0.3+Math.random()*0.4
  }
}))

const router = useRouter()

const email = ref('')
const password = ref('')
const code = ref('')
const showTerms = ref(false)
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
      type: 'login'
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

const handleLogin = async () => {
  error.value = ''
  success.value = ''

  if (!email.value || !password.value || !code.value) {
    error.value = '请填写邮箱、密码和验证码'
    return
  }

  loading.value = true
  try {
    const res = await request.post('/auth/login', {
      email: email.value,
      password: password.value,
      code: code.value
    })

    if (res.code === 0) {
      saveAuth(res.data)
      ElMessage.success('登录成功')
      router.push('/chat')
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
/* 雨滴容器 */
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

.subtitle { font-size:13px; color:#999; margin-top:8px; margin-bottom:0; }
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

.captcha-row input {
  flex: 1;
}

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

.agreement-text { font-size:12px; color:#999; text-align:center; margin-top:-8px; }
.agreement-text a { color:#576b95; cursor:pointer; }
.agreement-text a:hover { text-decoration:underline; }
.agreement-check { font-size:12px; color:#888; display:flex; align-items:center; gap:4px; margin:10px 0; }
.agreement-check input { accent-color:#07c160; }
.agreement-check a { color:#576b95; cursor:pointer; }
.agreement-check a:hover { text-decoration:underline; }
.terms-content { font-size:13px; line-height:1.8; color:#555; max-height:60vh; overflow-y:auto; padding-right:8px; }
.terms-content h3 { font-size:16px; color:#333; margin-bottom:12px; text-align:center; }
.terms-content p { margin-bottom:10px; }

.footer-links {
  margin-top: 28px;
  text-align: center;
  font-size: 13px;
  color: #576b95;
}

.footer-links span { cursor: pointer; }

.divider { color: #e0e0e0; margin: 0 12px; cursor: default !important; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
