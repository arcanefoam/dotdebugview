# DOT Debug View
Allows classes that provide a toDOT() method to be rendered into the eclipsegraphiz view during debuging. See https://github.com/abstratt/eclipsegraphviz.

To use the functionality add a method to your class with the signature: String toDOT();

During a debug session, the value returned by that method will be used to render a graph in the Eclipse Graphviz view.

## Installing DOT Debug View

- First, you will need the [Eclipsegraphviz plugin](https://github.com/abstratt/eclipsegraphviz)

###Add the DOT Debug View Update site
-   Open the [Software
    Updates](http://help.eclipse.org/stable/topic/org.eclipse.platform.doc.user/tasks/tasks-121.htm "http://help.eclipse.org/stable/topic/org.eclipse.platform.doc.user/tasks/tasks-121.htm")
    dialog (Help \> Install New Software...), and enter the following
    update site URL in the "Work with:" field:

<pre>https://dl.bintray.com/arcanefoam/DOTDebugView</pre>

-   Select the DOT Debug View.

-   Accept to restart Eclipse to make the changes effective.
