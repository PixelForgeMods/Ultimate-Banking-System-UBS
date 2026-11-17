# Writing guide pages

> The bundled guide lives at `assets/ultimatebankingsystem/wiki/pages/*.md` inside the mod source.

## Standard Markdown

Pages use CommonMark: headings, paragraphs, **bold**, *italic*, `inline code`, code blocks, lists, blockquotes, horizontal rules, links and images. GitHub-style pipe tables and ~~strikethrough~~ also work. Links to another guide page use `[label](wiki:currency)`.

HTML is shown as text, not executed. This keeps the in-game guide safe and independent of a browser.

Inline commands beginning with `/` are clickable when written as backticked code. Clicking copies the command, including placeholder arguments, to the clipboard. Commands in code blocks have a Copy control.

## In-game item models

Use a Markdown image with an `item:` destination. The ID is a registered Minecraft or mod item. `size` is a GUI-pixel square (clamped to 16-96); `tooltip` can be `true` or `false` and defaults to `true`.

```md
![Dollar bill](item:ultimatebankingsystem:one_dollar_bill?size=32&tooltip=true)
![Silent preview](item:minecraft:diamond?size=48&tooltip=false)
```

![One-dollar bill](item:ultimatebankingsystem:one_dollar_bill?size=32&tooltip=false)

For a bundled texture rather than an item model, use `texture:namespace:path` with the same options, for example `![Bill art](texture:ultimatebankingsystem:textures/item/one_dollar_american.png?size=48&tooltip=false)`. External web images are shown as clickable alt text rather than downloaded; the guide works offline.

## Screenshots

Place numbered PNGs in `textures/gui/wiki/screenshots/`, then add their actual pixel dimensions to a `screenshot:` image destination. The caption is the alt text. Screenshots fit the article without distortion and open larger when clicked.

```md
![ATM menu](screenshot:ultimatebankingsystem:07?width=851&height=602)
```

Avoid repeating near-identical screens. When a screenshot is unavailable, explain the action and expected result in text instead.
