package com.example.facerecognitionfinal.report

import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.ml.VpTree
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a self-contained HTML report for export/sharing.
 * Includes CSS styling, summary stats, profile library, recognition history,
 * VP-Tree metrics, and a printable layout.
 */
object HtmlReportBuilder {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun build(
        profiles: List<PersonProfile>,
        records: List<RecognitionRecord>,
        vpTree: VpTree,
        threshold: Float,
        summaryText: String
    ): String {
        val now = dateFormat.format(Date())
        val profileCount = profiles.size
        val totalSamples = profiles.sumOf { it.embeddings.size }
        val recordCount = records.size
        val successCount = records.count { it.status == "识别成功" || it.status == "识别成功（本地）" }
        val unknownCount = records.count { it.status == "未知人员" }
        val successRate = if (recordCount > 0) (successCount * 100f / recordCount) else 0f

        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n")
            appendHead()
            append("<body>\n")
            appendHeader(now)
            appendOverview(profileCount, totalSamples, recordCount, successCount, unknownCount, successRate, threshold)
            appendVpTreeStats(vpTree)
            appendSummary(summaryText)
            appendProfileLibrary(profiles)
            appendRecognitionHistory(records)
            appendFooter()
            append("</body>\n</html>")
        }
    }

    private fun StringBuilder.appendHead() {
        append("""
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>人脸识别演示台 — 测试报告</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
    background: #f0f4f8; color: #1a202c; line-height: 1.7;
    padding: 20px;
  }
  .container { max-width: 900px; margin: 0 auto; }
  .card {
    background: #fff; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.06);
    padding: 24px; margin-bottom: 20px;
  }
  h1 { font-size: 24px; color: #3F7DF6; margin-bottom: 4px; }
  h2 { font-size: 18px; color: #193A7A; margin-bottom: 12px; border-bottom: 2px solid #3F7DF6; padding-bottom: 8px; }
  h3 { font-size: 15px; color: #14A69A; margin: 12px 0 6px; }
  .subtitle { color: #8B9AAF; font-size: 13px; margin-bottom: 16px; }
  .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
  .stat-box {
    background: linear-gradient(135deg, #f7fafc 0%, #edf2f7 100%);
    border-radius: 10px; padding: 14px; text-align: center;
    border: 1px solid #e2e8f0;
  }
  .stat-value { font-size: 28px; font-weight: 700; color: #3F7DF6; }
  .stat-label { font-size: 12px; color: #718096; margin-top: 2px; }
  .success { color: #14A69A; }
  .warning { color: #E4576B; }
  .vp-tree { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12px; background: #f7fafc; padding: 12px; border-radius: 8px; white-space: pre-wrap; }
  .profile-item { background: #f7fafc; border-radius: 8px; padding: 12px; margin-bottom: 8px; border-left: 4px solid #3F7DF6; }
  .profile-name { font-weight: 600; color: #193A7A; }
  .profile-detail { font-size: 13px; color: #58677A; margin-top: 4px; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th { background: #f7fafc; padding: 10px; text-align: left; font-weight: 600; color: #193A7A; border-bottom: 2px solid #e2e8f0; }
  td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; }
  tr:hover { background: #f7fafc; }
  .badge {
    display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600;
  }
  .badge-success { background: #DDF8EF; color: #08756C; }
  .badge-unknown { background: #FFE8ED; color: #AF2D43; }
  .badge-info { background: #EAF6FF; color: #174176; }
  .footer { text-align: center; color: #8B9AAF; font-size: 12px; margin-top: 20px; }
  .summary-text { font-size: 14px; color: #58677A; white-space: pre-wrap; background: #f7fafc; padding: 12px; border-radius: 8px; }
  @media print {
    body { background: #fff; padding: 0; }
    .card { box-shadow: none; border: 1px solid #e2e8f0; break-inside: avoid; }
  }
</style>
</head>
""")
    }

    private fun StringBuilder.appendHeader(now: String) {
        append("""
<div class="container">
<div class="card">
  <h1>🎓 人脸识别演示台 — 测试报告</h1>
  <p class="subtitle">生成时间：$now &nbsp;|&nbsp; 基于 FaceNet (TensorFlow Lite) + ML Kit</p>
</div>
""")
    }

    private fun StringBuilder.appendOverview(
        profileCount: Int, totalSamples: Int,
        recordCount: Int, successCount: Int, unknownCount: Int,
        successRate: Float, threshold: Float
    ) {
        append("""
<div class="card">
  <h2>📊 数据概览</h2>
  <div class="stats-grid">
    <div class="stat-box">
      <div class="stat-value">$profileCount</div>
      <div class="stat-label">注册人数</div>
    </div>
    <div class="stat-box">
      <div class="stat-value">$totalSamples</div>
      <div class="stat-label">特征样本总数</div>
    </div>
    <div class="stat-box">
      <div class="stat-value">$recordCount</div>
      <div class="stat-label">识别记录</div>
    </div>
    <div class="stat-box">
      <div class="stat-value success">$successCount</div>
      <div class="stat-label">成功识别</div>
    </div>
    <div class="stat-box">
      <div class="stat-value warning">$unknownCount</div>
      <div class="stat-label">未知人员</div>
    </div>
    <div class="stat-box">
      <div class="stat-value">${"%.1f".format(successRate)}%</div>
      <div class="stat-label">识别成功率</div>
    </div>
  </div>
  <p style="margin-top:12px;font-size:13px;color:#58677A;">当前 L2 阈值：${"%.2f".format(threshold)} &nbsp;|&nbsp; 识别成功率 = 成功识别 / 总识别次数</p>
</div>
""")
    }

    private fun StringBuilder.appendVpTreeStats(vpTree: VpTree) {
        if (vpTree.size == 0) return
        append("""
<div class="card">
  <h2>🌳 VP-Tree 索引统计</h2>
  <div class="vp-tree">条目总数：${vpTree.size}
上次查询：扫描 ${vpTree.lastQueryIndexScans} 个节点 / 共 ${vpTree.lastQueryLinearScans} 个条目
加速比：跳过 ${vpTree.lastQuerySavingsPercent}% 条目
搜索路径：${vpTree.lastQueryRoute}
查询耗时：${vpTree.lastQueryDurationNs / 1000} μs</div>
</div>
""")
    }

    private fun StringBuilder.appendSummary(summaryText: String) {
        if (summaryText.isBlank()) return
        append("""
<div class="card">
  <h2>📝 测试摘要</h2>
  <div class="summary-text">${summaryText.replace("\n", "<br>")}</div>
</div>
""")
    }

    private fun StringBuilder.appendProfileLibrary(profiles: List<PersonProfile>) {
        if (profiles.isEmpty()) return
        append("""
<div class="card">
  <h2>👥 人脸库 ($profiles.size 人)</h2>
""")
        profiles.forEach { profile ->
            val validCount = profile.embeddings.count { it.size == 128 && it.all { f -> f.isFinite() } }
            append("""
  <div class="profile-item">
    <div class="profile-name">${profile.name}</div>
    <div class="profile-detail">样本数：${profile.embeddings.size}（有效 $validCount）&nbsp;|&nbsp; 特征维度：128</div>
  </div>
""")
        }
        append("</div>\n")
    }

    private fun StringBuilder.appendRecognitionHistory(records: List<RecognitionRecord>) {
        if (records.isEmpty()) return
        append("""
<div class="card">
  <h2>📋 识别记录 ($records.size 条)</h2>
  <table>
    <tr><th>时间</th><th>姓名</th><th>结果</th><th>置信度</th><th>说明</th></tr>
""")
        records.take(50).forEach { r ->
            val badgeClass = when {
                r.status.contains("成功") || r.status.contains("Match") -> "badge-success"
                r.status.contains("未知") || r.status.contains("Unknown") -> "badge-unknown"
                else -> "badge-info"
            }
            append("""
    <tr>
      <td>${r.timestamp}</td>
      <td>${r.name.ifEmpty { "—" }}</td>
      <td><span class="badge $badgeClass">${r.status}</span></td>
      <td>${"%.1f".format(r.confidence)}%</td>
      <td>${r.explanation.take(80)}</td>
    </tr>
""")
        }
        if (records.size > 50) {
            append("""    <tr><td colspan="5" style="text-align:center;color:#8B9AAF;">... 另有 ${records.size - 50} 条记录未显示</td></tr>""")
        }
        append("</table>\n</div>\n")
    }

    private fun StringBuilder.appendFooter() {
        append("""
<div class="footer">
  <p>人脸识别演示台 v1.0 &nbsp;|&nbsp; FaceNet (TensorFlow Lite) + ML Kit Face Detection</p>
  <p>离线识别 · VP-Tree 索引加速 · 活体检测 · 质量分析</p>
</div>
</div>
""")
    }
}
