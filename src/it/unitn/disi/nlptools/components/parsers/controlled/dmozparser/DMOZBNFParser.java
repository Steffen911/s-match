/* Generated By:JavaCC: Do not edit this line. DMOZBNFParser.java */
package it.unitn.disi.nlptools.components.parsers.controlled.dmozparser;

import it.unitn.disi.nlptools.NLPToolsConstants;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.pipelines.PipelineComponent;
import it.unitn.disi.nlptools.pipelines.IPipelineComponent;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.StringReader;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Label parsing using DMOZ BNF created by Ju Qi and implemented in JavaCC. Parses pretty much nothing but
 * very few DMoz-like label without any proper names. Outputs a formula pattern.
 *
 * @author Ju Qi
 */
public class DMOZBNFParser extends PipelineComponent implements IPipelineComponent, DMOZBNFParserConstants {

    private static final Logger log = Logger.getLogger(DMOZBNFParser.class);

    private DMOZBNFParser parser = null;

    //pattern to replace ", and" sequences. first comma is removed
    //regexp needed to preserve token indexes
    private final static Pattern ccCommaCCAnd = Pattern.compile("CC_,(\u005c\u005c|\u005c\u005cd+) CC_and(\u005c\u005c|\u005c\u005cd+)");

    public DMOZBNFParser() {
        parser = new DMOZBNFParser(new StringReader(""));
    }

    public void process(ILabel label) throws PipelineComponentException {
        String initialPattern = preparePattern(label);
        String inputPattern = processCCs(initialPattern);
        parser.ReInit(new StringReader(inputPattern));
        try {
            label.setFormula(parser.NL_Label());
        } catch (ParseException e) {
            if (log.isEnabledFor(Level.ERROR)) {
                log.error("Cannot parse the label (" + label.getText() + ") with pattern (" + initialPattern + "): " + e.getMessage(), e);
            }
            throw new PipelineComponentException(e.getMessage(), e);
        } catch (TokenMgrError e) {
            if (log.isEnabledFor(Level.ERROR)) {
                log.error("Cannot parse the label (" + label.getText() + ") with pattern (" + initialPattern + "): " + e.getMessage());
            }
            throw new PipelineComponentException(e.getMessage(), e);
        }
    }

    private String processCCs(String inputPattern) {
        String processedPattern = "";

        //used to split into set of formulas. by OR?
        //boolean or = false;

        //contains to pieces of formula - inside the brackets piece and outside the bracket
        String[] patternPieces = new String[2];

        //replace ", and" with "and" preserving token index
        if (ccCommaCCAnd.matcher(inputPattern).find()) {
            inputPattern = ccCommaCCAnd.matcher(inputPattern).replaceAll("CC_and$2");
        }

        //coordination disambiguation. too simple. and it should be supplied. by whom?
        if (inputPattern.contains("(") && inputPattern.contains(")")) {
            int index1 = inputPattern.indexOf("(");
            int index2 = inputPattern.indexOf(")");
            if (index1 == 0) {
                patternPieces[0] = inputPattern.substring(index1 + 1, index2);
                patternPieces[1] = inputPattern.substring(index2 + 1);
                if (patternPieces[0].contains("CC_and")) {
                    int ccAndIdx = patternPieces[0].indexOf("CC_and");
                    String before = patternPieces[0].substring(0, ccAndIdx);
                    int ccAndEndIdx = patternPieces[0].indexOf(" ", ccAndIdx);
                    String after = patternPieces[0].substring(ccAndEndIdx);
                    int ccPostFixIdx = patternPieces[0].indexOf("|", ccAndIdx);
                    String ccPostFix = patternPieces[0].substring(ccPostFixIdx, ccAndEndIdx); //|token_no
                    processedPattern = before + " " + patternPieces[1] + " " + "CC" + ccPostFix + " " + after + " " + patternPieces[1];
                }
            } else {
                patternPieces[0] = inputPattern.substring(0, index1 - 1);
                patternPieces[1] = inputPattern.substring(index1 + 1, index2);
                if (patternPieces[1].contains("CC_and")) {
                    int ccAndIdx = patternPieces[1].indexOf("CC_and");
                    String before = patternPieces[1].substring(0, ccAndIdx);
                    int ccAndEndIdx = patternPieces[1].indexOf(" ", ccAndIdx);
                    String after = patternPieces[1].substring(ccAndEndIdx);
                    int ccPostFixIdx = patternPieces[1].indexOf("|", ccAndIdx);
                    String ccPostFix = patternPieces[1].substring(ccPostFixIdx, ccAndEndIdx); //|token_no
                    processedPattern = patternPieces[0] + " " + before + "CC" + ccPostFix + " " + patternPieces[0] + " " + after;
                }
            }
        } else {
            String[] tokens = inputPattern.split(" ");
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].startsWith("CC_and") || tokens[i].startsWith("CC_,")) {
                    tokens[i] = "CC" + tokens[i].substring(tokens[i].indexOf("|"));
                }
                if (tokens[i].startsWith("CC_or")) {
                    //or = true;
                    tokens[i] = "CC" + tokens[i].substring(tokens[i].indexOf("|"));
                }
                if (i != 0) {
                    processedPattern = processedPattern + " " + tokens[i];
                } else {
                    processedPattern = tokens[0];
                }
            }
        }

        return processedPattern;
    }

    //prepares initial pattern for parsing, which looks like
    //NN|0 NN|1 CC_and|2 NN|3
    //NN|0 ( NN|1 CC_and|2 NN|3 )
    //( NN|1 CC_and|2 NN|3 ) NN|4
    private String preparePattern(ILabel label) throws PipelineComponentException {
        StringBuilder result = new StringBuilder("");
        List<IToken> nlTokens = label.getTokens();
        for (int i = 0; i < nlTokens.size(); i++) {
            IToken nlToken = nlTokens.get(i);
            String tokenLabel = nlToken.getText();
            String posTag = nlToken.getPOSTag();
            //if there are brackets in the input label (but they should not be there!)
            //we pass them down...
            if ("(".equals(tokenLabel) || ")".equals(tokenLabel)) {
                result.append(tokenLabel);
            } else {
                if (null != posTag && (posTag.length() > 0)) {
                    result.append(posTag);
                    final String lowerCasedToken = tokenLabel.toLowerCase();
                    if (NLPToolsConstants.COORDINATING_CON.equals(posTag) && (",".equals(tokenLabel) || "and".equals(lowerCasedToken) || "or".equals(lowerCasedToken))) {//CC
                        result.append("_").append(lowerCasedToken);
                    }
                    result.append("|").append(Integer.toString(i)).append(" ");
                } else {
                    if (log.isEnabledFor(Level.ERROR)) {
                        log.error("Empty POS tag for token " + tokenLabel);
                    }
                    throw new PipelineComponentException("Empty POS tag for token " + tokenLabel);
                }
            }
        }
        return result.toString();
    }

    private String getTokenIndex(String image) {
        if (image.contains("|")) {
            return image.split("\u005c\u005c|")[1];
        } else {
            return "";
        }
    }

  final public String NL_Label() throws ParseException {
 String label="",b,d,c="";
    c = Phrase();
              label=c;
    label_1:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CC:
      case COMMA:
      case IN:
        ;
        break;
      default:
        jj_la1[0] = jj_gen;
        break label_1;
      }
      b = Conn();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CC:
      case COMMA:
      case IN:
        c = Conn();
        break;
      default:
        jj_la1[1] = jj_gen;
        ;
      }
      d = Phrase();
                                      label=label+" "+b+" "+d;
    }
    jj_consume_token(0);
   {if (true) return label;}
    throw new Error("Missing return statement in function NL_Label");
  }

  final public String Phrase() throws ParseException {
 String a="",b="",c="";
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case JJ:
    case VBN:
      a = Adjectives();
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case NN:
      case NNS:
      case VBG:
        b = NounPhrase();
                                 {c=" & ";}
        break;
      default:
        jj_la1[2] = jj_gen;
        ;
      }
                                               a=a+c+b;
                                                         {if (true) return a;}
      break;
    case NN:
    case NNS:
    case VBG:
      a = NounPhrase();
    {if (true) return a;}
      break;
    default:
      jj_la1[3] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function Phrase");
  }

  final public String Adjectives() throws ParseException {
 String a="",b;
    b = Adjective();
                      a=b;
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case JJ:
      case VBN:
        ;
        break;
      default:
        jj_la1[4] = jj_gen;
        break label_2;
      }
      b = Adjective();
                   a=a+" & "+b;
    }
     {if (true) return a;}
    throw new Error("Missing return statement in function Adjectives");
  }

  final public String NounPhrase() throws ParseException {
 String a="",b;
    b = Noun();
                 a=b;
    label_3:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case NN:
      case NNS:
      case VBG:
        ;
        break;
      default:
        jj_la1[5] = jj_gen;
        break label_3;
      }
      b = Noun();
              a=a+" & "+b;
    }
     {if (true) return a;}
    throw new Error("Missing return statement in function NounPhrase");
  }

  final public String Conn() throws ParseException {
 String a,b;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CC:
    case COMMA:
      a = ConjunctionConn();
                       {if (true) return a;}
      break;
    case IN:
      a = PrepositionConn();
    {if (true) return a;}
      break;
    default:
      jj_la1[6] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function Conn");
  }

  final public String Noun() throws ParseException {
 Token t;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NN:
      t = jj_consume_token(NN);
               {if (true) return getTokenIndex(t.image);}
      break;
    case NNS:
      t = jj_consume_token(NNS);
               {if (true) return getTokenIndex(t.image);}
      break;
    case VBG:
      t = jj_consume_token(VBG);
               {if (true) return getTokenIndex(t.image);}
      break;
    default:
      jj_la1[7] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function Noun");
  }

  final public String Adjective() throws ParseException {
 Token t;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case JJ:
      t = jj_consume_token(JJ);
              {if (true) return getTokenIndex(t.image);}
      break;
    case VBN:
      t = jj_consume_token(VBN);
               {if (true) return getTokenIndex(t.image);}
      break;
    default:
      jj_la1[8] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function Adjective");
  }

  final public String ConjunctionConn() throws ParseException {
 String a;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CC:
      jj_consume_token(CC);
          a=" | ";
                    {if (true) return a;}
      break;
    case COMMA:
      jj_consume_token(COMMA);
             a=" | ";
                       {if (true) return a;}
      break;
    default:
      jj_la1[9] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function ConjunctionConn");
  }

  final public String PrepositionConn() throws ParseException {
 String a;
    jj_consume_token(IN);
        a=" & ";
   {if (true) return a;}
    throw new Error("Missing return statement in function PrepositionConn");
  }

  /** Generated Token Manager. */
  public DMOZBNFParserTokenManager token_source;
  SimpleCharStream jj_input_stream;
  /** Current token. */
  public Token token;
  /** Next token. */
  public Token jj_nt;
  private int jj_ntk;
  private int jj_gen;
  final private int[] jj_la1 = new int[10];
  static private int[] jj_la1_0;
  static {
      jj_la1_init_0();
   }
   private static void jj_la1_init_0() {
      jj_la1_0 = new int[] {0x1c000,0x1c000,0xe00,0x3e00,0x3000,0xe00,0x1c000,0xe00,0x3000,0xc000,};
   }

  /** Constructor with InputStream. */
  public DMOZBNFParser(java.io.InputStream stream) {
     this(stream, null);
  }
  /** Constructor with InputStream and supplied encoding */
  public DMOZBNFParser(java.io.InputStream stream, String encoding) {
    try { jj_input_stream = new SimpleCharStream(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source = new DMOZBNFParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 10; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream) {
     ReInit(stream, null);
  }
  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream, String encoding) {
    try { jj_input_stream.ReInit(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 10; i++) jj_la1[i] = -1;
  }

  /** Constructor. */
  public DMOZBNFParser(java.io.Reader stream) {
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new DMOZBNFParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 10; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 10; i++) jj_la1[i] = -1;
  }

  /** Constructor with generated Token Manager. */
  public DMOZBNFParser(DMOZBNFParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 10; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(DMOZBNFParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 10; i++) jj_la1[i] = -1;
  }

  private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }


/** Get the next Token. */
  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

/** Get the specific Token. */
  final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.List<int[]> jj_expentries = new java.util.ArrayList<int[]>();
  private int[] jj_expentry;
  private int jj_kind = -1;

  /** Generate ParseException. */
  public ParseException generateParseException() {
    jj_expentries.clear();
    boolean[] la1tokens = new boolean[17];
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 10; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 17; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.add(jj_expentry);
      }
    }
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = jj_expentries.get(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  /** Enable tracing. */
  final public void enable_tracing() {
  }

  /** Disable tracing. */
  final public void disable_tracing() {
  }

}
