/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime;

import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.core.api.InternalEvent;
import org.mule.runtime.extension.api.runtime.operation.ExecutionContext;

/**
 * A specialization of {@link ExecutionContext} which contains an {@link InternalEvent}
 *
 * @param <M> the generic type of of the model which represents the component being executed
 * @since 4.0
 */
public interface EventedExecutionContext<M extends ComponentModel> extends ExecutionContext<M> {

  /**
   * Returns the {@link InternalEvent} on which an operation is to be executed
   */
  InternalEvent getEvent();

  /**
   * Changes the {@link InternalEvent} on which an operation is to be executed. Not null.
   *
   * @param updated the event to use
   */
  void changeEvent(InternalEvent updated);

}
