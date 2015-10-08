/*******************************************************************************
 * Copyright (c) 2015 Horacio Hoyos
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Horacio Hoyos - Initial API and implementation
 *******************************************************************************/
package arcanebeans.eclipsegraphviz.debugview;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.contexts.IDebugContextListener;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.abstratt.graphviz.ui.DOTGraphicalContentProvider;
import com.abstratt.imageviewer.GraphicalView;

/**
 * A class that allows classes that provide a toDOT() method to be rendered
 * into the eclipsegraphiz view during debuging.
 * @see <a href="https://github.com/abstratt/eclipsegraphviz">Eclipse Graphviz</a>
 */
public class DebugListener extends Plugin implements IDebugContextListener, ISelectionListener, IPartListener2 {

	/** The sel listener ready. */
	private boolean selListenerReady;

	/** The graphviz view part id. */
	private final String GRAPHVIZ_VIEW_PART_ID = "com.abstratt.imageviewer.GraphicalView";

	/** The jdi object to dot. */
	public static String JDI_OBJECT_TO_DOT = "toDOT";

	/** The debug view part id. */
	private final String DEBUG_VIEW_PART_ID = "org.eclipse.debug.ui.VariableView";

	/** The details. */
	public final QualifiedName details =  new QualifiedName("com.github.eclipsegraphviz.debugview", "dotString");

	private final ILog logger;

	private IJavaObject lastObject;
	private String lastDOTString = "";

	/**
	 * Instantiates a new debug listener.
	 * @param logger
	 */
	public DebugListener(ILog logger) {
		super();
		this.logger = logger;
		installViewListener();
		selListenerReady = false;
		// The view might be already loaded
		installSelectionListener();

	}

	/**
	 * Keep track of the Graphviz view state. We only listen to the Variable view
	 * events if the Graphviz view is opened.
	 */
	private void installViewListener() {
		IWorkbenchPage p = getActivePage();
		if (p != null)
			p.addPartListener(this);

	}

	/**
	 * Removes the view listener.
	 */
	private void removeViewListener() {
		IWorkbenchPage p = getActivePage();
		if (p != null)
			p.removePartListener(this);
	}

	/**
	 * Install selection listener.
	 */
	private void installSelectionListener() {
		if (!selListenerReady) {
			ISelectionService ss = getSelectionService();
			if (ss != null) {
				ss.addSelectionListener(this);
				selListenerReady = true;
				logger.log(new Status(Status.INFO, Activator.PLUGIN_ID, "installSelectionListener."));
				IWorkbenchPage p = getActivePage();
				if (p != null) {
					IViewPart view = p.findView(DEBUG_VIEW_PART_ID);
					if (view != null) {
						DebugUITools.addPartDebugContextListener(view.getSite(), this);
					}
				}
			}
		}
	}

	/**
	 * Removes the selection listener.
	 */
	private void removeSelectionListener() {
		ISelectionService ss = getSelectionService();
		if (ss != null) {
			selListenerReady = false;
			ss.removeSelectionListener(this);
			logger.log(new Status(Status.INFO, Activator.PLUGIN_ID, "removeSelectionListener."));
			IWorkbenchPage p = getActivePage();
			if (p != null) {
				IViewPart view = p.findView(DEBUG_VIEW_PART_ID);
				if (view != null) {
					DebugUITools.removePartDebugContextListener(view.getSite(), this);
				}
			}
		}
	}


	/**
	 * Render dot string.
	 *
	 * @param input the input
	 */
	private void renderDotString(IJavaObject input) {

		//  If the view is not available, then don't do anything
		GraphicalView graphvizView = getGraphvizView();
		if (graphvizView != null) {
			final Job job = new Job("Graphviz Debug Send Message") {
		        protected IStatus run(IProgressMonitor monitor) {
		        	logger.log(new Status(Status.INFO, Activator.PLUGIN_ID, "Attempt execute toDOT method."));
		        	IJavaThread ijt = null;
		        	IJavaDebugTarget target = (IJavaDebugTarget) input.getDebugTarget();
	    			try {
	    				IThread[] threads = target.getThreads();
	    				for (int i = 0; i < threads.length; i++) {
	    					if (threads[i].isSuspended()) {
	    						ijt = (IJavaThread)threads[i];
	    						break;
	    					}
	    				}
	    			} catch (DebugException e) {
	    				// Failure communicating with the debug target, we can't do anything
						return Status.CANCEL_STATUS;
	    			}
	    			if (ijt != null) {
			    		IJavaValue toStringValue = null;
						try {
							toStringValue = input.sendMessage(JDI_OBJECT_TO_DOT, "()Ljava/lang/String;", null, ijt, false);
						} catch (DebugException e) {
							// If the error comes form the thread try later
							if (e.getStatus().getCode() == IJavaThread.ERR_NESTED_METHOD_INVOCATION
									|| e.getStatus().getCode() == IJavaThread.ERR_INCOMPATIBLE_THREAD_STATE
									|| e.getStatus().getCode() == IJavaThread.ERR_THREAD_NOT_SUSPENDED) {
								schedule(100);
							}
						}
						if (toStringValue != null) {
							String newValue;
							try {
								newValue = toStringValue.getValueString();
							} catch (DebugException e) {
								// IF we can not pass the result of the method, we can't do anything
								return Status.CANCEL_STATUS;
							}
							// Only update if there is a change in the representation
							if (!lastDOTString.equals(newValue)) {
								setProperty(details, newValue);
								lastObject = input;
								lastDOTString = newValue;
								return Status.OK_STATUS;
							}
						}
	    			}
	    			lastObject = null;
	    			lastDOTString = "";
					return Status.CANCEL_STATUS;
		        }
            };
            job.addJobChangeListener(new JobChangeAdapter() {
            	public void done(IJobChangeEvent event) {
            		if (event.getResult().isOK()) {
            			logger.log(new Status(Status.INFO, Activator.PLUGIN_ID, "Attempt rendering Graph."));
			        	syncWithUi(event);
			        }
			    }
		    });
		  	job.setSystem(true);
		  	job.schedule(); // start as soon as possible
		}
	}

	/**
	 * @return
	 */
	private GraphicalView getGraphvizView() {
		GraphicalView graphvizView = null;
		IWorkbenchPage p = getActivePage();
		if (p != null) {
			IViewPart view = p.findView(GRAPHVIZ_VIEW_PART_ID);
			if (view != null) {
				graphvizView = (GraphicalView) view;
			}
		}
		return graphvizView;
	}

    /**
     * Sync with ui.
     *
     * @param event the event
     */
    private void syncWithUi(IJobChangeEvent event) {
    	final GraphicalView graphvizView = getGraphvizView();
		if (graphvizView != null) {
		    Display.getDefault().asyncExec(new Runnable() {
		    	public void run() {
		    		graphvizView.setAutoSync(false);
		        	String dotString = (String) event.getJob().getProperty(details);
		        	graphvizView.setContents(dotString.getBytes(), new DOTGraphicalContentProvider());
		    	}
		    });
		}
	  }

	/**
	 * Gets the selection service.
	 *
	 * @return the selection service
	 */
	private ISelectionService getSelectionService() {
		IWorkbenchWindow w = getWorkbenchWindow();
		if (w != null) {
			ISelectionService ss = w.getSelectionService();
			return ss;
		}
		return null;

	}

	/**
	 * Gets the active page.
	 *
	 * @return the active page
	 */
	private IWorkbenchPage getActivePage() {
		IWorkbenchWindow w = getWorkbenchWindow();
		if (w != null) {
			IWorkbenchPage p = w.getActivePage();
			return p;
		}
		return null;
	}

	/**
	 * Gets the workbench window.
	 *
	 * @return the workbench window
	 */
	private IWorkbenchWindow getWorkbenchWindow() {
		IWorkbenchWindow[] ws = PlatformUI.getWorkbench().getWorkbenchWindows();
		if (ws.length > 0) {
			IWorkbenchWindow w = ws[0];
			return w;
		}
		return null;
	}

	/**
	 * We are only interested in selections from the debug view.
	 *
	 * @param part the part
	 * @param selection the selection
	 */
	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (!part.getSite().getId().equals(DEBUG_VIEW_PART_ID))
			return;
		if (!(selection instanceof IStructuredSelection))
			return;
		Object selected = ((IStructuredSelection)selection).getFirstElement();
		renderSelection(selected);
	}

	/**
	 * @param selection
	 */
	private void renderSelection(Object selected) {
		// WE can only draw one graph
		IValue val = null;
		if (selected instanceof IVariable) {
			try {
				val = ((IVariable)selected).getValue();
			} catch (DebugException e) {

			}
			if (val != null) { // val : JDIOBjectvalue
				if (val instanceof IJavaObject) {
					renderDotString((IJavaObject) val);
				}
			}
		}
	}


	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			installSelectionListener();
		}
	}


	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			installSelectionListener();
		}

	}


	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			removeSelectionListener();
		}
	}


	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {

	}


	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			installSelectionListener();
		}
	}


	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			removeSelectionListener();
		}
	}


	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			installSelectionListener();
		}
	}


	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
	}

	/**
	 * Dispose.
	 */
	public void dispose() {
		removeViewListener();
	}

	@Override
	public void debugContextChanged(DebugContextEvent event) {
		if ((event.getFlags() & DebugContextEvent.ACTIVATED) > 0) {
			contextActivated(event.getContext());
		}
	}

	/**
	 * If the stack frame changed, the variables' values may have changed,
	 * update the value of the last selected object, if it exists.
	 * TODO There should be a way to know if the value changed! Store the object and the rendered string?
	 * @param selection New selection to activate.
	 */
	protected void contextActivated(ISelection selection) {
		if (!(selection instanceof ITreeSelection))
			return;
		Object selected = ((ITreeSelection)selection).getFirstElement();
		if (selected instanceof IJavaStackFrame)
			try {
				for (IVariable lv : ((IJavaStackFrame) selected).getVariables()) {
					if (lv.getValue().equals(lastObject)) {
						renderSelection(lv);
						break;
					}
				}
			} catch (DebugException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

}
