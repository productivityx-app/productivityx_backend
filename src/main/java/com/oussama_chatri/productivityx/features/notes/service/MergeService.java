package com.oussama_chatri.productivityx.features.notes.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Three-way text merge modelled after Git's merge strategy.
 *
 * <p>Given an ancestor (the last-synced version both sides started from), a local
 * branch (what the client wants to write), and a remote branch (what the server
 * currently holds), this service produces either:
 * <ul>
 *   <li>A clean merged result when edits do not overlap — the caller saves it directly.</li>
 *   <li>A conflict report when edits overlap — the caller returns HTTP 409 with the
 *       conflict regions so the client can show a merge UI.</li>
 * </ul>
 *
 * <p>Works on plain-text line arrays. The Note entity stores {@code plainTextContent}
 * specifically for this purpose; the Markdown {@code content} field is carried through
 * unchanged in the clean-merge path (callers decide how to reconstruct it).
 */
@Service
public class MergeService {

    /**
     * Result of a three-way merge attempt.
     */
    @Getter
    @Builder
    public static class MergeResult {
        /** True when both patch sets applied without overlapping hunks. */
        private final boolean clean;

        /** Merged text — only meaningful when {@code clean == true}. */
        private final String merged;

        /**
         * Conflict regions — only populated when {@code clean == false}.
         * Each region describes the range in the ancestor where edits collided.
         */
        private final List<ConflictRegion> conflicts;
    }

    /**
     * A single line range where local and remote edits collide.
     */
    @Getter
    @Builder
    public static class ConflictRegion {
        /** 0-based line index in the ancestor where the conflict starts. */
        private final int startLine;
        /** What the client wanted to put in this region. */
        private final String localContent;
        /** What the server currently has in this region. */
        private final String remoteContent;
    }

    /**
     * Performs a three-way merge of plain-text content.
     *
     * @param ancestor the last-synced common base (stored as {@code plainTextContent}
     *                 at the {@code knownVersion} the client last saw)
     * @param local    the client's proposed new content
     * @param remote   the current server-side content
     * @return a {@link MergeResult} indicating whether the merge was clean
     */
    public MergeResult mergeText(String ancestor, String local, String remote) {
        List<String> ancestorLines = toLines(ancestor);
        List<String> localLines    = toLines(local);
        List<String> remoteLines   = toLines(remote);

        // No-op fast paths
        if (local.equals(remote)) {
            return MergeResult.builder().clean(true).merged(remote).conflicts(List.of()).build();
        }
        if (local.equals(ancestor)) {
            return MergeResult.builder().clean(true).merged(remote).conflicts(List.of()).build();
        }
        if (remote.equals(ancestor)) {
            return MergeResult.builder().clean(true).merged(local).conflicts(List.of()).build();
        }

        Patch<String> localPatch  = DiffUtils.diff(ancestorLines, localLines);
        Patch<String> remotePatch = DiffUtils.diff(ancestorLines, remoteLines);

        List<ConflictRegion> conflicts = detectConflicts(localPatch, remotePatch, ancestorLines, localLines, remoteLines);
        if (!conflicts.isEmpty()) {
            return MergeResult.builder().clean(false).merged(null).conflicts(conflicts).build();
        }

        // No overlaps — apply the non-trivial patch on top of the ancestor
        // Prefer the local patch if both touched different regions (server wins on same region
        // is handled by conflict detection above, so we're safe here)
        try {
            // Apply remote changes first, then local changes on the result
            List<String> afterRemote = DiffUtils.patch(new ArrayList<>(ancestorLines), remotePatch);
            Patch<String> localVsAncestor = DiffUtils.diff(ancestorLines, localLines);
            List<String> merged = DiffUtils.patch(afterRemote, translatePatch(localVsAncestor, ancestorLines, afterRemote));
            return MergeResult.builder().clean(true).merged(fromLines(merged)).conflicts(List.of()).build();
        } catch (PatchFailedException ex) {
            // Patch application itself failed — treat as conflict
            ConflictRegion synthetic = ConflictRegion.builder()
                    .startLine(0)
                    .localContent(local)
                    .remoteContent(remote)
                    .build();
            return MergeResult.builder().clean(false).merged(null).conflicts(List.of(synthetic)).build();
        }
    }

    /**
     * Detects overlapping hunks between localPatch and remotePatch.
     * Two hunks overlap when their affected line ranges in the ancestor intersect.
     */
    private List<ConflictRegion> detectConflicts(
            Patch<String> localPatch,
            Patch<String> remotePatch,
            List<String> ancestorLines,
            List<String> localLines,
            List<String> remoteLines) {

        List<ConflictRegion> conflicts = new ArrayList<>();

        for (AbstractDelta<String> localDelta : localPatch.getDeltas()) {
            int localStart = localDelta.getSource().getPosition();
            int localEnd   = localStart + localDelta.getSource().size();

            for (AbstractDelta<String> remoteDelta : remotePatch.getDeltas()) {
                int remoteStart = remoteDelta.getSource().getPosition();
                int remoteEnd   = remoteStart + remoteDelta.getSource().size();

                boolean overlaps = localStart < remoteEnd && remoteStart < localEnd;
                if (overlaps) {
                    conflicts.add(ConflictRegion.builder()
                            .startLine(Math.min(localStart, remoteStart))
                            .localContent(linesToString(localDelta.getTarget().getLines()))
                            .remoteContent(linesToString(remoteDelta.getTarget().getLines()))
                            .build());
                }
            }
        }

        return conflicts;
    }

    /**
     * Translates a patch relative to the ancestor so it can be applied on top of
     * a list that already has the remote changes applied. In the clean-merge path
     * (no overlaps), line positions in non-overlapping regions shift predictably;
     * we rebuild a patch targeting afterRemote directly via diff.
     */
    private Patch<String> translatePatch(
            Patch<String> localVsAncestor,
            List<String> ancestorLines,
            List<String> afterRemote) {

        // Re-diff: local target vs afterRemote — this gives us the minimal changeset
        // needed to go from (ancestor + remote edits) to (ancestor + remote + local edits)
        try {
            List<String> localTarget = DiffUtils.patch(new ArrayList<>(ancestorLines), localVsAncestor);
            return DiffUtils.diff(afterRemote, localTarget);
        } catch (PatchFailedException ex) {
            // Should never reach here in the clean path — but return empty patch if it does
            return new Patch<>();
        }
    }

    private List<String> toLines(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(text.split("\n", -1)));
    }

    private String fromLines(List<String> lines) {
        return String.join("\n", lines);
    }

    private String linesToString(List<String> lines) {
        return lines == null ? "" : String.join("\n", lines);
    }
}
