/*
 * Copyright (c) 2017, Red Hat Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sample.test;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.*;
import org.sample.LockFreeSet;
import org.sample.LockFreeSetImpl;

import java.util.HashSet;
import java.util.Random;

// See jcstress-samples or existing tests for API introduction and testing guidelines

@JCStressTest
// Outline the outcomes here. The default outcome is provided, you need to remove it:
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "All values were successfully removed.")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "Some values was not removed.")
@State
public class RemoveTest {

    private final int maxValue = 10000;
    private final int nElements = 1000;

    private final LockFreeSet<Integer> set = new LockFreeSetImpl<>();

    private final HashSet<Integer> add_data = new HashSet<>();
    private final HashSet<Integer> remove_data = new HashSet<>();

    RemoveTest() {
        Random r = new Random();

        for (int i = 0; i < nElements; ++i)
            add_data.add(r.nextInt(maxValue) + 1);

        for (int i = 0; i < nElements; ++i) {
            int value = r.nextInt(maxValue) + 1;
            if (!add_data.contains(value)) {
                remove_data.add(value);
                set.add(value);
            }
        }

    }

    @Actor
    public void actor1() {
        for (int value : add_data) {
            set.add(value);
        }
    }

    @Actor
    public void actor2() {
        for (int value : remove_data) {
            set.remove(value);
        }
    }

    @Arbiter
    public void arbiter(I_Result r) {

        boolean ok = true;
        for (int value : add_data) {
            ok &= set.contains(value);
        }
        for (int value : remove_data) {
            ok &= !set.contains(value);
        }

        if (ok)
            r.r1 = 0;
        else
            r.r1 = 1;
    }

}