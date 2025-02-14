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

package io.opentelemetry.javaagent.instrumentation.hypertrace.grpc.v1_6;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.opentelemetry.javaagent.instrumentation.hypertrace.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import io.opentelemetry.javaagent.instrumentation.hypertrace.com.google.protobuf.Descriptors.Descriptor;
import io.opentelemetry.javaagent.instrumentation.hypertrace.com.google.protobuf.Descriptors.FileDescriptor;
import io.opentelemetry.javaagent.instrumentation.hypertrace.com.google.protobuf.DynamicMessage;
import io.opentelemetry.javaagent.instrumentation.hypertrace.com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtobufMessageConverter {
  private static final Logger log = LoggerFactory.getLogger(ProtobufMessageConverter.class);
  /**
   * Converts an unrelocated protobuf message into a relocated DynamicMessage via a byte-array
   * round-trip.
   *
   * @param message The original protobuf message (an instance of com.google.protobuf.Message).
   * @return A relocated DynamicMessage built from your relocated protobuf classes.
   * @throws Exception if conversion fails.
   */
  public static DynamicMessage convertToRelocatedDynamicMessage(Message message) throws Exception {
    // 1. Serialize the original message to bytes.
    byte[] messageBytes = message.toByteArray();

    // 2. Obtain the original (unrelocated) message descriptor.
    Descriptors.Descriptor originalDescriptor = message.getDescriptorForType();

    // 3. Get the unrelocated file descriptor and its proto representation.
    Descriptors.FileDescriptor unrelocatedFileDescriptor = originalDescriptor.getFile();
    com.google.protobuf.DescriptorProtos.FileDescriptorProto unrelocatedFileProto =
        unrelocatedFileDescriptor.toProto();
    byte[] fileProtoBytes = unrelocatedFileProto.toByteArray();

    // 4. Parse the file descriptor proto using relocated classes.
    // This converts the unrelocated FileDescriptorProto into your relocated FileDescriptorProto.
    FileDescriptorProto relocatedFileProto = FileDescriptorProto.parseFrom(fileProtoBytes);

    // 5. Build the relocated FileDescriptor.
    FileDescriptor relocatedFileDescriptor =
        FileDescriptor.buildFrom(relocatedFileProto, new FileDescriptor[] {});

    // 6. Find the relocated message descriptor by name.
    Descriptor relocatedDescriptor =
        relocatedFileDescriptor.findMessageTypeByName(originalDescriptor.getName());
    if (relocatedDescriptor == null) {
      throw new IllegalStateException(
          "Could not find relocated descriptor for message type: " + originalDescriptor.getName());
    }

    // 7. Parse the original message bytes using the relocated descriptor.
    DynamicMessage relocatedMessage = DynamicMessage.parseFrom(relocatedDescriptor, messageBytes);
    return relocatedMessage;
  }

  /**
   * Method that takes an incoming message, converts it to a relocated one, prints it as JSON using
   * the relocated JsonFormat
   *
   * @param message The incoming (unrelocated) protobuf message.
   */
  public static String getMessage(Message message) {
    try {
      // Convert the unrelocated message into a relocated DynamicMessage.
      DynamicMessage relocatedMessage = convertToRelocatedDynamicMessage(message);

      // Use the relocated JsonFormat to print the message as JSON.
      JsonFormat.Printer relocatedPrinter = JsonFormat.printer();
      String jsonOutput = relocatedPrinter.print(relocatedMessage);

      return jsonOutput;
    } catch (Exception e) {
      log.error("Failed to convert message with relocated protobuf message: {}", e.getMessage());
    }
    return "";
  }
}
