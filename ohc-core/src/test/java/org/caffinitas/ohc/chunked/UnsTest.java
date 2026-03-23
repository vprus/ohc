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
package org.caffinitas.ohc.chunked;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.Random;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import sun.misc.Unsafe;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

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
    public void testAllocate() throws Exception
    {
        MemorySegment adr = Uns.allocate(100, true);
        assertNotNull(adr);
        Uns.free(adr);
    }

    @Test
    public void testGetTotalAllocated() throws Exception
    {
        long before = Uns.getTotalAllocated();
        if (before < 0L)
            return;

        MemorySegment adr = Uns.allocate(128 * 1024 * 1024, true);
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
    public void testAllocateAndAccess() throws Exception
    {
        MemorySegment seg = Uns.allocate(CAPACITY, true);
        try
        {
            // write via unsafe, read via segment
            long addr = seg.address();
            Random rand = new Random();
            for (int i = 0; i < CAPACITY; i++)
                unsafe.putByte(addr + i, (byte) (rand.nextInt() & 0xFF));

            for (int i = 0; i < CAPACITY; i++)
                assertEquals(seg.get(ValueLayout.JAVA_BYTE, i), unsafe.getByte(addr + i));

            // write via segment, read via unsafe
            for (int i = 0; i < CAPACITY; i++)
                seg.set(ValueLayout.JAVA_BYTE, i, (byte) i);

            for (int i = 0; i < CAPACITY; i++)
                assertEquals(unsafe.getByte(addr + i), (byte) i);

            // long/int/short access
            for (int i = 0; i < CAPACITY - 7; i += 8)
            {
                assertEquals(seg.get(ValueLayout.JAVA_LONG_UNALIGNED, i), unsafe.getLong(addr + i));
                assertEquals(seg.get(ValueLayout.JAVA_INT_UNALIGNED, i), unsafe.getInt(addr + i));
                assertEquals(seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, i), unsafe.getShort(addr + i));
            }
        }
        finally
        {
            Uns.free(seg);
        }
    }
}
