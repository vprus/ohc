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
package org.caffinitas.ohc.jmh;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.caffinitas.ohc.CacheSerializer;

public final class Utils
{
    public static final CacheSerializer<byte[]> byteArraySerializer = new CacheSerializer<byte[]>()
    {
        public void serialize(byte[] bytes, MemorySegment buf)
        {
            buf.set(ValueLayout.JAVA_INT_UNALIGNED, 0, bytes.length);
            MemorySegment.copy(bytes, 0, buf, ValueLayout.JAVA_BYTE, 4, bytes.length);
        }

        public byte[] deserialize(MemorySegment buf)
        {
            int len = buf.get(ValueLayout.JAVA_INT_UNALIGNED, 0);
            byte[] arr = new byte[len];
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 4, arr, 0, len);
            return arr;
        }

        public int serializedSize(byte[] bytes)
        {
            return 4 + bytes.length;
        }
    };

    public static final CacheSerializer<Integer> intSerializer = new CacheSerializer<Integer>()
    {
        public void serialize(Integer integer, MemorySegment buf)
        {
            buf.set(ValueLayout.JAVA_INT_UNALIGNED, 0, integer);
        }

        public Integer deserialize(MemorySegment buf)
        {
            return buf.get(ValueLayout.JAVA_INT_UNALIGNED, 0);
        }

        public int serializedSize(Integer integer)
        {
            return 4;
        }
    };
}
