# WhisperTime 签名配置说明

## APK 文件位置

- Debug 版本：`app/build/outputs/apk/debug/app-debug.apk`
- Release 版本：`app/build/outputs/apk/release/app-release.apk`

## 签名信息

| 项目 | 值 |
|------|-----|
| 密钥库文件 | `app/whispertime.keystore` |
| 密钥库密码 | `whispertime123` |
| 密钥别名 | `whispertime` |
| 密钥密码 | `whispertime123` |
| 有效期 | 10000 天 |

## 注意事项

1. **妥善保管密钥库文件** - `whispertime.keystore` 文件丢失后无法恢复
2. **记住密码** - 密码丢失后无法重新签名
3. **应用更新** - 后续发布新版本必须使用相同的签名，否则用户无法升级
4. **不要提交到版本控制** - 密钥库文件和密码不应提交到 Git 仓库

## 构建命令

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本（已签名）
./gradlew assembleRelease
```
