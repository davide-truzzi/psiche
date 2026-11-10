/*
 * Scrolls to a character of the Markdown, using the data-start and data-end marks the renderer puts on
 * each block. The character lands as far down the block starting nearest at or before it as it is through that
 * block's text, so a long paragraph is followed line by line; one between blocks lands at the bottom of the block
 * above. The blocks are not all in the Markdown's order: the footnotes come at the page's end wherever they are
 * written, so every block is looked at; of two starting at the same character, the later one, inside the other, is
 * taken.
 * That point goes the given share of the way down the view. Unless asked for exactly, the page only moves once that
 * point leaves the middle of the view, so it stays put while typing.
 */
(function (position, exactly, point) {
    let block = null, nearest = -1;
    for (const candidate of document.querySelectorAll('[data-start]')) {
        const start = Number(candidate.dataset.start);
        if (start <= position && start >= nearest) {
            block = candidate;
            nearest = start;
        }
    }
    let y = 0;
    if (block !== null) {
        const box = block.getBoundingClientRect();
        const start = Number(block.dataset.start), end = Number(block.dataset.end);
        const through = Math.min(1, (position - start) / Math.max(1, end - start));
        y = box.top + window.scrollY + box.height * through;
    }
    const height = window.innerHeight;
    if (exactly || y < window.scrollY + height * 0.2 || y > window.scrollY + height * 0.8)
        window.scrollTo(0, y - height * point);
})({{position}}, {{exactly}}, {{point}})
