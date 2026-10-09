<script setup lang="ts">
import Card from "@/components/Card.vue"
import {Camera, Lock, Message, Notebook, Refresh, Select, Setting, User} from "@element-plus/icons-vue";
import {useStore} from "@/store"
import {computed, onMounted, reactive, ref} from "vue";
import {ElMessage} from "element-plus";
import router from "@/router";
import {apiUploadImage} from "@/net/api/file";
import {apiSaveAvatar} from "@/net/api/user";
import {logout} from "@/net";
import {
  apiAskEmailCode,
  apiChangePassword,
  apiModifyEmail,
  apiSaveDetails,
  apiSavePrivacy,
  apiUserDetails,
  apiUserPrivacy
} from "@/net/api/user";

const store = useStore()

const uploadingAvatar = ref(false)
const registerTime = computed(() => new Date(store.user.registerTime).toLocaleString())

const baseFormRef = ref()
const emailFormRef = ref()
const passwordFormRef = ref()

const baseForm = reactive({
  username: '',
  gender: '1',
  phone: '',
  qq: '',
  wx: '',
  desc: '',
})

const emailForm = reactive({
  email: '',
  code: '',
})

const passwordForm = reactive({
  password: '',
  newPassword: '',
  confirmPassword: '',
})

/**
 * 隐私开关：true = 公开，false = 仅自己可见。
 * 控制的是「个人主页上，别人能看到我哪些资料」，自己看自己永远全部可见。
 */
const privacy = reactive({
  phone: true,
  email: true,
  wx: true,
  qq: true,
  gender: true,
})
const privacyLoading = ref(true)

/** 只提交被改动的那一个开关，和参考实现一样「一改就存」 */
function savePrivacy(type, status) {
  apiSavePrivacy({type, status}, () => {
    ElMessage.success('已保存')
  })
}

const validateUsername = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请输入用户名'))
  } else if (!/^[a-zA-Z0-9\u4e00-\u9fa5]+$/.test(value)) {
    callback(new Error('用户名不能包含特殊字符,只能是中/英文'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    {validator: validateUsername, trigger: ['blur', 'change']},
    {min: 1, max: 10, message: '用户名长度必须在 1~10 个字符之间', trigger: ['blur', 'change']}
  ],
  email: [
    {required: true, message: '请输入邮箱地址', trigger: 'blur'},
    {type: 'email', message: '请输入合法的电子邮件地址', trigger: ['blur', 'change']}
  ],
  code: [
    {required: true, message: '请输入获取到的验证码', trigger: 'blur'},
  ],
}

// 验证码 60 秒倒计时
const counter = ref(0)
let timer = null
function startCountdown() {
  counter.value = 60
  timer = setInterval(() => {
    if (--counter.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

onMounted(() => {
  apiUserDetails(data => {
    Object.assign(baseForm, {
      // ⚠️ 用户名不在 details 里：后端 AccountDetailsVO 只有 gender/phone/qq/wx/desc，
      // 用户名属于 db_account，要从登录时拉的 store.user 取
      username: store.user.username ?? '',
      gender: String(data.gender ?? 1),
      phone: data.phone ?? '',
      qq: data.qq ?? '',
      wx: data.wx ?? '',
      desc: data.desc ?? '',
    })
  })
  // 邮箱表单默认填当前邮箱，省得用户重新输一遍
  emailForm.email = store.user.email || ''

  apiUserPrivacy(data => {
    Object.assign(privacy, data)
    privacyLoading.value = false
  }, () => {
    privacyLoading.value = false
  })
})

/** 保存基本资料 */
/**
 * 换头像：选文件 → 上传到 MinIO → 保存 URL 到账号。
 *
 * 这个操作**立即生效**（不像下面的表单要点"保存用户信息"）——
 * 换头像本来就是"点一下看到结果"的动作，多一步确认反而别扭。
 */
function pickAvatar() {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png,image/gif,image/webp'
    input.onchange = () => {
        const file = input.files && input.files[0]
        if (!file) return
        if (file.size > 5 * 1024 * 1024) {
            return ElMessage.warning('图片不能超过 5MB')
        }
        uploadingAvatar.value = true
        apiUploadImage(file, url => {
            apiSaveAvatar(url, () => {
                // 本地状态同步：右上角、AI 页、评论区都会跟着变
                store.user.avatar = url
                uploadingAvatar.value = false
                ElMessage.success('头像已更新')
            }, message => {
                uploadingAvatar.value = false
                ElMessage.warning(message)
            })
        }, message => {
            uploadingAvatar.value = false
            ElMessage.warning(message)
        })
    }
    input.click()
}

function saveDetails() {
  baseFormRef.value.validate(valid => {
    if (!valid) return

    // 空串要转成 null：后端对 phone 用了 @Pattern(手机号格式)，空串会校验失败；
    // qq/wx/desc 用的 @Length 虽然放行空串，但统一成 null 语义更准（"没填"而不是"填空"）。
    const blankToNull = v => {
      const s = (v ?? '').trim()
      return s === '' ? null : s
    }

    apiSaveDetails({
      username: baseForm.username,
      gender: Number(baseForm.gender),
      phone: blankToNull(baseForm.phone),
      qq: blankToNull(baseForm.qq),
      wx: blankToNull(baseForm.wx),
      desc: blankToNull(baseForm.desc),
    }, () => {
      ElMessage.success('个人信息已保存')
      // 顶栏显示的用户名来自 store，改了要同步，否则会一直显示旧名字
      store.user.username = baseForm.username
    })
  })
}

/** 给「新邮箱」发验证码 */
function askCode() {
  if (counter.value > 0) return
  emailFormRef.value.validateField('email', err => {
    if (err) return
    apiAskEmailCode(emailForm.email, 'modify', () => {
      ElMessage.success('验证码已发送到新邮箱，请查收')
      startCountdown()
    })
  })
}

/** 更新绑定邮箱 */
function updateEmail() {
  emailFormRef.value.validate(valid => {
    if (!valid) return
    apiModifyEmail({email: emailForm.email, code: emailForm.code}, () => {
      ElMessage.success('邮箱已更新')
      // 顶栏和本页都可能显示邮箱，顺手同步一下
      store.user.email = emailForm.email
      emailForm.code = ''
    })
  })
}

/** 修改密码 */
function changePassword() {
  passwordFormRef.value.validate(valid => {
    if (!valid) return
    apiChangePassword({password: passwordForm.password, newPassword: passwordForm.newPassword}, () => {
      ElMessage.success('密码已修改，请重新登录')
      // 走 logout：后端会把当前令牌拉黑，本地令牌也会清掉，然后回登录页
      logout(() => router.push('/'))
    })
  })
}

const passwordRules = {
  password: [{required: true, message: '请输入原密码', trigger: 'blur'}],
  newPassword: [
    {required: true, message: '请输入新密码', trigger: 'blur'},
    {min: 6, max: 20, message: '新密码长度必须在 6-20 之间', trigger: ['blur', 'change']}
  ],
  confirmPassword: [
    {required: true, message: '请再次输入新密码', trigger: 'blur'},
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: ['blur', 'change']
    }
  ],
}
</script>

<template>
  <div style="display: flex">
    <div class="setting-left">
      <card :icon="User" title="账号信息设置" desc="在这里编辑自己的个人信息">
        <el-form :model="baseForm" :rules="rules" ref="baseFormRef" label-position="top" style="margin: 0 10px 10px 10px">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="baseForm.username" maxlength="10"/>
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="baseForm.gender">
              <el-radio value="0">男</el-radio>
              <el-radio value="1">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="baseForm.phone" maxlength="11"/>
          </el-form-item>
          <el-form-item label="QQ号" prop="qq">
            <el-input v-model="baseForm.qq" maxlength="13"/>
          </el-form-item>
          <el-form-item label="微信号" prop="wx">
            <el-input v-model="baseForm.wx" maxlength="20"/>
          </el-form-item>
          <el-form-item label="个人简介" prop="desc">
            <el-input v-model="baseForm.desc" type="textarea" :rows="6" maxlength="100"/>
          </el-form-item>
          <div>
            <el-button :icon="Select" type="success" @click="saveDetails">
              保存用户信息
            </el-button>
          </div>
        </el-form>
      </card>

      <card style="margin-top: 10px" :icon="Message" title="电子邮件地址" desc="修改后需要用新邮箱收到的验证码确认">
        <el-form :rules="rules" :model="emailForm" ref="emailFormRef" label-position="top" style="margin: 0 10px 10px 10px">
          <el-form-item label="新电子邮件" prop="email">
            <el-input v-model="emailForm.email"/>
          </el-form-item>
          <el-form-item prop="code">
            <el-row style="width: 100%" :gutter="10">
              <el-col :span="18">
                <el-input placeholder="请获取验证码" v-model="emailForm.code"/>
              </el-col>
              <el-col :span="6">
                <el-button type="success" style="width: 100%" :disabled="counter > 0" @click="askCode">
                  {{ counter > 0 ? counter + 's 后重试' : '获取验证码' }}
                </el-button>
              </el-col>
            </el-row>
          </el-form-item>
          <div>
            <el-button :icon="Refresh" type="success" @click="updateEmail">
              更新电子邮件
            </el-button>
          </div>
        </el-form>
      </card>

      <card style="margin-top: 10px" :icon="Lock" title="修改密码" desc="需要先验证原密码，修改后需重新登录">
        <el-form :rules="passwordRules" :model="passwordForm" ref="passwordFormRef" label-position="top" style="margin: 0 10px 10px 10px">
          <el-form-item label="原密码" prop="password">
            <el-input v-model="passwordForm.password" type="password" show-password maxlength="20"/>
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password maxlength="20"/>
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password maxlength="20"/>
          </el-form-item>
          <div>
            <el-button :icon="Notebook" type="success" @click="changePassword">
              确认修改密码
            </el-button>
          </div>
        </el-form>
      </card>

      <card style="margin-top: 10px" :icon="Setting" title="隐私设置"
            desc="控制你的个人主页上，别人能看到哪些资料（自己看自己永远全部可见）"
            v-loading="privacyLoading">
        <div class="privacy-list">
          <el-checkbox :model-value="privacy.phone"
                       @change="v => { privacy.phone = v; savePrivacy('phone', v) }">
            公开展示我的手机号
          </el-checkbox>
          <el-checkbox :model-value="privacy.email"
                       @change="v => { privacy.email = v; savePrivacy('email', v) }">
            公开展示我的电子邮件地址
          </el-checkbox>
          <el-checkbox :model-value="privacy.wx"
                       @change="v => { privacy.wx = v; savePrivacy('wx', v) }">
            公开展示我的微信号
          </el-checkbox>
          <el-checkbox :model-value="privacy.qq"
                       @change="v => { privacy.qq = v; savePrivacy('qq', v) }">
            公开展示我的 QQ 号
          </el-checkbox>
          <el-checkbox :model-value="privacy.gender"
                       @change="v => { privacy.gender = v; savePrivacy('gender', v) }">
            公开展示我的性别
          </el-checkbox>
        </div>
        <div class="privacy-tip">
          想看效果？点帖子里的用户名进个人主页，那里显示的就是按这些开关过滤后的资料。
        </div>
      </card>
    </div>

    <div class="setting-right">
      <div style="position: sticky; top: 20px">
        <card>
          <div style="text-align: center; padding: 5px 15px 0 15px">
            <!-- 头像可点：选图 → 上传 MinIO → 立即保存（见 pickAvatar） -->
            <el-tooltip content="点击更换头像" placement="top">
              <div class="avatar-wrap" v-loading="uploadingAvatar" @click="pickAvatar">
                <el-avatar :size="70" :src="store.user.avatar">
                  {{ (store.user.username || '我').charAt(0) }}
                </el-avatar>
                <div class="avatar-mask">
                  <el-icon><Camera/></el-icon>
                </div>
              </div>
            </el-tooltip>
            <div style="font-weight: bold">你好,{{ store.user.username }}</div>
          </div>
          <el-divider style="margin: 10px 0"/>
          <!-- 个人简介：名片的正文位置放这个比放邮箱合适（邮箱下面那张卡片里已经有了） -->
          <div style="font-size: 14px;color: gray;padding: 10px">
            {{ baseForm.desc || '这个用户很懒，没有填写个人简介~' }}
          </div>
        </card>
        <card style="margin-top: 10px; font-size: 14px">
          <div>账号注册时间: {{ registerTime }}</div>
          <div style="color: grey">欢迎来到校园论坛！</div>
        </card>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 头像上传（右侧名片用） ---------- */
.avatar-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
  line-height: 0;

  .avatar-mask {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    font-size: 11px;
    color: #fff;
    background: rgba(0, 0, 0, .45);
    opacity: 0;
    transition: opacity .2s;
    line-height: 1.2;
  }

  &:hover .avatar-mask {
    opacity: 1;
  }
}

.setting-left {
  flex: 1;
  margin: 20px;
}

.setting-right {
  width: 300px;
  margin: 20px 30px 20px;
}

.privacy-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 10px;
}

.privacy-tip {
  margin: 12px 10px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
