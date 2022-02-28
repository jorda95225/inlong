/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.client.api;

import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.Data;

@Data
@ApiModel("Stream sink configuration")
public abstract class StreamSink {

    public enum SinkType {
        HIVE, ES, KAFKA;

        public static SinkType forType(String type) {
            for (SinkType sinkType : values()) {
                if (sinkType.name().equals(type)) {
                    return sinkType;
                }
            }
            throw new IllegalArgumentException(String.format("Illegal sink type=%s for Inlong", type));
        }
    }

    public abstract SinkType getSinkType();

    public abstract List<StreamField> getStreamFields();

    public abstract DataFormat getDataFormat();

}
