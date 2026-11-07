package it.infog.psiche.git;

import it.infog.psiche.common.ChapterFolders;
import it.infog.psiche.git.model.Conflict;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeAlgorithm;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The merge of the book's last revision with the remote's, made file by file in memory, so the book's folder changes
 * only once it is whole. A file changed on one side only takes that change; one changed on both is merged line by line
 * if it is text. When the same lines changed, this device's version stays: a Markdown file, such as a chapter, gets the
 * other device's beside it as a copy, "&lt;name&gt; (other device, YYYY-MM-DD).md", and any other file has it only in
 * the history. No conflict marker is ever written.
 */
final class BookMerge {

    private static final String COPY = "%s (other device, %s)";
    // The trees of the walk, in the order it is given them.
    private static final int BASE = 0;
    private static final int OURS = 1;
    private static final int THEIRS = 2;

    private final Repository repository;
    private final ObjectInserter inserter;
    private final DirCache merged = DirCache.newInCore();
    private final DirCacheBuilder builder = merged.builder();
    // Every path in the merge, so a copy takes a name no other file has.
    private final Set<String> paths = new HashSet<>();
    private final List<Conflict> conflicts = new ArrayList<>();

    BookMerge(Repository repository, ObjectInserter inserter) {

        this.repository = repository;
        this.inserter = inserter;
    }

    /**
     * Merges {@code ours} and {@code theirs}, which both come from {@code base}, and writes the merged tree with
     * {@link #inserter}; the caller flushes it.
     *
     * @return the merged tree's id
     */
    ObjectId tree(RevCommit base, RevCommit ours, RevCommit theirs) throws IOException {

        List<String> conflicted = new ArrayList<>();
        try (ObjectReader reader = inserter.newReader(); TreeWalk tree = new TreeWalk(reader)) {
            tree.addTree(base.getTree());
            tree.addTree(ours.getTree());
            tree.addTree(theirs.getTree());
            tree.setRecursive(true);

            //file by file: the side that changed it, else both merged, else this device's
            while (tree.next()) {
                String path = tree.getPathString();
                ObjectId baseId = id(tree, BASE);
                ObjectId oursId = id(tree, OURS);
                ObjectId theirsId = id(tree, THEIRS);

                if (Objects.equals(oursId, theirsId) || Objects.equals(baseId, theirsId)) {
                    add(path, oursId, tree.getFileMode(OURS));
                    continue;
                }
                if (Objects.equals(baseId, oursId)) {
                    add(path, theirsId, tree.getFileMode(THEIRS));
                    continue;
                }

                Optional<byte[]> text = mergedText(reader, baseId, oursId, theirsId);
                if (text.isPresent()) {
                    add(path, inserter.insert(Constants.OBJ_BLOB, text.get()), tree.getFileMode(OURS));
                    continue;
                }

                add(path, oursId, tree.getFileMode(OURS));
                conflicted.add(path);
            }

            //the other device's versions, once every name in the merge is known
            String day = LocalDate.ofInstant(theirs.getCommitterIdent().getWhenAsInstant(), ZoneId.systemDefault())
                    .toString();
            for (String path : conflicted)
                conflicts.add(new Conflict(file(path), copy(reader, theirs, path, day).map(this::file)));
        }

        builder.finish();
        return merged.writeTree(inserter);
    }

    /** The files both sides changed in ways that could not be merged, in the order of their paths. */
    List<Conflict> conflicts() {

        return List.copyOf(conflicts);
    }

    /**
     * Adds {@code theirs}' version of {@code path}, if it is a Markdown file there, as a copy beside it; empty for any
     * other file.
     */
    private Optional<String> copy(ObjectReader reader, RevCommit theirs, String path, String day) throws IOException {

        if (!path.endsWith(ChapterFolders.CHAPTER_EXTENSION))
            return Optional.empty();

        try (TreeWalk entry = TreeWalk.forPath(reader, path, theirs.getTree())) {
            if (entry == null)
                return Optional.empty();

            String copy = copyPath(path, day);
            add(copy, entry.getObjectId(0), entry.getFileMode(0));
            return Optional.of(copy);
        }
    }

    /** Where the other device's version of {@code path} goes: beside it, with a number if the name is taken. */
    private String copyPath(String path, String day) {

        String stem = path.substring(0, path.length() - ChapterFolders.CHAPTER_EXTENSION.length());
        String copy = COPY.formatted(stem, day) + ChapterFolders.CHAPTER_EXTENSION;
        for (int number = 2; paths.contains(copy); number++)
            copy = COPY.formatted(stem, day + ", " + number) + ChapterFolders.CHAPTER_EXTENSION;

        return copy;
    }

    /** Adds {@code path} to the merge as the blob {@code id}; nothing if it is {@code null}, a file deleted. */
    private void add(String path, ObjectId id, FileMode mode) {

        if (id == null)
            return;

        DirCacheEntry entry = new DirCacheEntry(path);
        entry.setObjectId(id);
        entry.setFileMode(mode);
        builder.add(entry);
        paths.add(path);
    }

    /**
     * {@code ours} and {@code theirs} merged line by line from {@code base}; empty if either is missing or not text, or
     * if both changed the same lines.
     */
    private static Optional<byte[]> mergedText(ObjectReader reader, ObjectId base, ObjectId ours, ObjectId theirs)
            throws IOException {

        if (ours == null || theirs == null)
            return Optional.empty();

        Optional<RawText> oursText = text(reader, ours);
        Optional<RawText> theirsText = text(reader, theirs);
        Optional<RawText> baseText = base == null ? Optional.of(RawText.EMPTY_TEXT) : text(reader, base);
        if (oursText.isEmpty() || theirsText.isEmpty() || baseText.isEmpty())
            return Optional.empty();

        MergeResult<RawText> result = new MergeAlgorithm()
                .merge(RawTextComparator.DEFAULT, baseText.get(), oursText.get(), theirsText.get());
        if (result.containsConflicts())
            return Optional.empty();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MergeFormatter().formatMerge(out, result, List.of("base", "ours", "theirs"), StandardCharsets.UTF_8);
        return Optional.of(out.toByteArray());
    }

    /** The blob {@code id} as text; empty if it is binary, as an image, or too large to read at once. */
    private static Optional<RawText> text(ObjectReader reader, ObjectId id) throws IOException {

        ObjectLoader loader = reader.open(id, Constants.OBJ_BLOB);
        if (loader.isLarge())
            return Optional.empty();

        byte[] bytes = loader.getCachedBytes();
        return RawText.isBinary(bytes) ? Optional.empty() : Optional.of(new RawText(bytes));
    }

    /** The blob of the walk's tree {@code index}; {@code null} if that tree does not have the file. */
    private static ObjectId id(TreeWalk tree, int index) {

        return tree.getFileMode(index) == FileMode.MISSING ? null : tree.getObjectId(index);
    }

    private Path file(String path) {

        return repository.getWorkTree().toPath().resolve(path);
    }
}
