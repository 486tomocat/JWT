<script setup lang="ts">
import { login } from '@/net';
import router from '@/router';
import { Message, Lock } from '@element-plus/icons-vue'
import {reactive, ref} from "vue";

const formRef = ref()

/**
 * 登录凭据是**邮箱**，不是用户名。
 * 这样设计的原因：用户名可以改（改完很容易忘记自己叫什么），而邮箱是注册后基本不动的；
 * 后端的 SecurityConfiguration 也相应配了 .usernameParameter("email")。
 */
const form = reactive({
  email:'',
  password:'',
  remember:false
})

const rule = {
  email:[
    {required: true, message: '请输入邮箱地址', trigger: 'blur'},
    {type: 'email', message: '请输入合法的邮箱地址', trigger: ['blur', 'change']}
  ],
  password:[
    {required: true, message: '请输入密码'}
  ]
}

function userLogin(){
  formRef.value.validate((valid) => {
    if(valid) {
      login(form.email, form.password, form.remember,
          () => router.push('/index'))
    }
  })
}

</script>

<template>
  <div style="text-align: center;margin: 0 20px">
    <div style="margin-top: 150px">
      <div style="font-size: 25px;font-weight: bold">longin</div>
      <div style="font-size: 14px;color: grey">请使用注册时的邮箱登录</div>
    </div>
    <div style="margin-top: 50px">
      <el-form :model="form" :rules="rule" ref="formRef">
        <el-form-item prop="email">
          <el-input v-model="form.email" maxlength="50" type="text" placeholder="邮箱地址">
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" maxlength="16" show-password placeholder="密码">
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-row>
          <el-col :span="12" style="text-align: left">
            <el-form-item prop="remember">
              <el-checkbox v-model="form.remember" label="记住我"/>
            </el-form-item>
          </el-col>
          <el-col :span="12" style="text-align: right">
            <el-link @click="router.push('/reset')">忘记密码？</el-link>
          </el-col>
        </el-row>
      </el-form>
    </div>
    <div style="margin-top: 40px">
      <el-button @click="userLogin" style="width: 270px" type="success" plain>立即登录</el-button>
    </div>
    <el-divider>
      <span style="font-size: 13px;color: grey">没有账号</span>
    </el-divider>
    <div>
      <el-button @click="router.push('/register')" style="width: 270px" type="warning" plain>立即注册</el-button>
    </div>


  </div>
</template>

<style scoped>

</style>