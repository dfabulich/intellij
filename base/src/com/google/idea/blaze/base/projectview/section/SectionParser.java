/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview.section;

import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import javax.annotation.Nullable;

/** Parses a section. */
public abstract class SectionParser {

  public static final int INDENT = 2;

  /** The type of item(s) in this section */
  public enum ItemType {
    FileSystemItem, // files, directories, globs
    Label, // a blaze label
    Other, // anything else
  }

  public String getName() {
    return getSectionKey().getName();
  }

  public abstract SectionKey<?, ?> getSectionKey();

  @Nullable
  public abstract Section<?> parse(ProjectViewParser parser, ParseContext parseContext);

  public abstract void print(StringBuilder sb, Section<?> section);

  public boolean isDeprecated() {
    return false;
  }

  /** Allows the section to add a default value. Used during the wizard. */
  public ProjectView addProjectViewDefaultValue(ProjectView projectView) {
    return projectView;
  }

  /** The type of item(s) in this section. */
  public abstract ItemType getItemType();
}
