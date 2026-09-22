<script setup lang="ts">
import Card from  "@/components/Card.vue"
import {Message, Notebook, Refresh, Select, User} from "@element-plus/icons-vue";
import {useStore} from  "@/store"
import {computed, reactive, ref} from "vue";

const store = useStore()
const registerTime = computed(() => new Date(store.user.registerTime).toLocaleString())

const baseFormRef = ref()
const emailFormRef = ref()

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

const validateUsername = (rule, value, callback) => {
  if(value === '') {
    callback(new Error('请输入用户名'))
  } else if(!/^[a-zA-Z0-9\u4e00-\u9fa5]+$/.test(value)){
    callback(new Error('用户名不能包含特殊字符,只能是中/英文'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    {validator: validateUsername, trigger: ['blur', 'change']},
    {min: 1, max: 10, message: '用户名长度必须在2~8个字符之间', trigger: ['blur', 'change']}
  ],
  email: [
    {required: true, message:'请输入邮箱地址', trigger: 'blur' },
    {type: 'email', message: '请输入合法的电子邮件地址', trigger: ['blur', 'change']}
  ],
  code: [
    {required: true, message:'请输入获取到的验证码', trigger: 'blur' },
  ]
}

function saveDetails() {
  baseFormRef.value.validate
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
              <el-radio label=“0“>男</el-radio>
              <el-radio label="1">女</el-radio>
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
            <el-button :icon="Select" type="success">
              保存用户信息
            </el-button>
          </div>
        </el-form>
      </card>
      <card style="margin-top: 10px" :icon="Message" title="电子邮件地址" desc="你可以在这里编辑你的邮件地址">
        <el-form :rules="rules" :model="emailForm" ref="emailFormRef" label-position="top" style="margin: 0 10px 10px 10px">
          <el-form-item label="电子邮件" prop="email">
            <el-input v-model="emailForm.email"/>
          </el-form-item>
          <el-form-item prop="code">
            <el-row style="width: 100%" :gutter="10">
              <el-col :span="18">
                <el-input placeholder="请获取验证码" v-model="emailForm.code"/>
              </el-col>
              <el-col :span="6">
                <el-button type="success" style="width: 100%">获取验证码</el-button>
              </el-col>
            </el-row>
          </el-form-item>
          <div>
            <el-button :icon="Refresh" type="success">
              更新电子邮件
            </el-button>
          </div>
        </el-form>
      </card>
    </div>
    <div class="setting-right">
      <div style="position: sticky; top: 20px">
        <card>
          <div style="text-align: center; padding: 5px 15px 0 15px">
            <el-avatar :size="70" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"/>
            <div style="font-weight: bold">你好,{{store.user.username}}</div>
          </div>
          <el-divider style="margin: 10px 0"/>
          <div style="font-size: 14px;color: gray;padding: 10px">；
            《以撒的结合：重生（The Binding of Isaac: Rebirth）》
            是一款集合了推箱子、血统检验、少女换装、宠物养成等等元素的地牢探险/Roguelite类跨世代大作。
          </div>
        </card>
        <card style="margin-top: 10px; font-size: 14px">
          <div>账号注册时间: {{registerTime}}</div>
          <div style="color: grey">欢迎进入轨道</div>
        </card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.setting-left{
  flex: 1;
  margin: 20px;
}

.setting-right{
  width: 300px;
  margin: 20px 30px 20px ;
}



</style>