/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Google Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * See LICENSE for the full license text, also available.
 *******************************************************************************/
package org.spearce.egit.core.op;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.team.core.RepositoryProvider;
import org.spearce.egit.core.Activator;
import org.spearce.egit.core.CoreText;
import org.spearce.egit.core.GitProvider;
import org.spearce.egit.core.project.GitProjectData;
import org.spearce.egit.core.project.RepositoryFinder;
import org.spearce.egit.core.project.RepositoryMapping;
import org.spearce.jgit.lib.Repository;

/**
 * Connects Eclipse to an existing Git repository, or creates a new one.
 */
public class ConnectProviderOperation implements IWorkspaceRunnable {
	private final IProject project;

	private final File newGitDir;

	/**
	 * Create a new connection operation to execute within the workspace.
	 * 
	 * @param proj
	 *            the project to connect to the Git team provider.
	 * @param newdir
	 *            git repository to create if the user requested a new
	 *            repository be constructed for this project; null to scan for
	 *            an existing repository and connect to that.
	 */
	public ConnectProviderOperation(final IProject proj, final File newdir) {
		project = proj;
		newGitDir = newdir;
	}

	public void run(IProgressMonitor m) throws CoreException {
		if (m == null) {
			m = new NullProgressMonitor();
		}

		m.beginTask(CoreText.ConnectProviderOperation_connecting, 100);
		try {
			final Collection<RepositoryMapping> repos = new ArrayList<RepositoryMapping>();

			if (newGitDir != null) {
				try {
					final Repository db;

					m.subTask(CoreText.ConnectProviderOperation_creating);
					Activator.trace("Creating repository " + newGitDir);

					db = new Repository(newGitDir);
					db.create();
					repos.add(new RepositoryMapping(project, db.getDirectory()));
					db.close();

					// If we don't refresh the project directory right
					// now we won't later know that a .git directory
					// exists within it and we won't mark the .git
					// directory as a team-private member. Failure
					// to do so might allow someone to delete
					// the .git directory without us stopping them.
					//
					project.refreshLocal(IResource.DEPTH_ONE,
							new SubProgressMonitor(m, 10));

					m.worked(10);
				} catch (Throwable err) {
					throw Activator.error(
							CoreText.ConnectProviderOperation_creating, err);
				}
			} else {
				Activator.trace("Finding existing repositories.");
				repos.addAll(new RepositoryFinder(project)
						.find(new SubProgressMonitor(m, 20)));
			}

			m.subTask(CoreText.ConnectProviderOperation_recordingMapping);
			GitProjectData projectData = new GitProjectData(project);
			projectData.setRepositoryMappings(repos);
			projectData.store();

			try {
				RepositoryProvider.map(project, GitProvider.class.getName());
			} catch (CoreException ce) {
				GitProjectData.delete(project);
				throw ce;
			} catch (RuntimeException ce) {
				GitProjectData.delete(project);
				throw ce;
			}

			projectData = GitProjectData.get(project);
			project.refreshLocal(IResource.DEPTH_INFINITE,
					new SubProgressMonitor(m, 50));

			m.subTask(CoreText.ConnectProviderOperation_updatingCache);
		} finally {
			m.done();
		}
	}
}
