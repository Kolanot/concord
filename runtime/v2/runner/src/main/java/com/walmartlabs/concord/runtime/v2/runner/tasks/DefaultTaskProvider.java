package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.eclipse.sisu.Typed;

import javax.inject.Inject;
import javax.inject.Named;

@Named
@Typed
public class DefaultTaskProvider implements TaskProvider {

    private final Injector injector;
    private final TaskHolder holder;

    @Inject
    public DefaultTaskProvider(Injector injector, TaskHolder holder) {
        this.holder = holder;
        this.injector = injector;
    }

    @Override
    public Task createTask(Context ctx, String key) {
        Class<? extends Task> klass = holder.get(key);
        if (klass == null) {
            return null;
        }

        return injector.getInstance(klass);
    }
}
