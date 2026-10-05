import axios from "axios";
import {ElMessage} from "element-plus";
import router from "@/router";

const authItemName = "access_token"

/**
 * 请求失败兜底。
 *
 * 401 分两种，必须区别对待：
 *   1) /api/auth/** 的 401 = 「凭据错误」（登录时用户名/密码不对），要原样显示后端文案；
 *   2) 其它接口的 401 = 「登录态失效」，清本地令牌并引导去登录页。
 * 早先这里只弹一条 warning，令牌缺失/过期时页面会停在原地、只留一行控制台日志，
 * 看起来就像接口坏了，需要自己猜「是不是没登录」。
 */
const defaultFailure = (message, code, url) => {
    console.warn(`请求地址:${url}, 状态码:${code},错误信息:${message}`)
    const isAuthApi = (url || '').startsWith('/api/auth/')
    if (code === 401 && !isAuthApi) {
        deleteAccessToken()
        ElMessage.warning('登录已失效，请重新登录')
        if (router.currentRoute.value.path !== '/') {
            router.push('/')
        }
        return
    }
    ElMessage.warning(message)
}

const defaultError = (err) => {
    console.error(err)
    ElMessage.warning('发生了一些错误，请联系管理员')
}

function takeAccessToken(){
    const str = localStorage.getItem(authItemName) || sessionStorage.getItem(authItemName)
    if(!str) return null;

    let authObj
    try {
        authObj = JSON.parse(str)
    } catch (e) {
        // 存储内容坏了（手改过 / 旧格式）：当作没有登录态，别让整个请求流程崩掉
        deleteAccessToken()
        return null
    }

    // ⚠️ 字段名是 expires，不是 expire。
    // 后端 AuthorizeVO 里叫 expires，旧代码写成 authObj.expire 会取到 undefined，
    // 而 undefined <= Date 恒为 false —— 过期检查因此一直没生效。
    if (authObj.expires && new Date(authObj.expires).getTime() <= Date.now()) {
        deleteAccessToken()
        ElMessage.warning('登陆状态已过期，请重新登录')
        return null
    }
    return authObj.token
}

function storeAccessToken(token, remember, expires){
    const authObj = {token: token, expires: expires}
    const str = JSON.stringify(authObj)
    if(remember)
        localStorage.setItem(authItemName, str)
    else
        sessionStorage.setItem(authItemName, str)
}

function accessHeader() {
    // 只取一次：takeAccessToken 有副作用（可能删令牌并弹提示），调两次会弹两遍
    const token = takeAccessToken();
    return token ? {'Authorization': `Bearer ${token}`} : {}
}

function deleteAccessToken(){
    localStorage.removeItem(authItemName)
    sessionStorage.removeItem(authItemName)
}

function internalPost(url, data, header, success, failure, error = defaultError){
    axios.post(url, data, { headers: header }).then(({data}) =>{
        if(data.code === 200){
            success(data.data)
        }else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}

function internalGet(url, header, success, failure, error = defaultError){
    axios.get(url, { headers: header }).then(({data}) =>{
        if(data.code === 200){
            success(data.data)
        }else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}

function get(url, success, failure = defaultFailure) {
    internalGet(url, accessHeader(), success, failure)
}

function post(url, data, success, failure = defaultFailure){
    internalPost(url, data, accessHeader(), success, failure)
}

/**
 * 登录。
 * 凭据是**邮箱**：后端 SecurityConfiguration 里配了 .usernameParameter("email")，
 * 表单字段名必须是 email，用 username 会取不到值、永远登录失败。
 */
function login(email, password, remember, success, failure = defaultFailure){
    internalPost('/api/auth/login', {
        email: email,
        password: password,
    }, {
        'Content-Type': 'application/x-www-form-urlencoded'
    },(data) =>{
        // 后端返回的字段是 expires（见 AuthorizeVO）
        storeAccessToken(data.token, remember, data.expires)
        ElMessage.success(`登录成功,欢迎${data.username} 来到系统`)
        success(data)
    }, failure)
}

function logout (success, failure = defaultFailure) {
    get('/api/auth/logout', () =>{
        deleteAccessToken()
        ElMessage.success('退出登录成功，欢迎您再次使用')
        success()
    }, failure)

}

function unauthorized() {
    return !takeAccessToken()
}

// accessHeader 也导出：将来需要手动带鉴权头的场景（如上传）可直接复用
export { login, logout, get, post, unauthorized, accessHeader}
