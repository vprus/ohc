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
package org.caffinitas.ohc;

import java.lang.foreign.MemorySegment;

/**
 * Serialize and deserialize cached data using {@link java.lang.foreign.MemorySegment}.
 *
 * <p><strong>Lifecycle note:</strong> The {@link MemorySegment} passed to {@link #serialize} and
 * {@link #deserialize} is valid only for the duration of the method call. Callers must not retain
 * a reference to the segment beyond the method invocation.</p>
 */
public interface CacheSerializer<T>
{
    /**
     * Serialize the specified value into the provided {@link MemorySegment}.
     * The segment's byte size equals the value returned by {@link #serializedSize(Object)}.
     *
     * @param value non-{@code null} object that needs to be serialized
     * @param buf   {@link MemorySegment} into which serialization needs to happen.
     */
    void serialize(T value, MemorySegment buf);

    /**
     * Deserialize from the provided {@link MemorySegment}.
     *
     * @param buf {@link MemorySegment} from which deserialization needs to happen.
     * @return the type that was deserialized. Must not return {@code null}.
     */
    T deserialize(MemorySegment buf);

    /**
     * Calculate the number of bytes that will be produced by {@link #serialize(Object, MemorySegment)}.
     *
     * @param value non-{@code null} object to calculate serialized size for
     * @return serialized size of {@code value}
     */
    int serializedSize(T value);
}

