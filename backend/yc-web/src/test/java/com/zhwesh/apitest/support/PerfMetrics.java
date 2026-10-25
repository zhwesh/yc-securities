package com.zhwesh.apitest.support;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口吞吐量/延迟统计
 */
public class PerfMetrics {

    /**
     * 单接口指标
     */
    public record Metric(String name, int concurrency, int samples, double qps,
                         double avgMs, double p50Ms, double p95Ms, double p99Ms, int errors) {
    }

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    private final List<Metric> metrics = new ArrayList<>();

    /**
     * 测量接口吞吐量
     * @param name 接口名称
     * @param concurrency 并发数
     * @param warmup 预热次数
     * @param samples 测量次数
     * @param action 被测操作
     * @return 指标
     */
    public static Metric measure(String name, int concurrency, int warmup, int samples, Action action) {
        for (int i = 0; i < warmup; i++) {
            try {
                action.run();
            } catch (Exception ignored) {
            }
        }
        int workers = Math.max(1, concurrency);
        long[] nanos = new long[samples];
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger index = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        long begin = System.nanoTime();
        for (int worker = 0; worker < workers; worker++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int slot;
                while ((slot = index.getAndIncrement()) < samples) {
                    long started = System.nanoTime();
                    try {
                        action.run();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                    nanos[slot] = System.nanoTime() - started;
                }
            });
        }
        try {
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long elapsed = System.nanoTime() - begin;
        Arrays.sort(nanos);
        double seconds = Math.max(elapsed / 1e9, 1e-9);
        double avg = Arrays.stream(nanos).average().orElse(0) / 1e6;
        return new Metric(name, workers, samples, samples / seconds, avg,
            percentile(nanos, 50), percentile(nanos, 95), percentile(nanos, 99), errors.get());
    }

    private static double percentile(long[] sortedNanos, int percent) {
        if (sortedNanos.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(sortedNanos.length * percent / 100.0) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.length - 1));
        return sortedNanos[index] / 1e6;
    }

    public void add(Metric metric) {
        metrics.add(metric);
    }

    public List<Metric> metrics() {
        return metrics;
    }

    public void print(PrintStream out) {
        out.println();
        out.println("================ 接口吞吐量报告 ================");
        out.printf("%-32s %4s %6s %10s %8s %8s %8s %8s %6s%n",
            "接口", "并发", "样本", "QPS", "avg(ms)", "p50", "p95", "p99", "错误");
        for (Metric metric : metrics) {
            out.printf("%-32s %4d %6d %10.2f %8.2f %8.2f %8.2f %8.2f %6d%n",
                metric.name(), metric.concurrency(), metric.samples(), metric.qps(),
                metric.avgMs(), metric.p50Ms(), metric.p95Ms(), metric.p99Ms(), metric.errors());
        }
        out.println("================================================");
    }

    public void writeCsv(Path path) {
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("interface,concurrency,samples,qps,avg_ms,p50_ms,p95_ms,p99_ms,errors\n");
            for (Metric metric : metrics) {
                csv.append(metric.name()).append(',')
                    .append(metric.concurrency()).append(',')
                    .append(metric.samples()).append(',')
                    .append(String.format("%.2f", metric.qps())).append(',')
                    .append(String.format("%.2f", metric.avgMs())).append(',')
                    .append(String.format("%.2f", metric.p50Ms())).append(',')
                    .append(String.format("%.2f", metric.p95Ms())).append(',')
                    .append(String.format("%.2f", metric.p99Ms())).append(',')
                    .append(metric.errors()).append('\n');
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, csv.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("写入性能报告失败", e);
        }
    }

    public void sortByName() {
        metrics.sort(Comparator.comparing(Metric::name));
    }
}
