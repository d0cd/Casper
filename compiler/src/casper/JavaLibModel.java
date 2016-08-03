/* 
 * This class holds information about common java library functions.
 * It is used to identify how a library function affects an argument
 * passed to it i.e Read or Write.
 *    
 * - Maaz
 */

package casper;

import java.util.ArrayList;
import java.util.List;

import casper.types.ArrayAccessNode;
import casper.types.ArrayUpdateNode;
import casper.types.CallNode;
import casper.types.CustomASTNode;
import casper.types.IdentifierNode;
import polyglot.ast.Call;
import polyglot.ast.Expr;
import polyglot.ast.Node;

public class JavaLibModel {
	
	private static boolean debug = false;
	
	// A class used to encapsulate a function call in Sketch.
	// SketchCall objects are used to generate sketch code.
	public static class SketchCall{
		public String nameOrig;
		public String target;
		public String name;
		public String returnType;
		public List<String> args = new ArrayList<String>();
		public String toString(){ return "["+returnType+", "+name+", "+args+"]"; }
		public boolean equals(Object o){ return name.equals(((SketchCall)o).name) && returnType.equals(((SketchCall)o).returnType) && args.equals(((SketchCall)o).args); }
		public int hashCode(){ return 0; }
		public String resolve(String exp, List<String> argsR) {
			if(argsR.indexOf(exp) >= args.size()){
				return name+"("+casper.Util.join(argsR.subList(0, argsR.size()-1),",")+")";
			}
			return exp;
		}
	}
	
	// Extract all expressions being read by this function call
	static public List<Node> extractReads(Call exp){
		// Get the container class
		String targetType = exp.target().type().toString();
		
		// Is the container class a generic?
		int end = targetType.indexOf('<');
		if(end != -1)
			// It is, drop the generic part
			targetType = targetType.substring(0, end);
		
		List<Node> reads = new ArrayList<Node>();
		
		switch(targetType){
			case "java.lang.String":
				switch(exp.id().toString()){
					case "equals":
						reads.addAll(exp.arguments());
						reads.add(exp.target());
						break;
					case "split":
						reads.add(exp.target());
						break;
					default:
						break;
				}
				break;
			case "java.util.List":
				switch(exp.id().toString()){
					case "size":
						reads.add(exp.target());
						break;
					case "get":
						reads.add(exp.target());
						reads.addAll(exp.arguments());
						break;
					case "add":
						reads.addAll(exp.arguments());
						break;
					default:
						break;
				}
				break;
			case "java.util.Map":
				switch(exp.id().toString()){
					case "get":
						reads.add(exp.target());
						reads.addAll(exp.arguments());
						break;
					case "put":
						reads.addAll(exp.arguments());
						break;
					default:
						break;
				}
				break;
			default:
				break;
		}
		
		return reads;
	}
	
	// Extract all expressions being modified by this function call
	static public List<Node> extractWrites(Call exp){
		String targetType = exp.target().type().toString();
		int end = targetType.indexOf('<');
		if(end != -1)
			targetType = targetType.substring(0, end);
		
		List<Node> writes = new ArrayList<Node>();
		
		switch(targetType){
			case "java.lang.String":
				switch(exp.id().toString()){
					case "equals":
						break;
					case "split":
						break;
					default:
						break;
				}
				break;
			case "java.util.List":
				switch(exp.id().toString()){
					case "size":
						break;
					case "add":
						writes.add(exp.target());
						break;
					case "get":
						break;
					default:
						break;
				}
				break;
			case "java.util.Map":
				switch(exp.id().toString()){
					case "get":
						break;
					case "put":
						writes.add(exp.target());
						break;
					default:
						break;
				}
				break;
			default:
				break;
		}
		
		return writes;
	}
	
	// This method is used to judge whether we
	// 'handle' a certain method or not
	static public boolean recognizes(Call exp){
		String targetType = exp.target().type().toString();
		int end = targetType.indexOf('<');
		if(end != -1)
			targetType = targetType.substring(0, end);
		
		switch(targetType){
			case "java.lang.String":
				switch(exp.id().toString()){
					case "equals":
						return true;
					case "split":
						return false;
					default:
						return false;
				}
			case "java.util.List":
				switch(exp.id().toString()){
					case "size":
					case "get":
					case "add":
						return true;
					default:
						return false;
				}
			case "java.util.Map":
				switch(exp.id().toString()){
					case "get":
					case "put":
						return true;
					default:
						return false;
				}
			default:
				return false;
		}
	}
	
	// This method will translate a Java Call expression
	// in SketchCall.
	static public SketchCall translate(Call exp){
		SketchCall res = new SketchCall();
		
		// Get container type and template type
		String targetType = exp.target().type().toString();
		String templateType = exp.target().type().toString();
		
		// Is it a generic?
		int end = targetType.indexOf('<');
		if(end != -1){
			// Yes, extract container type and generic type
			targetType = targetType.substring(0, end);
			templateType = translateToSketchType(templateType);
		}
			
		switch(targetType){
			case "java.lang.String":
				switch(exp.id().toString()){
					case "equals":
						res.target = "first-arg";
						res.name = "str_equal";
						res.nameOrig = "equals";
						res.args.add(targetType);
						for(Expr arg : exp.arguments()){
							res.args.add(arg.type().toString());
						}
						res.returnType = exp.type().toString();
						return res;
					case "split":
						return res;
					default:
						return res;
				}
			case "java.util.List":
				switch(exp.id().toString()){
					case "size":
					case "get":
					case "add":
					default:
						return res;
				}
			case "java.util.Map":
				switch(exp.id().toString()){
					case "get":
					case "put":
					default:
						return res;
				}
			default:
				return res;
		}
	}

	// Currently cannot handle data structures containing non primitive types
	public static String translateToSketchType(String templateType) {
		// Get the substring "<...>"
		templateType = templateType.substring(templateType.indexOf("<")+1,templateType.lastIndexOf(">"));
		
		// Extract first generic type i.e first "," occurrence at depth 0
		int depth = 0;
		int i=0; 
		while(i<templateType.length()){
			if(templateType.charAt(i) == ',' && depth == 0){
				break;
			}
			else if(templateType.charAt(i) == '<'){
				depth++;
			}
			else if(templateType.charAt(i) == '>'){
				depth--;
			}
			i++;
		}
		
		// Create a sketch type...format is containertype concat with generic
		// types in original order
		String sketchType = "";
		
		if(i < templateType.length()){
			String p1 = templateType.substring(0, i);
			String p2 = templateType.substring(i+1, templateType.length());
			
			if(p1.equals("java.lang.String")){
				sketchType = "int";
			}
			else if(p1.equals("java.lang.Integer")){
				sketchType = "int";
			}
			
			if(p2.equals("java.lang.String")){
				sketchType += "int";
			}
			else if(p2.equals("java.lang.Integer")){
				sketchType += "int";
			}
		}
		else{
			if(templateType.equals("java.lang.String")){
				sketchType = "int";
			}
			else if(templateType.equals("java.lang.Integer")){
				sketchType = "int";
			}
		}
		
		return sketchType;
	}
	
	public static CustomASTNode updatePreCondition(Call c, CustomASTNode currVerifCondition){
		String target = c.target().toString();
		String targetType = c.target().type().toString();
		String targetTypeMain = targetType;
		if(targetType.contains("<"))
			targetTypeMain = targetType.substring(0,targetType.indexOf("<"));
		
		String id = c.id().toString();
		
		switch(targetTypeMain){
			case "java.util.Map":
				String[] targetSubTypes = targetType.substring(targetType.indexOf("<")+1,targetType.lastIndexOf(">")).split(",");
				switch(id){
					case "put":
						List<Expr> args = c.arguments();
						switch(targetSubTypes[0]){
		    				case "java.lang.Integer":
		    				case "java.lang.String":
		    				case "java.lang.Double":
		    				case "java.lang.Float":
		    				case "java.lang.Long":
		    				case "java.lang.Short":
		    				case "java.lang.Byte":
		    				case "java.lang.BigInteger":
		    					CustomASTNode upd = new ArrayUpdateNode(new IdentifierNode(target),CustomASTNode.convertToAST(args.get(0)),CustomASTNode.convertToAST(args.get(1)));
		    					return currVerifCondition.replaceAll(target,upd);
		    				default:
		    					if(debug){
		    						System.err.println("Currently not handling Map of custom types");
		    					}
		    						
		    					break;
	    				}
						break;
					default:
						if(debug){
							System.err.println("Method " + id + " of java.util.Map not currently supported. Please extend the JavaLibModel.");
						}
						break;
				}
				break;
			/*case "java.util.List":
				switch(id){
					case "get":
						List<Expr> args = c.arguments();
						CustomASTNode acc = new ArrayAccessNode(target+"["+args.get(0)+"]",new IdentifierNode(target),CustomASTNode.convertToAST(args.get(0)));
						return currVerifCondition.replaceAll(target,acc);
					default:
						if(debug){
							System.err.println("Method " + id + " of java.util.List not currently supported. Please extend the JavaLibModel.");
						}
						break;
				}
				break;*/
			default:
				if(debug){
					System.err.println("Container type " + targetTypeMain + " not currently supported. Please extend the JavaLibModel.");
				}
		}
		
		return null;
	}

	public static CustomASTNode convertToAST(Call c) {
		String target = c.target().toString();
		String targetType = c.target().type().toString();
		String targetTypeMain = targetType;
		if(targetType.contains("<"))
			targetTypeMain = targetType.substring(0,targetType.indexOf("<"));
		
		String id = c.id().toString();
		
		switch(targetTypeMain){
			case "java.util.Map":
				String[] targetSubTypes = targetType.substring(targetType.indexOf("<")+1,targetType.lastIndexOf(">")).split(",");
				switch(id){
					case "get":
						List<Expr> args = c.arguments();
						switch(targetSubTypes[0]){
		    				case "java.lang.Integer":
		    				case "java.lang.String":
		    				case "java.lang.Double":
		    				case "java.lang.Float":
		    				case "java.lang.Long":
		    				case "java.lang.Short":
		    				case "java.lang.Byte":
		    				case "java.lang.BigInteger":
		    					return new ArrayAccessNode("",new IdentifierNode(target),CustomASTNode.convertToAST(args.get(0)));
		    				default:
		    					if(debug){
		    						System.err.println("Currently not handling Map of type: " + targetSubTypes[0]);
		    					}
		    					break;
	    				}
						break;
					default:
						if(debug){
							System.err.println("Method " + id + " of java.util.Map not currently supported. Please extend the JavaLibModel.");
						}
						break;
				}
				break;
			case "java.util.List":
				switch(id){
					case "get":
						List<Expr> args = c.arguments();
						return new ArrayAccessNode(target+"["+args.get(0)+"]",new IdentifierNode(target),CustomASTNode.convertToAST(args.get(0)));
					default:
						if(debug){
							System.err.println("Method " + id + " of java.util.List not currently supported. Please extend the JavaLibModel.");
						}
						break;
				}
				break;
			case "java.lang.String":
				switch(id){
					case "equals":
						ArrayList<CustomASTNode> args = new ArrayList<CustomASTNode>();
						args.add(new IdentifierNode(target));
						args.add(CustomASTNode.convertToAST(c.arguments().get(0)));
						return new CallNode("str_equal",args);
					default:
						if(debug){
							System.err.println("Method " + id + " of java.lang.String not currently supported. Please extend the JavaLibModel.");
						}
						break;
				}
			default:
				if(debug){
					System.err.println("Container type " + targetTypeMain + " not currently supported. Please extend the JavaLibModel.");
				}
		}
		
		return new IdentifierNode("");
	}
}
