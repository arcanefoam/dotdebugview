# DOT Debug View
Allows classes that provide a toDOT() method to be rendered into the eclipsegraphiz view during debuging. See https://github.com/abstratt/eclipsegraphviz.

To use the functionality add a method to your class with the signature: String toDOT();

During a debug session, the value returned by that method will be used to render a graph in the Eclipse Graphviz view.
