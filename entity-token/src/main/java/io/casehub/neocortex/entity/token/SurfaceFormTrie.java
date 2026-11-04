package io.casehub.neocortex.entity.token;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

public final class SurfaceFormTrie {

    private final Node root;

    private SurfaceFormTrie(Node root) {
        this.root = root;
    }

    public static SurfaceFormTrie build(List<SurfaceForm> forms) {
        Node root = new Node();
        for (SurfaceForm form : forms) {
            String lower = form.text().toLowerCase(Locale.ROOT);
            Node current = root;
            for (int i = 0; i < lower.length(); i++) {
                current = current.children.computeIfAbsent(lower.charAt(i), k -> new Node());
            }
            if (current.output == null || form.text().length() > current.output.text().length()) {
                current.output = form;
            }
        }
        buildFailureLinks(root);
        return new SurfaceFormTrie(root);
    }

    public List<TrieMatch> scan(String text) {
        if (text == null || text.isEmpty() || root.children.isEmpty()) return List.of();

        String lower = text.toLowerCase(Locale.ROOT);
        List<TrieMatch> allMatches = new ArrayList<>();
        Node current = root;

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            while (current != root && !current.children.containsKey(c)) {
                current = current.failure;
            }
            current = current.children.getOrDefault(c, root);

            Node temp = current;
            while (temp != root) {
                if (temp.output != null) {
                    int end = i + 1;
                    int start = end - temp.output.text().length();
                    if (start >= 0 && isWordBoundary(text, start, end)) {
                        allMatches.add(new TrieMatch(temp.output, start, end));
                    }
                }
                temp = temp.failure;
            }
        }

        return filterLongestNonOverlapping(allMatches);
    }

    private static boolean isWordBoundary(String text, int start, int end) {
        if (start > 0 && isWordChar(text.charAt(start - 1))) return false;
        if (end < text.length() && isWordChar(text.charAt(end))) return false;
        return true;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static List<TrieMatch> filterLongestNonOverlapping(List<TrieMatch> matches) {
        if (matches.isEmpty()) return List.of();

        matches.sort(Comparator.comparingInt(TrieMatch::startOffset)
            .thenComparing(Comparator.comparingInt(TrieMatch::length).reversed()));

        List<TrieMatch> result = new ArrayList<>();
        int lastEnd = -1;
        for (TrieMatch m : matches) {
            if (m.startOffset() >= lastEnd) {
                result.add(m);
                lastEnd = m.endOffset();
            } else if (m.endOffset() > lastEnd && m.length() > (result.isEmpty() ? 0 : result.get(result.size() - 1).length())) {
                result.set(result.size() - 1, m);
                lastEnd = m.endOffset();
            }
        }
        return List.copyOf(result);
    }

    private static void buildFailureLinks(Node root) {
        Queue<Node> queue = new LinkedList<>();
        root.failure = root;
        for (Node child : root.children.values()) {
            child.failure = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char c = entry.getKey();
                Node child = entry.getValue();
                Node failNode = current.failure;
                while (failNode != root && !failNode.children.containsKey(c)) {
                    failNode = failNode.failure;
                }
                child.failure = failNode.children.getOrDefault(c, root);
                if (child.failure == child) child.failure = root;
                if (child.output == null && child.failure.output != null) {
                    child.output = child.failure.output;
                }
                queue.add(child);
            }
        }
    }

    private static class Node {
        final Map<Character, Node> children = new HashMap<>();
        Node failure;
        SurfaceForm output;
    }
}
