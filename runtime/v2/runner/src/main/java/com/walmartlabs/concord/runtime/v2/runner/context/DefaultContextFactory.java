package com.walmartlabs.concord.runtime.v2.runner.context;

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

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.vars.GlobalVariablesWithFrameOverrides;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.eclipse.sisu.Typed;

import javax.inject.Named;

@Named
@Typed
public class DefaultContextFactory implements ContextFactory {

    @Override
    public Context create(Runtime runtime, State state, ThreadId currentThreadId) {
        GlobalVariables globalVariables = runtime.getService(GlobalVariables.class);
        ProcessDefinition pd = runtime.getService(ProcessDefinition.class);

        Compiler compiler = runtime.getService(Compiler.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);

        Context ctx = new ContextImpl(globalVariables, currentThreadId, runtime, state, pd, compiler, ee);
        return new IntermediateGlobalsContext(ctx, new GlobalVariablesWithFrameOverrides(state, currentThreadId, globalVariables));
    }
}
