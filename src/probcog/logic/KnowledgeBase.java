package probcog.logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import probcog.logic.Formula.FormulaSimplification;
import probcog.logic.parser.FormulaParser;
import probcog.logic.parser.ParseException;
import probcog.srl.Database;

import edu.tum.cs.util.FileUtil;

/**
 * class that represents a logical knowledge base
 * @author jain
 *
 */
public class KnowledgeBase implements Iterable<Formula> {
	protected Vector<Formula> formulas;
	/**
	 * stores, for ground KBs, the index of the original formula from which the formula was instantiated
	 */
	protected HashMap<Formula, Integer> templateIDs;
	
	public KnowledgeBase() {
		formulas = new Vector<Formula>();
		templateIDs = new HashMap<Formula, Integer>();
	}
	
	/**
	 * constructor that reads a number of .-terminated formula statements from a file
	 * @param filename
	 * @throws IOException
	 * @throws ParseException
	 */
	public KnowledgeBase(String filename) throws IOException, ParseException {
		this();
		readFile(filename);
	}
	
	public void readFile(String filename) throws IOException, ParseException {
		// read KB file
		String fileContent = FileUtil.readTextFile(filename);
		// remove comments
		Pattern comments = Pattern.compile("//.*?$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = comments.matcher(fileContent);
		fileContent = matcher.replaceAll("");
		// read lines
		BufferedReader br = new BufferedReader(new StringReader(fileContent));
		String line;
		for(;;) {
			line = br.readLine();
			if(line == null)
				break;
			line = line.trim();
			if(line.length() == 0)
				continue;
			if(line.endsWith("."))
				addFormula(FormulaParser.parse(line.substring(0, line.length()-1)));
			else
				System.err.println("Warning: Line without terminating period ignored: " + line);
		}		
	}
	
	public void addFormula(Formula f) {
		this.formulas.add(f);
	}
	
	public Vector<Formula> getFormulas() {
		return formulas;
	}

	/**
	 * grounds this knowledge base (using a set of entities and the corresponding set of ground atoms)
	 * @param db
	 * @param worldVars the set of ground atoms
	 * @param simplify whether to use the evidence in the database to simplify ground formulas
	 * @return
	 * @throws Exception
	 */
	public KnowledgeBase ground(Database db, WorldVariables worldVars, FormulaSimplification simplify) throws Exception {
		KnowledgeBase ret = new KnowledgeBase();
		Integer formulaID = 0;
		for(Formula f : formulas) {
			int i = ret.formulas.size();
			f.addAllGroundingsTo(ret.formulas, db, worldVars, simplify);
			for(; i < ret.formulas.size(); i++) {
				ret.templateIDs.put(ret.formulas.get(i), formulaID);
			}
			formulaID++;
		}
		return ret;
	}

	public Iterator<Formula> iterator() {
		return formulas.iterator();
	}
	
	public int size() {
		return formulas.size();
	}
	
	public Integer getTemplateID(Formula f) {
		return templateIDs.get(f);
	}
}