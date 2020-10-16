/*
 * Copyright The Hypertrace Authors
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

package org.hypertrace.agent.blocking;

/** Result of blocking evaluation from {@link BlockingEvaluator} */
public interface BlockingResult {

  /**
   * Indicates that execution should be blocked.
   *
   * @return true if execution should be blocked otherwise false
   */
  boolean blockExecution();

  /**
   * The reason why execution was blocked.
   *
   * @return the reason why execution was blocked.
   */
  String getReason();
}