# OpenTalking 面试职责与提示词说明

## 当前架构

模拟面试和自由简历面试的问题、追问与报告都由 AI Interview Platform
后端的 DeepSeek 生成。浏览器创建 OpenTalking 会话时固定发送
`agent_enabled=false`，OpenTalking 只负责 WebRTC 视频、TTS 朗读和 STT
转写，不再调用千问生成追问。

所有题目朗读都会带 `【只朗读模式】` 前缀。服务器必须应用发布包中的
`docs/deployment/patches/opentalking-readonly-speak.patch`，由 OpenTalking 去掉
前缀并绕过 LLM、记忆和 Agent。这样“重读本题”不会把题目当作候选人回答，
也不会在第二次重读时生成“好的，你提到了……”之类的错误追问。

## OpenTalking 独立 Studio 提示词

`OPENTALKING_LLM_SYSTEM_PROMPT` 只影响 OpenTalking 自带 Studio 或其他主动
启用 Agent 的会话，不控制 AI Interview Platform。发布包仍提供
`opentalking/opentalking-prompt.env.example`，用于需要单独测试 Studio 时手工
同步；该文件不包含 API Key、TURN 密码或其他密钥。

## 服务器核验

```bash
cd /opt/digital_human/opentalking
grep -n '只朗读模式' opentalking/pipeline/speak/synthesis_runner.py
curl -s http://127.0.0.1:8210/runtime-config
curl -s http://127.0.0.1:8210/sessions/webrtc/ice-config
```

若第一条命令没有结果，先备份源码，再应用补丁并重启 OpenTalking。具体命令
见发布包中的 `docs/UPDATE_GUIDE.md`。
