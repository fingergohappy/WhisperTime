# Agent Execution Notes

每次完成需求后，请使用当前连接的真机进行安装、启动与效果检查。

安装并启动命令：

```bash
./gradlew installDebug && adb shell monkey -p com.example.whispertime 1
```

验证要求：

1. 安装与启动时，使用当前连接的真机进行调试。
2. 启动完成后，使用 `mobile-mcp` 连接真机查看效果。
3. 通过 `mobile-mcp` 截图、查看界面元素或执行必要交互，确认实际效果符合预期。
