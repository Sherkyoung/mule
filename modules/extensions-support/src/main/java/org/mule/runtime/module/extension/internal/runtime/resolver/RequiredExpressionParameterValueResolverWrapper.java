/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.resolver;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.MuleContext;

public class RequiredExpressionParameterValueResolverWrapper<T> extends AbstractValueResolverWrapper<T> {

  private final String expression;
  public RequiredExpressionParameterValueResolverWrapper(ValueResolver<T> delegate, MuleContext muleContext) {
    super(delegate, muleContext);
  }

  @Override
  public T resolve(ValueResolvingContext context) throws MuleException {
    T value = super.resolve(context);
    if (value == null) {
      throw new IllegalArgumentException("");
    }

    return value;
  }
}
