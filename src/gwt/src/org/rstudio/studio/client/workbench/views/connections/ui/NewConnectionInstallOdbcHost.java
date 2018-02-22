/*
 * NewConnectionInstallOdbcHost.java
 *
 * Copyright (C) 2009-18 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.workbench.views.connections.ui;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import org.rstudio.core.client.Debug;
import org.rstudio.core.client.HandlerRegistrations;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.MessageDialog;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.core.client.widget.ThemedButton;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.common.DelayedProgressRequestCallback;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.console.ConsoleOutputEvent;
import org.rstudio.studio.client.common.console.ConsoleProcess;
import org.rstudio.studio.client.common.console.ProcessExitEvent;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.server.VoidServerRequestCallback;
import org.rstudio.studio.client.workbench.views.connections.model.ConnectionOptions;
import org.rstudio.studio.client.workbench.views.connections.model.ConnectionsServerOperations;
import org.rstudio.studio.client.workbench.views.connections.model.NewConnectionContext.NewConnectionInfo;
import org.rstudio.studio.client.workbench.views.vcs.common.ConsoleProgressWidget;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class NewConnectionInstallOdbcHost extends Composite
                                          implements ConsoleOutputEvent.Handler, 
                                                     ProcessExitEvent.Handler
{
   interface Binder extends UiBinder<Widget, NewConnectionInstallOdbcHost>
   {}
   
   @Inject
   private void initialize(ConnectionsServerOperations server,
                           GlobalDisplay globalDisplay)
   {
      server_ = server;
      globalDisplay_ = globalDisplay;
   }
   
   public NewConnectionInstallOdbcHost()
   {
      RStudioGinjector.INSTANCE.injectMembers(this);

      mainWidget_ = GWT.<Binder>create(Binder.class).createAndBindUi(this);
 
      initWidget(createWidget());

      consoleProgressWidget_.setReadOnly(true);
   }

   public void onDeactivate(Operation operation)
   {
      consoleProgressWidget_.clearOutput();
      if (consoleProcess_ != null)
         consoleProcess_.reap(new VoidServerRequestCallback() {
            @Override
            public void onSuccess()
            {
               operation.execute();
            }
            
            @Override
            public void onFailure()
            {
               operation.execute();
            }
         });
   }
   
   private Widget createWidget()
   {
      return mainWidget_;
   }

   public void writeOutput(String output)
   {
      consoleProgressWidget_.consoleWriteOutput(output);
   }

   @Override
   public void onConsoleOutput(ConsoleOutputEvent event)
   {
      writeOutput(event.getOutput());
   }

   @Override
   public void onProcessExit(ProcessExitEvent event)
   {    
      unregisterHandlers();
      
      if (consoleProcess_ != null)
         consoleProcess_.reap(new VoidServerRequestCallback());
   }

   protected void addHandlerRegistration(HandlerRegistration reg)
   {
      registrations_.add(reg);
   }
   
   protected void unregisterHandlers()
   {
      registrations_.removeHandler();
   }

   public void attachToProcess(final ConsoleProcess consoleProcess)
   {
      consoleProcess_ = consoleProcess;
      
      addHandlerRegistration(consoleProcess.addConsoleOutputHandler(this));
      addHandlerRegistration(consoleProcess.addProcessExitHandler(this));

      consoleProcess.start(new SimpleRequestCallback<Void>()
      {
         @Override
         public void onError(ServerError error)
         {
            Debug.logError(error);
            globalDisplay_.showErrorMessage(
               "Installation failed",
               error.getUserMessage());
            
            unregisterHandlers();
         }
      });
   }

   private void installOdbcDriver()
   {
      server_.installOdbcDriver(
         info_.getName(), 
         new ServerRequestCallback<ConsoleProcess>() {
   
            @Override
            public void onResponseReceived(ConsoleProcess proc)
            {
               attachToProcess(proc);
               proc.addProcessExitHandler(
                  new ProcessExitEvent.Handler()
                  {
                     @Override
                     public void onProcessExit(ProcessExitEvent event)
                     {
                     }
                  }); 
            } 

            @Override
            public void onError(ServerError error)
            {
               Debug.logError(error);
               globalDisplay_.showErrorMessage(
                  "Installation failed",
                  error.getUserMessage());
            }
         });
   }

   public void initializeInfo(NewConnectionInfo info)
   {
      info_ = info;
      installOdbcDriver();
   }

   public ConnectionOptions collectInput()
   {
      return ConnectionOptions.create();
   }
   
   public interface Styles extends CssResource
   {
   }
   
   private ConnectionsServerOperations server_;
   private GlobalDisplay globalDisplay_;

   @UiField
   ConsoleProgressWidget consoleProgressWidget_;

   @UiField
   Label label_;

   private NewConnectionInfo info_;

   private Widget mainWidget_;

   private HandlerRegistrations registrations_ = new HandlerRegistrations();

   private ConsoleProcess consoleProcess_;
}