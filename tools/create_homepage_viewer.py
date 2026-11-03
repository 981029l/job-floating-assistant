import base64

img_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\job_floating_assistant_ui_concept_1793692788971.jpg'
with open(img_path, 'rb') as f:
    b64_data = base64.b64encode(f.read()).decode('utf-8')

html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
</head>
<body class="bg-transparent text-slate-800 antialiased p-3 flex justify-center">
  <div class="bg-white border border-slate-200 rounded-3xl p-5 shadow-xl max-w-xl w-full flex flex-col items-center text-center space-y-4">
    <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 text-blue-600 text-xs font-semibold">
      <span>📱</span>
      <span>AI 求职悬浮助手 · 首页 (Dashboard) 概念渲染图</span>
    </div>
    
    <div class="relative group rounded-2xl overflow-hidden shadow-2xl border-2 border-slate-100 w-full transition-transform hover:scale-[1.02] duration-300">
      <img src="data:image/jpeg;base64,{b64_data}" alt="AI 求职悬浮助手首页概念图" class="w-full h-auto object-cover block" />
    </div>

    <div class="space-y-1.5 pt-1 text-left w-full px-2">
      <h3 class="text-sm font-bold text-slate-900">核心模块视觉布局说明：</h3>
      <ul class="text-xs text-slate-500 space-y-1">
        <li>• <strong class="text-slate-700">顶部 Hero Banner</strong>：科技蓝微光发光卡片，带 AI 认知大脑徽标；</li>
        <li>• <strong class="text-slate-700">权限与悬浮开关</strong>：安全盾牌图标、精细对勾绿标与显眼的“开启屏幕悬浮球”胶囊开关；</li>
        <li>• <strong class="text-slate-700">大模型 API 设置</strong>：DeepSeek API Key 维护、快捷模型标签（Chips）与绿色获取模型按钮；</li>
        <li>• <strong class="text-slate-700">简历管理区</strong>：紫色“选择并上传简历 (PDF/Word/TXT)”按钮与本地简历文本预览。</li>
      </ul>
    </div>
  </div>
</body>
</html>"""

out_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\view_homepage_concept.html'
with open(out_path, 'w', encoding='utf-8') as f:
    f.write(html_content)

print('Successfully created view_homepage_concept.html')
