/*
 * Copyright (c) 2006 JMockit developers
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.integration.junit4;

import java.lang.reflect.*;
import javax.annotation.*;

import org.junit.*;
import org.junit.runners.model.*;

import mockit.integration.*;
import mockit.internal.expectations.*;
import mockit.internal.expectations.transformation.ArgumentCapturing;
import mockit.internal.faking.*;
import mockit.internal.state.*;
import static mockit.internal.util.StackTrace.*;

final class JUnit4TestRunnerDecorator extends TestRunnerDecorator
{
   @Nullable
   Object invokeExplosively(@Nonnull FakeInvocation invocation, @Nullable Object target, Object... params) throws Throwable {
      FrameworkMethod it = invocation.getInvokedInstance();
      assert it != null;

      // A @BeforeClass/@AfterClass method:
      if (target == null) {
         try {
            return executeClassMethod(invocation, params);
         }
         catch (Throwable t) {
            filterStackTrace(t);
            throw t;
         }
      }

      handleMockingOutsideTestMethods(target);

      // A @Before/@After method:
      if (it.getAnnotation(Test.class) == null) {
         if (shouldPrepareForNextTest && it.getAnnotation(Before.class) != null) {
            prepareToExecuteSetupMethod(target);
         }

         TestRun.setRunningIndividualTest(target);

         try {
            invocation.prepareToProceedFromNonRecursiveMock();
            return it.invokeExplosively(target, params);
         }
         catch (Throwable t) {
            //noinspection ThrowableNotThrown
            RecordAndReplayExecution.endCurrentReplayIfAny();
            filterStackTrace(t);
            throw t;
         }
         finally {
            if (it.getAnnotation(After.class) != null) {
               shouldPrepareForNextTest = true;
            }
         }
      }

      if (shouldPrepareForNextTest) {
         prepareForNextTest();
      }

      shouldPrepareForNextTest = true;

      try {
         executeTestMethod(invocation, target, params);
         return null; // it's a test method, therefore has void return type
      }
      catch (Throwable t) {
         filterStackTrace(t);
         throw t;
      }
      finally {
         TestRun.finishCurrentTestExecution();
      }
   }

   @Nullable
   private static Object executeClassMethod(@Nonnull FakeInvocation inv, @Nonnull Object[] params) throws Throwable {
      FrameworkMethod method = inv.getInvokedInstance();
      assert method != null;
      handleMockingOutsideTests(method);

      TestRun.clearCurrentTestInstance();
      inv.prepareToProceedFromNonRecursiveMock();

      return method.invokeExplosively(null, params);
   }

   private void prepareToExecuteSetupMethod(@Nonnull Object target) {
      discardTestLevelMockedTypes();
      prepareForNextTest();
      shouldPrepareForNextTest = false;
      createInstancesForTestedFieldsBeforeSetup(target);
   }

   private static void handleMockingOutsideTests(@Nonnull FrameworkMethod it) {
      Class<?> testClass = it.getMethod().getDeclaringClass();

      TestRun.enterNoMockingZone();

      try {
         Class<?> currentTestClass = TestRun.getCurrentTestClass();

         if (currentTestClass != null && testClass.isAssignableFrom(currentTestClass) && it.getAnnotation(AfterClass.class) != null) {
            cleanUpMocksFromPreviousTest();
         }

         if (it.getAnnotation(BeforeClass.class) != null) {
            updateTestClassState(null, testClass);
         }
      }
      finally {
         TestRun.exitNoMockingZone();
      }
   }

   private static void handleMockingOutsideTestMethods(@Nonnull Object target) {
      Class<?> testClass = target.getClass();

      TestRun.enterNoMockingZone();

      try {
         updateTestClassState(target, testClass);
      }
      finally {
         TestRun.exitNoMockingZone();
      }
   }

   private static void executeTestMethod(
      @Nonnull FakeInvocation invocation, @Nonnull Object testInstance, @Nullable Object... parameters
   ) throws Throwable {
      SavePoint savePoint = new SavePoint();

      TestRun.setRunningIndividualTest(testInstance);

      FrameworkMethod it = invocation.getInvokedInstance();
      assert it != null;
      Method testMethod = it.getMethod();
      Throwable testFailure = null;
      boolean testFailureExpected = false;

      ArgumentCapturing.clear();

      try {
         createInstancesForTestedFieldsFromBaseClasses(testInstance);
         Object[] annotatedParameters = createInstancesForAnnotatedParameters(testInstance, testMethod, parameters);
         createInstancesForTestedFields(testInstance);

         invocation.prepareToProceedFromNonRecursiveMock();

         Object[] params = annotatedParameters == null ? parameters : annotatedParameters;
         it.invokeExplosively(testInstance, params);
      }
      catch (Throwable thrownByTest) {
         testFailure = thrownByTest;
         Class<?> expectedType = testMethod.getAnnotation(Test.class).expected();
         testFailureExpected = expectedType.isAssignableFrom(thrownByTest.getClass());
      }
      finally {
         concludeTestMethodExecution(savePoint, testFailure, testFailureExpected);
      }
   }
}
