import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/', timeout: 15000 })

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (err) => {
    const msg = err.code === 'ECONNABORTED' ? '请求超时，请重试' : (err.message || '网络错误')
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http
