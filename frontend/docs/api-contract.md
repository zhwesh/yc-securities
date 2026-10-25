# OpenAPI 3.1.0
servers: []
info: {"title":"默认模块","description":"股票交易系统用户模块接口文档","version":"1.0.0"}

## PATHS

### POST /user/login/phone  -- 用户登录（手机号）
  desc: 使用手机验证码登录，成功后返回 JWT。
  tags: 
  security: []
  BODY[application/json] PhoneLoginDTO
    - phone: string req=true  手机号
    - code: string req=true  验证码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: LoginVO  数据

### POST /user/avatar  -- 上传头像
  desc: 上传当前登录用户的头像图片（multipart/form-data，字段名 file，最大 2MB），上传至阿里云OSS并返回头像URL
  tags: 用户接口
  security: [{"bearer":[]}]
  BODY[multipart/form-data] object
    - file: string req=true  头像图片文件（最大 2MB）
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: string  头像访问地址

### POST /user/register  -- 用户注册
  desc: 校验短信验证码后创建账号，成功即自动登录并返回 JWT。手机号、用户名唯一；身份证号不唯一（一人可开多个号）。
  tags: 用户接口
  security: []
  BODY[application/json] RegisterDTO
    - phone: string req=true  手机号
    - code: string req=true  短信验证码
    - username: string req=true  用户名（唯一）
    - password: string req=true  登录密码
    - name: string req=true  姓名
    - idNumber: string req=true  身份证号（18 位，不做唯一限制）
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: LoginVO  数据

### GET /user/code  -- 发送短信验证码
  desc: 按场景发送验证码（scene：0-注册 1-登录 2-找回密码 3-改绑手机号），60 秒内不可重复发送；0/3 场景要求手机号未注册，1/2 场景要求手机号已注册。验证码 5 分…
  tags: 用户接口
  security: []
  PARAM phone in=query req=true string  手机号
  PARAM scene in=query req=true int(0|1|2|3)  场景：0-注册 1-登录 2-找回密码 3-改绑手机号
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### POST /user/login/username  -- 用户登录（用户名）
  desc: 使用用户名和登录密码登录，成功后返回 JWT。
  tags: user
  security: []
  BODY[application/json] UsernameLoginDTO
    - username: string req=true  用户名
    - password: string req=true  登录密码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: LoginVO  数据

### PUT /user/password/reset  -- 重置密码（忘记密码）
  desc: 校验手机验证码后重置登录密码。
  tags: 用户接口
  security: []
  BODY[application/json] ResetPasswordDTO
    - phone: string req=true  手机号
    - code: string req=true  短信验证码
    - newPassword: string req=true  新密码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### GET /user  -- 查询当前用户信息
  desc: 查询当前登录用户的个人信息（用户名/姓名/头像/手机号/注册时间），用于前端刷新恢复登录状态；需登录。
  tags: 用户接口
  security: [{"bearer":[]}]
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: UserVO  数据

### PUT /user/update  -- 修改用户信息
  desc: 修改当前登录用户的资料（用户名、姓名、头像），可同时改绑手机号。只传需要修改的字段；用户名需唯一；改绑手机号时 phone 和 code 必须成对传，code 为 scene=3 …
  tags: 用户接口
  security: [{"bearer":[]}]
  BODY[application/json] UpdateUserDTO
    - username: string req=false  新用户名
    - image: string req=false  新头像URL
    - phone: string req=false  新手机号
    - code: string req=false  短信验证码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### GET /account  -- 查询当前用户的资金账户列表
  desc: 查询当前登录用户的全部资金账户。可用资产 = 现金总额 − 未成交买单占用，动态计算。
  tags: 资金账户接口
  security: []
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: AccountVO[]  数据

### PUT /account/deposit  -- 入金
  desc: 从绑定的银行卡向资金账户入金，增加可用资产（不校验交易密码）。
  tags: 资金账户接口
  security: []
  BODY[application/json] DepositDTO
    - accountId: long req=true  资金账号id
    - amount: num req=true  入金金额
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### POST /account/password/verify  -- 验证交易密码
  desc: 验证指定资金账户的交易密码；验证通过后按选择的时长保持在线（可选 6、12、24 小时），在线期间查询持仓/委托/成交、出金、下单等操作免交易密码。需登录且账户必须属于当前用户。
  tags: 资金账户接口
  security: []
  BODY[application/json] VerifyTradePasswordDTO
    - accountId: long req=true  资金账号id
    - tradePassword: string req=true  交易密码
    - onlineHours: int req=true  保持在线时长（小时），可选：6、12、24
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### PUT /account/password/update  -- 修改交易密码
  desc: 修改当前用户指定资金账户的交易密码；需验证原交易密码，账户冻结时不可修改。
  tags: 资金账户接口
  security: []
  BODY[application/json] UpdateTradePasswordDTO
    - accountId: long req=true  资金账号id
    - oldPassword: string req=true  原交易密码
    - newPassword: string req=true  新交易密码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### PUT /account/withdraw  -- 出金
  desc: 从资金账户出金至绑定的银行卡；需交易密码在线态（24 小时内验证过可免）；出金金额不能超过可用资产。
  tags: 资金账户接口
  security: []
  BODY[application/json] WithdrawDTO
    - accountId: long req=true  资金账号id
    - amount: num req=true  出金金额
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### POST /account/open  -- 开通资金账户
  desc: 当前登录用户开通一个资金账户并设置交易密码；一个用户可开通多个资金账户。新账户默认仅开通主板交易权限，其他板块需通过开通板块交易权限接口申请。
  tags: 资金账户接口
  security: []
  BODY[application/json] OpenAccountDTO
    - password: string req=true  交易密码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### PUT /account/board/open  -- 开通板块交易权限
  desc: 为指定资金账户开通板块交易权限；需登录且账户必须属于当前用户。开户默认仅开通 主板；仅支持申请 科创板、创业板、北交所（主板默认开通，无需申请）。开通门槛（按可用资产判定）：科创板…
  tags: 资金账户接口
  security: []
  BODY[application/json] OpenBoardDTO
    - accountId: string req=true  
    - boards: string[] req=true  
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### POST /bank-card  -- 绑定银行卡
  desc: 为指定资金账户绑定一张银行卡。前置流程：先调用验证卡号与预留手机号接口确认匹配，再调用发送银行卡验证码接口获取验证码后提交；服务端会校验银行卡号与预留手机号匹配、验证码及卡号唯一性…
  tags: 银行卡接口
  security: []
  BODY[application/json] BindBankCardDTO
    - accountId: long req=true  资金账号id
    - cardNumber: string req=true  银行卡号
    - bankName: string req=true  所属银行名称
    - phone: string req=true  银行预留手机号
    - code: string req=true  短信验证码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### GET /bank-card  -- 查询绑定银行卡
  desc: 查询指定资金账户绑定的全部银行卡；需登录且账户必须属于当前用户。
  tags: 银行卡接口
  security: []
  PARAM accountId in=query req=true long  资金账号id
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: BankCardVO[]  数据

### DELETE /bank-card  -- 解绑银行卡
  desc: 按卡号解除指定资金账户的银行卡绑定。前置流程：先调用验证卡号与预留手机号接口确认手机号与绑定记录一致，再调用发送银行卡验证码接口获取验证码后在请求中携带。直接删除绑定记录，不影响账…
  tags: 银行卡接口
  security: []
  BODY[application/json] DeleteBankCardDTO
    - accountId: int req=true  资金账号id
    - cardNumber: string req=true  银行卡号
    - phone: string req=true  银行预留手机号
    - code: string req=true  短信验证码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### GET /bank-card/code  -- 发送银行卡验证码
  desc: 发送短信验证码至指定的手机号，用于绑定/解绑银行卡（发送前校验手机号格式）。需登录。
  tags: 银行卡接口
  security: []
  PARAM phone in=query req=true string  银行预留手机号
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### POST /bank-card/verify  -- 验证卡号与预留手机号是否匹配
  desc: 验证银行卡号与预留手机号是否匹配；已绑定的银行卡与绑定记录中的预留手机号比对，未绑定的银行卡走银行侧校验（模拟，校验卡号格式）。用于绑卡/解绑前的前置校验。需登录。
  tags: 银行卡接口
  security: []
  BODY[application/json] VerifyBankCardDTO
    - cardNumber: string req=true  银行卡号
    - phone: string req=true  银行预留手机号
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### GET /position  -- 查询持仓列表
  desc: 查询指定资金账户的全部持仓（仅返回持仓数量大于 0 的记录）；需登录且只能查询自己的账户。现价、市值、浮动盈亏由行情模块实时计算。 需交易密码在线态（24 小时内验证过可免）。
  tags: 持仓接口
  security: []
  PARAM accountId in=query req=true long  资金账号id
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: PositionVO[]  数据

### GET /stock  -- 查询股票列表
  desc: 分页查询股票列表，支持按股票代码或名称搜索；公开接口，无需登录。
  tags: 股票接口
  security: []
  PARAM keyword in=query req=false string  股票代码或名称关键字
  PARAM pageNum in=query req=false int  页码，默认 1
  PARAM pageSize in=query req=false int  每页条数，默认 20，最大 100
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: object  数据

### GET /stock/{code}  -- 查询股票详情
  desc: 查询单只股票的基础资料；公开接口，无需登录。
  tags: 股票接口
  security: []
  PARAM code in=path req=true string  股票代码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: StockDetailVO  数据

### GET /market/order-book  -- 查询股票盘口
  desc: 查询股票实时盘口（卖盘/买盘的价格与挂单量）；depth 为盘口档位数，默认 5，范围 1~20；不足 depth 档时数组长度相应减少（如涨停时无卖盘、跌停时无买盘）；公开接口，…
  tags: 行情接口
  security: []
  PARAM code in=query req=true string  股票代码
  PARAM depth in=query req=false int  盘口档位数，默认 5，范围 1~20
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: OrderBookVO  数据

### GET /market/indexes  -- 查询指数行情
  desc: 查询全部指数的最新行情（点位/涨跌/成交量额）；无成分股成交时返回基准点位，成交量为零；公开接口，无需登录。
  tags: 行情接口
  security: []
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: IndexVO[]  数据

### GET /market/kline  -- 查询K线
  desc: 查询股票K线数据（日/周/月/季/年），按时间升序返回，用于K线图绘制；公开接口，无需登录。向前翻页：hasMore 为 true 时，将返回数据中最早一根的 time 作为 en…
  tags: 行情接口
  security: []
  PARAM code in=query req=true string  股票代码
  PARAM period in=query req=false int(1|2|3|4|5)  K线周期：1日K 2周K 3月K 4季K 5年K，默认1
  PARAM limit in=query req=false int  返回条数，默认 200
  PARAM endDate in=query req=false string  向前翻页游标：返回早于该日期的数据（不含），不传则返回最新数据
  PARAM adjust in=query req=false int(0|1|2)  复权方式：0不复权 1前复权 2后复权，默认1（基于前收盘价检测除权）
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: KLineVO  数据

### GET /market/quote  -- 查询个股行情
  desc: 查询单只股票的最新实时行情快照（现价/涨跌/今开/最高/最低/昨收/成交量额/换手率/振幅/量比/市盈率/市净率/市值等）；公开接口，无需登录。
  tags: 行情接口
  security: []
  PARAM code in=query req=true string  股票代码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: StockQuoteVO  数据

### GET /market/rank  -- 查询股票排行榜
  desc: 按行情指标排序查询股票列表（涨幅榜、跌幅榜、成交额榜等），数据来自实时行情快照；sortBy 取 1价格/2涨跌幅/3成交量/4成交额/5换手率/6市净率/7总市值/8流通市值/9…
  tags: 行情接口
  security: []
  PARAM sortBy in=query req=false int(1|2|3|4|5|6|7|8|9)  排序指标：1价格 2涨跌幅 3成交量 4成交额 5换手率 6市净率 7总市值 8流通市值 9每股净资产，默认2
  PARAM order in=query req=false int(1|2)  排序方向：1升序 2降序，默认2
  PARAM pageNum in=query req=false int  页码，默认 1
  PARAM pageSize in=query req=false int  每页条数，默认 20，最大 100
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: object  数据

### GET /market/quotes  -- 批量查询行情
  desc: 批量查询多只股票的实时行情快照，codes 为英文逗号分隔的股票代码（单次最多 50 只，超出部分截断；不存在的代码忽略），按请求顺序返回；公开接口，无需登录。
  tags: 行情接口
  security: []
  PARAM codes in=query req=true string  股票代码，英文逗号分隔，如 600000,000001
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: StockQuoteVO[]  数据

### GET /market/minute  -- 查询分时数据
  desc: 查询指定日期（默认今天）的当日分时走势数据，每分钟一个点（现价/分时均价/分钟成交量），按时间升序返回，用于分时图绘制；time 为秒级时间戳（本地时间按 UTC 换算，可直接用于…
  tags: 行情接口
  security: []
  PARAM code in=query req=true string  股票代码
  PARAM date in=query req=false string  日期（yyyy-MM-dd），默认今天；可查看历史某天的分时
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: MinuteVO  数据

### GET /market/trades  -- 查询逐笔成交
  desc: 查询股票逐笔成交明细，最新成交在前；time 为成交时间（HH:mm:ss），direction 区分主动买卖（0买 1卖）；数据来源于sold_orders，服务重启后保留；公开接口，无需登录。
  tags: 行情接口
  security: []
  PARAM code in=query req=true string  股票代码
  PARAM limit in=query req=false int  返回最近成交笔数，默认 50，最大 200
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: StockTradeDetailVO[]  数据

### POST /order  -- 下单（买入/卖出委托）
  desc: 创建买入/卖出委托：先落库再撮合。quantity 正数表示买入、负数表示卖出。orderKey 为客户端生成的UUID幂等键，同一键重复提交不会重复创建，直接返回已有委托。限价单价格须在涨跌幅范围内。资金账户冻结时返回业务失败。
  tags: 委托单接口
  security: [{"bearer":[]}]
  BODY[application/json] CreateOrderDTO
    - accountId: long req=true  资金账号id
    - code: string req=true  股票代码
    - type: int(1|2) req=true  委托类型：1限价 2市价
    - price: num req=true  委托价格（限价单必填，市价单忽略）
    - quantity: int req=true  委托数量（正数买入负数卖出，绝对值须为100的整数倍）
    - orderKey: string req=true  UUID幂等键；同一键重复提交返回已有委托
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: OrderVO  数据

### GET /order  -- 查询委托列表
  desc: 条件分页查询委托单，按委托时间倒序；需登录且只能查询自己的账户。 需交易密码在线态（24 小时内验证过可免）。
  tags: 委托单接口
  security: [{"bearer":[]}]
  PARAM accountId in=query req=true long  资金账号id
  PARAM code in=query req=false string  股票代码（可选，按股票过滤）
  PARAM status in=query req=false int  委托状态（可选）：1未成交 2部分成交 3全部成交 4已撤单
  PARAM startTime in=query req=false string  开始时间（可选，yyyy-MM-dd HH:mm:ss）
  PARAM endTime in=query req=false string  结束时间（可选，yyyy-MM-dd HH:mm:ss）
  PARAM pageNum in=query req=false int  页码，默认 1
  PARAM pageSize in=query req=false int  每页条数，默认 20
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: object  数据

### GET /order/{id}  -- 查询委托详情
  desc: 查询单笔委托详情；需登录且只能查询自己的委托。 需交易密码在线态（24 小时内验证过可免）。
  tags: 委托单接口
  security: [{"bearer":[]}]
  PARAM id in=path req=true long  委托单id
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: OrderVO  数据

### PUT /order/cancel  -- 撤单
  desc: 撤销未成交/部分成交的委托（撤回剩余未成交部分）；已全部成交的委托无法撤销。
  tags: 委托单接口
  security: [{"bearer":[]}]
  BODY[application/json] CancelOrderDTO
    - accountId: long req=true  资金账号id
    - orderId: long req=true  委托单id
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### GET /trade  -- 查询成交记录
  desc: 条件分页查询成交记录，按成交时间倒序；需登录且只能查询自己的账户。 需交易密码在线态（24 小时内验证过可免）。
  tags: 委托单接口
  security: [{"bearer":[]}]
  PARAM accountId in=query req=true long  资金账号id
  PARAM code in=query req=false string  股票代码（可选，按股票过滤）
  PARAM startTime in=query req=false string  开始时间（可选，yyyy-MM-dd HH:mm:ss）
  PARAM endTime in=query req=false string  结束时间（可选，yyyy-MM-dd HH:mm:ss）
  PARAM pageNum in=query req=false int  页码，默认 1
  PARAM pageSize in=query req=false int  每页条数，默认 20
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: object  数据

### GET /watchlist  -- 查询自选股列表
  desc: 查询当前登录用户的自选股列表，按用户自定义顺序返回股票代码和名称，最多 50 只。实时行情请配合 /market/quotes 或 WebSocket 订阅获取。
  tags: 自选股接口
  security: []
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: StockVO[]  数据

### POST /watchlist  -- 添加自选股
  desc: 将指定股票添加到当前用户自选列表。股票代码必须存在；重复添加、超过 50 只时返回业务错误。
  tags: 自选股接口
  security: []
  BODY[application/json] AddWatchlistDTO
    - code: string req=true  股票代码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### DELETE /watchlist  -- 删除自选股
  desc: 删除当前用户自选列表中的指定股票。股票不在自选中时返回业务错误。
  tags: 自选股接口
  security: []
  PARAM code in=query req=true string  股票代码
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

### PUT /watchlist  -- 更新自选股排序
  desc: 覆盖当前用户自选股列表，并按传入顺序保存（可用于拖拽排序）。传空数组表示清空自选；代码不能重复、必须存在，最多 50 只。
  tags: 自选股接口
  security: []
  BODY[application/json] UpdateWatchlistDTO
    - codes: string[] req=true  自选股代码列表（按展示顺序）
  RESP 200[application/json] object
    - code: int  1 成功，0 失败
    - msg: string,null  错误信息
    - data: null  无数据（操作成功）

## SCHEMAS

### UsernameLoginDTO  
  - username: string req=true  用户名
  - password: string req=true  登录密码

### PhoneLoginDTO  
  - phone: string req=true  手机号
  - code: string req=true  验证码

### OpenBoardDTO  
  - accountId: string req=true  
  - boards: string[] req=true  

### DeleteBankCardDTO  
  - accountId: int req=true  资金账号id
  - cardNumber: string req=true  银行卡号
  - phone: string req=true  银行预留手机号
  - code: string req=true  短信验证码

### RegisterDTO  用户注册请求参数
  - phone: string req=true  手机号
  - code: string req=true  短信验证码
  - username: string req=true  用户名（唯一）
  - password: string req=true  登录密码
  - name: string req=true  姓名
  - idNumber: string req=true  身份证号（18 位，不做唯一限制）

### ResetPasswordDTO  重置密码请求参数
  - phone: string req=true  手机号
  - code: string req=true  短信验证码
  - newPassword: string req=true  新密码

### UpdateUserDTO  修改用户信息请求参数
  - username: string req=false  新用户名
  - image: string req=false  新头像URL
  - phone: string req=false  新手机号
  - code: string req=false  短信验证码

### OpenAccountDTO  
  - password: string req=true  交易密码

### UpdateTradePasswordDTO  
  - accountId: long req=true  资金账号id
  - oldPassword: string req=true  原交易密码
  - newPassword: string req=true  新交易密码

### BindBankCardDTO  
  - accountId: long req=true  资金账号id
  - cardNumber: string req=true  银行卡号
  - bankName: string req=true  所属银行名称
  - phone: string req=true  银行预留手机号
  - code: string req=true  短信验证码

### DepositDTO  
  - accountId: long req=true  资金账号id
  - amount: num req=true  入金金额

### WithdrawDTO  
  - accountId: long req=true  资金账号id
  - amount: num req=true  出金金额

### CreateOrderDTO  
  - accountId: long req=true  资金账号id
  - code: string req=true  股票代码
  - type: int(1|2) req=true  委托类型：1限价 2市价
      enum: [1,2]
  - price: num req=true  委托价格（限价单必填，市价单忽略）
  - quantity: int req=true  委托数量（正数买入负数卖出，绝对值须为100的整数倍）
  - orderKey: string req=true  UUID幂等键；同一键重复提交返回已有委托

### CancelOrderDTO  
  - accountId: long req=true  资金账号id
  - orderId: long req=true  委托单id

### VerifyTradePasswordDTO  
  - accountId: long req=true  资金账号id
  - tradePassword: string req=true  交易密码
  - onlineHours: int req=true  保持在线时长（小时），可选：6、12、24

### VerifyBankCardDTO  
  - cardNumber: string req=true  银行卡号
  - phone: string req=true  银行预留手机号

### AddWatchlistDTO  
  - code: string req=true  股票代码

### UpdateWatchlistDTO  
  - codes: string[] req=true  自选股代码列表（按展示顺序）

### LoginVO  
  - token: string req=true  JWT 令牌，后续请求头带 Authorization: Bearer {token}
  - id: long req=true  用户 id
  - username: string req=true  用户名
  - name: string req=true  姓名
  - image: string,null req=false  头像 URL
  - phone: string req=true  手机号
  - createTime: string req=true  创建时间

### AccountVO  
  - id: long req=true  资金账号id
  - availableAssets: num req=true  可用资产（现金总额 − 未成交买单占用）
  - boards: string[] req=true  可交易板块列表（开户默认仅 主板，其他板块需申请开通）
  - status: int req=true  状态：1正常 2冻结
  - createTime: string req=true  开户时间

### BankCardVO  
  - id: long req=true  主键
  - accountId: long req=true  资金账号id
  - cardNumber: string req=true  银行卡号
  - bankName: string req=true  所属银行名称
  - phone: string req=true  银行预留手机号（掩码）
  - bindTime: string req=true  绑卡时间

### PositionVO  
  - id: long req=true  持仓记录id
  - accountId: long req=true  资金账号id
  - code: string req=true  股票代码
  - stockName: string req=true  股票名称
  - quantity: int req=true  持仓数量
  - availableQuantity: int req=true  可卖数量
  - costPrice: num req=true  成本价
  - currentPrice: num req=true  现价（行情模块提供）
  - marketValue: num req=true  持仓市值（现价 × 持仓数量）
  - profitLoss: num req=true  浮动盈亏 =（现价 − 成本价）× 持仓数量
  - profitLossRatio: num req=true  盈亏比例（浮动盈亏 / 成本金额）

### StockVO  
  - code: string req=true  股票代码
  - name: string req=true  股票名称

### StockDetailVO  
  - code: string req=true  股票代码
  - name: string req=true  股票名称
  - board: string req=true  所属板块
  - listDate: string req=true  上市日期
  - totalShares: long req=true  总股本（股）
  - circulateShares: long req=true  流通股本（股）
  - bookValue: number,null req=true  每股净资产
  - eps: number,null req=true  每股收益
  - initialPrice: num req=true  上市初始价

### StockRankVO  
  - code: string req=true  股票代码
  - stockName: string req=true  股票名称
  - price: num req=true  现价
  - changePercent: num req=true  涨跌幅（百分比，如 9.98 表示 +9.98%）
  - volume: num req=true  成交量（手，1 手 = 100 股）
  - amount: num req=true  成交额
  - turnoverRate: num req=true  换手率（百分比）
  - pb: num req=true  市净率

### OrderVO  
  - id: long req=true  委托单id
  - accountId: long req=true  资金账号id
  - stockCode: string req=true  股票代码
  - stockName: string req=true  股票名称
  - price: number,null req=true  委托价格
  - quantity: int req=true  委托数量（正数买入负数卖出）
  - soldQuantity: int req=true  已成交数量，非负
  - type: int(1|2) req=true  委托类型：1限价 2市价
      enum: [1,2]
  - status: int(1|2|3|4) req=true  委托状态：1未成交 2部分成交 3全部成交 4已撤单
      enum: [1,2,3,4]
  - createTime: string req=true  委托时间

### TradeVO  
  - id: long req=true  成交记录id
  - orderId: long req=true  委托单id
  - accountId: long req=true  资金账号id
  - stockCode: string req=true  股票代码
  - stockName: string req=true  股票名称
  - price: num req=true  成交价格
  - quantity: int req=true  成交数量（正数买入负数卖出）
  - amount: num req=true  成交金额，卖出为负数
  - createTime: string req=true  成交时间

### KLineVO  
  - code: string req=true  股票代码
  - period: int req=true  周期：1日K 2周K 3月K 4季K 5年K
  - hasMore: boolean req=true  是否存在更早的数据（向前翻页加载用）
  - kLine: KLineData[] req=true  K线数组（按时间升序）

### KLineData  
  - time: string req=true  时间（yyyy-MM-dd）
  - open: num req=true  开盘价
  - high: num req=true  最高价
  - low: num req=true  最低价
  - close: num req=true  收盘价
  - volume: num req=true  成交量（手，1 手 = 100 股）
  - turnover: num req=true  成交额

### MinuteVO  
  - code: string req=true  股票代码
  - preClose: num req=true  昨收价（用于分时图昨收参考线与涨跌幅轴）
  - minutes: MinuteData[] req=true  分时数据（按时间升序）

### MinuteData  
  - time: long req=true  时间戳（秒级，本地时间按 UTC 换算）
  - price: num req=true  当前价
  - avgPrice: num req=true  分时均价
  - volume: num req=true  该分钟成交量（手，1 手 = 100 股）

### StockQuoteVO  
  - code: string req=true  股票代码
  - name: string req=true  股票名称
  - price: num req=true  现价
  - change: num req=true  涨跌额
  - changePercent: num req=true  涨跌幅（百分比）
  - open: number,null req=true  今开
  - high: number,null req=true  最高
  - low: number,null req=true  最低
  - preClose: num req=true  昨收
  - volume: num req=true  成交量（手，1 手 = 100 股）
  - amount: num req=true  成交额
  - turnoverRate: num req=true  换手率（百分比）
  - amplitude: number,null req=true  振幅（百分比）
  - volumeRatio: number,null req=true  量比
  - pe: number,null req=true  市盈率
  - marketValue: num req=true  总市值
  - circulateMarketValue: num req=true  流通市值
  - pb: number,null req=true  市净率

### OrderBookVO  
  - code: string req=true  股票代码
  - depth: int req=true  盘口档位数
  - asks: OrderBookItemVO[] req=true  卖盘（按价格从低到高，最多 depth 档）
  - bids: OrderBookItemVO[] req=true  买盘（按价格从高到低，最多 depth 档）

### OrderBookItemVO  
  - price: num req=true  档位价格
  - volume: num req=true  档位数量（手，1 手 = 100 股）

### StockTradeDetailVO  
  - time: string req=true  成交时间（HH:mm:ss）
  - price: num req=true  成交价格
  - volume: long req=true  成交量（股）
  - direction: int req=true  买卖方向：0买 1卖

### IndexVO  
  - code: string req=true  指数代码
  - name: string req=true  指数名称
  - price: num req=true  当前点位
  - change: num req=true  涨跌点数
  - changePercent: num req=true  涨跌幅（百分比）
  - volume: num req=true  成交量（手，1 手 = 100 股）
  - amount: num req=true  成交额

### UserVO  
  - id: long req=true  用户id
  - username: string req=true  用户名
  - name: string req=true  姓名
  - image: string,null req=true  头像URL
  - phone: string req=true  手机号
  - createTime: string req=true  注册时间

### Result  
  - code: int req=true  1 成功，0 失败
  - msg: string,null req=true  错误信息
  - data: string,integer,boolean,array,object,number,null req=true  数据

### PageResult  
  - total: long req=true  总记录数
  - records: string[] req=true  当前页数据集合