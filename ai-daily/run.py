#!/usr/bin/env python3
import asyncio
from pathlib import Path

import yaml

from src.aggregator import Aggregator
from src.fetchers import FETCHERS
from src.formatter import Formatter
from src.summarizer import Summarizer
from src.wechat_bot import WeChatBot


def load_config() -> dict:
    config_path = Path(__file__).parent / "config.yaml"
    with open(config_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


async def run_daily() -> None:
    config = load_config()

    sources_config = config.get("sources", {})
    max_items = config.get("max_items_per_source", 5)

    aggregator = Aggregator()
    formatter = Formatter()
    wechat = WeChatBot(config.get("wechat_webhook", ""))
    summarizer = Summarizer(
        api_key=config.get("claude_api_key", ""),
        base_url=config.get("claude_base_url", ""),
        model=config.get("claude_model", "claude-opus-4-7"),
        max_length=config.get("summary_max_length", 60),
    )

    all_items = []
    fetcher_classes = {
        "github_trending": ("github_languages",),
        "arxiv": ("arxiv_categories",),
        "hackernews": ("hn_min_score",),
        "techcrunch": ("techcrunch_tags",),
        "producthunt": ("producthunt_access_token",),
        "anthropic": (),
    }

    tasks = []
    for source_name, fetcher_cls in FETCHERS.items():
        if not sources_config.get(source_name, False):
            continue

        kwargs = {"max_items": max_items}
        for key in fetcher_classes.get(source_name, ()):
            if key in config:
                arg_name = key.replace("github_", "").replace("arxiv_", "").replace("hn_", "").replace("techcrunch_", "").replace("producthunt_", "")
                kwargs[arg_name] = config[key]

        fetcher = fetcher_cls(**kwargs)
        tasks.append((source_name, fetcher.fetch()))

    results = await asyncio.gather(
        *[t[1] for t in tasks], return_exceptions=True
    )

    grouped = {}
    for (source_name, _), result in zip(tasks, results):
        if isinstance(result, Exception):
            print(f"[{source_name}] 抓取失败: {result}")
            grouped[source_name] = []
            continue

        new_items = aggregator.filter_new(result)
        if new_items:
            print(f"[{source_name}] 新内容: {len(new_items)} 条")
            summarized = summarizer.summarize(new_items)
            grouped[source_name] = summarized
            all_items.extend(summarized)
        else:
            print(f"[{source_name}] 无新内容")
            grouped[source_name] = []

    if not all_items:
        print("今日无新内容，跳过推送")
        return

    message = formatter.format_daily(grouped)
    print("\n" + "=" * 40)
    print(message)
    print("=" * 40 + "\n")

    success = await wechat.send_text(message)
    if success:
        aggregator.mark_pushed(all_items)
        stats = aggregator.get_stats()
        print(f"已入库 {len(all_items)} 条，累计 {stats['total']} 条")


if __name__ == "__main__":
    asyncio.run(run_daily())
