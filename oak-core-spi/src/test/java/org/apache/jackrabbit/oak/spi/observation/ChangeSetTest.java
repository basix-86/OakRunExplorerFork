/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.spi.observation;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChangeSetTest {

    @Test
    public void asJson() throws Exception{
        ChangeSet cs1 = new ChangeSet(2, Set.of("p-2", "p-3"), null,
                Set.of(), Set.of("pn-2"), Set.of("nt-2"));
        String json = cs1.asString();

        ChangeSet cs2 = ChangeSet.fromString(json);
        assertEquals(cs1, cs2);
        assertNull(cs2.getParentNodeNames());
        assertTrue(cs2.getParentNodeTypes().isEmpty());
    }

    @Test
    public void asJsonAll() throws Exception{
        ChangeSet cs1 = new ChangeSet(2, Set.of("p-2"), Set.of("nn-2"), Set.of("pnt-2"), Set.of("pn-2"), Set.of("nt-2"));
        String json = cs1.asString();
        ChangeSet cs2 = ChangeSet.fromString(json);
        assertEquals(cs1, cs2);
    }

}