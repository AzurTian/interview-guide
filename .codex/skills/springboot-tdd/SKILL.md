---
name: springboot-tdd
description: 使用 JUnit 5、Mockito、MockMvc、Testcontainers 和 JaCoCo 进行 Spring Boot 的测试驱动开发。在添加功能、修复错误或重构时使用。
---
# Spring Boot TDD 工作流程

针对 Spring Boot 服务的 TDD 指南，覆盖率为 80%（单元测试+集成测试）。

## 何时使用

- 新功能或接口
- Bug 修复或重构
- 添加数据访问逻辑或安全规则

## 工作流程

1) 先编写测试（测试应该失败）
2) 实现最少代码以通过测试
3) 在测试通过的情况下重构代码
4) 执行覆盖率检查（JaCoCo）

## 单元测试（JUnit 5 + Mockito）
```java
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {
  @Mock MarketRepository repo;
  @InjectMocks MarketService service;

  @Test
  void createsMarket() {
    CreateMarketRequest req = new CreateMarketRequest("name", "desc", Instant.now(), List.of("cat"));
    when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Market result = service.create(req);

    assertThat(result.name()).isEqualTo("name");
    verify(repo).save(any());
  }
}
```
模式：
- 安排-执行-断言
- 避免部分模拟；更倾向于显式存根
- 对于变体使用 `@ParameterizedTest`

## Web 层测试（MockMvc）
```java
@WebMvcTest(MarketController.class)
class MarketControllerTest {
  @Autowired MockMvc mockMvc;
  @MockBean MarketService marketService;

  @Test
  void returnsMarkets() throws Exception {
    when(marketService.list(any())).thenReturn(Page.empty());

    mockMvc.perform(get("/api/markets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }
}
```
## 集成测试（SpringBootTest）
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketIntegrationTest {
  @Autowired MockMvc mockMvc;

  @Test
  void createsMarket() throws Exception {
    mockMvc.perform(post("/api/markets")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"Test","description":"Desc","endDate":"2030-01-01T00:00:00Z","categories":["general"]}
        """))
      .andExpect(status().isCreated());
  }
}
```
## 持久性测试（DataJpaTest）
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainersConfig.class)
class MarketRepositoryTest {
  @Autowired MarketRepository repo;

  @Test
  void savesAndFinds() {
    MarketEntity entity = new MarketEntity();
    entity.setName("Test");
    repo.save(entity);

    Optional<MarketEntity> found = repo.findByName("Test");
    assertThat(found).isPresent();
  }
}
```
## 测试容器

- 使用可重用的 Postgres/Redis 容器来模拟生产环境
- 通过 `@DynamicPropertySource` 连接，将 JDBC URL 注入到 Spring 上下文中

## 覆盖率 (JaCoCo)

Maven 代码片段：
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.14</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>verify</phase>
      <goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```
## 断言

- 为了可读性，优先使用 AssertJ (`assertThat`)
- 对于 JSON 响应，使用 `jsonPath`
- 对于异常：`assertThatThrownBy(...)`

## 测试数据构建器
```java
class MarketBuilder {
  private String name = "Test";
  MarketBuilder withName(String name) { this.name = name; return this; }
  Market build() { return new Market(null, name, MarketStatus.ACTIVE); }
}
```
## CI 命令

- Maven: `mvn -T 4 test` 或 `mvn verify`
- Gradle: `./gradlew test jacocoTestReport`

**记住**：保持测试快速、独立且确定性。测试行为，而不是实现细节。
