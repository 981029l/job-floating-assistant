import base64

img_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\job_floating_assistant_icon_1793691562028.jpg'
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
  <div class="bg-white border border-slate-200 rounded-3xl p-6 shadow-xl max-w-md w-full flex flex-col items-center text-center space-y-4">
    <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 text-blue-600 text-xs font-semibold">
      <span>✦</span>
      <span>AI 求职悬浮助手 · 应用图标设计概念图</span>
    </div>
    
    <div class="relative group rounded-3xl overflow-hidden shadow-2xl border-4 border-slate-100 max-w-[320px] transition-transform hover:scale-105 duration-300">
      <img src="data:image/jpeg;base64,{b64_data}" alt="AI 求职悬浮助手应用图标" class="w-full h-auto object-cover block" />
    </div>

    <div class="space-y-1.5 pt-2">
      <h3 class="text-base font-bold text-slate-900">3D 智能职场助手 · 启动图标概念图</h3>
      <p class="text-xs text-slate-500 leading-relaxed">
        科技蓝微光底座 · 亲和力 3D 机器人面孔 · 职场领带与金色发光气泡
      </p>
    </div>

    <div class="grid grid-cols-3 gap-2 w-full pt-2 text-[11px] text-slate-600">
      <div class="bg-slate-50 p-2 rounded-xl border border-slate-100">
        <p class="font-bold text-slate-800">Squircle</p>
        <p class="text-slate-400 mt-0.5">标准圆角矩形</p>
      </div>
      <div class="bg-slate-50 p-2 rounded-xl border border-slate-100">
        <p class="font-bold text-blue-600">4K 渲染</p>
        <p class="text-slate-400 mt-0.5">全分辨率适配</p>
      </div>
      <div class="bg-slate-50 p-2 rounded-xl border border-slate-100">
        <p class="font-bold text-emerald-600">已集成</p>
        <p class="text-slate-400 mt-0.5">v1.0.3 安装包</p>
      </div>
    </div>
  </div>
</body>
</html>"""

out_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\view_concept_icon.html'
with open(out_path, 'w', encoding='utf-8') as f:
    f.write(html_content)

print('Successfully created view_concept_icon.html')
