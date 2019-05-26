package ru.cscenter.dxahtepb.lockfreeset;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockFreeSetImplTest {

  @Test
  public void testEmptyByDefault() {
    LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();
    assertTrue(set.isEmpty());
  }

  @Test
  public void testBasicOperations() {
    LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();

    assertFalse(set.contains(1));

    assertTrue(set.add(1));
    assertTrue(set.add(2));
    assertTrue(set.add(4));
    assertTrue(set.add(3));
    assertFalse(set.add(1));
    assertFalse(set.add(3));

    assertTrue(set.contains(1));
    assertTrue(set.contains(2));
    assertTrue(set.contains(3));
    assertTrue(set.contains(4));

    assertTrue(set.remove(1));
    assertFalse(set.contains(1));

    assertFalse(set.remove(20));

    assertFalse(set.isEmpty());

    assertTrue(set.remove(2));
    assertTrue(set.remove(3));
    assertTrue(set.remove(4));
    assertTrue(set.isEmpty());
  }
}
