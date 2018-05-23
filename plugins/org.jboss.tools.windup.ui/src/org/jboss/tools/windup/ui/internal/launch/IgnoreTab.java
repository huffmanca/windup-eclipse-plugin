/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.windup.ui.internal.launch;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.jboss.tools.windup.model.domain.ModelService;
import org.jboss.tools.windup.runtime.options.IOptionKeys;
import org.jboss.tools.windup.ui.WindupUIPlugin;
import org.jboss.tools.windup.ui.internal.Messages;
import org.jboss.tools.windup.windup.ConfigurationElement;
import org.jboss.tools.windup.windup.IgnorePattern;
import org.jboss.tools.windup.windup.Pair;
import org.jboss.tools.windup.windup.WindupFactory;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class IgnoreTab extends AbstractLaunchConfigurationTab {

	private Table ignoreTable;
	private Button addButton;
	private Button removeButton;
	
	private ModelService modelService;
	private ConfigurationElement configuration;
	
	public IgnoreTab(ModelService modelService) {
		this.modelService = modelService;
	}
	
	@Override
	public void createControl(Composite ancestor) {
		
		Composite parent = new Composite(ancestor, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 5;
		layout.marginHeight = 5;
		layout.numColumns = 2;
		parent.setLayout(layout);
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		parent.setLayoutData(data);

		Label l1 = new Label(parent, SWT.NULL);
		l1.setText(Messages.ignoreDescription);
		data = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		data.horizontalSpan = 2;
		l1.setLayoutData(data);

		ignoreTable = new Table(parent, SWT.CHECK | SWT.BORDER | SWT.MULTI);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.heightHint = 300;
		ignoreTable.setLayoutData(gd);
		ignoreTable.addListener(SWT.Selection, e -> handleSelection());

		Composite buttons = new Composite(parent, SWT.NULL);
		buttons.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		buttons.setLayout(layout);

		addButton = new Button(buttons, SWT.PUSH);
		addButton.setText(Messages.ignorePatternAdd);
		addButton.addListener(SWT.Selection, e -> addIgnore());
		GridDataFactory.fillDefaults().grab(true, false).applyTo(addButton);

		removeButton = new Button(buttons, SWT.PUSH);
		removeButton.setText(Messages.ignorePatternRemove);
		removeButton.setEnabled(false);
		removeButton.addListener(SWT.Selection, e -> removeIgnore());
		GridDataFactory.fillDefaults().grab(true, false).applyTo(removeButton);
		
		Dialog.applyDialogFont(ancestor);
		
		super.setControl(parent);
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy launchConfig) {
		int count = ignoreTable.getItemCount();
		String[] patterns = new String[count];
		boolean[] enabled = new boolean[count];
		TableItem[] items = ignoreTable.getItems();
		for (int i = 0; i < count; i++) {
			patterns[i] = items[i].getText();
			enabled[i] = items[i].getChecked();
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy launchConfig) {
		try {
			syncIgnoreWithConfig();
		} catch (IOException e) {
			WindupUIPlugin.log(e);
			MessageDialog.openConfirm(Display.getDefault().getActiveShell(),
    				"Ignore file error", 
    				"Error while reading ignore file.");
		}
		refresh();
	}

	private void refresh() {
		if (ignoreTable != null) {
			ignoreTable.removeAll();
			for (IgnorePattern ignore : configuration.getIgnorePatterns()) {
				TableItem item = new TableItem(ignoreTable, SWT.NONE);
				item.setText(TextProcessor.process(ignore.getPattern(), ".*")); //$NON-NLS-1$
				item.setChecked(ignore.isEnabled());
			}
		}
	}

	private void addIgnore() {
		InputDialog dialog = new InputDialog(getShell(), Messages.ignorePatternShort, Messages.ignorePatternLong, null, null);
		dialog.open();
		if (dialog.getReturnCode() != Window.OK) return;
		String pattern = dialog.getValue();
		if (pattern.equals("")) return; //$NON-NLS-1$
		TableItem[] items = ignoreTable.getItems();
		for (int i = 0; i < items.length; i++) {
			if (items[i].getText().equals(pattern)) {
				MessageDialog.openWarning(getShell(), Messages.ignorePatternExistsShort, Messages.ignorePatternExistsLong);
				return;
			}
		}
		
		IgnorePattern ignore = WindupFactory.eINSTANCE.createIgnorePattern();
		ignore.setPattern(pattern);
		ignore.setEnabled(true);
		configuration.getIgnorePatterns().add(ignore);
		
		TableItem item = new TableItem(ignoreTable, SWT.NONE);
		item.setText(TextProcessor.process(pattern, ".*")); //$NON-NLS-1$
		item.setChecked(true);
		item.setData(IgnorePattern.class.getName(), ignore);
	}

	private void removeIgnore() {
		for (TableItem item : ignoreTable.getSelection()) {
			IgnorePattern ignore = (IgnorePattern)item.getData(IgnorePattern.class.getName());
			configuration.getIgnorePatterns().remove(ignore);
		}
		int[] selection = ignoreTable.getSelectionIndices();
		ignoreTable.remove(selection);
	}
	
	private void handleSelection() {
		if (ignoreTable.getSelectionCount() > 0) {
			removeButton.setEnabled(true);
		} else {
			removeButton.setEnabled(false);
		}
	}
	
	@Override
	public void initializeFrom(ILaunchConfiguration launchConfig) {
		initializeConfiguration(launchConfig);
	}
	
	private void initializeConfiguration(ILaunchConfiguration launchConfig) {
		this.configuration = modelService.findConfiguration(launchConfig.getName());
		if (configuration == null) {
			this.configuration = LaunchUtils.createConfiguration(launchConfig.getName(), modelService);
		}
		refresh();
	}

	@Override
	public String getName() {
		return Messages.ignorePatternsLabel;
	}
	
	@Override
	public Image getImage() {
		return WindupUIPlugin.getDefault().getImageRegistry().get(WindupUIPlugin.IMG_PATTERN);
	}
	
	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		return true;
	}
	
	private void syncIgnoreWithConfig() throws IOException {
		Optional<Pair> optional = configuration.getOptions().stream().filter(option -> { 
			return Objects.equal(option.getKey(), IOptionKeys.userIgnorePathOption);
		}).findFirst();
		if (optional.isPresent()) {
			doSyncIgnoreWithConfig(configuration, new File(optional.get().getValue()));
			
		} else {
			doSyncIgnoreWithConfig(configuration, modelService.getDefaultUserIgnore());
		}
	}
	
	private void doSyncIgnoreWithConfig(ConfigurationElement configuration, 
			File ignoreFile) throws IOException {
		Map<String, IgnorePattern> defaultPatterns = generateDefaultPatterns();
		for (String line : FileUtils.readLines(ignoreFile)) {
			if (defaultPatterns.containsKey(line))  {
				defaultPatterns.remove(line);
			}
			IgnorePattern pattern = WindupFactory.eINSTANCE.createIgnorePattern();
			pattern.setPattern(line);
			pattern.setEnabled(true);
			configuration.getIgnorePatterns().add(pattern);
		}
	}
	
	private Map<String, IgnorePattern> generateDefaultPatterns() {
		return Maps.newHashMap();
	}
}
