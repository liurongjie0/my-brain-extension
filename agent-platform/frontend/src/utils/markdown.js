import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

// Assistant replies come back as Markdown (bold, lists, code, etc.). Render them to HTML and
// sanitize before injecting via v-html — never trust model output as safe HTML.
const md = new MarkdownIt({
  html: false,      // ignore raw HTML in the source — only Markdown syntax is honored
  linkify: true,
  breaks: true      // treat single newlines as <br>, matching chat-style expectations
})

// open links in a new tab safely
const defaultLinkOpen = md.renderer.rules.link_open
  || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

export function renderMarkdown(text) {
  if (!text) return ''
  return DOMPurify.sanitize(md.render(String(text)), { ADD_ATTR: ['target', 'rel'] })
}
