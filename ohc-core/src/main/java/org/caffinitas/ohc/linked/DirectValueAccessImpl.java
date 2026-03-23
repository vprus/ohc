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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.caffinitas.ohc.DirectValueAccess;

/**
 * Default implementation of {@link DirectValueAccess} for the linked cache.
 *
 * <p><strong>Lifecycle:</strong> The {@link MemorySegment} returned by {@link #segment()} is
 * backed by a confined {@link Arena}.  Closing this {@link DirectValueAccess} (via
 * {@link #close()}) closes the arena, making the segment inaccessible.  Any attempt to read from
 * the segment after {@code close()} has been called will throw {@link IllegalStateException}.
 * The underlying hash-entry reference count is decremented on close, so callers must not use
 * the segment after closing.</p>
 */
class DirectValueAccessImpl implements DirectValueAccess
{
    private final long hashEntryAdr;
    private boolean closed;
    private final Arena arena;
    private final MemorySegment segment;

    DirectValueAccessImpl(long hashEntryAdr, boolean readOnly)
    {
        long keyLen = HashEntries.getKeyLen(hashEntryAdr);
        long valueLen = HashEntries.getValueLen(hashEntryAdr);
        this.hashEntryAdr = hashEntryAdr;
        // Use a confined arena so that closing this DirectValueAccess immediately
        // invalidates the segment, preventing use-after-free bugs.
        this.arena = Arena.ofConfined();
        MemorySegment seg = Uns.memorySegmentFor(hashEntryAdr, Util.ENTRY_OFF_DATA + Util.roundUpTo8(keyLen), valueLen, arena);
        this.segment = readOnly ? seg.asReadOnly() : seg;
    }

    public MemorySegment segment()
    {
        if (closed)
            throw new IllegalStateException("already closed");
        return segment;
    }

    public void close()
    {
        if (!closed)
        {
            closed = true;
            arena.close();
            HashEntries.dereference(hashEntryAdr);
        }
    }
}
