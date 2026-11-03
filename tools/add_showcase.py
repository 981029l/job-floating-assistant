import base64

showcase_path = r'D:\job-floating-assistant\app_all_screens_showcase.jpg'
with open(showcase_path, 'rb') as f:
    showcase_b64 = base64.b64encode(f.read()).decode('utf-8')

html_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\view_all_pages.html'
with open(html_path, 'r', encoding='utf-8') as f:
    content = f.read()

banner_html = f"""
  <!-- 3D 渲染多屏全景展台大图 -->
  <div class="max-w-6xl w-full bg-white rounded-3xl p-4 shadow-xl border border-slate-200 mb-8">
    <div class="flex items-center justify-between mb-3 px-2">
      <div class="flex items-center gap-2">
        <span class="w-2.5 h-2.5 rounded-full bg-blue-600"></span>
        <span class="font-bold text-slate-800 text-sm">4 屏概念设计渲染图 (Dribbble 展台级别)</span>
      </div>
      <span class="text-xs text-slate-400">① 悬浮主控 · ② 求职档案 · ③ AI设置 · ④ 悬浮实测</span>
    </div>
    <div class="rounded-2xl overflow-hidden border border-slate-100 shadow-inner group">
      <img src="data:image/jpeg;base64,{showcase_b64}" alt="4屏概念设计全景" class="w-full h-auto object-cover block group-hover:scale-[1.01] transition-transform duration-300" />
    </div>
  </div>
"""

content = content.replace('<!-- 4 台手机并列容器 -->', banner_html + '\n  <!-- 4 台手机并列容器 -->')
with open(html_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Updated view_all_pages.html with showcase banner!')
