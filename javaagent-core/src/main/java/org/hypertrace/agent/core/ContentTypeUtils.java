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

package org.hypertrace.agent.core;

public class ContentTypeUtils {

  private ContentTypeUtils() {}

  /**
   * Returns true if the request/response with this content type should be captured.
   *
   * @param contentType request or response content type
   * @return whether body with this content type should be captured or not
   */
  public static boolean shouldCapture(String contentType) {
    if (contentType == null) {
      return false;
    }
    contentType = contentType.toLowerCase();
    return contentType.contains("json")
        || contentType.contains("graphql")
        || contentType.contains("x-www-form-urlencoded");
  }
}
