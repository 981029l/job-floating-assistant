import base64

img_path = r'D:\job-floating-assistant\app_redesign_concept.jpg'
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
  <div class="bg-white border border-slate-200/90 rounded-3xl p-5 shadow-2xl max-w-xl w-full flex flex-col items-center text-center space-y-4">
    <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 text-blue-600 text-xs font-semibold">
      <span>✨</span>
      <span>Material 3 Expressive · 2026 旗舰级全新 UI/UX 重构效果图</span>
    </div>
    
    <div class="relative group rounded-2xl overflow-hidden shadow-2xl border-2 border-slate-100 w-full transition-transform hover:scale-[1.01] duration-300">
      <img src="data:image/jpeg;base64,{b64_data}" alt="AI 求职悬浮助手全新重构界面概念图" class="w-full h-auto object-cover block" />
    </div>

    <div class="space-y-1.5 pt-1 text-left w-full px-2">
      <h3 class="text-sm font-bold text-slate-900">✨ 全新设计系统升级点：</h3>
      <ul class="text-xs text-slate-500 space-y-1.5">
        <li>• <strong class="text-slate-800">C1 好运猫沉浸顶栏</strong>：搭配呼吸光感状态徽标（“好运猫AI待命中”），告别呆板的表单排版；</li>
        <li>• <strong class="text-slate-800">Bento-Grid 触感卡片布局</strong>：悬浮开关主控、模型状态胶囊与权限快速自检卡片分离，层级分明；</li>
        <li>• <strong class="text-slate-800">智能求职档案专属视窗</strong>：将原本杂乱的长文本框重塑为带“技能标签”与“PDF/Word 解析状态”的专属卡片；</li>
        <li>• <strong class="text-slate-800">半透明发光悬浮球联动</strong>：屏幕侧边停靠可爱的【C1 招手好运猫】桌面悬浮微件，视觉体验高度统一。</li>
      </ul>
    </div>
  </div>
</body>
</html>"""

out_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\view_redesign_concept.html'
with open(out_path, 'w', encoding='utf-8') as f:
    f.write(html_content)

print('Successfully created view_redesign_concept.html')
