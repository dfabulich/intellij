/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.google.common.base.Strings;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.java.run.producers.JUnitConfigurationUtil;
import com.google.idea.blaze.java.run.producers.ProducerUtils;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * Producer for run configurations related to Android test classes in Blaze.
 *
 * <p>This class is based on {@link
 * org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer}.
 */
public class BlazeAndroidTestClassRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeAndroidTestClassRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      @NotNull BlazeCommandRunConfiguration configuration,
      @NotNull ConfigurationContext context,
      @NotNull Ref<PsiElement> sourceElement) {

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }

    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    PsiClass testClass = JUnitUtil.getTestClass(location);
    if (testClass == null) {
      return false;
    }
    sourceElement.set(testClass);

    TargetIdeInfo target = RunUtil.targetForTestClass(context.getProject(), testClass, null);
    if (target == null) {
      return false;
    }
    if (!target.kindIsOneOf(Kind.ANDROID_TEST)) {
      return false;
    }
    configuration.setTarget(target.key.label);
    BlazeAndroidTestRunConfigurationState configState =
        configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (configState == null) {
      return false;
    }
    configState.setTestingType(AndroidTestRunConfiguration.TEST_CLASS);
    configState.setClassName(testClass.getQualifiedName());
    configuration.setGeneratedName();

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      @NotNull BlazeCommandRunConfiguration configuration, @NotNull ConfigurationContext context) {

    final Location contextLocation = context.getLocation();
    assert contextLocation != null;
    final Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }

    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return false;
    }

    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(contextLocation);
    if (methodLocation != null) {
      return false;
    }

    PsiClass testClass = JUnitUtil.getTestClass(location);
    if (testClass == null) {
      return false;
    }

    return checkIfAttributesAreTheSame(configuration, testClass);
  }

  private static boolean checkIfAttributesAreTheSame(
      BlazeCommandRunConfiguration configuration, PsiClass testClass) {
    BlazeAndroidTestRunConfigurationState configState =
        configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState.class);
    if (configState == null) {
      return false;
    }
    if (Strings.isNullOrEmpty(configState.getClassName())) {
      return false;
    }

    return configState.getTestingType() == AndroidTestRunConfiguration.TEST_CLASS
        && configState.getClassName().equals(testClass.getQualifiedName());
  }
}
