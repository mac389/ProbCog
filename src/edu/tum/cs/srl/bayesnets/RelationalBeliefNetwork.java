package edu.tum.cs.srl.bayesnets;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.logic.Formula;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.srl.RelationKey;
import edu.tum.cs.srl.RelationalModel;
import edu.tum.cs.srl.Signature;
import edu.tum.cs.srl.bayesnets.RelationalNode.Aggregator;
import edu.tum.cs.srl.mln.MLNWriter;
import edu.tum.cs.srl.taxonomy.Taxonomy;
import edu.tum.cs.srldb.Database;
import edu.tum.cs.util.StringTool;
import edu.tum.cs.util.datastruct.CollectionFilter;
import edu.tum.cs.util.datastruct.MultiIterator;

public class RelationalBeliefNetwork extends BeliefNetworkEx implements RelationalModel {
	/**
	 * maps a node index to the corresponding extended node
	 */
	protected HashMap<Integer,ExtendedNode> extNodesByIdx;
	/**
	 * maps a function/predicate name to the signature of the corresponding function
	 */
	protected Map<String, Signature> signatures;
	/**
	 * maps the name of a fixed domain to the vector of elements it contains
	 */
	protected HashMap<String, String[]> guaranteedDomElements;	
	/**
	 * a mapping of function/relation names to RelationKey objects which signify argument groups that are keys of the relation (which may be used for a functional lookup)
	 */
	protected Map<String, Collection<RelationKey>> relationKeys;
	/**
	 * maps function/relation names to combining rules 
	 */
	protected Map<String, CombiningRule> combiningRules = new HashMap<String, CombiningRule>();
	
	/**
	 * a set of functions/predicates that are required to be fully specified in the evidence
	 */
	protected HashSet<String> evidenceFunctions = new HashSet<String>();
	protected Taxonomy taxonomy = null;
	protected Vector<String> prologRules = new Vector<String>();
	
	public Collection<RelationKey> getRelationKeys(String relation) {
		return relationKeys.get(relation.toLowerCase());
	}
	
	public RelationalBeliefNetwork(String networkFile) throws Exception {
		super(networkFile);
		extNodesByIdx = new HashMap<Integer, ExtendedNode>();		
		signatures = new HashMap<String, Signature>();
		relationKeys = new HashMap<String, Collection<RelationKey>>();
		guaranteedDomElements = new HashMap<String, String[]>();
		// store node data		
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			ExtendedNode n = createNode(nodes[i]);			
			addExtendedNode(n);
		}
	}

	/**
	 * creates a relational node from the given belief node
	 * @param node
	 * @return
	 * @throws Exception
	 */
	protected ExtendedNode createNode(BeliefNode node) throws Exception {
		switch(node.getType()) {
		case BeliefNode.NODE_CHANCE:
			return new RelationalNode(this, node);
		case BeliefNode.NODE_DECISION:
			return new DecisionNode(this, node);
		default:
			throw new Exception("Don't know how to treat node " + node.getName() + " of type " + node.getType());
		}	
	}
	
	public void addExtendedNode(ExtendedNode node) {
		extNodesByIdx.put(node.index, node);
	}
	
	/**
	 * gets the first relational node where the entire node label matches the given name
	 * @param name
	 * @return
	 */
	public RelationalNode getRelationalNode(String name) {
		BeliefNode node = this.getNode(name);
		if(node == null)
			return null;
		return getRelationalNode(this.getNodeIndex(node));
	}
	
	public RelationalNode getRelationalNode(int idx) {
		return (RelationalNode)getExtendedNode(idx);
	}
	
	public RelationalNode getRelationalNode(BeliefNode node) {
		return (RelationalNode)getExtendedNode(node);
	}
	
	public ExtendedNode getExtendedNode(int idx) {
		return extNodesByIdx.get(new Integer(idx));
	}
	
	public ExtendedNode getExtendedNode(BeliefNode node) {
		return getExtendedNode(this.getNodeIndex(node));
	}
	
	public Collection<ExtendedNode> getExtendedNodes() {
		return extNodesByIdx.values();
	}
	
	public Iterable<RelationalNode> getRelationalNodes() {
		return new CollectionFilter<RelationalNode, ExtendedNode>(getExtendedNodes(), RelationalNode.class);
	}
	
	public static boolean isBooleanDomain(Domain domain) {
		if(!(domain instanceof Discrete))
			return false;
		int order = domain.getOrder();
		if(order > 2 || order <= 0)
			return false;
		if(domain.getOrder() == 1) {
			if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(0).equalsIgnoreCase("false"))
				return true;
			return false;
		}
		if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(1).equalsIgnoreCase("true"))
			return true;
		return false;
	}
	
	/**
	 * obtains the names of parents of the variable that is given by a node name and its actual arguments
	 * @param nodeName 
	 * @param actualArgs
	 * @return an array of variable names
	 * @throws Exception
	 * TODO this should be rewritten with ParentGrounder
	 */
	public String[] getParentVariableNames(RelationalNode node, String[] actualArgs) throws Exception {
		RelationalNode child = node;
		BeliefNode[] parents = bn.getParents(child.node);
		String[] ret = new String[parents.length];
		for(int i = 0; i < parents.length; i++) {
			RelationalNode parent = getRelationalNode(getNodeIndex(parents[i].getName()));
			StringBuffer varName = new StringBuffer(parent.getFunctionName() + "(");
			String param = null;
			for(int iCur = 0; iCur < parent.params.length; iCur++) {
				for(int iMain = 0; iMain < child.params.length; iMain++) {
					if(child.params[iMain].equals(parent.params[iCur])) {
						param = actualArgs[iMain];
						break;
					}
				}
				if(param == null)
					throw new Exception(String.format("Could not determine parameters of parent '%s' for node '%s'", parent.getFunctionName(), node.getFunctionName() + actualArgs.toString()));
				varName.append(param);
				if(iCur < parent.params.length-1)
					varName.append(",");
			}
			varName.append(")");
			ret[i] = varName.toString();
		}
		return ret;
	}
	
	public void addSignature(String predicateName, Signature sig) {
		//relNodesByIdx.get(nodeIndex).sig = sig;
		String key = predicateName; //.toLowerCase()
		signatures.put(predicateName, sig);
	}
	
	public void addSignature(RelationalNode node, Signature sig) {
		addSignature(node.getFunctionName(), sig);
	}
	
	/**
	 * retrieves the signature of a function/predicate
	 * @param functionName the name of the function/predicate
	 * @return a Signature object
	 */
	public Signature getSignature(String functionName) {
		return signatures.get(functionName/*.toLowerCase()*/);
	}
	
	public Signature getSignature(RelationalNode node) {
		return signatures.get(node.getFunctionName());
	}
	
	public Collection<Signature> getSignatures() {
		return signatures.values();
	}
	
	/**
	 * replace a type by a new type in all function signatures
	 * @param oldType
	 * @param newType
	 */
	public void replaceType(String oldType, String newType) {
		for(Signature sig : getSignatures()) 
			sig.replaceType(oldType, newType);		
	}
	
	/**
	 * guesses the model's function signatures by assuming the same type whenever the same variable name is used (ignoring any numeric suffixes), setting the domain name to ObjType_x when x is the variable,
	 * and assuming a different domain of return values for each node, using Dom{NodeName} as the domain name.
	 * @throws Exception 
	 *
	 */
	public void guessSignatures() throws Exception {
		for(RelationalNode node : getRelationalNodes()) {
			if(node.isConstant) // signatures for constants are determined in checkSignatures (called below)
				continue;
			String[] argTypes = new String[node.params.length];
			for(int i = 0; i < node.params.length; i++) {
				String param = node.params[i].replaceAll("\\d+", "");
				argTypes[i] = "objType_" + param;
			}
			String retType = isBooleanDomain(((Discrete)node.node.getDomain())) ? "Boolean" : "dom" + node.getFunctionName();
			Signature sig = new Signature(node.getFunctionName(), retType, argTypes);
			addSignature(node.getFunctionName(), sig);		
		}
		checkSignatures(); // to fill constants
	}
	
	/**
	 * check fragments for type inconsistencies and write return types for constant nodes
	 * @throws Exception
	 */
	protected void checkSignatures() throws Exception {		
		for(RelationalNode node : getRelationalNodes()) {
			if(node.isFragment()) {
				MultiIterator<RelationalNode> relevantNodes = new MultiIterator<RelationalNode>();
				relevantNodes.add(node);
				relevantNodes.add(getRelationalParents(node));				
				checkFragment(node, relevantNodes);
			}			
		}			
	}
	
	protected void checkFragment(RelationalNode fragment, Iterable<RelationalNode> relevantNodes) throws Exception {		
		HashMap<String,String> types = new HashMap<String,String>(); // parameter/argument -> type name mapping
		for(RelationalNode node : relevantNodes) {
			if(node.isBuiltInPred())
				continue;
			if(node.isConstant) {
				// update constant return types using the mapping
				String type = types.get(node.getFunctionName());
				if(type == null) // constants that were referenced by any of their parents must now have a type assigned
					throw new Exception("Constant " + node + " not referenced and therefore not typed.");
				Signature sig = new Signature(node.getFunctionName(), type, new String[0]);
				addSignature(node, sig);
			}
			else {
				Signature sig = getSignature(node);
				if(sig == null) {
					throw new Exception("Node " + node + " has no signature!");
				}
				// check for the right number of arguments and their types
				if(sig.argTypes.length != node.params.length)
					throw new Exception(String.format("Signature of '%s' does not match node definition: It contains %d elements vs. %d elements in the node definition.", node.toString(), sig.argTypes.length, node.params.length));
				for(int i = 0; i < node.params.length; i++) {
					String key = node.params[i];
					String prevType = types.get(key);
					if(prevType == null)
						types.put(key, sig.argTypes[i]);
					else {
						if(!prevType.equals(sig.argTypes[i])) {
							boolean error = true;
							if(taxonomy != null && taxonomy.query_isa(prevType, sig.argTypes[i]))
								error = false;
							if(error)
								throw new Exception(String.format("Type mismatch while processing fragment '%s': '%s' has incompatible types '%s' and '%s'", fragment.getName(), key, prevType, sig.argTypes[i]));
						}
					}					
				}
			}
		}
	}

	/**
	 * gets all the parents of the given node that are instances of RelationalNode
	 * @param node
	 * @return
	 */
	public Vector<RelationalNode> getRelationalParents(RelationalNode node) {
		BeliefNode[] p = this.bn.getParents(node.node);
		Vector<RelationalNode> ret = new Vector<RelationalNode>();
		for(int i = 0; i < p.length; i++) {
			ExtendedNode n = getExtendedNode(p[i]);
			if(n instanceof RelationalNode)
				ret.add((RelationalNode)n);
		}
		return ret;
	}
	
	/**
	 * @deprecated
	 * converts the network to a Markov logic network
	 * @param out  the stream to write to
	 * @param compactFormulas  whether to write CPTs more compactly by first learning a classification tree
	 * @param numericWeights whether to print weighs as numbers (if false, print as log(x))
	 * @throws Exception
	 */
	public void toMLN(PrintStream out, boolean declarationsOnly, boolean compactFormulas, boolean numericWeights) throws Exception {
		MLNWriter writer = new MLNWriter(out);
		
		// write domain declarations
		out.println("// domain declarations");
		HashSet<String> handled = new HashSet<String>();
		HashMap<String, Vector<String>> domains = new HashMap<String,Vector<String>>();
		for(RelationalNode node : getRelationalNodes()) {
			Signature sig = getSignature(node.getFunctionName());
			if(sig == null)
				continue;
			if(sig.returnType.equals("Boolean"))
				continue;
			if(handled.contains(sig.returnType))
				continue;
			handled.add(sig.returnType);
			Vector<String> d = new Vector<String>();
			out.printf("%s = {", Database.lowerCaseString(sig.returnType));
			String[] dom = getDiscreteDomainAsArray(node.node);
			for(int i = 0; i < dom.length; i++) {
				if(i > 0) out.print(", ");
				String elem = Database.upperCaseString(dom[i]); 
				out.print(elem);
				d.add(elem);
			}
			out.println("}");
			domains.put(sig.returnType, d);
		}
		out.println();
		
		// write predicate declarations
		
		out.println("// predicate declarations");
		Set<Signature> handledSigs = new HashSet<Signature>();
		for(RelationalNode node : getRelationalNodes()) {
			if(node.isConstant)
				continue;
			Signature sig = node.getSignature();
			if(sig == null) continue;
			if(handledSigs.contains(sig))
				continue;
			handledSigs.add(sig);
			String[] argTypes;
			if(sig.returnType.equals("Boolean"))
				argTypes = new String[sig.argTypes.length];
			else {
				argTypes = new String[sig.argTypes.length + 1];
				argTypes[argTypes.length-1] = Database.lowerCaseString(sig.returnType);
			}
			for(int i = 0; i < sig.argTypes.length; i++) {
				if(sig.argTypes[i].length() == 0)
					throw new Exception("Parameter " + i + " of " + sig.functionName + " has empty type: " + sig);
				argTypes[i] = Database.lowerCaseString(sig.argTypes[i]);
			}
			out.printf("%s(%s)\n", Database.lowerCaseString(sig.functionName), StringTool.join(", ", argTypes));		
		}
		out.println();
		
		
		// mutual exclusiveness and exhaustiveness
		out.println("// mutual exclusiveness and exhaustiveness");
		// - non-boolean nodes
		for(RelationalNode node : getRelationalNodes()) {
			if(node.isConstant || node.isAuxiliary)
				continue;
			if(!node.isBoolean()) {
				out.print(Database.lowerCaseString(node.getFunctionName()));
				out.print('(');
				for(int i = 0; i <= node.params.length; i++) {
					if(i > 0) out.print(", ");
					out.printf("a%d", i);
					if(i == node.params.length) out.print('!');
				}
				out.println(")");
			}
		}
		// TODO - add constraints for functional dependencies in relations
		out.println();
		
		if(declarationsOnly)
			return;
		
		// write formulas (and auxiliary predicate definitions for special nodes) 
		int[] order = getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			RelationalNode node = getRelationalNode(order[i]);			
			if(node.isConstant || node.isAuxiliary)
				continue;
			CPT2MLNFormulas converter = new CPT2MLNFormulas(node);
			
			// write auxiliary definitions and formulas required by certain node types
			if(node.aggregator != null && node.parentMode != null) {
				if(node.aggregator == Aggregator.Average && node.parentMode.equals("CP")) { // average of conditional probabilities
					// get the relation that is responsible for grounding the free parameters
					RelationalNode rel = node.getFreeParamGroundingParent();
					if(rel == null)
						throw new Exception("Could not determine relevant relational parent");				
					// add predicate declaration for influence factor
					String influenceRelation = String.format("inflfac_%s_%s", node.getFunctionName(), rel.getFunctionName());
					Signature sig = rel.getSignature();
					writer.writePredicateDecl(influenceRelation, sig.argTypes, null);
					// write mutual exclusiveness and exhaustiveness definition
					writer.writeMutexDecl(influenceRelation, rel.params, node.addParams);
					// add a precondition that must be added to each CPT formula
					converter.addPrecondition(Signature.formatVarName(influenceRelation, rel.params));
					// write the formula connecting the influence predicate to the regular relation: if the relation does not hold, there is no influence 
					out.println("!" + rel.getCleanName() + " => !" + Signature.formatVarName(influenceRelation, rel.params) + ".");
				}
				else if(node.aggregator == Aggregator.NoisyOr) { // noisy or
					
				}
			}
			
			// write conditional probability distribution
			if(node.hasCPT()) {
				out.println("// CPT for " + node.getName());
				out.println("// <group>");			
				
				if(compactFormulas) { 
					// convert using decision trees for compactness
					converter.convert(out);
				}
				else {
					// old method: direct conversion
					CPF cpf = node.node.getCPF();
					int[] addr = new int[cpf.getDomainProduct().length];
					walkCPD_MLNformulas(out, cpf, addr, 0, converter.getPrecondition(), numericWeights);
				}				

				out.println("// </group>\n");
			}
			else {
				if(node.aggregator == Aggregator.NoisyOr) {
					out.print(writer.formatAsAtom(node.getCleanName()) + " <=> ");
					int k = 0;
					for(RelationalNode parent : node.getParents()) {
						// get the parameters that are free in this parent
						Vector<String> freeparams = new Vector<String>();
						for(String p : node.addParams)
							if(parent.hasParam(p))
								freeparams.add(p);
						if(freeparams.isEmpty())
							continue;
						// print the condition
						if(k++ > 0)
							out.print(" v ");						
						out.print("EXIST " + StringTool.join(",", freeparams.toArray(new String[0])) + " " + writer.formatAsAtom(parent.toAtom()));
					}
					if(k == 0)
						throw new Exception("None of the parents of OR-node " + node + " handle any of the free parameters.");
					out.println();
				}					
			}
		}
	}

	/**
	 * converts the network to a Markov logic network
	 * @param out  the stream to write to
	 * @param compactFormulas  whether to write CPTs more compactly by first learning a classification tree
	 * @param numericWeights whether to print weighs as numbers (if false, print as log(x))
	 * @throws Exception
	 */
	public void toMLN(MLNConverter converter, boolean declarationsOnly, boolean compactFormulas) throws Exception {
		
		// domain declarations
		for(java.util.Map.Entry<String, String[]> e : this.getGuaranteedDomainElements().entrySet()) {
			converter.addGuaranteedDomainElements(e.getKey(), e.getValue());
		}
		
		// predicate declarations
		for(Signature sig : this.getSignatures()) {
			Signature mlnSig = sig;
			if(!mlnSig.isBoolean()) {
				String[] argTypes = new String[sig.argTypes.length+1];
				for(int i = 0; i < sig.argTypes.length; i++)
					argTypes[i] = sig.argTypes[i];
				argTypes[argTypes.length-1] = sig.returnType;
				mlnSig = new Signature(sig.functionName, "Boolean", argTypes);
				converter.addFunctionalDependency(sig.functionName, argTypes.length-1);
			}
			converter.addSignature(mlnSig);
		}
		
		if(declarationsOnly)
			return;
		
		// write formulas (and auxiliary predicate definitions for special nodes) 
		int[] order = getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			ExtendedNode extNode = getExtendedNode(order[i]);
			if(!(extNode instanceof RelationalNode))
				continue;
			RelationalNode node = (RelationalNode) extNode;			

			if(!node.isFragment())
				continue;
			
			// TODO auxiliary definitions and formulas required by certain node types??
			
			// write conditional probability distribution
			if(!node.hasAggregator()) {
				converter.beginCPT(node);			
				
				CPT2MLNFormulas cptconverter = new CPT2MLNFormulas(node);
				if(compactFormulas) { 
					// convert using decision trees for compactness
					throw new RuntimeException("Compact formulas not yet supported");
				}
				else {
					// old method: direct conversion
					CPF cpf = node.node.getCPF();
					int[] addr = new int[cpf.getDomainProduct().length];
					walkCPD_MLNformulas(converter, cpf, addr, 0, cptconverter.getPrecondition(), true);
				}				

				converter.endCPT();
			}
			// for aggregators, consider specific conversion
			else {
				Formula f = node.toFormula(null);
				if(f == null)
					throw new Exception("Don't know how to generate a formula for " + node);
				converter.addHardFormula(f);
			}
		}
	}

	protected void walkCPD_MLNformulas(MLNConverter converter, CPF cpf, int[] addr, int i, String precondition, boolean numericWeights) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) { // we have a complete address
			// collect values of constants in order to replace references to them in the individual predicates 
			HashMap<String,String> constantValues = new HashMap<String,String>();
			for(int j = 0; j < addr.length; j++) {
				ExtendedNode extNode = getExtendedNode(nodes[j]);
				if(extNode instanceof RelationalNode) {
					RelationalNode rn = getRelationalNode(nodes[j]);
					if(rn.isConstant) {
						String value = ((Discrete)rn.node.getDomain()).getName(addr[j]);
						constantValues.put(rn.functionName, value);
					}
				}
			}
			// for each element of the address obtain the corresponding literal/predicate
			StringBuffer sb = new StringBuffer();
			for(int j = 0; j < addr.length; j++) {
				String conjunct = null;
				ExtendedNode extNode = getExtendedNode(nodes[j]);
				if(extNode instanceof DecisionNode) {
					conjunct = extNode.toString();
				}
				else {
					RelationalNode rn = (RelationalNode)extNode;
					if(!rn.isConstant)
						conjunct = rn.toLiteralString(addr[j], constantValues);					
				}
				if(conjunct != null) {
					if(sb.length() > 0)
						sb.append(" ^ ");
					sb.append(conjunct);
				}
			}
			if(precondition != null) {
				sb.append(" ^ " + precondition);
			}
			// get the weight
			double weight = Math.log(cpf.getDouble(addr));
			if(Double.isInfinite(weight)) weight = -100.0;
			// print weight and formula
			Formula f;
			try {
				f = Formula.fromString(sb.toString());
			}
			catch(Error e) {
				System.err.println("Error parsing formula: " + sb.toString());
				throw e;				
			}
			catch(Exception e) {
				System.err.println("Error parsing formula: " + sb.toString());
				throw e;
			}
			converter.addFormula(f, weight);
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			// if the node is a necessary precondition for the child node, there is only one possible setting (True)
			boolean isPrecondition = false;
			ExtendedNode extNode = getExtendedNode(nodes[i]);
			RelationalNode node;
			if(extNode instanceof DecisionNode) 
				isPrecondition = true;
			else {
				node = (RelationalNode) extNode;
				isPrecondition = node.isPrecondition;
			}
			Discrete dom = (Discrete)extNode.node.getDomain();
			if(isPrecondition) {
				addr[i] = dom.findName("True");
				if(addr[i] == -1)
					addr[i] = dom.findName("true");
				if(addr[i] == -1)
					throw new Exception("Domain of necessary precondition " + extNode + " must contain either 'True' or 'true'!");
				walkCPD_MLNformulas(converter, cpf, addr, i+1, precondition, numericWeights);
			}
			// otherwise consider all domain elements
			else {
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[i] = j;
					walkCPD_MLNformulas(converter, cpf, addr, i+1, precondition, numericWeights);
				}
			}
		}
	}
	
	/**
	 * @deprecated
	 * @param out
	 * @param cpf
	 * @param addr
	 * @param i
	 * @param precondition
	 * @param numericWeights
	 * @throws Exception
	 */
	protected void walkCPD_MLNformulas(PrintStream out, CPF cpf, int[] addr, int i, String precondition, boolean numericWeights) throws Exception {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) { // we have a complete address
			// collect values of constants in order to replace references to them in the individual predicates 
			HashMap<String,String> constantValues = new HashMap<String,String>();
			for(int j = 0; j < addr.length; j++) {
				RelationalNode rn = getRelationalNode(nodes[j]);
				if(rn.isConstant) {
					String value = ((Discrete)rn.node.getDomain()).getName(addr[j]);
					constantValues.put(rn.functionName, value);
				}
			}
			// for each element of the address obtain the corresponding literal/predicate
			StringBuffer sb = new StringBuffer();
			for(int j = 0; j < addr.length; j++) {
				RelationalNode rn = getRelationalNode(nodes[j]);
				if(!rn.isConstant) {
					if(j > 0)
						sb.append(" ^ ");
					sb.append(rn.toLiteralString(addr[j], constantValues));
				}
			}
			if(precondition != null) {
				sb.append(" ^ " + precondition);
			}
			// get the weight
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			double weight = Math.log(value);
			if(Double.isInfinite(weight)) weight = -100.0;
			// print weight and formula
			if(numericWeights)
				out.printf("%f %s\n", weight, sb.toString());
			else
				out.printf("logx(%f) %s\n", value, sb.toString());
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			// if the node is a necessary precondition for the child node, there is only one possible setting (True)
			RelationalNode node = getRelationalNode(nodes[i]);
			Discrete dom = (Discrete)node.node.getDomain();
			if(node.isPrecondition) {
				addr[i] = dom.findName("True");
				if(addr[i] == -1)
					addr[i] = dom.findName("true");
				if(addr[i] == -1)
					throw new Exception("Domain of necessary precondition " + node + " must contain either 'True' or 'true'!");
				walkCPD_MLNformulas(out, cpf, addr, i+1, precondition, numericWeights);
			}
			// otherwise consider all domain elements
			else {
				for(int j = 0; j < dom.getOrder(); j++) {
					addr[i] = j;
					walkCPD_MLNformulas(out, cpf, addr, i+1, precondition, numericWeights);
				}
			}
		}
	}
	
	public ParentGrounder getParentGrounder(RelationalNode node) throws Exception {
		return node.getParentGrounder();
	}
	
	public void addRelationKey(RelationKey k) {
		Collection<RelationKey> list = relationKeys.get(k.relation.toLowerCase());
		if(list == null) { 
			list = new Vector<RelationKey>();
			relationKeys.put(k.relation.toLowerCase(), list);
		}
		list.add(k);
		//System.out.println("Key: " + k);
		//System.out.println(" now: " + this.getRelationKeys(k.relation.toLowerCase()));
	}
	
	/**
	 * prepares this network for learning by materializing additional nodes (e.g. for noisy-or)
	 * @throws Exception 
	 */
	public void prepareForLearning() throws Exception {
		for(RelationalNode node : getRelationalNodes()) {
			if(node.parentMode != null && node.parentMode.equals("AUX")) { // create an auxiliary node that contains the ungrounded parameters 
				// create fully grounded variant
				String[] params = new String[node.params.length + node.addParams.length];
				int i = 0;
				for(int j = 0; j < node.params.length; j++)
					params[i++] = node.params[j];
				for(int j = 0; j < node.addParams.length; j++)
					params[i++] = node.addParams[j];
				String fullName = Signature.formatVarName(node.getFunctionName() + "_" + StringTool.join("", node.addParams), params);
				BeliefNode fullyGroundedNode = this.addNode(fullName);
				fullyGroundedNode.setDomain(node.node.getDomain());
				// create the corresponding relational node and define a signature for it
				RelationalNode fullyGroundedRelNode = new RelationalNode(this, fullyGroundedNode); 
				addExtendedNode(fullyGroundedRelNode);
				// - determine argument types for signature
				String[] argTypes = new String[params.length];
				Signature origSig = node.getSignature();
				Vector<RelationalNode> relParents = getRelationalParents(node);
				for(int j = 0; j < params.length; j++) {
					if(j < node.params.length)
						argTypes[j] = origSig.argTypes[j];
					else { // check relational parents for parameter match		
						boolean haveType = false;
						for(int k = 0; k < relParents.size() && !haveType; k++) {
							RelationalNode parent = relParents.get(k);
							Signature sig = parent.getSignature();
							for(int l = 0; l < parent.params.length; l++) {
								if(parent.params[l].equals(params[j])) {
									argTypes[j] = sig.argTypes[l];
									haveType = true;
									break;
								}									
							}
						}
						if(!haveType) 
							throw new Exception("Could not determine type of free parameter " + params[j]);
					}
				}
				// - add signature
				addSignature(fullyGroundedRelNode, new Signature(fullyGroundedRelNode.getFunctionName(), origSig.returnType, argTypes));
				// connect all parents of this node to the fully grounded version
				BeliefNode[] parents = this.bn.getParents(node.node);
				for(BeliefNode parent : parents) {
					this.bn.disconnect(parent, node.node);
					this.bn.connect(parent, fullyGroundedNode);
				}
				// connect the fully grounded version to the original child
				this.bn.connect(fullyGroundedNode, node.node);
				// modify the original child
				node.parentMode = "";
				node.setLabel();
				//node.node.setName(RelationalNode.formatName(node.getFunctionName(), node.params));				
			}
		}
		//show();
	}
	
	public HashMap<String, String[]> getGuaranteedDomainElements() {
		return guaranteedDomElements;
	}
	
	/**
	 * retrieves the name of the random variable that corresponds to a logical ground atom
	 * @return
	 */
	public String gndAtom2VarName(GroundAtom ga) {
		if(getSignature(ga.predicate).isBoolean())
			return ga.toString();
		else {
			StringBuffer s = new StringBuffer(ga.predicate + "(");
			for(int i = 0; i < ga.args.length-1; i++) {
				if(i > 0)
					s.append(',');
				s.append(ga.args);
			}
			s.append(')');
			return s.toString();
		}
	}
	
	@Override
	public BeliefNode getNode(String name) {
		throw new RuntimeException("This method should never be called in relational networks because they may contain several nodes with the same name, so the mapping may not be well-defined.");
	}
	
	@Override
	public int getNodeIndex(String nodeName) {
		throw new RuntimeException("This method should never be called in relational networks because they may contain several nodes with the same name.");
	}
	
	public boolean isBoolean(String functionName) {
		return getSignature(functionName).isBoolean();
	}
	
	/**
	 * sets the given function as an evidence function that must always be given
	 * @param functionName
	 */
	public void setEvidenceFunction(String functionName) {
		this.evidenceFunctions.add(functionName);
	}
	
	public boolean isEvidenceFunction(String functionName) {
		return evidenceFunctions.contains(functionName);
	}

	public Taxonomy getTaxonomy() {
		return taxonomy;
	}

	@Override
	public Collection<String> getPrologRules() {
		return prologRules;
	}
	
	public CombiningRule getCombiningRule(String function) {
		return this.combiningRules.get(function);
	}
}


