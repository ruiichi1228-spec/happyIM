import { ref, watch } from 'vue'

const KEY = 'happyim_theme'
const saved = localStorage.getItem(KEY)
const isDark = ref(saved === 'dark')

function apply(val) {
  document.documentElement.classList.toggle('dark', val)
  localStorage.setItem(KEY, val ? 'dark' : 'light')
}

apply(isDark.value)

watch(isDark, apply)

export function useTheme() {
  const toggle = () => { isDark.value = !isDark.value }
  return { isDark, toggle }
}
