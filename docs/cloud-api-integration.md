# 线上人脸识别 API 集成方案

## 推荐方案结论

当前 App 已经实现了 CompreFace REST API 客户端，但这不等于已经部署了 CompreFace 服务。CompreFace 需要单独用 Docker 启动服务端，Android APK 里不会包含它。

如果目标是“期末作业省事、少花钱、尽量不用本地部署”，推荐顺序调整为：

1. **Face++ 云端 API**：优先推荐。曾在 2026-05-28 查询到 Face++ 官方价格页说明免费计划不需要信用卡、免费 Key 有共享 QPS 限制；这类额度和价格信息可能变化，演示前必须登录控制台复核。
2. **FaceHub / deepface.dev 等托管 API**：也可考虑，但免费额度和接口稳定性需要实际注册验证。
3. **CompreFace 自部署**：适合写“开源服务化架构”，但 Docker 镜像和依赖较重，不适合临近答辩时临时部署。

## 当前项目状态

- Android 端已经有云端 API 模式入口。
- 当前已实现两个云端客户端：
  - Face++ 托管云端 API：支持 API Key、API Secret、FaceSet outer_id、连接测试、云端录入和云端搜索识别。
  - CompreFace-compatible：支持服务地址、Recognition API Key、连接测试、云端录入和云端识别。
- 还没有在本机或云服务器部署 CompreFace 服务。
- 如果不想本地部署，直接在云端模式选择 Face++。

## 成本判断

| 方案 | 是否要自己部署 | 费用倾向 | 适合程度 |
| --- | --- | --- | --- |
| Face++ 云端 API | 不需要 | 曾查询到免费 Key 可测试且有 QPS 限制；最终以演示前控制台显示为准 | 最适合作业云端模式 |
| CompreFace 自部署 | 需要 Docker | 软件免费，但占本机/服务器资源；如果租服务器则可能花钱 | 适合作为开源备选 |
| AWS Rekognition / Azure Face | 不需要 | 通常有免费额度或按量计费，但需要云账号，后续可能产生费用 | 可用但账号/地区/权限更麻烦 |
| GPT-5.5 / 通用多模态模型 | 不需要 | 按 Token/图片计费 | 不适合作为人脸身份识别 |

## 为什么不把 GPT-5.5 当人脸识别

通用多模态大模型适合做图片理解、质量分析和答辩说明，不适合做“这个人是谁”的身份匹配。人脸识别应该使用专门的人脸比对/检索 API，例如 Face++、CompreFace、Rekognition Face Search 等。

## 双模式结构

当前 App 已支持两种识别模式：

| 模式 | 运行位置 | 依赖 | 适合演示点 |
| --- | --- | --- | --- |
| 本地离线 | Android 手机本机 | TFLite FaceNet + 本地 JSON 人脸库 | 断网可运行、隐私更可控 |
| 云端 API | Face++ 或 CompreFace REST 服务 | 网络 + API Key/Secret 或服务地址 | 展示线上服务架构、云端人脸库 |

## Android 端实现

新增模块：

- `CloudFaceSettings.kt`：保存云端服务地址、API Key 和相似度阈值。
- `CloudFaceClient.kt`：调用 CompreFace REST API。
- `FacePlusPlusClient.kt`：调用 Face++ 托管云端 API。
- 首页新增“识别模式”切换：本地离线 / 云端 API。
- 云端 API 内部可选提供商：Face++ 托管云端 / CompreFace 自部署。

云端模式下：

1. 用户切换到云端 API。
2. 用户选择 Face++ 或 CompreFace。
3. Face++ 填写 API Key、API Secret、FaceSet outer_id；CompreFace 填写服务地址和 Recognition API Key。
4. 点击“测试云端连接”确认服务可用。
5. App 会保存最近一次连接测试状态，显示测试时间、提供商和成功/失败原因；切换云端提供商后会提示重新测试。
6. 点击“录入样本”时，App 会裁剪单张人脸并上传到对应云端人脸库。
7. 点击“拍照识别”时，App 会上传人脸到对应云端搜索接口。
8. App 根据返回的最高相似度/置信度和默认阈值 `0.75` 判断云端识别成功或未知人员。

## CompreFace 部署步骤（备选）

1. 从 GitHub Release 下载 CompreFace。
2. 解压后运行 `docker-compose up -d`。
3. 浏览器打开 `http://localhost:8000/login`。
4. 创建账号、创建 Face Recognition Service。
5. 复制该服务的 Recognition API Key。
6. Android 模拟器中服务地址填 `http://10.0.2.2:8000`；真机中填写电脑局域网 IP，例如 `http://192.168.1.10:8000`。
7. 在 App 中切换到“云端 API”，先点击“测试云端连接”，确认服务可用后再录入和识别。

注意：CompreFace 自部署会拉取多个 Docker 镜像，包含后端、数据库、识别服务等组件。官方安装说明中也要求 Docker / Docker Compose，并说明正常运行时会有 core、api、admin、ui、postgres-db 等多个服务。它不需要向厂商付 API 费，但对电脑性能、磁盘空间和网络下载速度有要求；如果只是期末作业，除非电脑环境已经准备好，否则不建议把它作为唯一云端演示方案。

## Face++ 云端 API 方案（推荐）

Face++ 更符合“不要本地部署、尽量不花钱”的要求：

1. 注册 Face++ 账号。
2. 创建免费 API Key 和 API Secret。
3. App 云端模式保存 `api_key`、`api_secret` 和 `outer_id`。
4. 点击“测试云端连接”时，App 会创建或确认 FaceSet。
5. 录入时调用 Face++ Detect API 获取 `face_token`，设置 `user_id` 为姓名，再加入 FaceSet。
6. 识别时调用 Face Search API，在 FaceSet 中搜索最相似的人脸。

报告中可以写：云端模式使用 Face++ 托管人脸识别 API，避免本地部署服务端；本地模式则保留离线识别能力，形成“离线可运行 + 云端可扩展”的双模式。

截至 2026-05-28 查询，Face++ 官方价格页说明免费计划不需要信用卡，价格详情页说明一个账号有一个免费 API Key，Facial Recognition 免费 Key 有共享 QPS 限制。该信息具有时效性，最终报告中应写成“答辩前已登录控制台复核”，不要把历史查询结果当作长期承诺。

## 资料来源

- Face++ Pricing: https://www.faceplusplus.com/v2/pricing/
- Face++ Pricing Details: https://www.faceplusplus.com/v2/pricing-details/
- CompreFace GitHub: https://github.com/exadel-inc/CompreFace
- CompreFace Installation Options: https://github.com/exadel-inc/CompreFace/blob/master/docs/Installation-options.md

## 报告写法

可以把本项目写成“双引擎人脸识别系统”：

- 本地引擎：ML Kit 检测 + TFLite FaceNet 特征提取 + L2 距离匹配。
- 云端引擎：Android 拍照裁剪 + Face++ / CompreFace REST API + 云端人脸库匹配。

对比角度：

- 本地模式优点是离线、低延迟、隐私更好。
- 云端模式优点是服务化、可统一管理人脸库、便于多端接入。
- 云端连接状态可以作为报告截图，说明已提前验证 API Key、FaceSet 或自部署服务是否可用。
- 本地模式缺点是模型和存储都在手机内，跨设备同步不方便。
- 云端模式缺点是依赖网络和服务部署，且需要注意人脸数据隐私。

## 风险说明

- 不要把商业云厂商的长期密钥硬编码到 App。
- CompreFace API Key 仍属于敏感信息，课程演示可以临时填写，正式系统应通过后端代理保护。
- 上传人脸图片属于敏感生物特征数据，报告中应说明仅用于课程演示和本地/自部署服务测试。
