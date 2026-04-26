import asyncio
import sys

sys.path.insert(0, ".")

from app.conversation.manager import ConversationManager


async def main():
    print("=" * 50)
    print("  🏔️  徒步路线规划机器人 - 本地测试")
    print("=" * 50)
    print()
    print("提示：")
    print("  • 输入消息模拟 @机器人")
    print("  • 输入 'quit' 退出")
    print("  • 输入 'reset' 重置当前对话")
    print()

    manager = ConversationManager()
    user_id = "test_user"
    group_id = "test_group"

    while True:
        try:
            message = input("你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n再见！")
            break

        if not message:
            continue

        if message.lower() == "quit":
            print("再见！")
            break

        if message.lower() == "reset":
            key = f"{group_id}:{user_id}"
            if key in manager._states:
                del manager._states[key]
            print("[对话已重置]")
            print()
            continue

        reply = await manager.handle(user_id, group_id, message)
        print()
        print("机器人:")
        print("-" * 40)
        print(reply)
        print("-" * 40)
        print()


if __name__ == "__main__":
    asyncio.run(main())
