import base64

cat_path = r'D:\job-floating-assistant\ip_candidates\C1_cat_lower_left.jpg'
with open(cat_path, 'rb') as f:
    cat_b64 = base64.b64encode(f.read()).decode('utf-8')

html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AI求职悬浮助手 · 全页面高保真概念图展示</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <style>
    .no-scrollbar::-webkit-scrollbar {{ display: none; }}
    .no-scrollbar {{ -ms-overflow-style: none; scrollbar-width: none; }}
  </style>
</head>
<body class="bg-slate-100 text-slate-800 antialiased p-4 md:p-6 flex flex-col items-center min-h-screen">

  <div class="max-w-6xl w-full text-center mb-6">
    <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 text-blue-600 text-xs font-semibold mb-2">
      <span>✨</span>
      <span>Material 3 Expressive · 全页面概念矩阵全览</span>
    </div>
    <h2 class="text-xl md:text-2xl font-bold text-slate-900">AI求职悬浮助手 · 4大核心界面全景概念图</h2>
    <p class="text-xs text-slate-500 mt-1 max-w-xl mx-auto">
      三段式底部导航分流架构 + BOSS直聘全局悬浮工作态 · 像素级真实渲染
    </p>

    <!-- 顶部页面快速切换 Tabs -->
    <div class="inline-flex bg-slate-200 p-1.5 rounded-xl mt-4 shadow-inner flex-wrap justify-center gap-1">
      <button onclick="filterScreen('all')" id="btn-all" class="px-4 py-1.5 rounded-lg text-xs font-bold transition-all bg-white text-blue-600 shadow-sm">
        👀 全景四屏并列
      </button>
      <button onclick="filterScreen(1)" id="btn-1" class="px-4 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 transition-all">
        📱 页面 1：悬浮主控
      </button>
      <button onclick="filterScreen(2)" id="btn-2" class="px-4 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 transition-all">
        📄 页面 2：求职档案
      </button>
      <button onclick="filterScreen(3)" id="btn-3" class="px-4 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 transition-all">
        ⚙️ 页面 3：AI智囊设置
      </button>
      <button onclick="filterScreen(4)" id="btn-4" class="px-4 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 transition-all">
        ⚡ 页面 4：BOSS悬浮实测
      </button>
    </div>
  </div>

  <!-- 4 台手机并列容器 -->
  <div class="max-w-7xl w-full grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 justify-items-center">

    <!-- 手机 1：悬浮主控 Dashboard -->
    <div id="screen-1" class="w-[310px] h-[670px] bg-slate-900 rounded-[44px] p-2.5 shadow-2xl border-4 border-slate-700/80 flex flex-col relative transition-all">
      <div class="w-full h-full bg-[#F8FAFC] rounded-[36px] overflow-hidden flex flex-col relative font-sans text-xs">
        
        <!-- 状态栏 -->
        <div class="h-7 bg-white text-slate-800 text-[10px] px-5 flex items-center justify-between font-medium border-b border-slate-100">
          <span>09:41</span>
          <span class="text-emerald-600 font-bold">5G 100%</span>
        </div>

        <div class="flex-1 overflow-y-auto p-3 space-y-3 no-scrollbar pb-16">
          <!-- 顶部 C1 好运猫卡片 -->
          <div class="bg-white p-3 rounded-2xl shadow-sm border border-slate-100 flex items-center gap-3">
            <img src="data:image/jpeg;base64,{cat_b64}" class="w-11 h-11 rounded-full border-2 border-slate-200 object-cover" />
            <div>
              <h3 class="font-bold text-slate-900 text-xs">AI 求职悬浮助手</h3>
              <div class="flex items-center gap-1 mt-0.5">
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping"></span>
                <span class="text-[10px] text-emerald-600 font-medium">好运猫待命中 · 绿点呼吸</span>
              </div>
            </div>
          </div>

          <!-- Bento 1: 主开关大卡片 -->
          <div class="bg-gradient-to-r from-[#1677FF] to-[#2F80ED] text-white p-3.5 rounded-2xl shadow-md space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="font-bold text-sm">▶ 点击开启悬浮窗</span>
              <span class="text-xl">⚡</span>
            </div>
            <p class="text-[10px] text-blue-100/90 leading-tight">
              开启后桌面将出现可爱的招手猫浮标，在 BOSS直聘 点它一键生成招呼语
            </p>
          </div>

          <!-- Bento 2: 核心权限自检 -->
          <div class="bg-white p-3 rounded-2xl shadow-sm border border-slate-100 space-y-2">
            <span class="font-bold text-slate-800 text-[11px] block border-b pb-1">核心权限自检</span>
            <div class="flex items-center justify-between">
              <div>
                <p class="font-medium text-slate-700">悬浮窗权限</p>
                <p class="text-[9px] text-emerald-600">已授权正常</p>
              </div>
              <span class="bg-slate-100 text-slate-500 px-2 py-0.5 rounded text-[10px]">已开通</span>
            </div>
            <div class="flex items-center justify-between border-t border-slate-50 pt-1">
              <div>
                <p class="font-medium text-slate-700">无障碍自动读屏</p>
                <p class="text-[9px] text-emerald-600">已开通正常</p>
              </div>
              <span class="bg-slate-100 text-slate-500 px-2 py-0.5 rounded text-[10px]">已开通</span>
            </div>
          </div>

          <!-- Bento 3: 当前模型快捷标签 -->
          <div class="bg-white p-3 rounded-2xl shadow-sm border border-slate-100 space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="font-bold text-slate-800 text-[11px]">当前生效大模型</span>
              <span class="text-[10px] text-blue-600 font-bold">deepseek-chat</span>
            </div>
            <div class="flex gap-1 pt-1">
              <span class="bg-blue-50 text-blue-600 border border-blue-200 px-1.5 py-0.5 rounded-full text-[9px] font-bold">deepseek-chat</span>
              <span class="bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded-full text-[9px]">deepseek-r1</span>
            </div>
          </div>
        </div>

        <!-- 底部导航栏 (Tab 0 激活) -->
        <div class="absolute bottom-0 inset-x-0 h-12 bg-white border-t border-slate-200 px-4 flex items-center justify-around z-20">
          <div class="flex flex-col items-center text-blue-600 font-bold">
            <span class="text-sm">🚀</span>
            <span class="text-[9px]">悬浮主控</span>
          </div>
          <div class="flex flex-col items-center text-slate-400">
            <span class="text-sm">📝</span>
            <span class="text-[9px]">求职档案</span>
          </div>
          <div class="flex flex-col items-center text-slate-400">
            <span class="text-sm">⚙️</span>
            <span class="text-[9px]">AI设置</span>
          </div>
        </div>
      </div>
      <div class="text-center text-[11px] font-bold text-slate-300 mt-2">页面 1：悬浮主控 (Dashboard)</div>
    </div>

    <!-- 手机 2：求职档案 Profile -->
    <div id="screen-2" class="w-[310px] h-[670px] bg-slate-900 rounded-[44px] p-2.5 shadow-2xl border-4 border-slate-700/80 flex flex-col relative transition-all">
      <div class="w-full h-full bg-[#F8FAFC] rounded-[36px] overflow-hidden flex flex-col relative font-sans text-xs">
        
        <div class="h-7 bg-white text-slate-800 text-[10px] px-5 flex items-center justify-between font-medium border-b border-slate-100">
          <span>09:41</span>
          <span class="text-purple-600 font-bold">档案中心</span>
        </div>

        <div class="flex-1 overflow-y-auto p-3 space-y-3 no-scrollbar pb-16">
          
          <!-- 上传简历卡片 (Purple) -->
          <div class="bg-gradient-to-r from-purple-50 to-indigo-50 border border-purple-200 p-3.5 rounded-2xl shadow-sm space-y-2">
            <div class="flex items-center gap-2">
              <span class="text-purple-600 text-lg">📁</span>
              <div>
                <h4 class="font-bold text-purple-900 text-xs">导入本地简历文件</h4>
                <p class="text-[9px] text-purple-600">支持 PDF、Word (.docx) 及纯文本</p>
              </div>
            </div>
            <button class="w-full bg-[#722ED1] text-white py-1.5 rounded-xl font-bold text-[11px] shadow-sm">
              选择手机简历文件导入
            </button>
            <p class="text-[9px] text-purple-700 font-medium">✓ 已导入: 吕宽友_前端7年简历.pdf (解析成功)</p>
          </div>

          <!-- 提取核心标签 -->
          <div class="bg-white p-3 rounded-2xl shadow-sm border border-slate-100 space-y-2">
            <div class="flex items-center justify-between border-b pb-1">
              <span class="font-bold text-slate-800 text-[11px]">简历核心提取亮点</span>
              <span class="text-[10px] text-blue-600">恢复预设</span>
            </div>
            <div class="flex flex-wrap gap-1">
              <span class="bg-slate-100 text-slate-700 px-2 py-0.5 rounded text-[9px]">7年前端</span>
              <span class="bg-slate-100 text-slate-700 px-2 py-0.5 rounded text-[9px]">Vue3+TS</span>
              <span class="bg-slate-100 text-slate-700 px-2 py-0.5 rounded text-[9px]">大模型SSE</span>
              <span class="bg-slate-100 text-slate-700 px-2 py-0.5 rounded text-[9px]">直播Canvas</span>
              <span class="bg-slate-100 text-slate-700 px-2 py-0.5 rounded text-[9px]">全日制科班</span>
            </div>
            
            <p class="text-[10px] text-slate-400 pt-1">简历内容（与 BOSS JD 进行交集匹配）：</p>
            <div class="bg-slate-50 border border-slate-200 rounded-xl p-2 font-mono text-[9px] text-slate-600 leading-relaxed h-32 overflow-y-auto no-scrollbar">
              吕宽友 | 7年前端研发经验 | 期望城市：深圳 | 期望薪资：15-20K<br/>
              【核心优势】精通 Vue3+TypeScript+Vite，深度主导 AI 落地项目（SSE流式打字机交互、语音合成TTS、知识库切片溯源）...
            </div>
          </div>
        </div>

        <!-- 底部导航栏 (Tab 1 激活) -->
        <div class="absolute bottom-0 inset-x-0 h-12 bg-white border-t border-slate-200 px-4 flex items-center justify-around z-20">
          <div class="flex flex-col items-center text-slate-400">
            <span class="text-sm">🚀</span>
            <span class="text-[9px]">悬浮主控</span>
          </div>
          <div class="flex flex-col items-center text-purple-600 font-bold">
            <span class="text-sm">📝</span>
            <span class="text-[9px]">求职档案</span>
          </div>
          <div class="flex flex-col items-center text-slate-400">
            <span class="text-sm">⚙️</span>
            <span class="text-[9px]">AI设置</span>
          </div>
        </div>
      </div>
      <div class="text-center text-[11px] font-bold text-slate-300 mt-2">页面 2：求职档案 (Profile)</div>
    </div>

    <!-- 手机 3：AI智囊设置 Settings -->
    <div id="screen-3" class="w-[310px] h-[670px] bg-slate-900 rounded-[44px] p-2.5 shadow-2xl border-4 border-slate-700/80 flex flex-col relative transition-all">
      <div class="w-full h-full bg-[#F8FAFC] rounded-[36px] overflow-hidden flex flex-col relative font-sans text-xs">
        
        <div class="h-7 bg-white text-slate-800 text-[10px] px-5 flex items-center justify-between font-medium border-b border-slate-100">
          <span>09:41</span>
          <span class="text-blue-600 font-bold">大模型参数</span>
        </div>

        <div class="flex-1 overflow-y-auto p-3 space-y-3 no-scrollbar pb-16">
          <!-- API 配置卡片 -->
          <div class="bg-white p-3 rounded-2xl shadow-sm border border-slate-100 space-y-2.5">
            <span class="font-bold text-slate-800 text-[11px] block border-b pb-1">API 接口配置</span>
            
            <div>
              <label class="text-[10px] text-slate-500">DeepSeek API Key</label>
              <input type="password" value="sk-7890aef********************" class="w-full mt-1 px-2.5 py-1 border border-slate-200 rounded-lg text-[10px] font-mono bg-slate-50" readonly />
            </div>

            <div>
              <label class="text-[10px] text-slate-500">Base URL 接口地址</label>
              <input type="text" value="https://api.deepseek.com/v1" class="w-full mt-1 px-2.5 py-1 border border-slate-200 rounded-lg text-[10px] font-mono bg-slate-50" readonly />
            </div>

            <div class="flex items-center gap-1.5 pt-1">
              <div class="flex-1">
                <label class="text-[10px] text-slate-500">当前模型</label>
                <input type="text" value="deepseek-chat" class="w-full mt-1 px-2 py-1 border border-slate-200 rounded-lg text-[10px] font-bold bg-slate-50" readonly />
              </div>
              <button class="mt-4 bg-emerald-600 text-white px-2 py-1.5 rounded-lg text-[10px] font-bold shrink-0">
                🔄 联网拉取
              </button>
            </div>
          </div>

          <!-- 自定义 Prompt 规则 -->
          <div class="bg-white p-3 rounded-2xl shadow-sm border border-slate-100 space-y-1.5">
            <div class="flex items-center justify-between border-b pb-1">
              <span class="font-bold text-slate-800 text-[11px]">AI 招呼语 Prompt 规则</span>
              <span class="text-[10px] text-blue-600">重置默认</span>
            </div>
            <div class="bg-slate-50 border border-slate-200 rounded-xl p-2 font-mono text-[9px] text-slate-600 leading-relaxed h-28 overflow-y-auto no-scrollbar">
              你是一位顶尖的技术求职顾问与技术专家。请仔细分析目标岗位JD与候选人简历，生成【技术硬核攻坚型】与【全面契合交流型】两套高转化打招呼语...
            </div>
          </div>
        </div>

        <!-- 底部导航栏 (Tab 2 激活) -->
        <div class="absolute bottom-0 inset-x-0 h-12 bg-white border-t border-slate-200 px-4 flex items-center justify-around z-20">
          <div class="flex flex-col items-center text-slate-400">
            <span class="text-sm">🚀</span>
            <span class="text-[9px]">悬浮主控</span>
          </div>
          <div class="flex flex-col items-center text-slate-400">
            <span class="text-sm">📝</span>
            <span class="text-[9px]">求职档案</span>
          </div>
          <div class="flex flex-col items-center text-blue-600 font-bold">
            <span class="text-sm">⚙️</span>
            <span class="text-[9px]">AI设置</span>
          </div>
        </div>
      </div>
      <div class="text-center text-[11px] font-bold text-slate-300 mt-2">页面 3：AI智囊设置 (Settings)</div>
    </div>

    <!-- 手机 4：BOSS直聘悬浮实测 Floating Overlay -->
    <div id="screen-4" class="w-[310px] h-[670px] bg-slate-900 rounded-[44px] p-2.5 shadow-2xl border-4 border-slate-700/80 flex flex-col relative transition-all">
      <div class="w-full h-full bg-[#F5F6F8] rounded-[36px] overflow-hidden flex flex-col relative font-sans text-xs">
        
        <!-- 模拟 BOSS 直聘详情背景 -->
        <div class="h-12 bg-white border-b px-3 flex items-center justify-between shrink-0 pt-2">
          <span class="font-bold text-slate-800 text-xs">‹ 高级前端开发 (大模型应用)</span>
          <span class="text-[10px] text-slate-400">分享</span>
        </div>

        <!-- 虚化背景文本 -->
        <div class="p-3 space-y-2 opacity-25 select-none">
          <div class="text-base font-extrabold text-[#00BEBD]">18-25K · 14薪</div>
          <p class="text-[10px] text-slate-500">深圳 南山区 · 5-10年 · 本科</p>
          <div class="h-px bg-slate-200"></div>
          <p class="text-[10px] text-slate-600 leading-tight">1. 负责大模型流式对话前端架构，落地 SSE 与知识库溯源；2. 攻坚 Canvas 万级弹幕调度与弱网长连接保活...</p>
        </div>

        <!-- 停靠在屏幕边缘的 C1 招手好运猫圆形悬浮球 -->
        <div class="absolute left-1.5 top-36 w-11 h-11 rounded-full border-2 border-white shadow-xl overflow-hidden z-20 cursor-pointer animate-pulse">
          <img src="data:image/jpeg;base64,{cat_b64}" class="w-full h-full object-cover" />
        </div>

        <!-- 居中弹出的 AI 招呼语生成卡片 -->
        <div class="absolute inset-x-2.5 top-20 bottom-14 bg-white/98 backdrop-blur-md rounded-2xl shadow-2xl border border-blue-100 flex flex-col z-30 overflow-hidden">
          <div class="p-2.5 bg-gradient-to-r from-blue-50 to-indigo-50 border-b flex items-center justify-between">
            <span class="font-bold text-slate-900 text-[11px]">AI 定制招呼语 (DeepSeek)</span>
            <span class="text-slate-400 font-bold text-xs">✕</span>
          </div>

          <div class="flex-1 overflow-y-auto p-2.5 space-y-2 text-[10px] no-scrollbar">
            <!-- 风格 1 -->
            <div class="bg-slate-50 border border-slate-200 p-2.5 rounded-xl space-y-1.5">
              <span class="text-[9px] font-bold text-blue-600 bg-blue-50 px-1.5 py-0.5 rounded">【技术硬核攻坚型】</span>
              <p class="text-slate-700 leading-relaxed">
                您好！关注到贵司大模型落地场景。我有7年前端经验，深度主导过 SSE 打字机流式消费、EdgeTTS 语音连续下发与知识库切片双向高亮溯源引擎。期望在深圳发展，随时可深入沟通！
              </p>
              <button class="w-full bg-[#1677FF] text-white py-1 rounded-lg font-bold text-[10px]">复制此话术并关闭</button>
            </div>

            <!-- 风格 2 -->
            <div class="bg-slate-50 border border-slate-200 p-2.5 rounded-xl space-y-1.5">
              <span class="text-[9px] font-bold text-emerald-600 bg-emerald-50 px-1.5 py-0.5 rounded">【全面契合交流型】</span>
              <p class="text-slate-700 leading-relaxed">
                您好，看贵司岗位与我技术栈高度契合。全日制软件工程科班出身，主导过 Web/APP/小程序多端架构统一与千万级业务闭环，工程化规范扎实，期待与您进一步交流！
              </p>
              <button class="w-full bg-[#1677FF] text-white py-1 rounded-lg font-bold text-[10px]">复制此话术并关闭</button>
            </div>
          </div>

          <div class="p-1.5 bg-slate-50 border-t text-center text-[9px] text-slate-400">
            点击按钮自动写入手机剪贴板
          </div>
        </div>

        <!-- 模拟 BOSS 底部 -->
        <div class="h-10 bg-white border-t px-3 flex items-center justify-between z-10 shrink-0">
          <span class="text-[10px] text-slate-400">点此进入沟通</span>
          <button class="bg-[#00BEBD] text-white text-[10px] font-bold px-4 py-1 rounded-full">立即沟通</button>
        </div>
      </div>
      <div class="text-center text-[11px] font-bold text-slate-300 mt-2">页面 4：BOSS悬浮实测 (Floating)</div>
    </div>

  </div>

  <script>
    function filterScreen(num) {{
      const screens = [
        document.getElementById('screen-1'),
        document.getElementById('screen-2'),
        document.getElementById('screen-3'),
        document.getElementById('screen-4')
      ];
      const buttons = [
        document.getElementById('btn-all'),
        document.getElementById('btn-1'),
        document.getElementById('btn-2'),
        document.getElementById('btn-3'),
        document.getElementById('btn-4')
      ];

      buttons.forEach(b => {{
        b.className = "px-4 py-1.5 rounded-lg text-xs font-medium text-slate-600 hover:text-slate-900 transition-all";
      }});

      if (num === 'all') {{
        screens.forEach(s => s.style.display = 'flex');
        buttons[0].className = "px-4 py-1.5 rounded-lg text-xs font-bold transition-all bg-white text-blue-600 shadow-sm";
      }} else {{
        screens.forEach((s, idx) => {{
          s.style.display = (idx + 1 === num) ? 'flex' : 'none';
        }});
        buttons[num].className = "px-4 py-1.5 rounded-lg text-xs font-bold transition-all bg-white text-blue-600 shadow-sm";
      }}
    }}
  </script>
</body>
</html>"""

out_path = r'C:\Users\79402\.gemini\antigravity\brain\38920ab1-bb3c-411f-ac50-b4f5a6502b7b\view_all_pages.html'
with open(out_path, 'w', encoding='utf-8') as f:
    f.write(html_content)

print("Successfully generated view_all_pages.html")
