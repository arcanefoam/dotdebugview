package com.github.eclipsegraphviz.debugview;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
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
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.abstratt.imageviewer.DefaultGraphicalContentProvider;
import com.abstratt.imageviewer.GraphicalView;

public class DebugListener implements ISelectionListener, IPartListener2 {
	
	private GraphicalView graphvizView; 

	private final String GRAPHVIZ_VIEW_PART_ID = "com.abstratt.imageviewer.GraphicalView";
	private final String DEBUG_VIEW_PART_ID = "org.eclipse.debug.ui.VariableView";
	public static String JDI_OBJECT_TO_DOT = "toDOT";
	public final QualifiedName details =  new QualifiedName("com.github.eclipsegraphviz.debugview", "dotString");

	public DebugListener() {
		super();
		installViewListener();
		// The view might be already loaded
		IWorkbenchPage p = getActivePage();
		IViewPart view = p.findView(GRAPHVIZ_VIEW_PART_ID);
		if (view != null) {
			installSelectionListener();
		}

	}

	/**
	 * Keep track of the Graphviz view state. We only listen to the Variable view
	 * events if the Graphviz view is opened.
	 */
	private void installViewListener() {
		IWorkbenchPage p = getActivePage();
		p.addPartListener(this);

	}

	private void removeViewListener() {
		IWorkbenchPage p = getActivePage();
		p.removePartListener(this);
	}

	private void installSelectionListener() {
		ISelectionService ss = getSelectionService();
		ss.addSelectionListener(this);
	}

	private void removeSelectionListener() {
		ISelectionService ss = getSelectionService();
		ss.removeSelectionListener(this);
	}

	private void renderDotString(IJavaObject input) {
		if (graphvizView != null) {
			final Job job = new Job("Graphviz Debug Query") {
				
		        protected IStatus run(IProgressMonitor monitor) {
		        	IJavaThread thread = JDIModelPresentation.getEvaluationThread((IJavaDebugTarget) input.getDebugTarget());
					IJavaValue toStringValue = null;
					try {
						toStringValue = input.sendMessage(JDI_OBJECT_TO_DOT, "()Ljava/lang/String;", null, thread, false);
					} catch (DebugException e) {
						// Silently ignore exceptions
						return Status.OK_STATUS;
					}
					try {
						setProperty(details, toStringValue.getValueString());
					} catch (DebugException e) {
						// Silently ignore exceptions
					}
					return Status.OK_STATUS;
		        }
		     };
		  job.addJobChangeListener(new JobChangeAdapter() {
		        public void done(IJobChangeEvent event) {
		        if (event.getResult().isOK()) {
		        	syncWithUi(event);
		        }
//		        else
//		        	postError("Job did not complete successfully");
		        }
		     });
		  	job.setSystem(true);
		  	job.schedule(); // start as soon as possible
		}
	}
	
    private void syncWithUi(IJobChangeEvent event) {
	    Display.getDefault().asyncExec(new Runnable() {
	    	public void run() {
	    		graphvizView.setAutoSync(false);
	        	String dotString = (String) event.getJob().getProperty(details);
	        	graphvizView.setContents(dotString.getBytes(), new DefaultGraphicalContentProvider());
	    	}
	    });

	  }

	/**
	 * @return
	 */
	private ISelectionService getSelectionService() {
		IWorkbenchWindow w = PlatformUI.getWorkbench().getWorkbenchWindows()[0];
		ISelectionService ss = w.getSelectionService();
		return ss;
	}

	/**
	 * @return
	 */
	private IWorkbenchPage getActivePage() {
		IWorkbenchWindow w = PlatformUI.getWorkbench().getWorkbenchWindows()[0];
		IWorkbenchPage p = w.getActivePage();
		return p;
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		// We are only interested in debug window selection
//		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
//		IViewPart view = page.findView(DEBUG_VIEW_PART_ID);
		if (graphvizView != part)
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

	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = (GraphicalView) partRef.getPart(false);
			installSelectionListener();
		}

	}

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = (GraphicalView) partRef.getPart(false);
			installSelectionListener();
		}

	}

	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = null;
			removeSelectionListener();
		}
			
	}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {

	}

	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = (GraphicalView) partRef.getPart(false);
			installSelectionListener();
		}
			
	}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = (GraphicalView) partRef.getPart(false);
			removeSelectionListener();
		}
	}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = (GraphicalView) partRef.getPart(false);
			installSelectionListener();
		}

	}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID)) {
			graphvizView = (GraphicalView) partRef.getPart(false);
			installSelectionListener();
		}
	}

	public void dispose() {
		removeViewListener();
	}

}
