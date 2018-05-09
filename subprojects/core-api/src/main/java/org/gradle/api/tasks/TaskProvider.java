/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

/**
 * Providers a task of the given type.
 * 
 * @param <T> Task type
 * @since 4.8
 */
@Incubating
public interface TaskProvider<T extends Task> extends Provider<T> {
    /**
     * Configures the task with the given action. Actions are run in the order added.
     *
     * @param action A {@link Action} that can configure the task when required.
     * @since 4.8
     */
    void configure(Action<? super Task> action);
}
