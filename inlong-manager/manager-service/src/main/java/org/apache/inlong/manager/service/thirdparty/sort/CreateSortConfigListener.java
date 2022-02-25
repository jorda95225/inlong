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

package org.apache.inlong.manager.service.thirdparty.sort;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.inlong.manager.common.beans.ClusterBean;
import org.apache.inlong.manager.common.enums.Constant;
import org.apache.inlong.manager.common.pojo.group.InlongGroupExtInfo;
import org.apache.inlong.manager.common.pojo.group.InlongGroupRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkBriefResponse;
import org.apache.inlong.manager.common.pojo.sink.SinkResponse;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamInfo;
import org.apache.inlong.manager.common.pojo.stream.StreamBriefResponse;
import org.apache.inlong.manager.common.pojo.workflow.form.GroupResourceProcessForm;
import org.apache.inlong.manager.common.pojo.workflow.form.ProcessForm;
import org.apache.inlong.manager.common.pojo.workflow.form.UpdateGroupProcessForm;
import org.apache.inlong.manager.common.settings.InlongGroupSettings;
import org.apache.inlong.manager.common.util.Preconditions;
import org.apache.inlong.manager.service.core.InlongStreamService;
import org.apache.inlong.manager.service.sink.StreamSinkService;
import org.apache.inlong.manager.workflow.WorkflowContext;
import org.apache.inlong.manager.workflow.event.ListenerResult;
import org.apache.inlong.manager.workflow.event.task.SortOperateListener;
import org.apache.inlong.manager.workflow.event.task.TaskEvent;
import org.apache.inlong.sort.formats.common.FormatInfo;
import org.apache.inlong.sort.protocol.DataFlowInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.deserialization.DeserializationInfo;
import org.apache.inlong.sort.protocol.deserialization.InLongMsgCsvDeserializationInfo;
import org.apache.inlong.sort.protocol.sink.SinkInfo;
import org.apache.inlong.sort.protocol.source.PulsarSourceInfo;
import org.apache.inlong.sort.protocol.source.SourceInfo;
import org.apache.inlong.sort.protocol.source.TubeSourceInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreateSortConfigListener implements SortOperateListener {

    @Autowired
    private ClusterBean clusterBean;
    @Autowired
    private InlongStreamService inlongStreamService;
    @Autowired
    private StreamSinkService streamSinkService;

    @Override
    public TaskEvent event() {
        return TaskEvent.CREATE;
    }

    @Override
    public ListenerResult listen(WorkflowContext context) throws Exception {
        log.info("Create sort config for context={}", context);
        ProcessForm form = context.getProcessForm();
        InlongGroupRequest groupRequest = getGroupRequest(form);
        String groupId = groupRequest.getInlongGroupId();
        if (StringUtils.isEmpty(groupId)) {
            log.warn("GroupId is null for context={}", context);
            return ListenerResult.success();
        }
        List<StreamBriefResponse> streamBriefResponses = inlongStreamService.getBriefList(groupId);
        if (CollectionUtils.isEmpty(streamBriefResponses)) {
            log.warn("Stream not found by groupId={}", groupId);
            return ListenerResult.success();
        }

        Map<String, DataFlowInfo> dataFlowInfoMap = streamBriefResponses.stream().map(streamBriefResponse -> {
                            DataFlowInfo flowInfo = createDataFlow(streamBriefResponse, groupRequest);
                            if (flowInfo != null) {
                                return Pair.of(streamBriefResponse.getInlongStreamId(), flowInfo);
                            } else {
                                return null;
                            }
                        }
                ).filter(pair -> pair != null)
                .collect(Collectors.toMap(pair -> pair.getKey(),
                        pair -> pair.getValue()));
        final ObjectMapper objectMapper = new ObjectMapper();
        String dataFlows = objectMapper.writeValueAsString(dataFlowInfoMap);
        InlongGroupExtInfo extInfo = new InlongGroupExtInfo();
        extInfo.setInlongGroupId(groupId);
        extInfo.setKeyName(InlongGroupSettings.DATA_FLOW);
        extInfo.setKeyValue(dataFlows);
        if (groupRequest.getExtList() == null) {
            groupRequest.setExtList(Lists.newArrayList());
        }
        groupRequest.getExtList().add(extInfo);
        return ListenerResult.success();
    }

    private DataFlowInfo createDataFlow(StreamBriefResponse streamBriefResponse,
            InlongGroupRequest inlongGroupRequest) {
        List<SinkBriefResponse> sinkBriefResponses = streamBriefResponse.getSinkList();
        if (CollectionUtils.isEmpty(sinkBriefResponses)) {
            throw new RuntimeException(String.format("No sink found by stream=%s", streamBriefResponse));
        }
        SinkBriefResponse sinkBriefResponse = sinkBriefResponses.get(0);
        String sinkType = sinkBriefResponse.getSinkType();
        int sinkId = sinkBriefResponse.getId();
        SinkResponse sinkResponse = streamSinkService.get(sinkId, sinkType);
        SourceInfo sourceInfo = createSourceInfo(inlongGroupRequest, sinkResponse);
        SinkInfo sinkInfo = SinkInfoUtils.createSinkInfo(sinkResponse);
        return new DataFlowInfo(sinkId, sourceInfo, sinkInfo);
    }

    private SourceInfo createSourceInfo(InlongGroupRequest groupRequest, SinkResponse sinkResponse) {
        String middleWareType = groupRequest.getMiddlewareType();
        String groupId = sinkResponse.getInlongGroupId();
        String streamId = sinkResponse.getInlongStreamId();
        InlongStreamInfo streamInfo = inlongStreamService.get(groupId, streamId);
        DeserializationInfo deserializationInfo = null;
        if (StringUtils.isNotEmpty(streamInfo.getDataSeparator())) {
            char separator = (char) Integer.parseInt(streamInfo.getDataSeparator());
            deserializationInfo = new InLongMsgCsvDeserializationInfo(streamId, separator);
        }
        List<FieldInfo> fieldInfos = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(streamInfo.getFieldList())) {
            fieldInfos = streamInfo.getFieldList().stream().map(inlongStreamFieldInfo -> {
                FormatInfo formatInfo = SortFieldFormatUtils.convertFieldFormat(
                        inlongStreamFieldInfo.getFieldType().toLowerCase());
                return new FieldInfo(inlongStreamFieldInfo.getFieldName(), formatInfo);
            }).collect(Collectors.toList());
        }
        if (Constant.MIDDLEWARE_PULSAR.equals(middleWareType)) {
            return createPulsarSourceInfo(groupRequest, streamInfo, deserializationInfo, fieldInfos);
        } else if (Constant.MIDDLEWARE_TUBE.equals(middleWareType)) {
            return createTubeSourceInfo(groupRequest, deserializationInfo, fieldInfos);
        } else {
            throw new RuntimeException(
                    String.format("MiddleWare:{} not support in CreateSortConfigListener", middleWareType));
        }
    }

    private PulsarSourceInfo createPulsarSourceInfo(InlongGroupRequest groupRequest,
            InlongStreamInfo streamInfo,
            DeserializationInfo deserializationInfo,
            List<FieldInfo> fieldInfos) {
        String topicName = streamInfo.getMqResourceObj();
        return SourceInfoUtils.createPulsarSourceInfo(groupRequest, topicName, deserializationInfo, fieldInfos,
                clusterBean);
    }

    private TubeSourceInfo createTubeSourceInfo(InlongGroupRequest groupRequest,
            DeserializationInfo deserializationInfo,
            List<FieldInfo> fieldInfos) {
        String masterAddress = clusterBean.getTubeMaster();
        Preconditions.checkNotNull(masterAddress, "tube cluster address cannot be empty");
        String topic = groupRequest.getMqResourceObj();
        // The consumer group name is: taskName_topicName_consumer_group
        String consumerGroup = clusterBean.getAppName() + "_" + topic + "_consumer_group";
        return new TubeSourceInfo(topic, masterAddress, consumerGroup,
                deserializationInfo, fieldInfos.toArray(new FieldInfo[0]));
    }

    private InlongGroupRequest getGroupRequest(ProcessForm processForm) {
        if (processForm instanceof GroupResourceProcessForm) {
            GroupResourceProcessForm groupResourceProcessForm = (GroupResourceProcessForm) processForm;
            return groupResourceProcessForm.getGroupInfo();
        } else if (processForm instanceof UpdateGroupProcessForm) {
            UpdateGroupProcessForm updateGroupProcessForm = (UpdateGroupProcessForm) processForm;
            return updateGroupProcessForm.getGroupInfo();
        } else {
            log.error("Illegal ProcessForm {} to get inlong group info", processForm.getFormName());
            throw new RuntimeException(String.format("Unsupport ProcessForm {} in CreateSortConfigListener",
                    processForm.getFormName()));
        }
    }

    @Override
    public boolean async() {
        return false;
    }
}