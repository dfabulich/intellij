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
package com.google.idea.blaze.android.run.binary.instantrun;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.InstantRunBuildAnalyzer;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.UpdateSessionTasksProvider;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryApplicationLaunchTaskProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryConsoleProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.binary.UserIdHelper;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/** Run context for InstantRun. */
public class BlazeAndroidBinaryInstantRunContext implements BlazeAndroidRunContext {

  private final Project project;
  private final AndroidFacet facet;
  private final RunConfiguration runConfiguration;
  private final ExecutionEnvironment env;
  private final BlazeAndroidBinaryRunConfigurationState configState;

  private final BlazeAndroidBinaryConsoleProvider consoleProvider;
  private final BlazeApkBuildStepInstantRun buildStep;

  public BlazeAndroidBinaryInstantRunContext(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      Label label,
      ImmutableList<String> buildFlags) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.configState = configState;
    this.consoleProvider = new BlazeAndroidBinaryConsoleProvider(project);
    this.buildStep = new BlazeApkBuildStepInstantRun(project, env, label, buildFlags);
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeInstantRunDeviceSelector();
  }

  @Override
  public void augmentEnvironment(ExecutionEnvironment env) {
    InstantRunUtils.setInstantRunEnabled(env, true);
  }

  @Override
  public void augmentLaunchOptions(@NotNull LaunchOptions.Builder options) {
    options.setDeploy(true).setOpenLogcatAutomatically(true);
  }

  @NotNull
  @Override
  public ConsoleProvider getConsoleProvider() {
    return consoleProvider;
  }

  @Override
  public ApplicationIdProvider getApplicationIdProvider() throws ExecutionException {
    return Futures.get(buildStep.getApplicationIdProvider(), ExecutionException.class);
  }

  @Override
  public BlazeApkBuildStep getBuildStep() {
    return buildStep;
  }

  @Override
  public LaunchTasksProvider getLaunchTasksProvider(
      LaunchOptions.Builder launchOptionsBuilder,
      boolean isDebug,
      BlazeAndroidRunConfigurationDebuggerManager debuggerManager)
      throws ExecutionException {
    InstantRunBuildAnalyzer analyzer =
        Futures.get(buildStep.getInstantRunBuildAnalyzer(), ExecutionException.class);

    if (analyzer.canReuseProcessHandler()) {
      return new UpdateSessionTasksProvider(analyzer);
    }
    return new BlazeAndroidLaunchTasksProvider(
        project,
        this,
        getApplicationIdProvider(),
        launchOptionsBuilder,
        isDebug,
        true,
        debuggerManager);
  }

  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    InstantRunBuildAnalyzer analyzer =
        Futures.get(buildStep.getInstantRunBuildAnalyzer(), ExecutionException.class);
    return ImmutableList.<LaunchTask>builder()
        .addAll(analyzer.getDeployTasks(launchOptions))
        .add(analyzer.getNotificationTask())
        .build();
  }

  @Nullable
  @Override
  public LaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions,
      @Nullable Integer userId,
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      ProcessHandlerLaunchStatus processHandlerLaunchStatus)
      throws ExecutionException {
    BlazeApkBuildStepInstantRun.BuildResult buildResult =
        Futures.get(buildStep.getBuildResult(), ExecutionException.class);

    final StartActivityFlagsProvider startActivityFlagsProvider =
        new DefaultStartActivityFlagsProvider(
            androidDebugger,
            androidDebuggerState,
            project,
            launchOptions.isDebug(),
            UserIdHelper.getFlagsFromUserId(userId));

    ApplicationIdProvider applicationIdProvider = getApplicationIdProvider();
    return BlazeAndroidBinaryApplicationLaunchTaskProvider.getApplicationLaunchTask(
        project,
        applicationIdProvider,
        buildResult.mergedManifestFile,
        configState,
        startActivityFlagsProvider,
        processHandlerLaunchStatus);
  }

  @Nullable
  @Override
  public DebugConnectorTask getDebuggerTask(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      Set<String> packageIds)
      throws ExecutionException {
    //noinspection unchecked
    return androidDebugger.getConnectDebuggerTask(
        env, null, packageIds, facet, androidDebuggerState, runConfiguration.getType().getId());
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device, ConsolePrinter consolePrinter)
      throws ExecutionException {
    return UserIdHelper.getUserIdFromConfigurationState(device, consolePrinter, configState);
  }
}
