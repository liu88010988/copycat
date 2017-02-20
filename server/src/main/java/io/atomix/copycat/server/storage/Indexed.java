/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.copycat.server.storage;

import io.atomix.copycat.server.storage.compaction.Compaction;
import io.atomix.copycat.server.storage.entry.Entry;
import io.atomix.copycat.util.CopycatSerializer;
import io.atomix.copycat.util.buffer.BufferInput;
import io.atomix.copycat.util.buffer.BufferOutput;

/**
 * Indexed log entry.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class Indexed<T extends Entry<T>> {
  private final long index;
  private final long term;
  private final T entry;
  private final int size;
  final EntryCleaner cleaner;

  public Indexed(long index, long term, T entry, int size) {
    this(index, term, entry, size, null);
  }

  public Indexed(long index, long term, T entry, int size, EntryCleaner cleaner) {
    this.index = index;
    this.term = term;
    this.entry = entry;
    this.size = size;
    this.cleaner = cleaner;
  }

  /**
   * Returns the entry index.
   *
   * @return The entry index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the entry term.
   *
   * @return The entry term.
   */
  public long term() {
    return term;
  }

  /**
   * Returns the entry type.
   *
   * @return The entry type.
   */
  public Entry.Type<T> type() {
    return entry.type();
  }

  /**
   * Returns the indexed entry.
   *
   * @return The indexed entry.
   */
  public T entry() {
    return entry;
  }

  /**
   * Returns the serialized entry size.
   *
   * @return The serialized entry size.
   */
  public int size() {
    return size;
  }

  /**
   * Returns the entry offset.
   *
   * @return The entry offset is {@code -1} if the offset is unknown.
   */
  public long offset() {
    return cleaner != null ? cleaner.offset : -1;
  }

  /**
   * Cleans the entry from the log.
   *
   * @param mode The compaction mode with which to compact the entry from the log.
   */
  public void compact(Compaction.Mode mode) {
    if (cleaner == null) {
      throw new IllegalStateException("Cannot clean entry");
    } else {
      cleaner.clean(mode);
    }
  }

  /**
   * Returns a boolean indicating whether the entry has been committed to the log.
   *
   * @return Indicates whether the entry has been committed to the log.
   */
  public boolean isCommitted() {
    return entry == null || cleaner != null;
  }

  /**
   * Returns the entry compaction mode.
   *
   * @return The entry compaction mode.
   */
  public Compaction.Mode compaction() {
    return cleaner != null ? cleaner.mode() : Compaction.Mode.NONE;
  }

  /**
   * Returns a boolean indicating whether the entry has been compacted from the log.
   *
   * @return Indicates whether the entry has been compacted from the log.
   */
  public boolean isCompacted() {
    return entry == null;
  }

  @Override
  public String toString() {
    return String.format("%s[index=%d, term=%d, entry=%s]", getClass().getSimpleName(), index, term, entry);
  }

  /**
   * Indexed entry serializer.
   */
  public static class Serializer implements CopycatSerializer<Indexed> {
    @Override
    @SuppressWarnings("unchecked")
    public void writeObject(BufferOutput output, Indexed entry) {
      output.writeLong(entry.index);
      output.writeLong(entry.term);
      output.writeByte(entry.entry.type().id());
      entry.type().serializer().writeObject(output, entry.entry);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Indexed readObject(BufferInput input, Class<Indexed> type) {
      long index = input.readLong();
      long term = input.readLong();
      Entry.Type<?> entryType = Entry.Type.forId(input.readByte());
      return new Indexed(index, term, entryType.serializer().readObject(input, entryType.type()), (int) input.position());
    }
  }
}