# JavaWeb 开发规范文档

## 1. 项目结构规范

### 1.1 Spring Boot 项目结构
```
src/main/java/com/example/
├── config/           # 配置类
├── controller/       # 控制器层
├── service/          # 服务层
│   └── impl/         # 服务实现
├── repository/       # 数据访问层
├── entity/           # 实体类
├── dto/              # 数据传输对象
├── vo/               # 视图对象
├── utils/            # 工具类
└── exception/        # 自定义异常
```

### 1.2 命名规范
- 包名：全小写，如 `com.example.user`
- 类名：大驼峰，如 `UserService`
- 方法名：小驼峰，如 `getUserById()`
- 常量：全大写下划线，如 `MAX_RETRY_COUNT`

## 2. 分层规范

### 2.1 Controller层
- 只负责接收参数、调用Service、返回结果
- 不处理业务逻辑
- 使用 `@RestController` + `@RequestMapping`

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }
}
```

### 2.2 Service层
- 处理业务逻辑
- 接口 + 实现分离
- 使用 `@Service` + `@Transactional`

```java
public interface UserService {
    UserVO getUserById(Long id);
}

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public UserVO getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        return UserConverter.toVO(user);
    }
}
```

### 2.3 Repository层
- 继承 `JpaRepository`
- 方法命名查询

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
}
```

## 3. 统一响应规范

### 3.1 响应格式
```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 3.2 响应类
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}
```

## 4. 异常处理规范

### 4.1 全局异常处理
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(message);
    }
}
```

### 4.2 自定义业务异常
```java
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

## 5. 参数校验规范

### 5.1 DTO校验
```java
@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度2-20位")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码至少6位")
    private String password;
}
```

### 5.2 Controller使用
```java
@PostMapping
public Result<UserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
    return Result.success(userService.createUser(request));
}
```

## 6. 日志规范

### 6.1 日志级别
- ERROR：系统错误、异常
- WARN：警告信息
- INFO：关键业务流程
- DEBUG：调试信息

### 6.2 日志格式
```java
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Override
    public UserVO getUserById(Long id) {
        log.info("查询用户, userId: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("用户不存在, userId: {}", id);
                return new RuntimeException("用户不存在");
            });
        return UserConverter.toVO(user);
    }
}
```

## 7. 数据库规范

### 7.1 表名
- 小写下划线，如 `user_order`
- 以 `t_` 前缀（可选），如 `t_user`

### 7.2 字段名
- 小写下划线，如 `created_at`
- 主键：`id`
- 创建时间：`created_at`
- 更新时间：`updated_at`
- 逻辑删除：`deleted`

### 7.3 索引规范
- 主键索引：`pk_表名`
- 唯一索引：`uk_字段名`
- 普通索引：`idx_字段名`

## 8. 接口设计规范

### 8.1 RESTful 风格
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/users | 查询用户列表 |
| GET | /api/v1/users/{id} | 查询用户详情 |
| POST | /api/v1/users | 创建用户 |
| PUT | /api/v1/users/{id} | 更新用户 |
| DELETE | /api/v1/users/{id} | 删除用户 |

### 8.2 分页查询
```java
@GetMapping
public Result<PageResult<UserVO>> listUsers(UserQueryRequest request) {
    return Result.success(userService.listUsers(request));
}
```

## 9. 安全规范

### 9.1 密码处理
- 使用 BCrypt 加密存储
- 禁止明文存储密码

### 9.2 JWT Token
- Token 有效期：2小时
- Refresh Token 有效期：7天
- Token 存储在 localStorage

### 9.3 接口权限
- 使用 Spring Security + JWT
- 公开接口：登录、注册
- 需认证接口：其他所有接口

## 10. 前端规范（Vue 3）

### 10.1 组件命名
- 文件名：PascalCase，如 `UserCard.vue`
- 组件名：PascalCase，如 `UserCard`

### 10.2 状态管理
- 使用 Pinia
- 按功能模块划分 Store

### 10.3 API 调用
```typescript
// api/user.ts
import request from '@/utils/axios'

export const userApi = {
  getUser(id: number) {
    return request.get<any, User>(`/api/v1/users/${id}`)
  },
  createUser(data: CreateUserRequest) {
    return request.post<any, User>('/api/v1/users', data)
  }
}
```

### 10.4 表单校验
```vue
<el-form :model="form" :rules="rules" ref="formRef">
  <el-form-item label="用户名" prop="username">
    <el-input v-model="form.username" />
  </el-form-item>
</el-form>
```
