import { createApp } from 'vue'
import App from './App.vue'
import router from "@/router/index.js";
import axios from "axios";
import { createPinia} from "pinia";

import 'element-plus/theme-chalk/dark/css-vars.css'

// 【命令式组件的样式要手工补】
//
// ElMessage / ElMessageBox / ElNotification 这类是**命令式 API**
// （ElMessage.success(...)、ElMessageBox.confirm(...)），它们不走模板组件那条
// 自动按需引入的路径；而代码里又是显式 import 的，等于绕开了 ElementPlusResolver
// 的样式注入 —— 结果组件一点 CSS 都没有。
//
// 典型表现：
//   ElMessage     缺 .el-message   → 少了 position:fixed，提示条掉到页面底部
//   ElMessageBox  缺 .el-overlay*  → 弹窗不居中、没有遮罩和背景
//
// 将来再用别的命令式组件（如 ElNotification）时，照此加一行对应样式即可。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'

// 默认用相对路径：请求打到当前源（dev 时是 vite 的 5173），
// 再由 vite.config.js 里的 /api 代理转发到后端（8081）。
// 这样换后端端口只需要改 vite.config.js 一处，dev 环境也不会碰到跨域。
// 若将来前端与后端不同域部署，在 jwt-frontend/.env 里设 VITE_API_BASE=http://host:port 覆盖。
axios.defaults.baseURL = import.meta.env.VITE_API_BASE || ""

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
