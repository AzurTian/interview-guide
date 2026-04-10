---
name: springboot-verification
description: Spring Boot 项目的验证循环：构建、静态分析、覆盖率测试、安全扫描，以及发布或提交请求前的差异审查。
---
# Spring Boot 验证循环

在提交 PR 之前、重大更改之后以及预部署阶段运行。

## 何时激活

- 在为 Spring Boot 服务创建拉取请求之前
- 在重大重构或依赖升级之后
- 针对预发布或生产环境的预部署验证
- 运行完整的构建 → 代码风格检查 → 测试 → 安全扫描流程
- 验证测试覆盖率是否达到阈值

## 第一阶段：构建
```bash
mvn -T 4 clean verify -DskipTests
# or
./gradlew clean assemble -x test
```
如果构建失败，停止并修复。

## 第二阶段：静态分析

Maven（常用插件）:
```bash
mvn -T 4 spotbugs:check pmd:check checkstyle:check
```
Gradle（如果已配置）：
```bash
./gradlew checkstyleMain pmdMain spotbugsMain
```
## 第三阶段：测试   覆盖范围
```bash
mvn -T 4 test
mvn jacoco:report   # verify 80%+ coverage
# or
./gradlew test jacocoTestReport
```
报告：
- 总测试数，通过/失败
- 覆盖率 %（行/分支）

### 单元测试

使用模拟依赖项独立测试服务逻辑：
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private UserService userService;

  @Test
  void createUser_validInput_returnsUser() {
    var dto = new CreateUserDto("Alice", "alice@example.com");
    var expected = new User(1L, "Alice", "alice@example.com");
    when(userRepository.save(any(User.class))).thenReturn(expected);

    var result = userService.create(dto);

    assertThat(result.name()).isEqualTo("Alice");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void createUser_duplicateEmail_throwsException() {
    var dto = new CreateUserDto("Alice", "existing@example.com");
    when(userRepository.existsByEmail(dto.email())).thenReturn(true);

    assertThatThrownBy(() -> userService.create(dto))
        .isInstanceOf(DuplicateEmailException.class);
  }
}
```
### 使用 Testcontainers 的集成测试

针对真实数据库进行测试，而不是 H2：
```java
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("testdb");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private UserRepository userRepository;

  @Test
  void findByEmail_existingUser_returnsUser() {
    userRepository.save(new User("Alice", "alice@example.com"));

    var found = userRepository.findByEmail("alice@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Alice");
  }
}
```
### 使用 MockMvc 的 API 测试

在完整的 Spring 上下文中测试控制器层：
```java
@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private UserService userService;

  @Test
  void createUser_validInput_returns201() throws Exception {
    var user = new UserDto(1L, "Alice", "alice@example.com");
    when(userService.create(any())).thenReturn(user);

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "Alice", "email": "alice@example.com"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Alice"));
  }

  @Test
  void createUser_invalidEmail_returns400() throws Exception {
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "Alice", "email": "not-an-email"}
                """))
        .andExpect(status().isBadRequest());
  }
}
```
## 第四阶段：安全扫描
```bash
# Dependency CVEs
mvn org.owasp:dependency-check-maven:check
# or
./gradlew dependencyCheckAnalyze

# Secrets in source
grep -rn "password\s*=\s*\"" src/ --include="*.java" --include="*.yml" --include="*.properties"
grep -rn "sk-\|api_key\|secret" src/ --include="*.java" --include="*.yml"

# Secrets (git history)
git secrets --scan  # if configured
```
### 常见安全发现
```
# Check for System.out.println (use logger instead)
grep -rn "System\.out\.print" src/main/ --include="*.java"

# Check for raw exception messages in responses
grep -rn "e\.getMessage()" src/main/ --include="*.java"

# Check for wildcard CORS
grep -rn "allowedOrigins.*\*" src/main/ --include="*.java"
```
## 第5阶段：Lint/格式化（可选关卡）
```bash
mvn spotless:apply   # if using Spotless plugin
./gradlew spotlessApply
```
## 第6阶段：差异审查
```bash
git diff --stat
git diff
```
清单：
- 没有遗留调试日志（未加保护的 `System.out`、`log.debug`）
- 有意义的错误和 HTTP 状态
- 在需要的地方存在事务和验证
- 配置更改已记录

## 输出模板
```
VERIFICATION REPORT
===================
Build:     [PASS/FAIL]
Static:    [PASS/FAIL] (spotbugs/pmd/checkstyle)
Tests:     [PASS/FAIL] (X/Y passed, Z% coverage)
Security:  [PASS/FAIL] (CVE findings: N)
Diff:      [X files changed]

Overall:   [READY / NOT READY]

Issues to Fix:
1. ...
2. ...
```
## 连续模式

- 在重大更改时或长时间会话中每 30–60 分钟重新运行阶段
- 保持短循环：`mvn -T 4 test`   spotbugs 以获得快速反馈

**记住**：快速反馈胜过晚来的意外。保持严格的关卡——将警告视为生产系统中的缺陷。
