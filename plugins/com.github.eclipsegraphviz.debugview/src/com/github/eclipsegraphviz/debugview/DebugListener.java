package com.github.eclipsegraphviz.debugview;

import org.eclipse.core.runtime.Platform;
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

	private class RenderRunnable implements Runnable {

		private GraphicalView gview;
		private IJavaObject debugObject;

		public RenderRunnable(GraphicalView gview, IJavaObject debugObject) {
			super();
			this.gview = gview;
			this.debugObject = debugObject;
		}


		@Override
		public void run() {
			IJavaThread thread = JDIModelPresentation.getEvaluationThread((IJavaDebugTarget) debugObject.getDebugTarget());
			IJavaValue toStringValue = null;
			try {
				toStringValue = debugObject.sendMessage(JDI_OBJECT_TO_DOT, "()Ljava/lang/String;", null, thread, false);
			} catch (DebugException e) {
				//Silently return
				return;
			}
			String details = null;
			try {
				details  = toStringValue.getValueString();
			} catch (DebugException e) {
				return;
			}
			if (details != null) {
				gview.setAutoSync(false);
				gview.setContents(details.getBytes(), new DefaultGraphicalContentProvider());
	        }

		}

	}

	private final String GRAPHVIZ_VIEW_PART_ID = "com.abstratt.imageviewer.GraphicalView";
	private final String DEBUG_VIEW_PART_ID = "org.eclipse.debug.ui.VariableView";
	public static String JDI_OBJECT_TO_DOT = "toDOT";

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
		String details = null;
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart view = page.findView(GRAPHVIZ_VIEW_PART_ID);
		if (view != null) {
			GraphicalView gview = (GraphicalView) view;
			Thread dotThread = new Thread(this.new RenderRunnable(gview, input));
			dotThread.start();
		}
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
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart view = page.findView(DEBUG_VIEW_PART_ID);
		if (view != part)
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
		// TODO Auto-generated method stub

	}

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub

	}

	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID))
			removeSelectionListener();
	}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub

	}

	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		if (partRef.getId().equals(GRAPHVIZ_VIEW_PART_ID))
			installSelectionListener();
	}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub

	}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub

	}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub

	}

	public void dispose() {
		removeSelectionListener();
		removeViewListener();
	}

}
