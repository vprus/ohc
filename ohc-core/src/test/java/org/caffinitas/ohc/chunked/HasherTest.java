package org.caffinitas.ohc.chunked;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

import org.caffinitas.ohc.HashAlgorithm;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HasherTest
{
    @Test
    public void testMurmur3()
    {
        test(HashAlgorithm.MURMUR3);
    }

    @Test
    public void testCRC32()
    {
        test(HashAlgorithm.CRC32);
    }

    @Test
    public void testCRC32C()
    {
        test(HashAlgorithm.CRC32C);
    }

    @Test
    public void testXX()
    {
        test(HashAlgorithm.XX);
    }

    private void test(HashAlgorithm hash)
    {
        Random rand = new Random();

        byte[] buf = new byte[3211];
        rand.nextBytes(buf);

        Hasher hasher = Hasher.create(hash);
        long arrayVal = hasher.hash(MemorySegment.ofArray(buf));
        MemorySegment nativeMem = Uns.allocate(buf.length + 99, true);
        try
        {
            // write 99 zero bytes, then buf
            for (int i = 0; i < buf.length; i++)
                nativeMem.set(ValueLayout.JAVA_BYTE, 99 + i, buf[i]);

            long memoryVal = hasher.hash(nativeMem.asSlice(99, buf.length));

            Assert.assertEquals(memoryVal, arrayVal);
        }
        finally
        {
            Uns.free(nativeMem);
        }
    }
}
