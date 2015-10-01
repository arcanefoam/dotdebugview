package com.github.eclipsegraphviz.debugview.test;

public class SmallGraph {
	
	public String toDOT() {
		return "digraph G {"
				+ "main -> parse -> execute;\n"
				+ "main -> init;\n"
				+ "main -> cleanup;\n"
				+ "execute -> make_string;\n"
				+ "execute -> printf\n"
				+ "init -> make_string;\n"
				+ "main -> printf;\n"
				+ "execute -> compare;\n"
				+ "}";
	}

}
