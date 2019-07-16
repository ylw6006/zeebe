/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model;

public enum BpmnStep {

  // new steps are just lifecycle state
  ELEMENT_ACTIVATING,
  ELEMENT_ACTIVATED,
  EVENT_OCCURRED,
  ELEMENT_COMPLETING,
  ELEMENT_COMPLETED,
  ELEMENT_TERMINATING,
  ELEMENT_TERMINATED,

  ACTIVITY_ELEMENT_ACTIVATING,
  ACTIVITY_ELEMENT_ACTIVATED,
  ACTIVITY_EVENT_OCCURRED,
  ACTIVITY_ELEMENT_COMPLETING,
  ACTIVITY_ELEMENT_TERMINATING,
  ACTIVITY_ELEMENT_TERMINATED,

  CONTAINER_ELEMENT_ACTIVATED,
  CONTAINER_ELEMENT_TERMINATING,

  EVENT_BASED_GATEWAY_ELEMENT_ACTIVATING,
  EVENT_BASED_GATEWAY_ELEMENT_ACTIVATED,
  EVENT_BASED_GATEWAY_EVENT_OCCURRED,
  EVENT_BASED_GATEWAY_ELEMENT_COMPLETING,
  EVENT_BASED_GATEWAY_ELEMENT_COMPLETED,
  EVENT_BASED_GATEWAY_ELEMENT_TERMINATING,

  EXCLUSIVE_GATEWAY_ELEMENT_ACTIVATING,
  EXCLUSIVE_GATEWAY_ELEMENT_COMPLETED,

  FLOWOUT_ELEMENT_COMPLETED,

  INTERMEDIATE_CATCH_EVENT_ELEMENT_ACTIVATING,
  INTERMEDIATE_CATCH_EVENT_ELEMENT_ACTIVATED,
  INTERMEDIATE_CATCH_EVENT_EVENT_OCCURRED,
  INTERMEDIATE_CATCH_EVENT_ELEMENT_COMPLETING,
  INTERMEDIATE_CATCH_EVENT_ELEMENT_TERMINATING,

  PARALLEL_MERGE_SEQUENCE_FLOW_TAKEN,

  RECEIVE_TASK_EVENT_OCCURRED,

  SEQUENCE_FLOW_TAKEN,

  SERVICE_TASK_ELEMENT_ACTIVATED,
  SERVICE_TASK_ELEMENT_TERMINATING,

  START_EVENT_EVENT_OCCURRED,
}
