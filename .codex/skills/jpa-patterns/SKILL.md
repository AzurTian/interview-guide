---
name: jpa-patterns
description: Spring Boot 中用于实体设计、关系、查询优化、事务、审计、索引、分页和连接池的 JPA/Hibernate 模式。
---
# JPA/Hibernate 模式

用于 Spring Boot 中的数据建模、仓库和性能调优。

## 何时启用

- 设计 JPA 实体和表映射
- 定义关系（@OneToMany, @ManyToOne, @ManyToMany）
- 优化查询（N+1 问题防止、抓取策略、投影）
- 配置事务、审计或软删除
- 设置分页、排序或自定义仓库方法
- 调优连接池（HikariCP）或二级缓存

## 实体设计
```java
@Entity
@Table(name = "markets", indexes = {
  @Index(name = "idx_markets_slug", columnList = "slug", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class MarketEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, unique = true, length = 120)
  private String slug;

  @Enumerated(EnumType.STRING)
  private MarketStatus status = MarketStatus.ACTIVE;

  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
}
```
启用审计：
```java
@Configuration
@EnableJpaAuditing
class JpaConfig {}
```
## 关系与 N 1 预防
```java
@OneToMany(mappedBy = "market", cascade = CascadeType.ALL, orphanRemoval = true)
private List<PositionEntity> positions = new ArrayList<>();
```
- 默认使用懒加载；在需要时在查询中使用 `JOIN FETCH`
- 避免在集合上使用 `EAGER`；在读取路径上使用 DTO 投影
```java
@Query("select m from MarketEntity m left join fetch m.positions where m.id = :id")
Optional<MarketEntity> findWithPositions(@Param("id") Long id);
```
## 仓储模式
```java
public interface MarketRepository extends JpaRepository<MarketEntity, Long> {
  Optional<MarketEntity> findBySlug(String slug);

  @Query("select m from MarketEntity m where m.status = :status")
  Page<MarketEntity> findByStatus(@Param("status") MarketStatus status, Pageable pageable);
}
```
- 对轻量级查询使用投影：
```java
public interface MarketSummary {
  Long getId();
  String getName();
  MarketStatus getStatus();
}
Page<MarketSummary> findAllBy(Pageable pageable);
```
## 事务

- 使用 `@Transactional` 注释服务方法
- 对于读取路径，使用 `@Transactional(readOnly = true)` 进行优化
- 仔细选择传播行为；避免长时间运行的事务
```java
@Transactional
public Market updateStatus(Long id, MarketStatus status) {
  MarketEntity entity = repo.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Market"));
  entity.setStatus(status);
  return Market.from(entity);
}
```
## 分页
```java
PageRequest page = PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").descending());
Page<MarketEntity> markets = repo.findByStatus(MarketStatus.ACTIVE, page);
```
对于类似光标的分页，在 JPQL 中包含 `id > :lastId` 并设置排序。

## 索引和性能

- 为常用过滤条件添加索引（`status`、`slug`、外键）
- 使用与查询模式匹配的复合索引（`status, created_at`）
- 避免 `select *`；只投影所需列
- 使用 `saveAll` 和 `hibernate.jdbc.batch_size` 进行批量写入

## 连接池（HikariCP）

推荐属性：
```
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.validation-timeout=5000
```
对于 PostgreSQL LOB 处理，添加：
```
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true
```
## 缓存

- 一级缓存是针对每个 EntityManager 的；避免在事务之间保留实体
- 对于读操作频繁的实体，可谨慎考虑二级缓存；验证逐出策略

## 数据迁移

- 使用 Flyway 或 Liquibase；在生产环境中绝不依赖 Hibernate 自动 DDL
- 保持迁移的幂等性和累加性；避免在没有计划的情况下删除列

## 测试数据访问

- 优先使用 `@DataJpaTest` 与 Testcontainers 来模拟生产环境
- 使用日志断言 SQL 效率：设置 `logging.level.org.hibernate.SQL=DEBUG` 和 `logging.level.org.hibernate.orm.jdbc.bind=TRACE` 来查看参数值

**记住**：保持实体精简，查询有意，事务短小。通过 fetch 策略和投影防止 N+1 问题，并为你的读/写路径建立索引。
