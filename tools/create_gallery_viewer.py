import base64
import os

files = [
    {
        "id": "A1",
        "title": "方向 A1 · 智能小机器人",
        "corner": "左下角浮现 (Lower-Left)",
        "meaning": "呼应随时待命的 AI 悬浮读屏助手",
        "colors": ["#FFFFFF 暖白", "#1677FF 科技蓝", "#E8EFF8 灰蓝底"],
        "path": r"D:\job-floating-assistant\ip_candidates\A1_robot_lower_left.jpg"
    },
    {
        "id": "A2",
        "title": "方向 A2 · 智能小机器人",
        "corner": "右下角浮现 (Lower-Right)",
        "meaning": "呼应随时待命的 AI 悬浮读屏助手",
        "colors": ["#FFFFFF 暖白", "#1677FF 科技蓝", "#E8EFF8 灰蓝底"],
        "path": r"D:\job-floating-assistant\ip_candidates\A2_robot_lower_right.jpg"
    },
    {
        "id": "B1",
        "title": "方向 B1 · 破冰送信小鸟",
        "corner": "左下角浮现 (Lower-Left)",
        "meaning": "呼应神速传信与打破招聘沟通僵局",
        "colors": ["#FFEAA7 鹅黄", "#FF7675 暖橙", "#EEF2F7 浅灰底"],
        "path": r"D:\job-floating-assistant\ip_candidates\B1_bird_lower_left.jpg"
    },
    {
        "id": "B2",
        "title": "方向 B2 · 破冰送信小鸟",
        "corner": "右下角浮现 (Lower-Right)",
        "meaning": "呼应神速传信与打破招聘沟通僵局",
        "colors": ["#FFEAA7 鹅黄", "#FF7675 暖橙", "#EEF2F7 浅灰底"],
        "path": r"D:\job-floating-assistant\ip_candidates\B2_bird_lower_right.jpg"
    },
    {
        "id": "C1",
        "title": "方向 C1 · 招手好运猫",
        "corner": "左下角浮现 (Lower-Left)",
        "meaning": "呼应求职好运与招揽海量面试邀约",
        "colors": ["#FFF5EB 暖橘白", "#D35400 焦糖棕", "#EBF2FA 米蓝底"],
        "path": r"D:\job-floating-assistant\ip_candidates\C1_cat_lower_left.jpg"
    },
    {
        "id": "C2",
        "title": "方向 C2 · 招手好运猫",
        "corner": "右下角浮现 (Lower-Right)",
        "meaning": "呼应求职好运与招揽海量面试邀约",
        "colors": ["#FFF5EB 暖橘白", "#D35400 焦糖棕", "#EBF2FA 米蓝底"],
        "path": r"D:\job-floating-assistant\ip_candidates\C2_cat_lower_right.jpg"
    }
]

cards_html = ""
for item in files:
    with open(item["path"], "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")
    
    colors_badges = " ".join([f"<span class='px-1.5 py-0.5 bg-slate-100 rounded text-[10px] text-slate-600 font-mono'>{c}</span>" for c in item["colors"]])

    cards_html += f"""
    <div class="bg-white border border-slate-200/90 rounded-2xl p-4 shadow-sm hover:shadow-md transition-all flex flex-col space-y-3">
      <div class="flex items-center justify-between">
        <span class="text-xs font-bold text-slate-900 bg-slate-100 px-2.5 py-1 rounded-md">{item["id"]}</span>
        <span class="text-[11px] font-medium text-blue-600 bg-blue-50 px-2 py-0.5 rounded">{item["corner"]}</span>
      </div>

      <div class="relative rounded-xl overflow-hidden shadow-inner border border-slate-100 bg-slate-50 aspect-square">
        <img src="data:image/jpeg;base64,{b64}" alt="{item['title']}" class="w-full h-full object-cover block hover:scale-105 transition-transform duration-300" />
      </div>

      <div class="space-y-1">
        <h4 class="text-sm font-bold text-slate-900">{item["title"]}</h4>
        <p class="text-xs text-slate-500 leading-snug">{item["meaning"]}</p>
      </div>

      <div class="pt-2 border-t border-slate-100 flex flex-wrap gap-1">
        {colors_badges}
      </div>
    </div>
    """

full_html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>IP as Logo · 6款候选方案全览</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
</head>
<body class="bg-slate-50 text-slate-800 antialiased p-4 md:p-6 flex flex-col items-center min-h-screen">

  <div class="max-w-5xl w-full text-center mb-6">
    <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 text-blue-600 text-xs font-semibold mb-2">
      <span>✦</span>
      <span>ip-as-logo 官方规范 · 6款独立并发概念抽签</span>
    </div>
    <h2 class="text-xl md:text-2xl font-bold text-slate-900">AI求职悬浮助手 · 极简萌系 IP Logo 方案矩阵</h2>
    <p class="text-xs text-slate-500 mt-1 max-w-xl mx-auto">
      严格遵循：4-7个大几何形状、重质感大色块、严格3种语义色、角落探出构图、在 32×32 超小尺寸下一眼可认
    </p>
  </div>

  <div class="max-w-5xl w-full grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
    {cards_html}
  </div>

  <div class="mt-8 text-center text-xs text-slate-400">
    已同步归档至本地工程目录：<code class="font-mono text-slate-600">D:\job-floating-assistant\ip_candidates</code>
  </div>

</body>
</html>"""

out_path = r"C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\view_ip_candidates.html"
with open(out_path, "w", encoding="utf-8") as f:
    f.write(full_html)

print("Successfully generated view_ip_candidates.html")
