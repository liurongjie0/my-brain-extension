from .github_trending import GitHubTrendingFetcher
from .arxiv import ArxivFetcher
from .hackernews import HackerNewsFetcher
from .techcrunch import TechCrunchFetcher
from .producthunt import ProductHuntFetcher
from .anthropic import AnthropicFetcher

FETCHERS = {
    "github_trending": GitHubTrendingFetcher,
    "arxiv": ArxivFetcher,
    "hackernews": HackerNewsFetcher,
    "techcrunch": TechCrunchFetcher,
    "producthunt": ProductHuntFetcher,
    "anthropic": AnthropicFetcher,
}
