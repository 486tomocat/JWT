import axios from "axios";
import {accessHeader} from "@/net";

/**
 * 文件上传。
 *
 * ⚠️ 这里**不能**用 net/index.js 的 post()：那个封装会把 body 按 JSON 序列化，
 * 而文件必须走 FormData（multipart/form-data），并且**不能手写 Content-Type** ——
 * 浏览器要自己生成带 boundary 的头。所以这里直接用 axios。
 */
export const apiUploadImage = (file, success, failure) => {
    const form = new FormData()
    form.append('file', file)

    axios.post('/api/upload/image', form, {headers: accessHeader()})
        .then(({data}) => {
            if (data.code === 200) {
                success(data.data)
            } else {
                failure(data.message, data.code)
            }
        })
        .catch(err => {
            // 上传失败时后端可能返回非 200 状态码，这里兜一层
            failure(err.response?.data?.message || '图片上传失败，请稍后再试')
        })
}
