/*
 * Copyright 2023 the original author or authors.
 *
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
 */

package org.gradle.internal.classpath;

import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

import static org.gradle.internal.classpath.Instrumented.INTERCEPTOR_RESOLVER;

/**
 * Injects the logic for Groovy calls instrumentation into the Groovy metaclasses.
 */
@NonNullApi
public class InstrumentedGroovyMetaClassHelper {
    /**
     * Should be invoked on an object that a Groovy Closure can dispatch the calls to. Injects the call interception logic into the metaclass of that object.
     * This is done for closure delegates that are reassigned, while the owner, thisObject, and the initial delegate are covered in
     * {@link InstrumentedGroovyMetaClassHelper#addInvocationHooksToEffectivelyInstrumentClosure(Closure)}
     */
    public static void addInvocationHooksInClosureDispatchObject(@Nullable Object object, boolean isEffectivelyInstrumented) {
        if (object == null) {
            return;
        }
        if (isEffectivelyInstrumented) {
            addInvocationHooksToMetaClass(object.getClass());
        }
    }

    public static void addInvocationHooksToEffectivelyInstrumentClosure(Closure<?> closure) {
        addInvocationHooksToMetaClass(closure.getThisObject().getClass());
        addInvocationHooksToMetaClass(closure.getOwner().getClass());
        addInvocationHooksToMetaClass(closure.getDelegate().getClass());
    }

    @SuppressWarnings("unused") // resolved via a method handle
    public static void addInvocationHooksToMetaClassIfInstrumented(Class<?> javaClass, String callableName) {
        if (INTERCEPTOR_RESOLVER.isAwareOfCallSiteName(callableName)) {
            addInvocationHooksToMetaClass(javaClass);
        }
    }

    public static void addInvocationHooksToMetaClass(Class<?> javaClass) {
        MetaClassRegistry metaClassRegistry = GroovySystem.getMetaClassRegistry();

        MetaClass originalMetaClass = metaClassRegistry.getMetaClass(javaClass);
        if (!(originalMetaClass instanceof CallInterceptingMetaClass)) {
            metaClassRegistry.setMetaClass(javaClass, interceptedMetaClass(javaClass, metaClassRegistry, originalMetaClass));
        }
    }

    private static CallInterceptingMetaClass interceptedMetaClass(Class<?> javaClass, MetaClassRegistry metaClassRegistry, MetaClass originalMetaClass) {
        return new CallInterceptingMetaClass(metaClassRegistry, javaClass, originalMetaClass, InstrumentedGroovyCallsHelper.INSTANCE, INTERCEPTOR_RESOLVER);
    }
}
