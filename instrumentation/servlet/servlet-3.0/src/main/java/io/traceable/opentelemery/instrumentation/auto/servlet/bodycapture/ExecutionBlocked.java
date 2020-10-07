/*
 * Copyright Traceable.ai Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.traceable.opentelemery.instrumentation.auto.servlet.bodycapture;

import net.bytebuddy.asm.Advice.OnMethodEnter;

/**
 * Advice methods can return this object to indicate that the execution should be blocked. The class
 * should be added to {@link OnMethodEnter#skipOn()}. If the block object is returned then all exit
 * advices are executed as well but the user code "in-between" is skipped.
 */
public final class ExecutionBlocked {}
