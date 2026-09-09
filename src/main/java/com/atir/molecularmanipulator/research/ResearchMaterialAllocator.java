package com.atir.molecularmanipulator.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/** Exact long-capacity allocation for overlapping item/tag requirements; no sum of all flows is needed. */
public final class ResearchMaterialAllocator {
    private ResearchMaterialAllocator() {}

    public static <K> Map<K, Long> plan(List<Long> requirements, Map<K, Long> available, BiPredicate<Integer, K> accepts) {
        return planPortion(requirements, requirements, available, accepts);
    }

    /** Allocate the whole reservation first, then take a feasible portion without stranding overlapping demands. */
    public static <K> Map<K, Long> planPortion(List<Long> requirements, List<Long> portion, Map<K, Long> available, BiPredicate<Integer, K> accepts) {
        if (portion.size() != requirements.size()) throw new IllegalArgumentException("Mismatched allocation sizes");
        if (requirements.size() == 1) {
            long needed = requirements.getFirst(), take = portion.getFirst();
            if (needed < 0 || take < 0 || take > needed) throw new IllegalArgumentException("Invalid reservation portion");
            var result = new LinkedHashMap<K, Long>();
            for (var entry : available.entrySet()) {
                if (needed == 0) break;
                if (entry.getValue() <= 0 || !accepts.test(0, entry.getKey())) continue;
                long reserved = Math.min(needed, entry.getValue());
                long selected = Math.min(take, reserved);
                if (selected > 0) result.put(entry.getKey(), selected);
                needed -= reserved;
                take -= selected;
            }
            return needed == 0 ? result : null;
        }
        var keys = available.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey).filter(key -> {
                    for (int i = 0; i < requirements.size(); i++) if (requirements.get(i) > 0 && accepts.test(i, key)) return true;
                    return false;
                }).toList();
        int source = 0, itemStart = 1 + requirements.size(), sink = itemStart + keys.size();
        var graph = new Flow(sink + 1);
        var demands = new ArrayList<Edge>();
        var allocations = new ArrayList<Map<K, Edge>>();
        for (int i = 0; i < requirements.size(); i++) {
            long needed = requirements.get(i);
            if (needed < 0) throw new IllegalArgumentException("Negative material requirement");
            if (portion.get(i) < 0 || portion.get(i) > needed) throw new IllegalArgumentException("Invalid reservation portion");
            demands.add(graph.add(source, 1 + i, needed));
            var allocated = new LinkedHashMap<K, Edge>();
            for (int j = 0; j < keys.size(); j++) if (needed > 0 && accepts.test(i, keys.get(j))) allocated.put(keys.get(j), graph.add(1 + i, itemStart + j, needed));
            allocations.add(allocated);
        }
        for (int j = 0; j < keys.size(); j++) graph.add(itemStart + j, sink, available.get(keys.get(j)));
        graph.run(source, sink);
        if (demands.stream().anyMatch(edge -> edge.remaining != 0)) return null;
        var result = new LinkedHashMap<K, Long>();
        for (int i = 0; i < requirements.size(); i++) {
            long remaining = portion.get(i);
            for (var allocation : allocations.get(i).entrySet()) {
                long amount = Math.min(remaining, requirements.get(i) - allocation.getValue().remaining);
                if (amount > 0) { result.merge(allocation.getKey(), amount, Math::addExact); remaining -= amount; }
            }
        }
        return result;
    }

    private static final class Edge {
        final int to, reverse;
        long remaining;
        Edge(int to, int reverse, long remaining) { this.to = to; this.reverse = reverse; this.remaining = remaining; }
    }
    private static final class Flow {
        final List<List<Edge>> edges = new ArrayList<>();
        final int[] level, cursor;
        Flow(int count) { level = new int[count]; cursor = new int[count]; for (int i = 0; i < count; i++) edges.add(new ArrayList<>()); }
        Edge add(int from, int to, long capacity) {
            var forward = new Edge(to, edges.get(to).size(), capacity);
            var reverse = new Edge(from, edges.get(from).size(), 0);
            edges.get(from).add(forward); edges.get(to).add(reverse); return forward;
        }
        void run(int source, int sink) {
            while (true) {
                Arrays.fill(level, -1); level[source] = 0;
                var queue = new ArrayDeque<Integer>(); queue.add(source);
                while (!queue.isEmpty()) {
                    int from = queue.remove();
                    for (var edge : edges.get(from)) if (edge.remaining > 0 && level[edge.to] < 0) {
                        level[edge.to] = level[from] + 1; queue.add(edge.to);
                    }
                }
                if (level[sink] < 0) return;
                Arrays.fill(cursor, 0);
                while (push(source, sink, Long.MAX_VALUE) > 0) { }
            }
        }
        long push(int from, int sink, long limit) {
            if (from == sink) return limit;
            for (; cursor[from] < edges.get(from).size(); cursor[from]++) {
                var edge = edges.get(from).get(cursor[from]);
                if (edge.remaining == 0 || level[edge.to] != level[from] + 1) continue;
                long sent = push(edge.to, sink, Math.min(limit, edge.remaining));
                if (sent > 0) {
                    edge.remaining -= sent; edges.get(edge.to).get(edge.reverse).remaining += sent; return sent;
                }
            }
            return 0;
        }
    }
}
