# FloatClock 项目规范

## 日志规范

- 业务代码统一使用 SLF4J API，通过 `LoggerFactory` 获取类或文件级的私有 logger；Logback 是唯一运行时日志实现，不直接依赖其 API。
- logger 名称必须使用固定的简单类名字符串，不带包名，例如 `LoggerFactory.getLogger("SingleInstanceChecker")`。禁止通过 `SomeClass::class.java`、`javaClass` 或反射推导名称，避免发布混淆后日志标识失去可读性；不要为日志可读性而保留业务类名。
- 禁止使用 `print`、`println`、`System.out`、`System.err`、`printStackTrace` 或原生 `printf`/`fprintf` 打印应用日志。JNI 错误应传播给 Kotlin 调用方，由调用方记录日志。
- 日志使用 `{}` 占位符传参，不使用字符串插值或拼接，例如 `logger.debug("Window size: {} x {}", width, height)`。
- 捕获异常时将异常对象作为最后一个参数传入，保留堆栈，例如 `logger.warn("Failed to configure window {}", title, exception)`；不要只记录 `exception.message`。
- `DEBUG` 用于窗口尺寸、初始化状态和诊断细节；`INFO` 用于重要正常事件；`WARN` 用于可恢复异常或降级；`ERROR` 用于操作失败。高频刷新不得使用 `INFO` 及以上级别输出常规状态。
- 日志描述应包含操作和必要上下文，不手工添加时间、级别、类名或 `[FloatClock]` 前缀，不记录凭据等敏感信息。
- 日志格式与级别统一配置在 `src/desktopMain/resources/logback.xml`，默认 `INFO`；调试时可通过 JVM 参数 `-Dfloatclock.log.level=DEBUG` 调整。
- 修改日志依赖或配置时，检查桌面运行时仅有一个 SLF4J provider，并验证发布混淆后的 provider 发现及 XML 配置加载。
