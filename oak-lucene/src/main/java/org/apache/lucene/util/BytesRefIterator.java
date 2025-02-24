/*
 * COPIED FROM APACHE LUCENE 4.7.2
 *
 * Git URL: git@github.com:apache/lucene.git, tag: releases/lucene-solr/4.7.2, path: lucene/core/src/java
 *
 * (see https://issues.apache.org/jira/browse/OAK-10786 for details)
 */

package org.apache.lucene.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Comparator;

/**
 * A simple iterator interface for {@link BytesRef} iteration.
 */
public interface BytesRefIterator {

  /**
   * Increments the iteration to the next {@link BytesRef} in the iterator.
   * Returns the resulting {@link BytesRef} or <code>null</code> if the end of
   * the iterator is reached. The returned BytesRef may be re-used across calls
   * to next. After this method returns null, do not call it again: the results
   * are undefined.
   * 
   * @return the next {@link BytesRef} in the iterator or <code>null</code> if
   *         the end of the iterator is reached.
   * @throws IOException If there is a low-level I/O error.
   */
  public BytesRef next() throws IOException;
  
  /**
   * Return the {@link BytesRef} Comparator used to sort terms provided by the
   * iterator. This may return null if there are no items or the iterator is not
   * sorted. Callers may invoke this method many times, so it's best to cache a
   * single instance & reuse it.
   */
  public Comparator<BytesRef> getComparator();

  /** Singleton BytesRefIterator that iterates over 0 BytesRefs. */
  public static final BytesRefIterator EMPTY = new BytesRefIterator() {

    @Override
    public BytesRef next() {
      return null;
    }
    
    @Override
    public Comparator<BytesRef> getComparator() {
      return null;
    }
  };
}
