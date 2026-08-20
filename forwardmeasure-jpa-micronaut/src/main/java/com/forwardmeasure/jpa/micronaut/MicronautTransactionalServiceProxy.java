package com.forwardmeasure.jpa.micronaut;

import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import jakarta.transaction.Transactional;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import org.hibernate.Session;

/**
 * Applies Jakarta Transaction semantics to precompiled portable services.
 *
 * <p>Micronaut normally generates transactional AOP at compilation time. The shared service
 * implementations are deliberately compiled without a Micronaut dependency, so the adapter supplies
 * one interface proxy instead of duplicating every domain service in the Micronaut module.
 */
final class MicronautTransactionalServiceProxy {

  private MicronautTransactionalServiceProxy() {}

  static <S> S create(Class<S> serviceType, S target, TransactionOperations<Session> transactions) {
    Objects.requireNonNull(serviceType, "serviceType");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(transactions, "transactions");
    if (!serviceType.isInterface()) {
      throw new IllegalArgumentException(
          "Transactional service type must be an interface: " + serviceType.getName());
    }

    Object proxy =
        Proxy.newProxyInstance(
            serviceType.getClassLoader(),
            new Class<?>[] {serviceType},
            (instance, method, arguments) ->
                method.getDeclaringClass() == Object.class
                    ? invokeObjectMethod(instance, serviceType, method, arguments)
                    : invoke(target, method, arguments, transactions));
    return serviceType.cast(proxy);
  }

  private static Object invokeObjectMethod(
      Object proxy, Class<?> serviceType, Method method, Object[] arguments) {
    return switch (method.getName()) {
      case "equals" -> proxy == arguments[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "MicronautTransactionalServiceProxy[" + serviceType.getName() + "]";
      default -> throw new IllegalStateException("Unsupported Object method: " + method);
    };
  }

  @SuppressWarnings("unchecked")
  private static Object invoke(
      Object target,
      Method serviceMethod,
      Object[] arguments,
      TransactionOperations<Session> transactions)
      throws Throwable {
    Method targetMethod =
        target.getClass().getMethod(serviceMethod.getName(), serviceMethod.getParameterTypes());
    Transactional transactional = targetMethod.getAnnotation(Transactional.class);
    if (transactional == null) {
      transactional = target.getClass().getAnnotation(Transactional.class);
    }
    if (transactional == null) {
      return invokeTarget(target, targetMethod, arguments);
    }

    DefaultTransactionDefinition definition =
        new DefaultTransactionDefinition(
            TransactionDefinition.Propagation.valueOf(transactional.value().name()));
    definition.setRollbackOn(Arrays.asList(transactional.rollbackOn()));
    definition.setDontRollbackOn(Arrays.asList(transactional.dontRollbackOn()));
    return transactions.execute(
        definition, status -> invokeTarget(target, targetMethod, arguments));
  }

  private static Object invokeTarget(Object target, Method method, Object[] arguments)
      throws Exception {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Error error) {
        throw error;
      }
      if (cause instanceof Exception exception) {
        throw exception;
      }
      throw new IllegalStateException(cause);
    }
  }
}
