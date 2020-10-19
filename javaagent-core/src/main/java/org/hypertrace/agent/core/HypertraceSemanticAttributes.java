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

import static io.opentelemetry.common.AttributeKey.booleanKey;
import static io.opentelemetry.common.AttributeKey.stringKey;

import io.opentelemetry.common.AttributeKey;

public class HypertraceSemanticAttributes {
  private HypertraceSemanticAttributes() {}

  public static AttributeKey<String> requestHeader(String header) {
    return stringKey("request.header." + header);
  }

  public static AttributeKey<String> responseHeader(String header) {
    return stringKey("response.header." + header);
  }

  public static final AttributeKey<String> REQUEST_BODY = stringKey("request.body");

  public static final AttributeKey<String> RESPONSE_BODY = stringKey("response.body");

  public static final AttributeKey<Boolean> OPA_RESULT = booleanKey("hypertrace.opa.result");

  public static final AttributeKey<String> OPA_REASON = stringKey("hypertrace.opa.reason");
}
