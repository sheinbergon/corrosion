package org.sheinbergon.needle.concurrent

import com.google.common.collect.Sets
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.sheinbergon.needle.*
import java.util.concurrent.*

class PinnedExecutorsTest {

  @Test
  fun `Single pinnned thread executor`() {
    val executor = PinnedExecutors.newSinglePinnedThreadExecutor(TestMaskPinnedThreadFactory)
    testPinnedThreadExecutor(`1`, executor)
  }

  @Test
  fun `Fixed pinned thread pool executor`() {
    val executor = PinnedExecutors.newFixedPinnedThreadPool(availableCores, TestMaskPinnedThreadFactory)
    testPinnedThreadExecutor(availableCores, executor)
  }


  @Test
  fun `Single pinned thread scheduled executor`() {
    PinnedExecutors
        .newSinglePinnedThreadScheduledExecutor(TestMaskPinnedThreadFactory)
        .let { testScheduledPinnedThreadExecutor(`1`, it) }
  }

  @Test
  fun `Pooled pinned thread scheduled executor`() {
    PinnedExecutors
        .newScheduledPinnedThreadPool(availableCores, TestMaskPinnedThreadFactory)
        .let { testScheduledPinnedThreadExecutor(availableCores, it) }
  }

  @Test
  fun `Fixed affinity PinnedForkJoinPool behavior`() {
    PinnedExecutors
        .newPinnedWorkStealingPool(availableCores, TestMaskPinnedThreadFactory)
        .let { testPinnedThreadExecutor(availableCores, it) }
  }

  private fun testPinnedThreadExecutor(
    concurrency: Int,
    pool: ExecutorService
  ) {
    val visited = Sets.newConcurrentHashSet<Pinned>()
    val latch = CountDownLatch(concurrency)
    val tasks = (`0` until concurrency).map { callableTask(latch, visited) }
    val futures = pool.invokeAll(tasks)
    latch.await()
    Thread.sleep(5L)
    futures.forEach { it.isDone shouldBe true }
    visited.size shouldBeEqualTo concurrency
  }

  private fun testScheduledPinnedThreadExecutor(
    concurrency: Int,
    scheduler: ScheduledExecutorService
  ) {
    val visited = Sets.newConcurrentHashSet<Pinned>()
    val latch = CountDownLatch(concurrency)
    val futures = (`0` until concurrency)
        .map { runnableTask(latch, visited) }
        .map { scheduler.schedule(it, SCHEDULING_DELAY, TimeUnit.MILLISECONDS) }
    latch.await()
    Thread.sleep(5L)
    visited.size shouldBeEqualTo concurrency
    futures.forEach { it.isDone shouldBeEqualTo true }
  }

  private fun testPinnedWorkStealingExecutor(concurrency: Int, pool: ForkJoinPool) {
    val visited = Sets.newConcurrentHashSet<Pinned>()
    val latch = CountDownLatch(concurrency)
    pool.parallelism shouldBeEqualTo concurrency
    val actions = (`0` until concurrency).map { recursiveAction(latch, visited) }
    val tasks = actions.map { pool.submit(it) }
    latch.await()
    Thread.sleep(5L)
    tasks.forEach { it.isDone shouldBe true }
    visited.size shouldBeEqualTo concurrency
  }
}