/** v0.0.4 🍊 Block-level structure of a chat message (a small, safe Markdown subset). */
export type Block =
  | { type: 'heading'; level: 1 | 2 | 3; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; text: string }
  | { type: 'paragraph'; text: string };

const HEADING = /^(#{1,3})\s+(.*)$/;
const LIST_ITEM = /^\s*([-*•]|\d+[.)])\s+(.*)$/;

/** v0.0.4 🍊 Splits message text into headings, lists, fenced code and paragraphs. */
export function parseBlocks(source: string): Block[] {
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const blocks: Block[] = [];
  let paragraph: string[] = [];
  const flush = () => {
    if (paragraph.length > 0) blocks.push({ type: 'paragraph', text: paragraph.join('\n') });
    paragraph = [];
  };
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? '';
    if (line.trimStart().startsWith('```')) {
      flush();
      const code: string[] = [];
      for (i++; i < lines.length && !(lines[i] ?? '').trimStart().startsWith('```'); i++) code.push(lines[i] ?? '');
      blocks.push({ type: 'code', text: code.join('\n') });
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      flush();
      blocks.push({ type: 'heading', level: Math.min(3, heading[1]?.length ?? 1) as 1 | 2 | 3, text: heading[2] ?? '' });
      continue;
    }
    const item = LIST_ITEM.exec(line);
    if (item) {
      flush();
      const ordered = /\d/.test(item[1] ?? '');
      const last = blocks[blocks.length - 1];
      if (last?.type === 'list' && last.ordered === ordered) last.items.push(item[2] ?? '');
      else blocks.push({ type: 'list', ordered, items: [item[2] ?? ''] });
      continue;
    }
    if (line.trim() === '') {
      flush();
      continue;
    }
    paragraph.push(line);
  }
  flush();
  return blocks;
}

/** v0.0.4 🍊 Matches http(s) URLs inside text (trailing punctuation excluded). */
export const URL_PATTERN = /(https?:\/\/[^\s<>()]+[^\s<>().,;:!?'"])/g;
