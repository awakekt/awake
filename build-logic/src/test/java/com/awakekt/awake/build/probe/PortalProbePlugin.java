/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.probe;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** The plugin `PluginPortalOutageTest`'s local repository serves: Java, so it needs no runtime of its own. */
public class PortalProbePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("probe", task -> task.doLast(t -> System.out.println("PROBE APPLIED")));
    }
}
