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

package org.apache.inlong.manager.client.api.inner;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.inlong.manager.common.pojo.sink.SinkRequest;
import org.apache.inlong.manager.common.pojo.source.SourceRequest;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamFieldInfo;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamInfo;

@Data
@NoArgsConstructor
public class InnerStreamContext {

    private InlongStreamInfo streamInfo;

    private SourceRequest sourceRequest;

    private SinkRequest sinkRequest;

    public InnerStreamContext(InlongStreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    public void updateStreamFields(List<InlongStreamFieldInfo> fieldInfoList) {
        streamInfo.setFieldList(fieldInfoList);
    }

}
