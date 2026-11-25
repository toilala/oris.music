package com.orismusic.client.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeXWarmupTest {

  private val mb = 1024L * 1024

  @Test
  fun `warmup pays its cost only online with heap headroom`() {
    assertFalse(shouldSkipWarmup(online = true, lowMemory = false, freeHeapBytes = 64 * mb, minFreeHeapBytes = 32 * mb))
  }

  @Test
  fun `warmup skips offline`() {
    assertTrue(shouldSkipWarmup(online = false, lowMemory = false, freeHeapBytes = 256 * mb, minFreeHeapBytes = 32 * mb))
  }

  @Test
  fun `warmup skips when the system is low on memory`() {
    assertTrue(shouldSkipWarmup(online = true, lowMemory = true, freeHeapBytes = 256 * mb, minFreeHeapBytes = 32 * mb))
  }

  @Test
  fun `warmup skips below the heap floor and pays at it`() {
    assertTrue(shouldSkipWarmup(online = true, lowMemory = false, freeHeapBytes = 32 * mb - 1, minFreeHeapBytes = 32 * mb))
    assertFalse(shouldSkipWarmup(online = true, lowMemory = false, freeHeapBytes = 32 * mb, minFreeHeapBytes = 32 * mb))
  }
}
