package org.caffinitas.ohc.linked;

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
        long arrayVal = hasher.hash(buf);
        long memAddr = Uns.allocate(buf.length + 99);
        try
        {
            Uns.copyMemory(buf, 0, memAddr, 99L, buf.length);

            long memoryVal = hasher.hash(Uns.memorySegmentFor(memAddr, 99L, buf.length));

            Assert.assertEquals(memoryVal, arrayVal);
        }
        finally
        {
            Uns.free(memAddr);
        }
    }
}
