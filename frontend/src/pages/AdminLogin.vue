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
        <div class="logo">A</div>
        <h1 class="title">管理后台</h1>
        <p class="subtitle">HappyIM Admin Console</p>
      </div>

      <div class="form-body">
        <div class="input-group">
          <input
            type="text"
            v-model.trim="username"
            placeholder="管理员用户名"
            @keyup.enter="handleLogin"
          />
        </div>

        <div class="input-group">
          <input
            type="password"
            v-model="password"
            placeholder="管理员密码"
            @keyup.enter="handleLogin"
          />
        </div>

        <button class="submit-btn" :disabled="loading" @click="handleLogin">
          {{ loading ? "登录中..." : "登 录" }}
        </button>

        <transition name="fade">
          <p class="error-msg" v-if="error">{{ error }}</p>
        </transition>
      </div>

      <div class="footer-links">
        <span @click="$router.push('/login')">返回用户登录</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

const handleLogin = async () => {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入管理员用户名和密码'
    return
  }

  loading.value = true
  try {
    const res = await axios.post('/api/admin/login', {
      username: username.value,
      password: password.value
    })
    if (res.data.code === 0) {
      const d = res.data.data
      localStorage.setItem('admin_token', d.token)
      localStorage.setItem('admin_info', JSON.stringify({
        adminId: d.adminId,
        username: d.username,
        nickname: d.nickname
      }))
      ElMessage.success('登录成功')
      router.push('/admin/dashboard')
    } else {
      error.value = res.data.message
    }
  } catch (err) {
    error.value = err.response?.data?.message || '连接服务器失败'
  } finally {
    loading.value = false
  }
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

.logo-area { margin-bottom: 28px; text-align: center; }
.logo {
  width: 52px; height: 52px;
  background: linear-gradient(135deg, #e6a23c, #c4561a);
  border-radius: 14px; margin: 0 auto 16px;
  display: flex; align-items: center; justify-content: center;
  font-size: 26px; font-weight: 700; color: #fff;
  box-shadow: 0 4px 12px rgba(198, 86, 26, 0.25);
}
.subtitle { font-size:13px; color:#999; margin-top:-8px; margin-bottom:0; text-align:center; }
.title { font-size: 20px; color: #1a1a1a; font-weight: 500; }

.input-group { margin-bottom: 12px; }
.input-group input {
  width: 100%; box-sizing: border-box;
  padding: 12px 14px; background: #f7f7f7;
  border: 1px solid transparent; border-radius: 8px;
  font-size: 14px; transition: all 0.3s;
}
.input-group input:focus {
  background: #fff; border-color: #e6a23c; outline: none;
  box-shadow: 0 0 0 3px rgba(230, 162, 60, 0.08);
}

.submit-btn {
  width: 100%; padding: 12px; margin-top: 16px;
  background-color: #e6a23c; color: #fff;
  border: none; border-radius: 8px;
  font-size: 16px; font-weight: 500; cursor: pointer; transition: background 0.3s;
}
.submit-btn:hover { background-color: #cf9236; }
.submit-btn:disabled { background-color: #f0d19b; cursor: not-allowed; }

.error-msg { color: #fa5151; font-size: 13px; margin-top: 12px; text-align: center; }
.footer-links { margin-top: 22px; text-align: center; font-size: 13px; color: #576b95; }
.footer-links span { cursor: pointer; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
