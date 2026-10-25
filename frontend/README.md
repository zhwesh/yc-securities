# 易财证券 前端

A 股模拟交易终端前端，白色同花顺风格，红涨绿跌。品牌名「易财证券」。

- 技术栈：Vue 3.5 + Vite 8 + TypeScript + Pinia + Vue Router + Element Plus + Axios + TradingView Lightweight Charts v5
- 后端：`E:\program\java\yc-securities\backend`（Spring Boot 4，端口 8080）
- 接口契约导出：`docs/openapi.json`、可读版 `docs/api-contract.md`
- 桌面浏览器优先（1280px+），手机端未适配

## 快速开始

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # vue-tsc --noEmit && vite build
npm run typecheck  # 只做类型检查
```

后端先启动（见后端仓库 `AGENTS.md`）。**不要直连 8080**，后端没有 CORS：

| 前端路径 | 转发到 | 说明 |
|---|---|---|
| `/api/*` | `http://localhost:8080/*` | 去掉 `/api` 前缀 |
| `/ws` | `ws://localhost:8080/ws` | WebSocket，`ws: true` |

## 目录结构

```
src/
  api/            http.ts（axios 实例 + 拦截器）、user / account / market / order / watchlist
  components/     见下方组件表
  composables/    useCountdown（短信验证码倒计时）
  config/         brand.ts（易财证券，品牌名/Logo 集中在这里）
  layouts/        MainLayout.vue（顶栏 + 横向导航 + 内容区）
  router/         路由 + 登录守卫 + 登录过期跳转
  stores/         auth / account / market / tradeAuth
  styles/         theme.css（主题变量）、element.css（Element Plus 覆盖）
  types/          与后端 VO/DTO 一一对应的类型定义
  utils/          format.ts（展示格式化）、trade.ts（费率/手数）、chart.ts（图表配置）
  views/          Login / Register / ResetPassword / Home / Watchlist / Screener
                  / StockDetail / Trade / Assets / Profile
  ws/             marketSocket.ts（单例连接、订阅引用计数、重连、心跳）
```

### 页面（顶栏导航：大盘 / 自选 / 选股 / 持仓 / 账号）

| 路由 | 页面 | 内容 |
|---|---|---|
| `/login` `/register` `/reset-password` | 登录 / 注册 / 找回密码 | 用户名密码 + 手机验证码两种登录，验证码 60s 倒计时 |
| `/` | 大盘 | 5 个指数卡片、自选股侧栏、排行榜（涨幅/跌幅/成交额/换手率/总市值） |
| `/watchlist` | 自选 | 自选表格（实时行情）、按代码添加、上移/下移排序、删除，上限 50 只 |
| `/screener` | 选股 | 基于 `/market/rank` 的全市场排序选股：6 个预设榜 + 表头点击切换排序 + 分页；另支持代码/名称搜索 |
| `/stock/:code` | 个股详情 | 见下方「个股页版式」 |
| `/trade` | 持仓 | 账户切换、总资产/可用/持仓市值/浮动盈亏、下单面板、持仓/当前委托/全部委托/成交记录、撤单 |
| `/assets` | 账号 | 开户、开板块、入金/出金、银行卡绑定/解绑、交易密码修改与在线态 |
| `/profile` | 个人中心 | 头像上传、改用户名、换绑手机号、退出登录（从右上角头像菜单进入） |

### 个股页版式（按设计图）

```
顶栏（logo + 易财证券 + 导航 + 用户名/头像）
股票头：价格 + 涨跌幅 │ 3×3 指标（开盘价/市值/量比、最高价/流通市值/换手率、最低价/市盈率/成交额） │ ○买入 ○卖出 ○撤单 ○明细
tab 行：分时 日k 周k 月k 季k 年k 1分 5分 15分 30分 60分 120分    （右侧：复权切换，仅日/周/月/季/年可用）
三栏：左「集合竞价」小面板 │ 中：主图 + 副图（成交量/成交额下拉） + 底部 10 项统计量 │ 右：五档盘口 + 明细（逐笔成交）
```

「买入/卖出/撤单/明细」四个圆形按钮各打开一个弹框，页面本身不常驻下单面板。

### 关键组件

| 组件 | 说明 |
|---|---|
| `StockChart.vue` | 主图 + 副图两个联动图表。分时用面积图（价格 + 均价 + 昨收虚线），K 线用蜡烛图 + MA5/10/20。成交量/成交额副图可切换；向左拖到边缘按 `endDate`/`endTime` 游标加载更早数据 |
| `QuoteHeader.vue` | 个股页顶部：大价格 + 3×3 指标 + 四个圆形操作按钮 |
| `AuctionPanel.vue` | 集合竞价：竞价阶段 1 秒轮询 `/market/auction`，连续交易阶段降频 |
| `StockStats.vue` | 个股页底部 10 项统计量（总股本/流通股本/每股净资产/每股收益/发行价/上市日期/板块/振幅/昨收/成交量） |
| `OrderBook.vue` | 五档盘口，卖盘倒序展示，量条按最大挂单量比例 |
| `TradeTape.vue` | 逐笔成交（`volume` 单位是**股**，展示除以 100） |
| `OrderForm.vue` | 限价/市价、买卖、预估费用、涨跌停校验、T+1 可卖校验、二次确认 |
| `TradePasswordDialog.vue` | 全局交易密码验证弹框（`useTradeAuthStore().ensure()`） |
| `OrderTable.vue` | 委托列表 + 撤单，监听 WS `orderUpdate` 自动刷新 |
| `PositionTable.vue` / `TradeTable.vue` | 持仓（含 T+1「可卖不足」标记）/ 成交记录 |
| `RankTable.vue` / `WatchlistPanel.vue` / `IndexCards.vue` | 大盘页的排行榜 / 自选侧栏 / 指数卡片 |

## 与后端契约的关键点（踩过的坑）

1. **业务失败也是 HTTP 200**。`{code:1}` 成功、`{code:0}` 失败；登录过期同样是 200 + `msg="未登录或登录已过期"`。拦截器只认 `code`，见 `src/api/http.ts`。
2. **`GET /position` 和 `PUT /account/withdraw` 需要「交易密码在线态」**。未验证返回 `请先验证交易密码`，先调 `POST /account/password/verify`（注意是 POST，不是 PUT）。其余接口不需要。
3. **时间格式不是 ISO**。REST 返回 `yyyy-MM-dd HH:mm:ss`（空格），包括 `CallAuctionVO.updateTime`；只有 WS `orderUpdate` 是 `yyyy-MM-ddTHH:mm:ss`。`/order`、`/trade` 的 `startTime`/`endTime`、`/market/minute-kline` 的 `endTime` 都必须传空格格式。
4. **单位不统一**：`/market/trades` 的 `volume` 是**股**，其余行情接口是**手**；下单 `quantity` 是**股**且买正卖负，绝对值必须 100 的整数倍。
5. **`PositionVO.profitLossRatio` 是比例**（`0.1234` = +12.34%），展示要 ×100；而 `changePercent`/`turnoverRate`/`amplitude` 已经是百分数。
6. **`TradeVO.amount` 卖出为负数**；`OrderVO.soldQuantity` 恒为非负（无符号）。
7. `/market/rank` 的 `records` 是完整的 `StockQuoteVO`，**后端没有 `StockRankVO`**；`total` 是全市场只数（5624）。
8. `AccountVO` 没有总资产/冻结金额字段，总资产 = `availableAssets + Σ position.marketValue`，未成交买单冻结的资金前端看不到。
9. **分时 `time`** 是「本地时间按 UTC 换算」的秒级时间戳，格式化必须用 `getUTCHours()`。`/market/minute-kline` 的 `time` 是字符串 `yyyy-MM-dd HH:mm`，前端按同样的「墙上时间当 UTC」约定转成时间戳，坐标轴才不会错位。
10. **分钟 K 线数据很稀**：历史来自 `minute_quote`，开发库没导入历史分钟数据，只有应用运行期间才会逐分钟积累。所以「1/5/15/30/60 分」默认经常是空态，需要让做市机器人运行几分钟攒数据。
11. **`120 分` 后端不支持**（`period` 只到 60）。界面上的 120 分 tab 是前端用 60 分按「上午/下午」各自合并出来的（`StockChart.aggregate120`）。
12. **集合竞价只在 `trading.always-on=false` 时产生数据**。开发默认 24 小时连续交易，`/market/auction` 会一直返回 `phase=4 连续交易`、价格为空。
13. `POST /order` 的 `orderKey` 幂等键由前端生成UUID（`nextOrderKey()`）；后端校验交易时段和账户冻结状态。
14. **头像地址是私有 OSS 裸链接**。后端把 `https://yc-securities.oss-cn-beijing.aliyuncs.com/<uuid>.png` 直接写进 `user.image`，没有签名，bucket 是私有读，浏览器请求必然是 **403**。而且这类请求在部分网络下要十几秒才失败，会把页面拖得很卡。
    前端用 `components/UserAvatar.vue` 兜住：3 秒超时 / 出错就回退成文字头像，并把失败的 URL 记到 localStorage（`utils/avatar.ts`）一小时内不再重试。
    要真正修好，得后端返回签名 URL 或把 bucket 改成公共读。

## 主题

`src/styles/theme.css` 定义全部设计变量：涨 `--up: #e5484d`、跌 `--down: #00a870`、平 `--flat`、主色 `--primary`。

暗色主题变量已写好（`html.dark { ... }`），但**没有实现切换入口**，后续只需在 `<html>` 上 toggle `dark` 类。

品牌名/Logo 在 `src/config/brand.ts`，正式 Logo 待替换 `brand.logoText` 所在的圆形占位。
