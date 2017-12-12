package com.xiaolyuh.controller;

import com.github.benmanes.caffeine.cache.*;
import com.google.common.testing.FakeTicker;
import com.xiaolyuh.entity.Person;
import com.xiaolyuh.service.PersonService;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RestController
public class CaffeineCacheController {

    @Autowired
    PersonService personService;

    Cache<String, Object> manualCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    LoadingCache<String, Object> loadingCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(key -> createExpensiveGraph(key));

    AsyncLoadingCache<String, Object> asyncLoadingCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            // Either: Build with a synchronous computation that is wrapped as asynchronous
            .buildAsync(key -> createExpensiveGraph(key));
    // Or: Build with a asynchronous computation that returns a future
    // .buildAsync((key, executor) -> createExpensiveGraphAsync(key, executor));

    private CompletableFuture<Object> createExpensiveGraphAsync(String key, Executor executor) {
        CompletableFuture<Object> objectCompletableFuture = new CompletableFuture<>();
        return objectCompletableFuture;
    }

    private Object createExpensiveGraph(String key) {
        System.out.println("缓存不存在或过期，调用了createExpensiveGraph方法获取缓存key的值");
        if (key.equals("name")) {
            throw new RuntimeException("调用了该方法获取缓存key的值的时候出现异常");
        }
        return personService.findOne1();
    }

    @RequestMapping("/testManual")
    public Object testManual(Person person) {
        String key = "name1";
        Object graph = null;

        // 根据key查询一个缓存，如果没有返回NULL
        graph = manualCache.getIfPresent(key);
        // 根据Key查询一个缓存，如果没有调用createExpensiveGraph方法，并将返回值保存到缓存。
        // 如果该方法返回Null则manualCache.get返回null，如果该方法抛出异常则manualCache.get抛出异常
        graph = manualCache.get(key, k -> createExpensiveGraph(k));
        // 将一个值放入缓存，如果以前有值就覆盖以前的值
        manualCache.put(key, graph);
        // 删除一个缓存
        manualCache.invalidate(key);

        ConcurrentMap<String, Object> map = manualCache.asMap();
        System.out.println(map.toString());
        return graph;
    }

    @RequestMapping("/testLoading")
    public Object testLoading(Person person) {
        String key = "name1";

        // 采用同步方式去获取一个缓存和上面的手动方式是一个原理。在build Cache的时候会提供一个createExpensiveGraph函数。
        // 查询并在缺失的情况下使用同步的方式来构建一个缓存
        Object graph = loadingCache.get(key);

        // 获取组key的值返回一个Map
        List<String> keys = new ArrayList<>();
        keys.add(key);
        Map<String, Object> graphs = loadingCache.getAll(keys);
        return graph;
    }

    @RequestMapping("/testAsyncLoading")
    public Object testAsyncLoading(Person person) {
        String key = "name1";

        // 查询并在缺失的情况下使用异步的方式来构建缓存
        CompletableFuture<Object> graph = asyncLoadingCache.get(key);
        // 查询一组缓存并在缺失的情况下使用异步的方式来构建缓存
        List<String> keys = new ArrayList<>();
        keys.add(key);
        CompletableFuture<Map<String, Object>> graphs = asyncLoadingCache.getAll(keys);

        // 异步转同步
        loadingCache = asyncLoadingCache.synchronous();
        return graph;
    }

    @RequestMapping("/testSizeBased")
    public Object testSizeBased(Person person) {
        LoadingCache<String, Object> cache = Caffeine.newBuilder()
                .maximumSize(1)
                .build(k -> createExpensiveGraph(k));

        cache.get("A");
        System.out.println(cache.estimatedSize());
        cache.get("B");
        // 因为执行回收的方法是异步的，所以需要调用该方法，来保证缓存回收了。
        cache.cleanUp();
        System.out.println(cache.estimatedSize());

        return "";
    }

    @RequestMapping("/testTimeBased")
    public Object testTimeBased(Person person) {
        String key = "name1";
        // 用户测试，一个时间源，返回一个时间值，表示从某个固定但任意时间点开始经过的纳秒数。
        FakeTicker ticker = new FakeTicker();

        // 基于固定的到期策略进行退出
        // expireAfterAccess
        LoadingCache<String, Object> graphs1 = Caffeine.newBuilder()
                .ticker(ticker::read)
                .expireAfterAccess(5, TimeUnit.SECONDS)
                .build(k -> createExpensiveGraph(k));

        System.out.println("expireAfterAccess：第一次获取缓存");
        graphs1.get(key);

        System.out.println("expireAfterAccess：等待4.9S后，第二次次获取缓存");
        // 直接指定时钟
        ticker.advance(4900, TimeUnit.MILLISECONDS);
        graphs1.get(key);

        System.out.println("expireAfterAccess：等待0.101S后，第三次次获取缓存");
        ticker.advance(101, TimeUnit.MILLISECONDS);
        graphs1.get(key);

        // expireAfterWrite
        LoadingCache<String, Object> graphs2 = Caffeine.newBuilder()
                .ticker(ticker::read)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build(k -> createExpensiveGraph(k));

        System.out.println("expireAfterWrite：第一次获取缓存");
        graphs2.get(key);

        System.out.println("expireAfterWrite：等待4.9S后，第二次次获取缓存");
        ticker.advance(4900, TimeUnit.MILLISECONDS);
        graphs2.get(key);

        System.out.println("expireAfterWrite：等待0.101S后，第三次次获取缓存");
        ticker.advance(101, TimeUnit.MILLISECONDS);
        graphs2.get(key);

        // Evict based on a varying expiration policy
        // 基于不同的到期策略进行退出
        LoadingCache<String, Object> graphs3 = Caffeine.newBuilder()
                .ticker(ticker::read)
                .expireAfter(new Expiry<String, Object>() {

                    @Override
                    public long expireAfterCreate(String key, Object value, long currentTime) {
                        // Use wall clock time, rather than nanotime, if from an external resource
                        return TimeUnit.SECONDS.toNanos(5);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Object graph,
                                                  long currentTime, long currentDuration) {

                        System.out.println("调用了 expireAfterUpdate：" + TimeUnit.NANOSECONDS.toMillis(currentDuration));
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, Object graph,
                                                long currentTime, long currentDuration) {

                        System.out.println("调用了 expireAfterRead：" + TimeUnit.NANOSECONDS.toMillis(currentDuration));
                        return currentDuration;
                    }
                })
                .build(k -> createExpensiveGraph(k));

        System.out.println("expireAfter：第一次获取缓存");
        graphs3.get(key);

        System.out.println("expireAfter：等待4.9S后，第二次次获取缓存");
        ticker.advance(4900, TimeUnit.MILLISECONDS);
        graphs3.get(key);

        System.out.println("expireAfter：等待0.101S后，第三次次获取缓存");
        ticker.advance(101, TimeUnit.MILLISECONDS);
        Object object = graphs3.get(key);

        return object;
    }

    @RequestMapping("/testRemoval")
    public Object testRemoval(Person person) {
        String key = "name1";
        // 用户测试，一个时间源，返回一个时间值，表示从某个固定但任意时间点开始经过的纳秒数。
        FakeTicker ticker = new FakeTicker();

        // 基于固定的到期策略进行退出
        // expireAfterAccess
        LoadingCache<String, Object> graphs = Caffeine.newBuilder()
                .removalListener((String k, Object graph, RemovalCause cause) ->
                        System.out.printf("Key %s was removed (%s)%n", k, cause))
                .ticker(ticker::read)
                .expireAfterAccess(5, TimeUnit.SECONDS)
                .build(k -> createExpensiveGraph(k));

        System.out.println("第一次获取缓存");
        Object object = graphs.get(key);

        System.out.println("等待6S后，第二次次获取缓存");
        // 直接指定时钟
        ticker.advance(6000, TimeUnit.MILLISECONDS);
        graphs.get(key);

        System.out.println("手动删除缓存");
        graphs.invalidate(key);

        return object;
    }

    @RequestMapping("/testRefresh")
    public Object testRefresh(Person person) {
        String key = "name1";
        // 用户测试，一个时间源，返回一个时间值，表示从某个固定但任意时间点开始经过的纳秒数。
        FakeTicker ticker = new FakeTicker();

        // 基于固定的到期策略进行退出
        // expireAfterAccess
        LoadingCache<String, Object> graphs = Caffeine.newBuilder()
                .removalListener((String k, Object graph, RemovalCause cause) ->
                        System.out.printf("执行移除监听器- Key %s was removed (%s)%n", k, cause))
                .ticker(ticker::read)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                // 指定在创建缓存或者最近一次更新缓存后经过固定的时间间隔，刷新缓存
                .refreshAfterWrite(4, TimeUnit.SECONDS)
                .build(k -> createExpensiveGraph(k));

        System.out.println("第一次获取缓存");
        Object object = graphs.get(key);

        System.out.println("等待4.1S后，第二次次获取缓存");
        // 直接指定时钟
        ticker.advance(4100, TimeUnit.MILLISECONDS);
        graphs.get(key);

        System.out.println("等待5.1S后，第三次次获取缓存");
        // 直接指定时钟
        ticker.advance(5100, TimeUnit.MILLISECONDS);
        graphs.get(key);

        return object;
    }


}