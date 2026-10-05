/**
 * 展示层格式化小工具。
 * 抽出来是因为帖子列表、详情页、评论都要用同一套相对时间规则。
 */

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 具体日期 */
export function formatTime(time) {
    if (!time) return ''
    const date = new Date(time)
    if (Number.isNaN(date.getTime())) return ''
    const diff = (Date.now() - date.getTime()) / 1000
    if (diff < 60) return '刚刚'
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
    if (diff < 86400 * 7) return Math.floor(diff / 86400) + ' 天前'
    return date.toLocaleDateString()
}

/**
 * 从 Quill Delta 里抽出纯文本。
 * 后端详情返回的 content 是 Delta JSON 字符串，在富文本渲染组件就位之前，
 * 详情页先用这个函数降级显示（图片等非文字节点直接跳过）。
 */
export function deltaToText(content) {
    if (!content) return ''
    try {
        const delta = typeof content === 'string' ? JSON.parse(content) : content
        const ops = delta && delta.ops
        if (!Array.isArray(ops)) return ''
        return ops
            .map(op => (typeof op.insert === 'string' ? op.insert : ''))
            .join('')
    } catch (e) {
        return ''
    }
}
