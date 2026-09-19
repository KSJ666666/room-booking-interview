# Answers

四道题目的实现说明与书面答案。起始 commit:`9579868`。

./mvnw test` 全绿，共 **22 个测试**（`BookingWindowPolicyTest` 9 个 + `BookingApiTest` 13 个）。

---

## 任务一：实现时间窗口规则

### 实现要点

`BookingWindowPolicy.evaluate` 按 README 给定的优先级**短路**执行，只返回枚举值，**不抛异常**：

1. `start == null || end == null` → `MISSING_BOUNDARY`
2. `start.isAfter(end) || start.isEqual(end)` → `END_NOT_AFTER_START`
3. `duration < 30 || duration > 120` → `DURATION_OUT_OF_RANGE`
4. 其余 → `VALID`

几点关键判断：

- **判空必须排第一**。第 3 步要调用 `start` 的方法，若 `start` 为 null 又先执行第 3 步，会直接抛 `NullPointerException`（在空引用上调用实例方法），异常飞到 `handleUnexpected` 变成 500，而不是应有的 `MISSING_BOUNDARY`。“把非法输入在参数校验之前就用掉”是后端最常见的 500 来源之一。
- **第 2 步必须包含相等**。README 原文是“`end` 不晚于 `start`”，不是“早于”。只写 `start.isAfter(end)` 会漏掉 `end == start`，该输入会掉到第 3 步被判成 `DURATION_OUT_OF_RANGE`（时长 0 分钟），与规范不符。
- **30 和 120 是合法边界**，所以时长判断用严格的 `< 30` 和 `> 120`。
- `evaluate` 不抛异常，是因为调用方 `BookingService.isAvailable` 里已有 `if (result != VALID) throw new InvalidBookingWindowException(result)`——“结果不合法就抛异常”是上层的职责，重复抛会破坏接口约定（也拿不到返回值做单元测试）。

### 边界测试的预期与判断过程

以 `returnsValidForExact30Minutes`（`09:00 → 09:30`）为例：

- **预期**：`VALID`
- **判断过程**：时长 = `ChronoUnit.MINUTES.between(09:00, 09:30)` = 30；规范说“30 分钟和 120 分钟均为合法边界”，且代码里的不合法条件是严格小于 30；30 不小于 30，120 分钟那条也不成立；因此落在最后的 `VALID`
- **验证**：测试跑绿即确认；再把断言临时改成 `DURATION_OUT_OF_RANGE` 会变红，证明断言真的在生效

### 用例设计思路

先按规则划**等价类**（每条规则取一个代表），再对数字边界取**边界值 ±1**：

| 用例 | 输入 | 预期 |
|---|---|---|
| 下边界 | 09:00 → 09:30（30 min） | VALID |
| 上边界 | 09:00 → 11:00（120 min） | VALID |
| 下边界相邻 | 09:00 → 09:29（29 min） | DURATION_OUT_OF_RANGE |
| 上边界相邻 | 09:00 → 11:01（121 min） | DURATION_OUT_OF_RANGE |
| start 缺失 | null → 11:00 | MISSING_BOUNDARY |
| end 缺失 | 09:00 → null | MISSING_BOUNDARY |
| end 早于 start | 09:10 → 09:00 | END_NOT_AFTER_START |
| end 等于 start | 09:00 → 09:00 | END_NOT_AFTER_START |
| 多规则同时违反 | 11:00 → 09:00 | END_NOT_AFTER_START |

**多规则用例的说明**：该输入同时违反“顺序”和“时长”（反转后时长为 −120，必然也小于 30）。因为规则按优先级短路执行，顺序规则排在前面，所以返回 `END_NOT_AFTER_START` 而不是 `DURATION_OUT_OF_RANGE`。任何反转输入都必然同时违反时长规则——这正是“规则优先级”的体现。

### Commit type

`feat`：原本 `evaluate` 直接 `throw new UnsupportedOperationException`，即这个行为此前**不存在**，本次是新增能力，而不是修正一个已经运行但错误的行为，因此用 `feat` 而非 `fix`。scope 用 `domain`，因为改动落在领域层的规则类及其测试。

---

## 任务二：修复缺失预约的错误响应

### 修改前的三种假设与验证方式

| 假设 | 内容 | 如何验证 | 结论 |
|---|---|---|---|
| 1 | service 没有抛异常，返回了空值/请求正常返回 | 在 `BookingService.get` 打断点看返回值；看响应体的 `code` | **排除** |
| 2 | 抛了异常，但全局处理器没有对应入口，走了兜底 handler | 看响应 `code` 是否为 `INTERNAL_ERROR`；在 `handleUnexpected` 打断点 | **排除** |
| 3 | 有对应 handler，但映射的状态码写错 | 在 `handleBookingNotFound` 打断点，看它实际返回的 `HttpStatus` | **支持** |

**排除假设 1 的理由**：`orElseThrow` 的语义是二选一——有值就返回，为空就抛异常，**不存在返回 null 的路径**。如果把它改成 `orElse(null)`，service 返回 null 后 controller 里 `booking.id()` 会抛 `NullPointerException`，最终响应 code 会是 `INTERNAL_ERROR`，与实测的 `BOOKING_NOT_FOUND` 不符。

**排除假设 2 的理由**：实测响应 code 是 `BOOKING_NOT_FOUND`（不是兜底的 `INTERNAL_ERROR`），且断点确实停在 `handleBookingNotFound` 而不是 `handleUnexpected`。

### Debugger 观察到的证据链

以 `GET /rooms/room-101/bookings/booking-missing` 为例：

1. `BookingController.getBooking`：`roomId = "room-101"`、`bookingId = "booking-missing"`
2. `BookingService.get` → `requireRoom("room-101")` 通过（房间存在）
3. `findByIdAndRoomId("booking-missing", "room-101")` 返回 `Optional.empty()`
4. `orElseThrow` 抛出 `BookingNotFoundException("Booking booking-missing was not found in room room-101")`
5. 异常沿调用栈上抛，被 Spring MVC 的 `DispatcherServlet.doDispatch` 捕获，装进 `dispatchException`
6. 交给 `ApiExceptionHandler.handleBookingNotFound`，它返回 `HttpStatus.INTERNAL_SERVER_ERROR` → **500**

（另注：实测时发现用 `//rooms/...` 这种多打一个斜杠的 URL 请求不会匹配到 controller，走的是另一条失败路径，响应 code 为 `INTERNAL_ERROR`。排查时注意用规范的单斜杠 URL。）

### 最小修改

只改 `ApiExceptionHandler.handleBookingNotFound` 里的一行：`HttpStatus.INTERNAL_SERVER_ERROR` → `HttpStatus.NOT_FOUND`。`service`、`repository`、`controller` 均未改动。

**为什么改 handler 而不是改 service**：

- **分层职责**：状态码是 HTTP 协议概念，属于 web 层。service 层表达的是业务事实（“booking 不存在”），由 handler 负责翻译成 HTTP 响应。翻译错了就改翻译层。
- **不能改成返回 null**：null 是“无声的失败”，调用方容易漏检查；异常是“响亮的失败”，强制中断并携带上下文。
- **爆炸半径可控**：`handleBookingNotFound` 只处理 `BookingNotFoundException`，该异常只在 `BookingService.get` 一处抛出，受影响范围只有一个接口的分支。

### 为什么这里应返回 404 而不是 400

- **404 Not Found**：客户端请求的 URI 指向的资源**不存在**。本题查询的 booking 不存在，语义完全吻合。
- **400 Bad Request**：请求本身**不合法**（格式、语义错误），服务器无法处理。若请求参数格式错误（如时间字符串解析失败）才应是 400。

其它常见 4xx：

| 状态码 | 含义 | 例子 |
|---|---|---|
| 401 Unauthorized | 未认证（没登录/凭据无效） | 未带 token 访问受保护接口 |
| 403 Forbidden | 已认证但无权限 | 普通用户删除他人订单 |
| 405 Method Not Allowed | 方法不被允许 | 对只支持 GET 的地址发 DELETE |
| 409 Conflict | 请求合法但与资源当前状态冲突 | 本题任务四的预约时间重叠 |
| 415 Unsupported Media Type | 请求格式不支持 | 要求 JSON 却发了 text/plain |

### Commit type

`fix`：修改的是**已存在但行为错误**的功能（把错误的状态码改对），不是新增能力。

---

## 任务三：修复可用性判断故障

### 假设与排除

| 假设 | 验证方式 | 结论 |
|---|---|---|
| 重叠判定式本身写错（`<` 写成 `<=`） | 检查两个比较表达式，均为严格的 `isBefore` | 排除 |
| 查询时没有按 roomId 过滤，拿错了数据 | Evaluate Expression 求 `findByRoomId("room-202").size()` = 2；循环只转 2 圈 | 排除 |
| 循环体内结果被覆盖，只保留了最后一条的结论 | 逐圈观察三个布尔值与 `hasConflict` | **支持** |

关于“过滤没生效”的误判：Debugger 里能看到 `bookings.values()` 里有 3 条，那是**过滤前的源头数据**（仓库共 3 条种子数据），Stream 的 filter 是惰性逐元素执行的，返回值才是 2。另外 `findByRoomId` 按 roomId 过滤（一个房间可有多条），`findByIdAndRoomId` 按 bookingId 过滤（最多 1 条）——两者容易混淆。

### 根因

```java
hasConflict = overlaps;   // 每圈无条件覆盖
```

业务要的是**存在性**——“只要有任意一条已有预约重叠，就不可订”；而代码表达的是“**最后一条**预约是否重叠”。每圈把上一圈的结论擦掉，最终只剩最后一圈的结果。

对 room-202（booking-2021 10:00–10:30、booking-2022 12:00–12:30），候选 10:15–10:45 的逐圈推演：

| 圈 | existing | 条件一 | 条件二 | overlaps | hasConflict |
|---|---|---|---|---|---|
| 1 | 2021 | true | true | **true** | true |
| 2 | 2022 | true | false | false | **false** |

返回 `!false = true`，即错误地“可订”。**该房间只有 1 条预约时不暴露问题**（最后一条就是唯一一条），这也是原有测试抓不到它的原因。

### 修复

`hasConflict = hasConflict || overlaps;` —— 一旦为 true 就锁死。（等价写法：发现重叠立刻 `return false`，还能少做无用功。）

### 测试及其验证的行为

| 测试 | 验证什么 | 修复前会红吗 |
|---|---|---|
| `returnsUnavailableForOverlappingWindow` | 与 booking-2021 重叠 → `available=false` | **会红**（真正捕获该缺陷的回归测试） |
| `returnsAvailableForNonOverlappingWindow` | 完全空档 → true | 不会红（覆盖率测试） |
| `returnsAvailableForAdjacentWindow` | 首尾相接（10:30 接 10:30 结束）在半开区间下不算重叠 → true | 不会红（覆盖率测试） |

需要区分两类测试：**回归测试**（修复前红、修复后绿，证明缺陷被真正解决）和**覆盖率测试**（修复前后皆绿，防止后续改动把正确实现破坏掉）。只有第一条属于前者。

### 扩展问题：某 room 有数十亿条 booking 时的优化

当前实现的问题：

1. **全量遍历 O(N)**：`findByRoomId` 把所有该房间的 booking 读出来在内存里逐个比对；N 达到数十亿时，内存放不下、延迟不可接受，且每行都要反序列化成对象。
2. **内存 Map 替代了持久化**：数据无法共享给多实例、重启即丢失，`synchronized` 串行化也限制了吞吐。
3. **锁粒度粗**：`synchronized` 锁住整个仓库方法。

优化方向：

1. **落库 + 索引**：把重叠判断下推到数据库，建**复合索引 `(room_id, start, end)`**，用区间条件把“扫描整表”变成一次**索引范围扫描**：
   `WHERE room_id = ? AND start < ? AND end > ?`（半开区间下用严格不等；存在即冲突），复杂度从 O(N) 降到 O(log N)。
2. **提前终止**：SQL 层面用 `SELECT ... LIMIT 1` 或 `EXISTS`，命中一条冲突就返回，不必读完全部。
3. **并发控制**：内存 `synchronized` 换成数据库的**事务隔离级别 + 行锁/间隙锁**，或在冲突写入时用**唯一约束/排他约束**兜底；必要时加乐观锁版本号。
4. **数据量继续增长时**：按 room 或时间做**分区/分库分表**、冷热数据分离，缓存“某房间某天的占用区间”以加速查询。

### Commit type

`fix`：修改的是已存在但行为错误的逻辑，scope 用 `service`（改动在业务层）。

---

## 任务四：实现创建预约接口

### 实现流程

`POST /rooms/{roomId}/bookings`，请求体为 JSON。服务端按以下顺序处理：

1. **时间窗口校验**：`BookingWindowPolicy.evaluate` → 非法则抛 `InvalidBookingWindowException` → **400 `INVALID_BOOKING_WINDOW`**
2. **房间校验**：`requireRoom` → 不存在则抛 `RoomNotFoundException` → **404 `ROOM_NOT_FOUND`**
3. **重叠校验**：`AvailabilityService.isAvailable` → 与已有预约重叠则抛 `BookingConflictException` → **409 `BOOKING_CONFLICT`**
4. **生成 ID 并落库**：`InMemoryBookingRepository.create` 用 `AtomicInteger` 生成 `booking-3001` 递增 ID
5. **返回**：**201 Created** + 创建后的 booking + `Location: /rooms/{roomId}/bookings/{id}`

三步校验复用了 `BookingService.isAvailable`（它内部已包含 2 和 1 两步），service/controller/handler 都不需要新增异常分支。因为**只有全部校验通过才调用 `create`**，所以“失败请求不能新增或修改 booking”天然成立。

半开区间 `[start, end)`：结束时刻本身不算占用，因此首尾相接（`10:30` 接在 `09:00–10:30` 之后）不算重叠。

`Location` 用 `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(booking.id()).toUri()` 构造，它以当前请求路径为基底拼上 ID。

### 为什么用 POST 而不是 PUT 或 PATCH

| 方法 | REST 语义 | 幂等性 | 是否适用于创建 |
|---|---|---|---|
| **POST** | 在父资源集合下**创建子资源**，URL 由服务端决定 | **非幂等**：同样的请求重复提交会创建多个资源、产生不同 ID | ✅ 适用 |
| PUT | **整体替换**指定 URL 上的资源，不存在则可创建（需客户端指定完整 URL） | 幂等：多次执行结果一致 | ❌ 本题 ID 由服务端生成，客户端无法预知 URL |
| PATCH | 对资源做**部分更新** | 幂等（语义上） | ❌ 更新语义，不用于创建 |

核心原因：创建预约时 **booking ID 由服务端生成**，客户端无法指定目标 URL，因此不能用需要客户端指定 URL 的 PUT；同时创建动作在语义上是非幂等的（重复提交会生成新预约），这与 POST 的语义一致。若业务要求“重复提交不产生重复数据”，则需要客户端携带**幂等键（Idempotency-Key）**由服务端去重——那是额外的设计，不是 POST 的默认行为。

其它常见 HTTP 方法：

| 方法 | 用途 |
|---|---|
| GET | 读取资源，安全且幂等 |
| HEAD | 只取响应头，用于探活/校验缓存 |
| POST | 创建子资源，或执行非幂等操作 |
| PUT | 整体替换（可创建），幂等 |
| PATCH | 部分更新，幂等 |
| DELETE | 删除资源，幂等 |
| OPTIONS | 查询服务器/资源支持的方法（CORS 预检） |
| TRACE | 回显请求，用于诊断（生产一般禁用） |

### 测试及其验证的行为

| 测试 | 验证什么 |
|---|---|
| `createsBookingSuccessfully` | 201、服务端生成 ID（`booking-` 前缀）、响应体字段正确、`Location` 头存在 |
| `returnsCreatedBookingAtLocation` | **沿 `Location` 再发一次 GET 能查到新资源** |
| `createsBookingAdjacentToExistingBooking` | **边界**：room-101 的 `09:30–10:30` 紧接已有 `09:00–09:30`，半开区间下应允许 → 201 |
| `rejectsInvalidBookingWindow` | 10 分钟窗口 → 400 + `INVALID_BOOKING_WINDOW` |
| `returnsNotFoundWhenCreatingForMissingRoom` | 对不存在的 room 创建 → 404 + `ROOM_NOT_FOUND` |
| `returnsConflictForOverlappingBooking` | 与已有预约重叠 → 409 + `BOOKING_CONFLICT` |
| `doesNotStoreBookingWhenRequestFails` | **失败请求前后 `countByRoomId` 不变**，证明没有写入任何数据 |



`feat`：新增了此前不存在的创建能力（原 `BookingService.create` 直接抛 `UnsupportedOperationException`），scope 用 `api`（入口在 web 层）。
