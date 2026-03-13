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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.caffinitas.ohc.OHCacheBuilder;
import org.caffinitas.ohc.alloc.IAllocator;
import org.caffinitas.ohc.alloc.JNANativeAllocator;
import org.caffinitas.ohc.alloc.UnsafeAllocator;

import sun.misc.Unsafe;

final class Uns
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Uns.class);

    private static final Unsafe unsafe;
    private static final IAllocator allocator;

    private static final boolean __DEBUG_OFF_HEAP_MEMORY_ACCESS = Boolean.parseBoolean(System.getProperty(OHCacheBuilder.SYSTEM_PROPERTY_PREFIX + "debugOffHeapAccess", "false"));
    private static final String __ALLOCATOR = System.getProperty(OHCacheBuilder.SYSTEM_PROPERTY_PREFIX + "allocator");

    //
    // #ifdef __DEBUG_OFF_HEAP_MEMORY_ACCESS
    //
    private static final ConcurrentMap<Long, AllocInfo> ohDebug = __DEBUG_OFF_HEAP_MEMORY_ACCESS ? new ConcurrentHashMap<>(16384) : null;
    private static final Map<Long, Throwable> ohFreeDebug = __DEBUG_OFF_HEAP_MEMORY_ACCESS ? new ConcurrentHashMap<>(16384) : null;

    private static final class AllocInfo
    {
        final long size;
        final Throwable trace;

        AllocInfo(Long size, Throwable trace)
        {
            this.size = size;
            this.trace = trace;
        }
    }

    static void clearUnsDebugForTest()
    {
        if (__DEBUG_OFF_HEAP_MEMORY_ACCESS)
        {
            try
            {
                if (!ohDebug.isEmpty())
                {
                    for (Map.Entry<Long, AllocInfo> addrSize : ohDebug.entrySet())
                    {
                        System.err.printf("  still allocated: address=%d, size=%d, refCount=%d%n", addrSize.getKey(), addrSize.getValue().size, getInt(addrSize.getKey(), Util.ENTRY_OFF_REFCOUNT));
                        addrSize.getValue().trace.printStackTrace();
                    }
                    throw new RuntimeException("Not all allocated memory has been freed!");
                }
            }
            finally
            {
                ohDebug.clear();
                ohFreeDebug.clear();
            }
        }
    }

    private static void freed(long address)
    {
        if (__DEBUG_OFF_HEAP_MEMORY_ACCESS)
        {
            AllocInfo allocInfo = ohDebug.remove(address);
            if (allocInfo == null)
            {
                Throwable freedAt = ohFreeDebug.get(address);
                throw new IllegalStateException("Free of unallocated region " + address, freedAt);
            }
            ohFreeDebug.put(address, new Exception("free backtrace - t=" + System.nanoTime()));
        }
    }

    private static void allocated(long address, long bytes)
    {
        if (__DEBUG_OFF_HEAP_MEMORY_ACCESS)
        {
            AllocInfo allocatedLen = ohDebug.putIfAbsent(address, new AllocInfo(bytes, new Exception("Thread: "+Thread.currentThread())));
            if (allocatedLen != null)
                throw new Error("Oops - allocate() got duplicate address");
            ohFreeDebug.remove(address);
        }
    }

    static void validate(long address, long offset, long len)
    {
        if (__DEBUG_OFF_HEAP_MEMORY_ACCESS)
        {
            if (address == 0L)
                throw new NullPointerException();
            AllocInfo allocInfo = ohDebug.get(address);
            if (allocInfo == null)
            {
                Throwable freedAt = ohFreeDebug.get(address);
                throw new IllegalStateException("Access to unallocated region " + address + " - t=" + System.nanoTime(), freedAt);
            }
            if (offset < 0L)
                throw new IllegalArgumentException("Negative offset");
            if (len < 0L)
                throw new IllegalArgumentException("Negative length");
            if (offset + len > allocInfo.size)
                throw new IllegalArgumentException("Access outside allocated region");
        }
    }
    //
    // #endif
    //

    static
    {
        try
        {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            if (unsafe.addressSize() > 8)
                throw new RuntimeException("Address size " + unsafe.addressSize() + " not supported yet (max 8 bytes)");

            if (__DEBUG_OFF_HEAP_MEMORY_ACCESS)
                LOGGER.warn("Degraded performance due to off-heap memory allocations and access guarded by debug code enabled via system property " + OHCacheBuilder.SYSTEM_PROPERTY_PREFIX + "debugOffHeapAccess=true");

            IAllocator alloc;
            String allocType = __ALLOCATOR != null ? __ALLOCATOR : "jna";
            switch (allocType)
            {
                case "unsafe":
                    alloc = new UnsafeAllocator();
                    LOGGER.info("OHC using sun.misc.Unsafe memory allocation");
                    break;
                case "jna":
                default:
                    alloc = new JNANativeAllocator();
                    LOGGER.info("OHC using JNA OS native malloc/free");
            }

            allocator = alloc;
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private Uns()
    {
    }

    static long getLongFromByteArray(byte[] array, int offset)
    {
        if (offset < 0 || offset + 8 > array.length)
            throw new ArrayIndexOutOfBoundsException();
        return unsafe.getLong(array, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + offset);
    }

    static int getIntFromByteArray(byte[] array, int offset)
    {
        if (offset < 0 || offset + 4 > array.length)
            throw new ArrayIndexOutOfBoundsException();
        return unsafe.getInt(array, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + offset);
    }

    static short getShortFromByteArray(byte[] array, int offset)
    {
        if (offset < 0 || offset + 2 > array.length)
            throw new ArrayIndexOutOfBoundsException();
        return unsafe.getShort(array, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + offset);
    }

    static long getAndPutLong(long address, long offset, long value)
    {
        validate(address, offset, 8L);

        return unsafe.getAndSetLong(null, address + offset, value);
    }

    static void putLong(long address, long offset, long value)
    {
        validate(address, offset, 8L);
        unsafe.putLong(null, address + offset, value);
    }

    static long getLong(long address, long offset)
    {
        validate(address, offset, 8L);
        return unsafe.getLong(null, address + offset);
    }

    static void putInt(long address, long offset, int value)
    {
        validate(address, offset, 4L);
        unsafe.putInt(null, address + offset, value);
    }

    static int getInt(long address, long offset)
    {
        validate(address, offset, 4L);
        return unsafe.getInt(null, address + offset);
    }

    static void putShort(long address, long offset, short value)
    {
        validate(address, offset, 2L);
        unsafe.putShort(null, address + offset, value);
    }

    static short getShort(long address, long offset)
    {
        validate(address, offset, 2L);
        return unsafe.getShort(null, address + offset);
    }

    static void putByte(long address, long offset, byte value)
    {
        validate(address, offset, 1L);
        unsafe.putByte(null, address + offset, value);
    }

    static byte getByte(long address, long offset)
    {
        validate(address, offset, 1L);
        return unsafe.getByte(null, address + offset);
    }

    static boolean decrement(long address, long offset)
    {
        validate(address, offset, 4L);
        long v = unsafe.getAndAddInt(null, address + offset, -1);
        return v == 1;
    }

    static void increment(long address, long offset)
    {
        validate(address, offset, 4L);
        unsafe.getAndAddInt(null, address + offset, 1);
    }

    static void copyMemory(byte[] arr, int off, long address, long offset, long len)
    {
        validate(address, offset, len);
        unsafe.copyMemory(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, null, address + offset, len);
    }

    static void copyMemory(long address, long offset, byte[] arr, int off, long len)
    {
        validate(address, offset, len);
        unsafe.copyMemory(null, address + offset, arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, len);
    }

    static void copyMemory(long src, long srcOffset, long dst, long dstOffset, long len)
    {
        validate(src, srcOffset, len);
        validate(dst, dstOffset, len);
        unsafe.copyMemory(null, src + srcOffset, null, dst + dstOffset, len);
    }

    static void setMemory(long address, long offset, long len, byte val)
    {
        validate(address, offset, len);
        unsafe.setMemory(address + offset, len, val);
    }

    static boolean memoryCompare(long adr1, long off1, long adr2, long off2, long len)
    {
        if (adr1 == 0L)
            return false;

        if (adr1 == adr2)
        {
            assert off1 == off2;
            return true;
        }

        for (; len >= 8; len -= 8, off1 += 8, off2 += 8)
            if (Uns.getLong(adr1, off1) != Uns.getLong(adr2, off2))
                return false;
        for (; len >= 4; len -= 4, off1 += 4, off2 += 4)
            if (Uns.getInt(adr1, off1) != Uns.getInt(adr2, off2))
                return false;
        for (; len >= 2; len -= 2, off1 += 2, off2 += 2)
            if (Uns.getShort(adr1, off1) != Uns.getShort(adr2, off2))
                return false;
        for (; len > 0; len--, off1++, off2++)
            if (Uns.getByte(adr1, off1) != Uns.getByte(adr2, off2))
                return false;

        return true;
    }

    static long getTotalAllocated()
    {
        return allocator.getTotalAllocated();
    }

    static long allocate(long bytes)
    {
        return allocate(bytes, false);
    }

    static long allocate(long bytes, boolean throwOOME)
    {
        long address = allocator.allocate(bytes);
        if (address != 0L)
            allocated(address, bytes);
        else if (throwOOME)
            throw new OutOfMemoryError("unable to allocate " + bytes + " in off-heap");
        return address;
    }

    static long allocateIOException(long bytes) throws IOException
    {
        return allocateIOException(bytes, false);
    }

    static long allocateIOException(long bytes, boolean throwOOME) throws IOException
    {
        long address = allocate(bytes, throwOOME);
        if (address == 0L)
            throw new IOException("unable to allocate " + bytes + " in off-heap");
        return address;
    }

    static void free(long address)
    {
        if (address == 0L)
            return;
        freed(address);
        allocator.free(address);
    }

    /**
     * Creates a {@link MemorySegment} view of native memory at the given address and offset.
     *
     * <p><strong>Lifecycle note:</strong> The caller is responsible for ensuring the underlying
     * native memory remains valid for as long as the returned segment (or any derived segment) is
     * used. The segment does not own the memory; {@link #free(long)} must still be called
     * separately to release the allocation.</p>
     *
     * @param address base address of the native allocation
     * @param offset  byte offset from {@code address}
     * @param len     number of bytes to expose
     * @return a {@link MemorySegment} backed by the specified native memory region
     */
    @SuppressWarnings("restricted")
    static MemorySegment memorySegmentFor(long address, long offset, long len)
    {
        return MemorySegment.ofAddress(address + offset).reinterpret(len);
    }

    /**
     * Creates a {@link MemorySegment} whose accessibility is tied to the given {@link Arena}.
     * When {@code arena} is closed the segment becomes inaccessible.
     *
     * @param address base address of the native allocation
     * @param offset  byte offset from {@code address}
     * @param len     number of bytes to expose
     * @param arena   arena that controls segment lifetime
     * @return a {@link MemorySegment} backed by the specified native memory region
     */
    @SuppressWarnings("restricted")
    static MemorySegment memorySegmentFor(long address, long offset, long len, Arena arena)
    {
        return MemorySegment.ofAddress(address + offset).reinterpret(len, arena, null);
    }

    static MemorySegment keySegmentR(long hashEntryAdr)
    {
        long keyLen = HashEntries.getKeyLen(hashEntryAdr);
        return memorySegmentFor(hashEntryAdr + Util.ENTRY_OFF_DATA, 0, keyLen).asReadOnly();
    }

    static MemorySegment keySegment(long hashEntryAdr, long keyLen)
    {
        return memorySegmentFor(hashEntryAdr + Util.ENTRY_OFF_DATA, 0, keyLen);
    }

    static MemorySegment valueSegmentR(long hashEntryAdr)
    {
        long valueLen = HashEntries.getValueLen(hashEntryAdr);
        return memorySegmentFor(hashEntryAdr + Util.ENTRY_OFF_DATA + Util.roundUpTo8(HashEntries.getKeyLen(hashEntryAdr)), 0, valueLen).asReadOnly();
    }

    static MemorySegment valueSegment(long hashEntryAdr, long keyLen, long valueLen)
    {
        return memorySegmentFor(hashEntryAdr + Util.ENTRY_OFF_DATA + Util.roundUpTo8(keyLen), 0, valueLen);
    }

    /**
     * Creates a read-only {@link MemorySegment} view of a range within an off-heap hash entry,
     * whose validity is tied to {@code arena}.  Closing {@code arena} invalidates the segment.
     *
     * @param address base address of the native allocation
     * @param offset  byte offset within the allocation
     * @param len     number of bytes to expose
     * @param arena   arena controlling the segment's accessibility lifetime
     * @return a read-only {@link MemorySegment}
     */
    static MemorySegment valueSegmentR(long address, long offset, long len, Arena arena)
    {
        return memorySegmentFor(address, offset, len, arena).asReadOnly();
    }
}
