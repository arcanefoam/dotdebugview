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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.internal.debug.ui.JDIModelPresentation;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
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
public class DebugListener implements ISelectionListener, IPartListener2 {

	/** The graphviz view. */
	private GraphicalView graphvizView;

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

	/**
	 * Instantiates a new debug listener.
	 */
	public DebugListener() {
		super();
		installViewListener();
		selListenerReady = false;
		// The view might be already loaded
		IWorkbenchPage p = getActivePage();
		if (p != null) {
			IViewPart view = p.findView(GRAPHVIZ_VIEW_PART_ID);
			if (view != null) {
				graphvizView = (GraphicalView) view;
				installSelectionListener();
			}
		}

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
			}
		}
	}

	/**
	 * Removes the selection listener.
	 */
	private void removeSelectionListener() {
		ISelectionService ss = getSelectionService();
		if (ss != null)
			ss.removeSelectionListener(this);
	}


	/**
	 * Render dot string.
	 *
	 * @param input the input
	 */
	private void renderDotString(IJavaObject input) {
		if (graphvizView != null) {
			final Job job = new Job("Graphviz Debug Query") {

		        protected IStatus run(IProgressMonitor monitor) {
		        	@SuppressWarnings("restriction")
					IJavaThread thread = JDIModelPresentation.getEvaluationThread((IJavaDebugTarget) input.getDebugTarget());
					IJavaValue toStringValue = null;
					try {
						toStringValue = input.sendMessage(JDI_OBJECT_TO_DOT, "()Ljava/lang/String;", null, thread, false);
					} catch (DebugException e) {
						// Silently ignore exceptions
						System.out.println(e.getStatus());
						return Status.CANCEL_STATUS;
					}
					if (toStringValue != null) {
						try {
							setProperty(details, toStringValue.getValueString());
						} catch (DebugException e) {
							// Silently ignore exceptions
							return Status.CANCEL_STATUS;
						}
					} else {
						return Status.CANCEL_STATUS;
					}
					return Status.OK_STATUS;
		        }
		     };
		  job.addJobChangeListener(new JobChangeAdapter() {
		        public void done(IJobChangeEvent event) {
			        if (event.getResult().isOK()) {
			        	syncWithUi(event);
			        }
			    }
		     });
		  	job.setSystem(true);
		  	job.schedule(); // start as soon as possible
		}
	}

    /**
     * Sync with ui.
     *
     * @param event the event
     */
    private void syncWithUi(IJobChangeEvent event) {
	    Display.getDefault().asyncExec(new Runnable() {
	    	public void run() {
	    		graphvizView.setAutoSync(false);
	        	String dotString = (String) event.getJob().getProperty(details);
	        	graphvizView.setContents(dotString.getBytes(), new DOTGraphicalContentProvider());
	    	}
	    });

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
		IStructuredSelection structured = (IStructuredSelection) selection;
		// WE can only draw one graph
		if (structured.size() != 1)
			return;
		Object selected = structured.getFirstElement();
		IValue val = null;
		if (selected instanceof IVariable) {
			try {
				val = ((IVariable)selected).getValue();
			} catch (DebugException e) {
				//if (Platform.inDebugMode())
				//	Activator.logUnexpected(null, e);
			} finally {
				if (val != null) { // val : JDIOBjectvalue
					if (val instanceof IJavaObject)
						renderDotString((IJavaObject) val);
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partActivated(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		if (graphvizView == null)
			if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
				graphvizView = (GraphicalView) partRef.getPart(false);
				installSelectionListener();
			}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partBroughtToTop(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
		if (graphvizView == null)
			if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
				graphvizView = (GraphicalView) partRef.getPart(false);
				installSelectionListener();
			}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partClosed(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = null;
			removeSelectionListener();
		}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partDeactivated(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partOpened(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		if (graphvizView == null)
			if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
				graphvizView = (GraphicalView) partRef.getPart(false);
				installSelectionListener();
			}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partHidden(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = null;
			removeSelectionListener();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partVisible(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
		if (graphvizView == null)
			if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
				graphvizView = (GraphicalView) partRef.getPart(false);
				installSelectionListener();
			}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener2#partInputChanged(org.eclipse.ui.IWorkbenchPartReference)
	 */
	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
	}

	/**
	 * Dispose.
	 */
	public void dispose() {
		graphvizView = null;
	}

}
