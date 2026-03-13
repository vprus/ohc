/*
 *      Copyright (C) 2014 Robert Stupp, Koeln, Germany, robert-stupp.de
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.caffinitas.ohc.linked;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;

import static org.testng.Assert.*;

public class UnsTest
{
    @AfterMethod(alwaysRun = true)
    public void deinit()
    {
        Uns.clearUnsDebugForTest();
    }

    private static final Unsafe unsafe;

    static final int CAPACITY = 65536;

    static
    {
        try
        {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            if (unsafe.addressSize() > 8)
                throw new RuntimeException("Address size " + unsafe.addressSize() + " not supported yet (max 8 bytes)");
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testMemorySegmentFor() throws Exception
    {
        long adr = Uns.allocate(CAPACITY);
        try
        {
            // write known bytes via unsafe
            for (int i = 0; i < CAPACITY; i++)
                unsafe.putByte(adr + i, (byte) (i & 0xFF));

            MemorySegment seg = Uns.memorySegmentFor(adr, 0, CAPACITY);
            assertEquals(seg.byteSize(), CAPACITY);

            for (int i = 0; i < CAPACITY; i++)
                assertEquals(seg.get(ValueLayout.JAVA_BYTE, i), (byte) (i & 0xFF));

            // absolute int/long access
            for (int i = 0; i < CAPACITY - 7; i += 8)
            {
                assertEquals(seg.get(ValueLayout.JAVA_LONG_UNALIGNED, i), unsafe.getLong(adr + i));
                assertEquals(seg.get(ValueLayout.JAVA_INT_UNALIGNED, i), unsafe.getInt(adr + i));
                assertEquals(seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, i), unsafe.getShort(adr + i));
            }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testAllocate() throws Exception
    {
        long adr = Uns.allocate(100);
        assertNotEquals(adr, 0L);
        Uns.free(adr);

        adr = Uns.allocateIOException(100);
        Uns.free(adr);
    }

    @Test(expectedExceptions = IOException.class)
    public void testAllocateTooMuch() throws Exception
    {
        Uns.allocateIOException(Long.MAX_VALUE >> 2);
    }

    @Test
    public void testGetTotalAllocated() throws Exception
    {
        long before = Uns.getTotalAllocated();
        if (before < 0L)
            return;

        // TODO Uns.getTotalAllocated() seems not to respect "small" areas - need to check that ... eventually.
//        long[] adrs = new long[10000];
//        try
//        {
//            for (int i=0;i<adrs.length;i++)
//                adrs[i] = Uns.allocate(100);
//            assertTrue(Uns.getTotalAllocated() > before);
//        }
//        finally
//        {
//            for (long adr : adrs)
//                Uns.free(adr);
//        }

        long adr = Uns.allocate(128 * 1024 * 1024);
        try
        {
            assertTrue(Uns.getTotalAllocated() > before);
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testCopyMemory() throws Exception
    {
        byte[] ref = TestUtils.randomBytes(7777 + 130);
        byte[] arr = new byte[7777 + 130];

        long adr = Uns.allocate(7777 + 130);
        try
        {
            for (int offset = 0; offset < 10; offset += 13)
                for (int off = 0; off < 10; off += 13)
                {
                    Uns.copyMemory(ref, off, adr, offset, 7777);

                    equals(ref, adr, offset, 7777);

                    Uns.copyMemory(adr, offset, arr, off, 7777);

                    equals(ref, arr, off, 7777);
                }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    private static void equals(byte[] ref, long adr, int off, int len)
    {
        for (; len-- > 0; off++)
            assertEquals(unsafe.getByte(adr + off), ref[off]);
    }

    private static void equals(byte[] ref, byte[] arr, int off, int len)
    {
        for (; len-- > 0; off++)
            assertEquals(arr[off], ref[off]);
    }

    @Test
    public void testSetMemory() throws Exception
    {
        long adr = Uns.allocate(7777 + 130);
        try
        {
            for (byte b = 0; b < 13; b++)
                for (int offset = 0; offset < 10; offset += 13)
                {
                    Uns.setMemory(adr, offset, 7777, b);

                    for (int off = 0; off < 7777; off++)
                        assertEquals(unsafe.getByte(adr + offset), b);
                }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetLongFromByteArray() throws Exception
    {
        byte[] arr = TestUtils.randomBytes(32);
        for (int i = 0; i < 14; i++)
        {
            long u = Uns.getLongFromByteArray(arr, i);
            long expected = unsafe.getLong(arr, (long) sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET + i);
            assertEquals(u, expected);
        }
    }

    @Test
    public void testGetPutLong() throws Exception
    {
        long adr = Uns.allocate(128);
        try
        {
            Uns.copyMemory(TestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++)
            {
                long l = Uns.getLong(adr, i);
                assertEquals(unsafe.getLong(adr + i), Uns.getLong(adr, i));

                Uns.putLong(adr, i, l);
                assertEquals(unsafe.getLong(adr + i), l);
            }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetPutInt() throws Exception
    {
        long adr = Uns.allocate(128);
        try
        {
            Uns.copyMemory(TestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++)
            {
                int l = Uns.getInt(adr, i);
                assertEquals(unsafe.getInt(adr + i), Uns.getInt(adr, i));

                Uns.putInt(adr, i, l);
                assertEquals(unsafe.getInt(adr + i), l);
            }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetPutShort() throws Exception
    {
        long adr = Uns.allocate(128);
        try
        {
            Uns.copyMemory(TestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++)
            {
                short l = Uns.getShort(adr, i);
                assertEquals(unsafe.getShort(adr + i), Uns.getShort(adr, i));

                Uns.putShort(adr, i, l);
                assertEquals(unsafe.getShort(adr + i), l);
            }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testGetPutByte() throws Exception
    {
        long adr = Uns.allocate(128);
        try
        {
            Uns.copyMemory(TestUtils.randomBytes(128), 0, adr, 0, 128);

            for (int i = 0; i < 14; i++)
            {
                MemorySegment buf = Uns.memorySegmentFor(adr, i, 8);
                byte l = Uns.getByte(adr, i);
                assertEquals(buf.get(ValueLayout.JAVA_BYTE, 0), Uns.getByte(adr, i));
                assertEquals(unsafe.getByte(adr + i), Uns.getByte(adr, i));

                Uns.putByte(adr, i, l);
                assertEquals(buf.get(ValueLayout.JAVA_BYTE, 0), l);
                assertEquals(unsafe.getByte(adr + i), l);
            }
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testDecrementIncrement() throws Exception
    {
        long adr = Uns.allocate(128);
        try
        {
            for (int i = 0; i < 120; i++)
            {
                String loop = "at loop #" + i;
                long v = Uns.getInt(adr, i);
                Uns.increment(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 1, loop);
                Uns.increment(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 2, loop);
                Uns.increment(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 3, loop);
                Uns.decrement(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 2, loop);
                Uns.decrement(adr, i);
                assertEquals(Uns.getInt(adr, i), v + 1, loop);
            }

            Uns.putLong(adr, 8, 1);
            assertTrue(Uns.decrement(adr, 8));
            Uns.putLong(adr, 8, 2);
            assertFalse(Uns.decrement(adr, 8));
        }
        finally
        {
            Uns.free(adr);
        }
    }

    @Test
    public void testCompare() throws Exception
    {
        long adr = Uns.allocate(CAPACITY);
        try
        {
            long adr2 = Uns.allocate(CAPACITY);
            try
            {

                Uns.setMemory(adr, 5, 11, (byte) 0);
                Uns.setMemory(adr2, 5, 11, (byte) 1);

                assertFalse(Uns.memoryCompare(adr, 5, adr2, 5, 11));

                assertTrue(Uns.memoryCompare(adr, 5, adr, 5, 11));
                assertTrue(Uns.memoryCompare(adr2, 5, adr2, 5, 11));

                Uns.setMemory(adr, 5, 11, (byte) 1);

                assertTrue(Uns.memoryCompare(adr, 5, adr2, 5, 11));
            }
            finally
            {
                Uns.free(adr2);
            }
        }
        finally
        {
            Uns.free(adr);
        }
    }
}
