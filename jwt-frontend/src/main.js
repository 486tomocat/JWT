import { createApp } from 'vue'
import App from './App.vue'
import router from "@/router/index.js";
import axios from "axios";
import { createPinia} from "pinia";

import 'element-plus/theme-chalk/dark/css-vars.css'

// ElMessageBox 是命令式 API（ElMessageBox.confirm(...)），不走模板组件那条自动按需引入的路径；
// 而代码里又是显式 import 的，等于绕开了 resolver 的样式注入 —— 结果确认框一点 CSS 都没有：
// 不居中（缺 .el-overlay-message-box）、没有遮罩和背景（缺 .el-overlay/.el-message-box 的 background-color）。
// 这里显式补上。将来再用别的命令式组件（如 ElNotification）时照此加一行。
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
