/*
 * Scrolls to a character of the Markdown, using the data-start and data-end marks the renderer puts on
 * each block. The character lands as far down the last block starting at or before it as it is through that block's
 * text, so a long paragraph is followed line by line; one between blocks lands at the bottom of the block above.
 * That point goes the given share of the way down the view. Unless asked for exactly, the page only moves once that
 * point leaves the middle of the view, so it stays put while typing.
 */
(function (position, exactly, point) {
    let block = null;
    for (const candidate of document.querySelectorAll('[data-start]')) {
        if (Number(candidate.dataset.start) > position)
            break;
        block = candidate;
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
