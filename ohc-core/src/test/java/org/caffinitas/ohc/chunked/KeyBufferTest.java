package org.caffinitas.ohc.chunked;

import java.lang.foreign.MemorySegment;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.caffinitas.ohc.HashAlgorithm;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class KeyBufferTest
{
    @AfterMethod(alwaysRun = true)
    public void deinit()
    {
        Uns.clearUnsDebugForTest();
    }

    @Test
    public void testHashFinish() throws Exception
    {
        byte[] ref = TestUtils.randomBytes(10);
        byte[] arr = new byte[12];
        arr[0] = (byte)(42 & 0xff);
        System.arraycopy(ref, 0, arr, 1, ref.length);
        arr[11] = (byte)(0xf0 & 0xff);
        KeyBuffer out = new KeyBuffer(MemorySegment.ofArray(arr)).finish(org.caffinitas.ohc.chunked.Hasher.create(HashAlgorithm.MURMUR3));

        Hasher hasher = Hashing.murmur3_128().newHasher();
        hasher.putByte((byte) 42);
        hasher.putBytes(ref);
        hasher.putByte((byte) 0xf0);

        assertEquals(out.hash(), hasher.hash().asLong());
    }

    @Test(dependsOnMethods = "testHashFinish")
    public void testHashFinish16() throws Exception
    {
        byte[] ref = TestUtils.randomBytes(14);
        byte[] arr = new byte[16];
        arr[0] = (byte)(42 & 0xff);
        System.arraycopy(ref, 0, arr, 1, ref.length);
        arr[15] = (byte)(0xf0 & 0xff);
        KeyBuffer out = new KeyBuffer(MemorySegment.ofArray(arr)).finish(org.caffinitas.ohc.chunked.Hasher.create(HashAlgorithm.MURMUR3));

        Hasher hasher = Hashing.murmur3_128().newHasher();
        hasher.putByte((byte) 42);
        hasher.putBytes(ref);
        hasher.putByte((byte) 0xf0);

        assertEquals(out.hash(), hasher.hash().asLong());
    }

    @Test(dependsOnMethods = "testHashFinish16")
    public void testHashRandom() throws Exception
    {
        for (int i = 1; i < 4100; i++)
        {
            for (int j = 0; j < 10; j++)
            {
                byte[] ref = TestUtils.randomBytes(i);
                KeyBuffer out = new KeyBuffer(MemorySegment.ofArray(ref)).finish(org.caffinitas.ohc.chunked.Hasher.create(HashAlgorithm.MURMUR3));

                Hasher hasher = Hashing.murmur3_128().newHasher();
                hasher.putBytes(ref);

                assertEquals(out.hash(), hasher.hash().asLong());
            }
        }
    }
}
