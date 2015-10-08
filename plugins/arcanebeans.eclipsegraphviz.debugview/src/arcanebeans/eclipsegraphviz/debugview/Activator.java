package arcanebeans.eclipsegraphviz.debugview;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

public class Activator extends Plugin {

	// The plug-in ID
    public static final String PLUGIN_ID = "arcanebeans.eclipsegraphviz.debugview";

	private static Activator plugin;

	/**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static Activator getDefault() {
        return plugin;
    }

	private DebugListener ls;

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		super.start(bundleContext);
		plugin = this;
		ls = new DebugListener(getLog());
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		super.stop(bundleContext);
		plugin = null;
		ls.dispose();
		ls = null;
	}

}
